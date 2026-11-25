package cz.recaption.core

import java.util.Base64

/**
 * Přihlášení účtem PlayStation s pomocí telefonu, bez opisování kódu.
 *
 * Televize na dobu přihlašování vystaví v domácí síti malou stránku; její adresa je ve čtverečku
 * (QR kódu) na obrazovce. Telefon na ní buď vloží adresu, na kterou ho Sony po přihlášení
 * přesměrovala, nebo otevře ReCaption v telefonu, které se přihlásí samo a pošle televizi rovnou
 * číslo účtu.
 *
 * Tady je jen logika bez Androidu a bez sítě: adresy, rozbor požadavku a text stránky.
 */
object PhoneLogin {
    /** Účet: zakódované číslo účtu (base64, 8 bajtů) a jméno, pod kterým ho uživatel pozná. */
    data class Account(val id: String, val name: String)

    /** Kde televize čeká na účet: adresa v domácí síti, port a jednorázový klíč ze čtverečku. */
    data class Target(val host: String, val port: Int, val key: String)

    /** Port, na kterém televize stránku vystavuje (když je obsazený, vezme se jiný volný). */
    const val PORT = 8765

    /** Kolik účtů si telefon pamatuje. */
    const val MAX_ACCOUNTS = 5

    private const val KEY_CHARS = "abcdefghjkmnpqrstuvwxyz23456789"

    /** Jednorázový klíč do adresy stránky – bez něj televize nic nepřijme. */
    fun newKey(random: java.util.Random = java.security.SecureRandom()): String =
        (1..8).map { KEY_CHARS[random.nextInt(KEY_CHARS.length)] }.joinToString("")

    fun validKey(k: String) = k.length in 6..16 && k.all { it in KEY_CHARS }

    /** Adresa stránky, kterou televize vystavuje (do čtverečku). */
    fun pageUrl(t: Target) = "http://${t.host}:${t.port}${path(t.key)}"

    fun path(key: String) = "/p/$key"

    /** Odkaz, kterým stránka v telefonu otevře ReCaption v telefonu (nese i jazyk televize). */
    fun appLink(t: Target, lang: Lang? = null, self: Boolean = false) =
        "recaption://login?h=${t.host}&p=${t.port}&k=${t.key}" + (lang?.let { "&l=" + it.code } ?: "") + (if (self) "&s=1" else "")

    /**
     * Odkaz z vlastní aplikace v tomtéž telefonu (ReCaption jako čtečka, krok přihlášení – [appLink] se `self`):
     * pomocník pak bez uložených účtů rovnou otevře přihlášení Sony a po odeslání účtu se zavře.
     */
    fun selfFromAppLink(link: String?): Boolean =
        parseForm((link ?: "").substringAfter('?', "").substringBefore('#'))["s"] == "1"

    /**
     * Totéž jako [appLink], ale pro tlačítko na stránce v prohlížeči Androidu (od 0.97.57): aplikace v telefonu je →
     * otevře se pomocník (přihlásí se sám, nic se nekopíruje); není → prohlížeč rovnou stáhne [apkUrl].
     */
    fun appIntentLink(t: Target, lang: Lang?, apkUrl: String): String =
        "intent://login?h=${t.host}&p=${t.port}&k=${t.key}" + (lang?.let { "&l=" + it.code } ?: "") +
            "#Intent;scheme=recaption;package=$PACKAGE;S.browser_fallback_url=" + java.net.URLEncoder.encode(apkUrl, "UTF-8") + ";end"

    const val PACKAGE = "cz.recaption"

    /** Jazyk televize z odkazu – telefon ho převezme, dokud si uživatel nevybral vlastní. */
    fun langFromAppLink(link: String?): Lang? {
        val code = parseForm((link ?: "").substringAfter('?', "").substringBefore('#'))["l"] ?: return null
        return Lang.ui(code)
    }

    /**
     * Rozebere odkaz pro ReCaption v telefonu. Přijme jen adresu v domácí síti – účet se nikdy
     * neposílá nikam do internetu, i kdyby odkaz podstrčila cizí stránka.
     */
    fun parseAppLink(link: String?): Target? {
        val s = link?.trim() ?: return null
        if (!s.startsWith("recaption://login?")) return null
        val q = parseForm(s.substringAfter('?').substringBefore('#'))
        val host = q["h"] ?: return null
        val port = q["p"]?.toIntOrNull() ?: return null
        val key = q["k"] ?: return null
        if (!isHomeAddress(host) || port !in 1..65535 || !validKey(key)) return null
        return Target(host, port, key)
    }

    /** Adresa IPv4 z rozsahů domácích sítí (10.x, 172.16–31.x, 192.168.x, 169.254.x). */
    fun isHomeAddress(host: String): Boolean {
        val p = host.split('.')
        if (p.size != 4 || p.any { it.isEmpty() || it.length > 3 || !it.all(Char::isDigit) }) return false
        val n = p.map { it.toInt() }
        if (n.any { it > 255 }) return false
        return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) || (n[0] == 192 && n[1] == 168) || (n[0] == 169 && n[1] == 254)
    }

    // ---------------- požadavek z telefonu ----------------

    /** První řádek požadavku → způsob (GET/POST) a cesta bez dotazu; null = nesmysl. */
    fun requestLine(line: String): Pair<String, String>? {
        val p = line.trim().split(' ')
        if (p.size != 3 || !p[2].startsWith("HTTP/")) return null
        val method = p[0].uppercase()
        if (method.isEmpty() || !method.all { it in 'A'..'Z' }) return null
        return method to p[1].substringBefore('?').substringBefore('#')
    }

    /** Délka těla požadavku podle hlaviček (0, když chybí). */
    fun contentLength(headers: List<String>): Int =
        headers.firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    /** Údaje z formuláře (a=1&b=2) → mapa; nečitelné kousky se přeskočí. */
    fun parseForm(body: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (part in body.split('&')) {
            if (part.isEmpty()) continue
            try {
                val k = java.net.URLDecoder.decode(part.substringBefore('='), "UTF-8")
                val v = java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
                if (k.isNotEmpty()) out[k] = v
            } catch (_: Exception) {}
        }
        return out
    }

    /** Údaje pro odeslání formulářem. */
    fun form(values: Map<String, String>): String =
        values.entries.joinToString("&") { java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8") }

    /** Odpověď telefonu (stránka nebo krátký text) i s hlavičkami. */
    fun response(status: Int, body: String, html: Boolean = true): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error" }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: " + (if (html) "text/html" else "text/plain") + "; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.US_ASCII) + bytes
    }

    // ---------------- účty ----------------

    /** Zkontroluje číslo účtu, které poslal telefon, a upraví jméno; null = není to číslo účtu. */
    fun cleanAccount(id: String?, name: String?): Account? {
        val bytes = Ps5.parseAccountId(id ?: return null) ?: return null
        return Account(Ps5.accountIdToBase64(bytes), cleanName(name))
    }

    fun cleanName(name: String?): String =
        (name ?: "").filter { !it.isISOControl() && it != '|' }.trim().take(40)

    /** Jméno účtu (Online ID) z odpovědi Sony o účtu; null, když ho odpověď neobsahuje. */
    fun nameFromUserInfo(json: String): String? =
        Regex("\"online_id\"\\s*:\\s*\"([^\"\\\\]{1,40})\"").find(json)?.groupValues?.get(1)?.let { cleanName(it) }?.takeIf { it.isNotEmpty() }

    /** Uložené účty v telefonu: řádek na účet, „číslo|jméno“ (jméno zakódované, ať nevadí zvláštní znaky). */
    fun serializeAccounts(list: List<Account>): String =
        list.joinToString("\n") { it.id + "|" + Base64.getEncoder().encodeToString(it.name.toByteArray(Charsets.UTF_8)) }

    fun parseAccounts(s: String?): List<Account> {
        val out = ArrayList<Account>()
        for (line in (s ?: "").split('\n')) {
            val id = line.substringBefore('|').trim()
            if (id.isEmpty()) continue
            val name = try { String(Base64.getDecoder().decode(line.substringAfter('|', "")), Charsets.UTF_8) } catch (_: IllegalArgumentException) { "" }
            val a = cleanAccount(id, name) ?: continue
            if (out.none { it.id == a.id }) out.add(a)
        }
        return out.take(MAX_ACCOUNTS)
    }

    /** Přidá účet na začátek seznamu (stejný účet se nahradí, nejstarší nad limit vypadnou). */
    fun withAccount(list: List<Account>, a: Account): List<Account> =
        (listOf(a) + list.filter { it.id != a.id }).take(MAX_ACCOUNTS)

    fun withoutAccount(list: List<Account>, id: String): List<Account> = list.filter { it.id != id }

    // ---------------- stránka ----------------

    /**
     * Chování stránky v prohlížeči telefonu:
     *  - ReCaption do telefonu je jen pro Android, jinde se ta nabídka schová; na Androidu je naopak první
     *    a přihlášení v prohlížeči (s kopírováním adresy) je sbalené jako záloha (od 0.97.57);
     *  - přihlášení Sony otevře stránka sama na nové kartě, aby ji po úspěchu mohla sama zavřít
     *    (když to prohlížeč nedovolí, zůstane obyčejný odkaz);
     *  - po vložení adresy se formulář odešle sám, na pozadí – výsledek se ukáže na téže kartě;
     *    kartu Sony zavře jen po úspěchu (značka úspěchu je ve skriptu schválně rozdělená na dva
     *    kusy – stránka s chybou obsahuje skript taky a celou značku by v něm našla);
     *  - po návratu z karty Sony je políčko rovnou připravené na vložení.
     * Adresu z karty Sony si stránka sama přečíst nemůže (cizí stránka) a schránku prohlížeč
     * stránce z domácí sítě bez zabezpečeného spojení nevydá – zkopírovat a vložit musí člověk.
     */
    private const val SCRIPT = "<script>(function(){" +
        "var a=document.getElementById('app'),wb=document.getElementById('web');" +
        "if(!/Android/i.test(navigator.userAgent)){if(a)a.style.display='none'}else if(a&&wb){wb.open=false}" +
        "var u=document.getElementById('u'),f=u.form,l=document.getElementById('l'),b=f.querySelector('button'),busy=false;" +
        "l.addEventListener('click',function(e){var w=null;try{w=window.open(l.href,'_blank')}catch(x){}" +
        "if(w){window.sonyTab=w;e.preventDefault()}});" +
        "function send(){if(busy)return;busy=true;b.style.opacity='.5';var x=new XMLHttpRequest();" +
        "x.open('POST',f.getAttribute('action'));x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');" +
        "x.onload=function(){if(x.responseText.indexOf('class=\"o'+'k\"')>=0){try{if(window.sonyTab)window.sonyTab.close()}catch(e){}}" +
        "document.open();document.write(x.responseText);document.close()};" +
        "x.onerror=function(){f.submit()};x.send('url='+encodeURIComponent(u.value))}" +
        "f.addEventListener('submit',function(e){e.preventDefault();send()});" +
        "u.addEventListener('paste',function(){setTimeout(function(){if(u.value.indexOf('code=')>=0)send()},80)});" +
        "document.addEventListener('visibilitychange',function(){if(!document.hidden&&window.sonyTab){" +
        "try{u.focus();u.scrollIntoView({block:'center'})}catch(e){}}});" +
        "})();</script>"

    /** Co stránka ukazuje: postup, postup s chybou, hotovo, nebo že odkaz už neplatí. */
    enum class Page { ASK, BAD, DONE, GONE }

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun para(s: String): String = esc(s).replace("\n\n", "</p><p>").replace("\n", "<br>")

    /**
     * Stránka pro telefon v jazyce aplikace. [key] je jednorázový klíč (kam se formulář odešle),
     * [target] adresa televize tak, jak se k ní telefon dostal (pro odkaz do ReCaptionu v telefonu;
     * null = odkaz se nenabídne), [loginUrl] přihlášení Sony, [apkUrl] soubor aplikace do telefonu.
     */
    fun page(lang: Lang, state: Page, key: String, target: Target?, loginUrl: String, apkUrl: String): String {
        fun t(k: String) = I18n.t(lang, k)
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"").append(lang.code).append("\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>ReCaption</title><style>")
            .append("body{margin:0;background:#0e1014;color:#f2f2f2;font:17px/1.45 system-ui,sans-serif}")
            .append("main{max-width:34em;margin:0 auto;padding:18px 16px 44px}")
            .append("h1{font-size:24px;margin:6px 0 4px}h2{font-size:15px;color:#f2c14e;margin:26px 0 8px}")
            .append("p{margin:8px 0}.card{background:#1b1f27;border-radius:14px;padding:14px 16px;margin:10px 0}")
            .append(".note{color:#f2c14e;font-size:15px}.muted{color:#9aa4ae;font-size:15px}")
            .append(".ok{color:#4ade80;font-size:20px;font-weight:600}.bad{color:#ef4444;font-weight:600}")
            .append("a.btn,button{display:block;width:100%;box-sizing:border-box;text-align:center;background:#f2c14e;color:#1a1405;")
            .append("font:600 18px system-ui,sans-serif;border:0;border-radius:12px;padding:14px;margin:12px 0 4px;text-decoration:none}")
            .append("a.btn.plain{background:#2a303b;color:#f2f2f2}")
            .append("summary{cursor:pointer;margin:26px 0 8px;color:#f2c14e}")
            .append("input{width:100%;box-sizing:border-box;font-size:16px;padding:13px;border-radius:12px;border:2px solid #3a4250;")
            .append("background:#0e1014;color:#f2f2f2;margin-top:6px}")
            .append("</style></head><body><main><h1>ReCaption</h1>")
        when (state) {
            Page.DONE -> sb.append("<p class=\"ok\">").append(para(t("wp_ok"))).append("</p>")
            Page.GONE -> sb.append("<p class=\"bad\">").append(para(t("wp_gone"))).append("</p>")
            Page.ASK, Page.BAD -> {
                if (state == Page.BAD) sb.append("<p class=\"bad\">").append(para(t("wp_bad"))).append("</p>")
                sb.append("<div class=\"card note\"><p>").append(para(t("acc2"))).append("</p></div>")
                // Android: nejdřív ReCaption v telefonu (bez kopírování); skript ho jinde schová
                if (target != null) {
                    sb.append("<div id=\"app\"><h2>").append(esc(t("wp_easy_h"))).append("</h2><div class=\"card\">")
                        .append("<p>").append(para(t("wp_app_text"))).append("</p>")
                        .append("<a class=\"btn\" href=\"").append(esc(appIntentLink(target, lang, apkUrl))).append("\">").append(esc(t("wp_app_btn"))).append("</a>")
                        .append("<p class=\"muted\">").append(para(t("wp_app_inst"))).append("</p>")
                        .append("<a class=\"btn plain\" href=\"").append(esc(apkUrl)).append("\">").append(esc(t("wp_app_get"))).append("</a>")
                        .append("</div></div>")
                }
                // přihlášení v prohlížeči (iPhone, nebo bez aplikace): rozbalené; na Androidu s aplikací ho skript sbalí
                sb.append("<details id=\"web\" open><summary><h2 style=\"display:inline\">").append(esc(t("wp_web_h"))).append("</h2></summary><div class=\"card\">")
                    .append("<p>").append(para(t("wp_1"))).append("</p>")
                    .append("<a id=\"l\" class=\"btn\" target=\"_blank\" rel=\"noopener\" href=\"").append(esc(loginUrl)).append("\">").append(esc(t("wp_login_btn"))).append("</a>")
                    .append("<p>").append(para(t("wp_2"))).append("</p>")
                    .append("<form method=\"post\" action=\"").append(esc(path(key))).append("\">")
                    .append("<p>").append(para(t("wp_3"))).append("</p>")
                    .append("<input name=\"url\" id=\"u\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\" placeholder=\"https://…/redirect?code=…\">")
                    .append("<button type=\"submit\">").append(esc(t("wp_send"))).append("</button></form></div></details>")
                sb.append(SCRIPT)
            }
        }
        sb.append("</main></body></html>")
        return sb.toString()
    }
}
