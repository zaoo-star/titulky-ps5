package cz.recaption.core

/**
 * Stránka „Nastavení TV z telefonu“ (od 0.97.63, přání majitele: nejhorší byly účty PlayStation a adresa „redirect“).
 * Vystavuje ji Android TV ([cz.recaption.RemoteHost], stálý klíč, jeden čtvereček v průvodci i v ⚙ → Obecné) a otevře
 * se v prohlížeči telefonu – iPhonu i Androidu. Všechno, co se jinak píše ovladačem, jde na jedné stránce:
 *  1. 🔵 účet pro ReCaption – Android: ReCaption v telefonu (nic se nekopíruje, [PhoneLogin.appIntentLink]),
 *     jinak přihlášení Sony v prohlížeči s obrázkem, kde adresu „redirect“ zkopírovat ([OP_ACCOUNT]),
 *  2. PS5 pod 🔵 účtem – osmimístné číslo se opíše v telefonu a TV páruje sama ([OP_CODE]); dřív se kvůli
 *     němu na TV přepínalo mezi vstupem PS5 a aplikací,
 *  3. DeepL (nepovinné) – klíč ([RemoteSetup.OP_DEEPL]).
 * Stránka se každé 2 s ptá TV na stav ([OP_INFO]) a odškrtává kroky; totéž odškrtává TV.
 * Starý zápis JS (ES5) kvůli starším telefonům. Logika bez Androidu (testy `SetupPageTest`).
 */
object SetupPage {
    const val OP_INFO = "info"        // stav pro stránku (JSON [status])
    const val OP_ACCOUNT = "ucet"     // adresa „redirect“ z přihlášení Sony (pole `url`)
    const val OP_CODE = "kod"         // osmimístné číslo z PS5 (pole `pin`)

    /** Jak je na tom spárování, které TV spustila podle čísla z telefonu. */
    enum class Pair { IDLE, BUSY, OK, FAIL }

    data class Status(
        /** Jméno 🔵 účtu pro ReCaption; prázdné = účet ověřený bez jména (starší), null = žádný. */
        val account: String?,
        val pair: Pair,
        /** Jméno spárované PS5 (PS5-151), když je spárovaná. */
        val console: String?,
        val deepl: Boolean,
        /** Spárování nevyšlo, protože je v síti víc PS5 (vybrat v TV). */
        val multiPs5: Boolean = false,
    )

    /** Stav jako JSON pro stránku. */
    fun status(s: Status, web: String? = null): String = statusCore(s).let { if (web == null) it else it.dropLast(1) + ",\"web\":" + web + "}" }

    private fun statusCore(s: Status): String =
        "{\"acc\":" + (s.account?.let { RemoteSetup.json(it) } ?: "null") +
            ",\"pair\":\"" + s.pair.name.lowercase() + "\"" +
            ",\"ps5\":" + (s.console?.let { RemoteSetup.json(it) } ?: "null") +
            ",\"deepl\":" + s.deepl + (if (s.multiPs5) ",\"multi\":true" else "") + "}"

    /**
     * Odkaz `recaption://…` jako odkaz pro prohlížeč Androidu: ReCaption v telefonu je → otevře se, není → stáhne se
     * [apkUrl]. (Pro tlačítko „Nastavit vše v aplikaci“ a „📷 Načíst kamerou“.)
     */
    fun intentLink(recaptionLink: String, apkUrl: String): String {
        val rest = recaptionLink.removePrefix("recaption://")
        return "intent://" + rest + "#Intent;scheme=recaption;package=" + PhoneLogin.PACKAGE +
            ";S.browser_fallback_url=" + java.net.URLEncoder.encode(apkUrl, "UTF-8") + ";end"
    }

    /** Odkaz pro čtečku kódu z PS5 v ReCaptionu v telefonu: přečte osmimístné číslo kamerou a pošle ho téhle TV. */
    fun scanLink(t: PhoneLogin.Target): String = "recaption://kod?h=${t.host}&p=${t.port}&k=${t.key}"

    /** Odkaz čtečky → kam poslat přečtené číslo. Jen adresa v domácí síti (nikdy do internetu). */
    fun parseScanLink(link: String?): PhoneLogin.Target? {
        val s = link?.trim() ?: return null
        if (!s.startsWith("recaption://kod?")) return null
        val q = PhoneLogin.parseForm(s.substringAfter('?').substringBefore('#'))
        val host = q["h"] ?: return null
        val port = q["p"]?.toIntOrNull() ?: return null
        val key = q["k"] ?: return null
        if (!PhoneLogin.isHomeAddress(host) || port !in 1..65535 || !PhoneLogin.validKey(key)) return null
        return PhoneLogin.Target(host, port, key)
    }

    /** Je to osmimístné číslo z PS5 (mezery a pomlčky se ignorují)? */
    fun pin(input: String?): Int? = Ps5.parsePin((input ?: "").replace(" ", "").replace("-", ""))

    /**
     * Stránka. [target] = adresa TV, jak ji telefon vidí (pro tlačítko do ReCaptionu v telefonu; null = bez něj),
     * [apkUrl] ReCaption do telefonu, [loginUrl] přihlášení Sony, [signupUrl]/[keysUrl] DeepL.
     */
    fun page(lang: Lang, tvName: String, target: PhoneLogin.Target?, apkUrl: String, loginUrl: String,
             signupUrl: String, keysUrl: String, shortcutUrl: String? = null, appModeLink: String? = null,
             web: WebSettings.Options? = null, xbox: Boolean = false): String {
        fun t(k: String) = I18n.t(lang, k)
        fun e(k: String) = PhoneLogin.esc(t(k))
        fun para(k: String) = PhoneLogin.esc(t(k)).replace("\n", "<br>")
        // texty pro skript (se zástupným %s pro jméno účtu / konzole)
        val texts = listOf("sp_acc_ok", "sp_acc_none", "sp_ps5_h", "sp_ps5_how", "sp_pair_btn", "sp_pair_busy", "sp_pair_ok",
            "sp_pair_fail", "sp_need_acc", "sp_bad_url", "sp_bad_pin", "sp_deepl_ok", "sp_deepl_bad", "sp_sent", "sp_change", "sp_repair", "mp_multi",
            "l_lang", "l_src_lang", "gp_profile", "l_opacity", "l_size", "l_pos", "l_original", "gp_prompts", "obj_row", "gp_menu",
            "dl_toggle", "on", "off")
            .joinToString(",", "{", "}") { RemoteSetup.json(it) + ":" + RemoteSetup.json(t(it)) }
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"").append(lang.code).append("\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Titulky PS5</title><style>")
            .append("body{margin:0;background:#0e1014;color:#f2f2f2;font:17px/1.45 system-ui,sans-serif}")
            .append("main{max-width:34em;margin:0 auto;padding:18px 16px 44px}h1{font-size:23px;margin:6px 0 2px}")
            .append("h2{font-size:18px;margin:0 0 6px}p{margin:8px 0}.muted{color:#9aa4ae;font-size:15px}")
            .append(".card{background:#1b1f27;border-radius:14px;padding:14px 16px;margin:12px 0}")
            .append(".legend{border-left:4px solid #4a90e2;white-space:pre-line}")
            .append(".st{font-weight:600;margin:4px 0 8px}.ok{color:#4ade80}.wait{color:#9aa4ae}.bad{color:#ef4444}")
            .append("a.btn,button{display:block;width:100%;box-sizing:border-box;text-align:center;background:#f2c14e;color:#1a1405;")
            .append("font:600 18px system-ui,sans-serif;border:0;border-radius:12px;padding:14px;margin:10px 0 4px;text-decoration:none}")
            .append("a.btn.plain,button.plain{background:#2a303b;color:#f2f2f2}")
            .append("input{width:100%;box-sizing:border-box;font-size:17px;padding:13px;border-radius:12px;border:2px solid #3a4250;")
            .append("background:#0e1014;color:#f2f2f2;margin-top:6px}summary{cursor:pointer;color:#f2c14e;margin:10px 0}")
            .append("#done{display:none;border-left:4px solid #4ade80}#off{display:none}#stav,#c4{display:none}")
            .append(".dot{display:inline-block;width:11px;height:11px;border-radius:50%;margin-right:10px}")
            .append("label.row{display:block;margin:10px 0 0}select{width:100%;box-sizing:border-box;font-size:17px;padding:11px;")
            .append("border-radius:12px;border:2px solid #3a4250;background:#0e1014;color:#f2f2f2;margin-top:4px}")
            // slabý běžící pruh pod stavem, na který se čeká (jako v aplikaci: tenký, poloprůhledný, barva akcentu)
            .append(".wb{display:none;position:relative;overflow:hidden;height:4px;max-width:20em;border-radius:2px;margin:-2px 0 10px;")
            .append("opacity:.45;background:rgba(242,193,78,.3)}.wb i{position:absolute;left:0;top:0;width:40%;height:100%;border-radius:2px;")
            .append("background:#f2c14e;-webkit-animation:wb 1.4s linear infinite;animation:wb 1.4s linear infinite}")
            .append("@-webkit-keyframes wb{from{-webkit-transform:translateX(-100%)}to{-webkit-transform:translateX(250%)}}")
            .append("@keyframes wb{from{transform:translateX(-100%)}to{transform:translateX(250%)}}")
            // Xbox (od šablony průvodce 24. 11. 2026): účet PlayStation, spárování PS5 a aplikace v telefonu se tu nenastavují – jen DeepL a titulky
            .append(if (xbox) "#appmode,#c1,#c2,.legend{display:none!important}" else "")
            .append("</style></head><body><main>")
            .append("<h1>").append(e("rs_page_title")).append("</h1>")
            .append("<p class=\"muted\">").append(PhoneLogin.esc(tvName)).append("</p>")
            .append("<p id=\"off\" class=\"card bad\">").append(e("sp_off")).append("</p>")
            // stav TV jako na Domů (webová obdoba aplikace, od 0.97.67) – plní skript z op=info
            .append("<div class=\"card\" id=\"stav\"><h2>").append(e("web_status_h")).append("</h2><div id=\"rows\"></div></div>")
            .append("<div class=\"card legend\">").append(e("acc_legend")).append("</div>")
        // Android: celé nastavení v ReCaptionu v telefonu (vezme si současné nastavení TV, každá změna hned v TV)
        // dvě rovnocenné cesty (od 0.97.66, přání majitele): aplikace (jen Android) / prohlížeč – iPhone a počítač vidí rovnou prohlížeč
        if (appModeLink != null) sb.append("<div class=\"card\" id=\"appmode\"><h2>").append(e("sp_how_h")).append("</h2>")
            .append("<a class=\"btn\" href=\"").append(PhoneLogin.esc(intentLink(appModeLink, apkUrl))).append("\">").append(e("sp_app_mode"))
            .append("</a><p class=\"muted\">").append(e("sp_app_mode_d")).append("</p>")
            .append("<a class=\"btn plain\" href=\"#c1\" id=\"webgo\">").append(e("sp_web_go")).append("</a><p class=\"muted\">")
            .append(e("sp_web_d")).append("</p></div>")
        // kde se co dělá: telefon / PS5 / TV (majitel: „lepší popis, kdy se kde přihlásit“)
        sb.append("<details class=\"card\"><summary>").append(e("sp_where_h")).append("</summary><p style=\"white-space:pre-line\">")
            .append(e("sp_where")).append("</p></details>")

        // ---- 1. 🔵 účet pro ReCaption ----
        sb.append("<div class=\"card\" id=\"c1\"><h2>").append(e("sp_1_h")).append("</h2>")
            .append("<p id=\"accst\" class=\"st wait\">").append(e("sp_acc_none")).append("</p>").append(waitBar("accw"))
            .append("<a href=\"#\" id=\"accx\" class=\"muted\" style=\"display:none\">").append(e("sp_change")).append("</a><div id=\"acc\">")
        if (target != null) {
            // Android: ReCaption v telefonu se přihlásí samo (skript tuhle část jinde schová)
            sb.append("<div id=\"app\"><a class=\"btn\" href=\"").append(PhoneLogin.esc(PhoneLogin.appIntentLink(target, lang, apkUrl)))
                .append("\">").append(e("wp_app_btn")).append("</a>")
                .append("<p class=\"muted\">").append(para("wp_app_inst")).append("</p></div>")
        }
        sb.append("<details id=\"web\" open><summary>").append(e("wp_web_h")).append("</summary>")
            .append("<p>").append(para("wp_1")).append("</p>")
            .append("<a id=\"l\" class=\"btn plain\" target=\"_blank\" rel=\"noopener\" href=\"").append(PhoneLogin.esc(loginUrl)).append("\">")
            .append(e("wp_login_btn")).append("</a>")
            .append("<p>").append(para("sp_copy_how")).append("</p>")
            .append(picture(t("sp_copy_word")))
            .append("<p>").append(para("wp_3")).append("</p>")
            .append("<input id=\"u\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\" placeholder=\"https://…/redirect?code=…\">")
            .append("<button id=\"ub\">").append(e("wp_send")).append("</button><p id=\"um\" class=\"bad\"></p>")
        // iPhone: zkratka „ReCaption“ v Sdílet pošle adresu „redirect“ sem sama (stránka na GitHub Pages si zapamatuje TV)
        if (shortcutUrl != null) sb.append("<a id=\"sc\" class=\"btn plain\" data-base=\"").append(PhoneLogin.esc(shortcutUrl))
            .append("\" href=\"").append(PhoneLogin.esc(shortcutUrl)).append("\">").append(e("sp_shortcut")).append("</a>")
        sb.append("</details></div></div>")

        // ---- 2. PS5 pod 🔵 účtem: číslo opsat sem, TV páruje sama ----
        sb.append("<div class=\"card\" id=\"c2\"><h2 id=\"ps5h\">").append(PhoneLogin.esc(t("sp_ps5_h").replace("%s", "…"))).append("</h2>")
            .append("<p id=\"pairst\" class=\"st wait\"></p>").append(waitBar("pairw"))
            .append("<p id=\"ps5how\">").append(PhoneLogin.esc(t("sp_ps5_how").replace("%s", "…")).replace("\n", "<br>")).append("</p>")
            .append("<a href=\"#\" id=\"pairx\" class=\"muted\" style=\"display:none\">").append(e("sp_repair")).append("</a>")
            .append("<input id=\"pin\" inputmode=\"numeric\" autocomplete=\"off\" maxlength=\"9\" placeholder=\"12345678\">")
            .append("<button id=\"pb\">").append(e("sp_pair_btn")).append("</button>")
        // číslo kamerou: Android čtečkou v ReCaptionu v telefonu (pošle ho rovnou TV), iPhone sám (Skenovat text v políčku)
        if (target != null) sb.append("<a id=\"scan\" class=\"btn plain\" href=\"").append(PhoneLogin.esc(intentLink(scanLink(target), apkUrl)))
            .append("\">").append(e("sp_scan")).append("</a>")
        sb.append("<p id=\"scanios\" class=\"muted\">").append(e("sp_scan_ios")).append("</p>")
            .append("<p id=\"pm\" class=\"bad\"></p></div>")

        // ---- 3. DeepL (nepovinné) ----
        sb.append("<div class=\"card\" id=\"c3\"><h2>").append(e("sp_3_h")).append("</h2>")
            .append("<p id=\"dlst\" class=\"st wait\"></p>").append(waitBar("dlw"))
            .append("<a href=\"#\" id=\"dlx\" class=\"muted\" style=\"display:none\">").append(e("sp_change")).append("</a><div id=\"dl\">")
            .append("<a class=\"btn plain\" target=\"_blank\" rel=\"noopener\" href=\"").append(PhoneLogin.esc(signupUrl)).append("\">").append(e("dl_tv_1")).append("</a>")
            .append("<a class=\"btn plain\" target=\"_blank\" rel=\"noopener\" href=\"").append(PhoneLogin.esc(keysUrl)).append("\">").append(e("dl_tv_2")).append("</a>")
            .append("<input id=\"dk\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\" placeholder=\"xxxxxxxx-xxxx-…\">")
            .append("<button id=\"db\">").append(e("dl_tv_3")).append("</button><p id=\"dm\" class=\"bad\"></p></div></div>")

        // ---- 4. Titulky a hra – hlavní volby jako v aplikaci (webová obdoba, od 0.97.67) ----
        if (web != null) sb.append("<div class=\"card\" id=\"c4\"><h2>").append(e("web_h")).append("</h2><div id=\"set\"></div></div>")
            .append("<script type=\"application/json\" id=\"o\">").append(WebSettings.optionsJson(web).replace("</", "<\\/")).append("</script>")
        sb.append("<div class=\"card\" id=\"done\"><p class=\"st ok\">").append(e("sp_done")).append("</p></div>")
        sb.append("<script type=\"application/json\" id=\"t\">").append(texts.replace("</", "<\\/")).append("</script>")
        // ReCaption na Facebooku (od 1.0.7, přání majitele) – nenápadně úplně dole
        sb.append("<p class=\"muted\" style=\"text-align:center;margin-top:18px\"><a target=\"_blank\" rel=\"noopener\" href=\"").append(HelpLinks.FACEBOOK).append("\">").append(e("fb_row")).append("</a></p>")
        sb.append(SCRIPT).append("</main></body></html>")
        return sb.toString()
    }

    /** Slabý běžící pruh pod stavem (skrytý; ukáže ho skript, dokud se na něco čeká). */
    private fun waitBar(id: String): String = "<div class=\"wb\" id=\"" + id + "\"><i></i></div>"

    /** Obrázek: adresní řádek se stránkou „redirect“, kam klepnout, a bublina „Kopírovat“. */
    private fun picture(copyWord: String): String =
        "<svg viewBox=\"0 0 320 150\" width=\"100%\" role=\"img\" style=\"max-width:420px;display:block;margin:6px auto\">" +
            "<rect x=\"8\" y=\"8\" width=\"304\" height=\"134\" rx=\"16\" fill=\"#1b1f27\" stroke=\"#3a4250\" stroke-width=\"2\"/>" +
            "<rect x=\"22\" y=\"22\" width=\"276\" height=\"32\" rx=\"9\" fill=\"#2a303b\" stroke=\"#f2c14e\" stroke-width=\"3\"/>" +
            "<text x=\"34\" y=\"43\" fill=\"#f2f2f2\" font-size=\"13\" font-family=\"system-ui,sans-serif\">…playstation.net/…/redirect?code=…</text>" +
            "<circle cx=\"262\" cy=\"38\" r=\"13\" fill=\"#f2c14e\" opacity=\".55\"/>" +
            "<text x=\"262\" y=\"43\" fill=\"#1a1405\" font-size=\"14\" font-weight=\"700\" text-anchor=\"middle\" font-family=\"system-ui,sans-serif\">1</text>" +
            "<rect x=\"170\" y=\"64\" width=\"120\" height=\"34\" rx=\"8\" fill=\"#f2f2f2\"/>" +
            "<path d=\"M222 64 l8 -8 l8 8 z\" fill=\"#f2f2f2\"/>" +
            "<text x=\"230\" y=\"86\" fill=\"#1a1405\" font-size=\"15\" font-weight=\"600\" text-anchor=\"middle\" font-family=\"system-ui,sans-serif\">2 · " +
            PhoneLogin.esc(copyWord) + "</text>" +
            "<text x=\"34\" y=\"126\" fill=\"#9aa4ae\" font-size=\"16\" font-family=\"system-ui,sans-serif\">redirect</text></svg>"

    /**
     * Chování stránky: dotaz na stav každé 2 s (odškrtá kroky, sbalí hotové), odeslání adresy hned po vložení,
     * čísla z PS5 po osmi číslicích, klíče DeepL tlačítkem. Přihlášení Sony na nové kartě (po návratu je políčko
     * připravené). Na iPhonu se tlačítko do ReCaptionu v telefonu schová, na Androidu se sbalí přihlášení v prohlížeči.
     */
    private const val SCRIPT = "<script>(function(){" +
        "var P=location.pathname,T={};try{T=JSON.parse(document.getElementById('t').textContent)}catch(e){}" +
        "function $(i){return document.getElementById(i)}" +
        "function post(d,cb){var x=new XMLHttpRequest();x.open('POST',P);x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');" +
        "x.onload=function(){cb(x.status==200?x.responseText:null)};x.onerror=function(){cb(null)};x.send(d)}" +
        "var android=/Android/i.test(navigator.userAgent);" +
        "if(!android){if($('app'))$('app').style.display='none';if($('appmode'))$('appmode').style.display='none';if($('scan'))$('scan').style.display='none'}" +
        "else{if($('app')&&$('web'))$('web').open=false;$('scanios').style.display='none'}" +
        // zkratka pro iPhone: odkaz nese adresu téhle stránky (TV si ji zapamatuje stránka zkratky); na Androidu není potřeba
        "var sc=$('sc');if(sc){if(android)sc.style.display='none';else sc.href=sc.getAttribute('data-base')+'?tv='+encodeURIComponent(location.protocol+'//'+location.host+P)}" +
        // „Změnit“ u hotové části ji znovu otevře (jiný účet, znovu spárovat, jiný klíč DeepL) – do dalšího obnovení
        // „Pokračovat v prohlížeči“ volbu schová (na této kartě) a posune na krok 1
        "if($('webgo'))$('webgo').addEventListener('click',function(e){e.preventDefault();$('appmode').style.display='none';try{$('c1').scrollIntoView()}catch(x){}});" +
        "var acc=null,busy=false,openAcc=false,openPair=false,openDl=false;" +
        // běžící pruh: účet (odesílá se adresa / po otevření přihlášení čeká na účet, nejvýš 10 min), spárování, ověření klíče DeepL
        "var sending=false,waitAcc=0,dlBusy=false;function bar(id,on){var e=$(id);if(e)e.style.display=on?'block':'none'}" +
        "function bars(){bar('accw',sending||(acc===null&&waitAcc&&new Date().getTime()-waitAcc<600000));bar('pairw',busy);bar('dlw',dlBusy)}" +
        "function waitAccount(){waitAcc=new Date().getTime();bars()}" +
        "function link(id,f){$(id).addEventListener('click',function(e){e.preventDefault();f();info()})}" +
        "function st(id,txt,cls){var e=$(id);e.textContent=txt;e.className='st '+cls}" +
        "function info(){post('op=info',function(r){var s=null;try{s=r&&JSON.parse(r)}catch(e){}" +
        "$('off').style.display=s?'none':'block';if(!s)return;acc=s.acc;var name=acc||'?';if(acc!==null)waitAcc=0;" +
        "if(acc!==null){st('accst',T.sp_acc_ok.replace('%s',name),'ok');$('acc').style.display=openAcc?'block':'none';$('accx').style.display=openAcc?'none':'inline'}" +
        "else{st('accst',T.sp_acc_none,'wait');$('acc').style.display='block';$('accx').style.display='none'}" +
        "$('ps5h').textContent=T.sp_ps5_h.replace('%s',name);$('ps5how').innerHTML='';" +
        "var lines=T.sp_ps5_how.replace('%s',name).split('\\n');for(var i=0;i<lines.length;i++){if(i)$('ps5how').appendChild(document.createElement('br'));$('ps5how').appendChild(document.createTextNode(lines[i]))}" +
        "busy=s.pair=='busy';$('pb').textContent=busy?T.sp_pair_busy:T.sp_pair_btn;bars();" +
        "var pinOn=s.pair!='ok'||openPair;$('pin').style.display=pinOn?'block':'none';$('pb').style.display=pinOn?'block':'none';" +
        "if($('scan'))$('scan').style.display=(pinOn&&android)?'block':'none';$('scanios').style.display=(pinOn&&!android)?'block':'none';" +
        "$('pairx').style.display=(s.pair=='ok'&&!openPair)?'inline':'none';" +
        "if(s.pair=='ok'){st('pairst',T.sp_pair_ok.replace('%s',s.ps5||'PS5'),'ok')}" +
        "else{" +
        "if(s.pair=='fail')st('pairst',s.multi?T.mp_multi:T.sp_pair_fail.replace('%s',name),'bad');else if(acc===null)st('pairst',T.sp_need_acc,'wait');else st('pairst','','wait')}" +
        "if(s.deepl){st('dlst',T.sp_deepl_ok,'ok');$('dl').style.display=openDl?'block':'none';$('dlx').style.display=openDl?'none':'inline'}" +
        "else{st('dlst','','wait');$('dl').style.display='block';$('dlx').style.display='none'}" +
        "$('done').style.display=(acc!==null&&s.pair=='ok')?'block':'none';if(s.web){rows(s.web.rows);settings(s.web)}})}" +
        // přehled stavu: tečka (zelená / oranžová / šedá) a text
        "function rows(r){var b=$('rows');b.innerHTML='';for(var i=0;i<r.length;i++){var p=document.createElement('p');" +
        "var d=document.createElement('span');d.className='dot';d.style.background=r[i][0]=='ok'?'#4ade80':r[i][0]=='pozor'?'#f2a03c':'#9aa4ae';" +
        "p.appendChild(d);p.appendChild(document.createTextNode(r[i][1]));b.appendChild(p)}$('stav').style.display=r.length?'block':'none'}" +
        // volby: výběrové seznamy; právě upravovaný seznam se nepřepisuje
        "var O=null;try{O=JSON.parse($('o').textContent)}catch(e){}" +
        "function settings(w){var c=$('set');if(!O||!c||c.contains(document.activeElement))return;c.innerHTML='';$('c4').style.display='block';" +
        "function row(label,opts,cur,klic){var l=document.createElement('label');l.className='row';l.appendChild(document.createTextNode(label));" +
        "var s=document.createElement('select'),hit=0;for(var i=0;i<opts.length;i++){var o=document.createElement('option');o.value=opts[i][0];" +
        "o.textContent=opts[i][1];if(String(opts[i][0])==String(cur)){o.selected=true;hit=1}s.appendChild(o)}" +
        // hodnota z TV, kterou stránka nenabízí (vlastní výška, starší průhlednost): ukázat „–“, nepřepsat ji jinou
        "if(!hit){var x=document.createElement('option');x.value='';x.textContent='–';x.selected=true;x.disabled=true;s.insertBefore(x,s.firstChild)}" +
        "s.addEventListener('change',function(){post('op=web&klic='+klic+'&hodnota='+encodeURIComponent(s.value),function(){s.blur();info()})});" +
        "l.appendChild(s);c.appendChild(l)}" +
        "var yn=[['1',T.on],['0',T.off]],g=null,games=[],sizes=[],opac=[];" +
        "for(var i=0;i<O.games.length;i++){games.push([O.games[i][0],O.games[i][1]]);if(O.games[i][0]==w.game)g=O.games[i]}" +
        "for(i=0;i<O.sizes.length;i++)sizes.push([O.sizes[i],O.sizes[i]?String(O.sizes[i]):O.sizeGame]);" +
        "for(i=0;i<O.opac.length;i++)opac.push([O.opac[i],O.opac[i]+' %']);" +
        "row(T.l_lang,O.langs,w.lang,'lang');row(T.l_src_lang,O.srcs,w.src,'src');row(T.gp_profile,games,w.game,'game');" +
        "row(T.l_size,sizes,w.velikost,'velikost');row(T.l_opacity,opac,w.pruhlednost,'pruhlednost');row(T.l_pos,O.pos,w.poloha,'poloha');" +
        "row(T.l_original,yn,w.original?'1':'0','original');" +
        "if(g&&g[2]){row(T.gp_prompts,yn,w.pokyny?'1':'0','pokyny');row(T.obj_row,yn,w.ukol?'1':'0','ukol')}" +
        "if(g&&g[3])row(T.gp_menu,yn,w.menu?'1':'0','menu');if(w.deeplKey)row(T.dl_toggle,yn,w.deepl?'1':'0','deepl')}" +
        "function sendUrl(){var v=$('u').value;if(v.indexOf('code=')<0){$('um').textContent=T.sp_bad_url;return}" +
        "$('um').textContent=T.sp_sent;sending=true;bars();post('op=ucet&url='+encodeURIComponent(v),function(r){sending=false;bars();" +
        "if(r=='OK'){openAcc=false;$('um').textContent='';$('u').value='';try{if(window.sonyTab)window.sonyTab.close()}catch(e){}}else $('um').textContent=T.sp_bad_url;info()})}" +
        "$('ub').addEventListener('click',sendUrl);" +
        "$('u').addEventListener('paste',function(){setTimeout(function(){if($('u').value.indexOf('code=')>=0)sendUrl()},80)});" +
        "var l=$('l');l.addEventListener('click',function(e){var w=null;try{w=window.open(l.href,'_blank')}catch(x){}waitAccount();if(w){window.sonyTab=w;e.preventDefault()}});" +
        "if($('app')){var al=$('app').getElementsByTagName('a');if(al.length)al[0].addEventListener('click',waitAccount)}" +
        "document.addEventListener('visibilitychange',function(){if(!document.hidden){info();if(window.sonyTab&&acc===null){try{$('u').focus()}catch(e){}}}});" +
        "function sendPin(){if(busy)return;var v=$('pin').value.replace(/[^0-9]/g,'');if(v.length!=8){$('pm').textContent=T.sp_bad_pin;return}" +
        "$('pm').textContent='';busy=true;$('pb').textContent=T.sp_pair_busy;bars();post('op=kod&pin='+v,function(r){" +
        "if(r=='NOACC')$('pm').textContent=T.sp_need_acc;else if(r!='OK')$('pm').textContent=T.sp_bad_pin;else{$('pin').value='';openPair=false}info()})}" +
        "$('pb').addEventListener('click',sendPin);" +
        "link('accx',function(){openAcc=true});link('pairx',function(){openPair=true});link('dlx',function(){openDl=true});" +
        "$('pin').addEventListener('input',function(){if($('pin').value.replace(/[^0-9]/g,'').length==8)sendPin()});" +
        "$('db').addEventListener('click',function(){var v=$('dk').value.trim();if(!v)return;$('dm').textContent=T.sp_sent;dlBusy=true;bars();" +
        "post('op=deepl&key='+encodeURIComponent(v),function(r){dlBusy=false;bars();$('dm').textContent=r=='OK'?'':T.sp_deepl_bad;if(r=='OK'){$('dk').value='';openDl=false}info()})});" +
        // adresa „redirect“ poslaná zkratkou z iPhonu (?u=…) → rovnou odeslat a adresu stránky uklidit
        "var m=/[?&]u=([^&]+)/.exec(location.search);if(m){try{$('u').value=decodeURIComponent(m[1]);if(history.replaceState)history.replaceState(null,'',P)}catch(e){}" +
        "if($('u').value.indexOf('code=')>=0)sendUrl()}" +
        "info();setInterval(info,2000);" +
        "})();</script>"
}
