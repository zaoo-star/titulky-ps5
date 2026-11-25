package cz.recaption.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Posílání přeložených titulků do televize LG (webOS) nebo Samsung (Tizen) po domácí síti.
 *
 * Televize LG a Samsung si obraz hry samy přečíst neumí, takže tam ReCaption běží jen jako
 * obrazovka (`webos/obchod/app` a `tizen/app` se společným app.js): ukazuje hru ze vstupu HDMI
 * a přes ni pruh. Číst a překládat musí jiné
 * zařízení – toto. Aplikace v televizi si ho v síti najde sama (zkouší port [PORT]) a připojí se;
 * odsud jí pak chodí hotové věty.
 *
 * Spojení je WebSocket (starý prohlížeč televize nic jiného pro trvalé spojení nemá) a obslouží
 * se tu ručně, bez knihoven. Zprávy jsou krátké texty:
 *  - `{"typ":"vitej"}` hned po připojení (podle toho televize pozná ReCaption),
 *  - `{"typ":"ukaz","id":1,"text":"…"}` a `{"typ":"skryj","id":2}`.
 *    Od 0.86 může nést i `"vzhled":{…}` – průhlednost, písmo a polohu pruhu podle Nastavení ([TvLook]).
 *    Od verze s barevnými jmény a volbami může `ukaz` nést navíc `"casti":[…]` – řádky pruhu zvlášť:
 *    `{"jmeno":"Mira","barva":"#F2C14E","text":"…"}` (replika s mluvčím a jeho barvou; barva chybí,
 *    když má uživatel jména bez barvy), `{"volba":true,"text":"…"}` (jedna z voleb odpovědi)
 *    a `{"text":"…"}` (replika bez jména). `text` zůstává celý jako dřív („Jméno: …“, volby s „• “),
 *    takže starší aplikace v televizi, která `casti` nezná, ukáže všechno jako obyčejný text.
 * Televize po připojení pošle `{"typ":"pripojeno","televize":"…","system":"…","hdmi":1}`, od verze 1.1.0
 * navíc `"aplikace":"Titulky PS5","platforma":"webos"|"tizen","verze":"1.1.0"` ([TvAnnounce]). Podle toho
 * průvodce nahráním do televize a stránka Domů poznají, že je aplikace v televizi nainstalovaná
 * a v jaké verzi ([lastSeen]). Od verze 1.2.0 televize posílá i svůj záznam událostí
 * `{"typ":"zaznam","radky":["…"]}` (po připojení a pak po dávkách) – ukládá ho [TvLog].
 * Od 0.89 Android posílá `{"typ":"nastaveni",…}` a televize (od 1.4.0) smí poslat `{"typ":"zmen",…}` –
 * pár voleb vzhledu z ovladače, viz [TvSettings].
 * Ostatní zprávy od televize (`hotovo`) se jen přečtou a zahodí.
 * Bez Androidu, aby šlo vyzkoušet v unit testech.
 */
class TvSender(private val log: (String) -> Unit = {}) {

    /** Volá se po každém ohlášení televize (z vlákna spojení) – např. přepnutí nastavení pro tu televizi. */
    @Volatile var onAnnounce: ((TvAnnounce) -> Unit)? = null

    companion object {
        const val PORT = 8787
        private const val MAX_CLIENTS = 4
        private const val MAX_HEAD = 8 * 1024
        private const val MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        /** Delší zprávy od televize se nečtou (ohlášení má pár set bajtů). */
        private const val MAX_IN = 16 * 1024

        /** Poslední ohlášení aplikace z televize (od kteréhokoli TvSender v procesu) a kdy přišlo. */
        class Seen(val announce: TvAnnounce, val at: Long)
        @Volatile var lastSeen: Seen? = null

        /**
         * Přečte jednu zprávu od televize (od prohlížeče jsou vždy maskované). Vrací text textové
         * zprávy, "" pro jinou (ping, binární…), null = konec spojení nebo nesmyslná délka.
         */
        fun readFrame(input: InputStream): String? {
            val b0 = input.read(); val b1 = input.read()
            if (b0 < 0 || b1 < 0) return null
            val opcode = b0 and 0x0F
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) {
                val hi = input.read(); val lo = input.read()
                if (hi < 0 || lo < 0) return null
                len = ((hi shl 8) or lo).toLong()
            } else if (len == 127L) return null   // takhle dlouhou zprávu televize neposílá
            if (len > MAX_IN) return null
            val mask = if (b1 and 0x80 != 0) ByteArray(4).also { if (!readFully(input, it)) return null } else null
            val data = ByteArray(len.toInt())
            if (!readFully(input, data)) return null
            if (mask != null) for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
            return when (opcode) {
                0x8 -> null
                0x1 -> String(data, Charsets.UTF_8)
                else -> ""
            }
        }

        private fun readFully(input: InputStream, buf: ByteArray): Boolean {
            var got = 0
            while (got < buf.size) { val n = input.read(buf, got, buf.size - got); if (n < 0) return false; got += n }
            return true
        }

        const val HELLO = "{\"typ\":\"vitej\"}"
        fun showMessage(id: Int, text: String, parts: String? = null, look: String? = null) =
            "{\"typ\":\"ukaz\",\"id\":$id,\"text\":${json(text)}" + (if (parts != null) ",\"casti\":$parts" else "") +
                (if (look != null) ",\"vzhled\":$look" else "") + "}"

        /** Řádky pruhu pro pole `casti` (JSON); [colors] = barvy jmen, bez jména v ní se barva nepošle. */
        fun partsJson(segments: List<Segment>, colors: Map<String, Int> = emptyMap()): String =
            segments.joinToString(",", "[", "]") { s ->
                when {
                    s.selected -> "{\"vybrana\":true,\"text\":${json(s.body)}}"
                    s.choice -> "{\"volba\":true,\"text\":${json(s.body)}}"
                    s.prompt -> "{\"pokyn\":true,\"text\":${json(s.body)}}"
                    s.speaker != null -> "{\"jmeno\":${json(s.speaker)}" +
                        (colors[s.speaker]?.let { ",\"barva\":\"${SpeakerColors.hex(it)}\"" } ?: "") +
                        ",\"text\":${json(s.body)}}"
                    else -> "{\"text\":${json(s.body)}}"
                }
            }
        /**
         * Čtvereček „Podpořit ReCaption“ (od ReCaptionu 1.00, aplikace v TV 1.5.39: úvodní obrazovka a vedle nabídky OK) – obrázek PNG
         * jako data: adresa. Skládá ho [cz.recaption.App] jednou při spuštění; každá TV ho dostane po připojení. TV bez spojení
         * s Androidem (testeři obchodu LG) čtvereček nemá. Starší TV zprávu přeskočí.
         */
        @Volatile var support: String? = null
        fun supportMessage(png: String) = "{\"typ\":\"podpora\",\"obrazek\":${json(png)}}"

        fun hideMessage(id: Int) = "{\"typ\":\"skryj\",\"id\":$id}"

        /**
         * Řádek s úkolem pod titulky (od 0.97.43, aplikace v TV 1.5.11) – samostatný pruh, nezávislý na titulku:
         * `{"typ":"ukol","text":…,"misto":[vlevo,nahoře,vpravo,dole],"pismo":px,"pozadi":0–100,"barva":"#RRGGBB"}`,
         * schování `{"typ":"ukol","text":""}`. [misto] = kde byl anglický úkol (zlomky obrazu 0..1). Starší TV zprávu
         * neznají a nic neukážou. [casti] = barevná slova úkolu (pokus, [ObjectiveColors]):
         * `"casti":[{"text":…,"barva":"#RRGGBB"|null}]` na konci zprávy; bez nich je zpráva stejná jako dřív.
         */
        fun objectiveMessage(text: String?, misto: FloatArray? = null, pismo: Int = 0, pozadi: Int = 100, barva: String = "#FFFFFF",
                             casti: List<ObjectivePart>? = null): String {
            if (text.isNullOrBlank() || misto == null || misto.size < 4) return "{\"typ\":\"ukol\",\"text\":\"\"}"
            val m = misto.joinToString(",") { String.format(java.util.Locale.ROOT, "%.4f", it.coerceIn(0f, 1f)) }
            val c = casti?.takeIf { p -> p.any { it.color != null } }?.joinToString(",", ",\"casti\":[", "]") { p ->
                "{\"text\":${json(p.text)},\"barva\":" + (p.color?.let { "\"${SpeakerColors.hex(it)}\"" } ?: "null") + "}"
            } ?: ""
            return "{\"typ\":\"ukol\",\"text\":${json(text)},\"misto\":[$m],\"pismo\":${pismo.coerceIn(16, 120)}," +
                "\"pozadi\":${pozadi.coerceIn(0, 100)},\"barva\":\"$barva\"$c}"
        }

        /**
         * Nadpis a popis vybrané volby Nastavení hry (od 0.97.58, aplikace v TV 1.5.13) – vlastní okno přes anglický popis,
         * nezávislé na pruhu (v pruhu je mezitím vybraná záložka): `{"typ":"menupopis","nadpis":…,"text":…,
         * "misto":[vlevo,nahoře,vpravo,dole],"pismo":px,"pozadi":0–100,"barva":"#RRGGBB"}`, text zarovnaný vlevo
         * od levého horního rohu `misto`, šířka `misto`. Schování `{"typ":"menupopis","text":""}`. Starší TV zprávu
         * přeskočí (neznámý typ) a popis neukážou.
         * Od 0.97.102 (aplikace v TV 1.5.25) volitelně `"nadpisMisto":[vlevo,nahoře,vpravo,dole]` a `"pismoNadpis":px` – nadpis
         * zvlášť nad anglickým nadpisem (dole zarovnaný na `dole`), popis pod anglickým popisem. Starší TV pole přeskočí a ukáže
         * nadpis s popisem v `misto` jako dřív.
         */
        fun menuDescMessage(title: String?, text: String?, misto: FloatArray? = null, pismo: Int = 0, pozadi: Int = 100,
                            barva: String = "#FFFFFF", nadpisMisto: FloatArray? = null, pismoNadpis: Int = 0,
                            seznam: List<String> = emptyList(), seznamMisto: FloatArray? = null, seznamKrok: Int = 0): String {
            if ((text.isNullOrBlank() && title.isNullOrBlank() && seznam.isEmpty()) || misto == null || misto.size < 4) return "{\"typ\":\"menupopis\",\"text\":\"\"}"
            fun arr(a: FloatArray) = a.joinToString(",") { String.format(java.util.Locale.ROOT, "%.4f", it.coerceIn(0f, 1f)) }
            val n = if (nadpisMisto != null && nadpisMisto.size >= 4 && !title.isNullOrBlank())
                ",\"nadpisMisto\":[${arr(nadpisMisto)}],\"pismoNadpis\":${pismoNadpis.coerceIn(16, 140)}" else ""
            // seznam pod popisem (od 0.97.111, aplikace v TV 1.5.29): položky pod sebou vpravo od originálu, řádek = rozestup ve hře
            val l = if (seznam.isNotEmpty() && seznamMisto != null && seznamMisto.size >= 4)
                ",\"seznam\":[${seznam.take(16).joinToString(",") { json(it) }}],\"seznamMisto\":[${arr(seznamMisto)}],\"seznamKrok\":${seznamKrok.coerceIn(12, 120)}" else ""
            return "{\"typ\":\"menupopis\",\"nadpis\":${json(title ?: "")},\"text\":${json(text ?: "")},\"misto\":[${arr(misto)}]," +
                "\"pismo\":${pismo.coerceIn(16, 120)},\"pozadi\":${pozadi.coerceIn(0, 100)},\"barva\":\"$barva\"$n$l}"
        }

        /** Aplikace v TV, která umí překlad telefonu přímo přes text (`"rezim":"pres"`). */
        const val PHONE_OVER_TV = "1.5.28"

        /**
         * Překlad telefonu přímo přes původní text (od 0.97.110, aplikace v TV 1.5.28): `{"typ":"telefon","rezim":"pres",
         * "pismo":px,"zpravy":[{"text":…,"misto":[vlevo,nahoře,vpravo,dole]}]}` – každý odstavec na místě originálu,
         * světlé pozadí a tmavé písmo. Prázdné zprávy = schovat ([PHONE_HIDE]).
         */
        fun phoneOverMessage(items: List<Pair<String, FloatArray>>, pismo: Int, beside: Boolean = false): String {
            if (items.isEmpty()) return PHONE_HIDE
            val z = items.take(10).joinToString(",") { (t, b) ->
                val m = b.take(4).joinToString(",") { String.format(java.util.Locale.ROOT, "%.4f", it.coerceIn(0f, 1f)) }
                "{\"text\":${json(t)},\"misto\":[$m]}"
            }
            // „vedle“ (od 1.00-rc21): náhledy seznamu zpráv vedle telefonu – průhledné tmavé pozadí, bílé písmo; starší TV je
            // ukáže světle jako překlad přes text
            return "{\"typ\":\"telefon\",\"rezim\":\"pres\"" + (if (beside) ",\"vedle\":true" else "") +
                ",\"pismo\":${pismo.coerceIn(14, 60)},\"zpravy\":[$z]}"
        }

        /** Schování panelu telefonu ([phoneMessage]). */
        const val PHONE_HIDE = "{\"typ\":\"telefon\",\"zpravy\":[]}"

        /** Aplikace v TV, která umí panel telefonu s bublinami (`#telefon`). */
        const val PHONE_TV = "1.5.22"
        /** Aplikace v TV, která v panelu telefonu ukáže obrázek ze hry (`obrazek` u čáry „c“). */
        const val PHONE_IMAGE_TV = "1.5.24"

        /**
         * Panel telefonu (aplikace v TV 1.5.22, `#telefon`): nadpis a zprávy se stranou „l“ (příchozí), „p“ (vlastní),
         * „s“ (jiný text); `misto` = levý horní roh, šířka a nejnižší spodek (zlomky), písmo v px na obrazovce 1080.
         */
        fun phoneMessage(title: String?, msgs: List<Pair<String, Char>>, misto: FloatArray, pismo: Int, pozadi: Int,
                         images: List<String?> = emptyList(), volna: Boolean = false, barvy: IntArray? = null): String {
            if (msgs.isEmpty() || misto.size < 4) return PHONE_HIDE
            val m = misto.take(4).joinToString(",") { String.format(java.util.Locale.ROOT, "%.4f", it.coerceIn(0f, 1f)) }
            val z = msgs.indices.joinToString(",") { i ->
                val (t, s) = msgs[i]
                // obrázek u čáry; od 1.00-rc21 i u bubliny bez textu (jen emoji) – starší TV prázdnou bublinu vynechá
                val img = images.getOrNull(i)?.takeIf { (s == 'c' || t.isBlank()) && it.startsWith("data:image/jpeg;base64,") }
                "{\"text\":${json(t)},\"strana\":\"${if (s == 'p' || s == 'l' || s == 'c') s else 's'}\"" +
                    (if (img != null) ",\"obrazek\":\"$img\"" else "") + "}"
            }
            // „volna“ (ReCaption 1.0.5, aplikace v TV 1.5.46): panel na volné straně obrazu – TV přesun na druhou stranu
            // plynule dojede; starší TV pole přeskočí a panel jen přeskočí
            return "{\"typ\":\"telefon\",\"misto\":[$m],\"pismo\":${pismo.coerceIn(12, 60)},\"pozadi\":${pozadi.coerceIn(0, 100)}," +
                (if (volna) "\"volna\":true," else "") +
                // barvy bublin z profilu hry (vlastní, příchozí – „#RRGGBB“); TV bez podpory pole přeskočí a kreslí své výchozí
                (if (barvy != null && barvy.size >= 2) "\"barvy\":{\"p\":\"#${String.format(java.util.Locale.ROOT, "%06X", barvy[0] and 0xFFFFFF)}\"," +
                    "\"l\":\"#${String.format(java.util.Locale.ROOT, "%06X", barvy[1] and 0xFFFFFF)}\"}," else "") +
                (if (title.isNullOrBlank()) "" else "\"nadpis\":${json(title)},") + "\"zpravy\":[$z]}"
        }

        /** Verze „a.b.c“ ≥ [min]? Neznámá = ne. */
        fun atLeast(version: String?, min: String): Boolean {
            val a = version?.split('.')?.map { it.toIntOrNull() ?: return false } ?: return false
            val b = min.split('.').map { it.toInt() }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return true
        }

        /** Aplikace v TV, která umí popisky menu hry na jejich místě (`#menupolozky`, od 0.97.115). */
        const val MENU_LABELS_TV = "1.5.31"
        /** Aplikace v TV, která u popisků menu ukáže ikonu položky ze hry (`ikona`). */
        const val MENU_ICONS_TV = "1.5.32"

        /** Schování popisků menu ([menuLabelsMessage]). */
        const val MENU_LABELS_HIDE = "{\"typ\":\"menupolozky\",\"polozky\":[]}"

        /**
         * Popisky menu hry na jejich místě (aplikace v TV 1.5.31, `#menupolozky`; od 0.97.115, přání majitele):
         * `{"typ":"menupolozky","polozky":[{"text":…,"misto":[vlevo,nahoře,vpravo,dole],"pismo":px,
         * "zarovnani":"l"|"r","pozadi":0–100,"vybrana":true?}]}` – text v obdélníku `misto` (zlomky obrazu, výška = řádek), zarovnaný vlevo
         * k `misto[0]` nebo vpravo k `misto[2]`; vybraná položka menu černým písmem bez pozadí (leží v bílém pruhu hry), ostatní
         * bílým písmem na pozadí `pozadi` % položky (0 = bez pozadí, se stínem). Prázdné = schovat ([MENU_LABELS_HIDE]). Starší TV přeskočí.
         */
        fun menuLabelsMessage(items: List<MenuLabel>, icons: Map<String, String> = emptyMap()): String {
            val ok = items.filter { it.box.size >= 4 && it.text.isNotBlank() }.take(32)   // TV od 1.5.38 ukáže 32 (dřív 16)
            if (ok.isEmpty()) return MENU_LABELS_HIDE
            val p = ok.joinToString(",") { it ->
                val m = it.box.take(4).joinToString(",") { v -> String.format(java.util.Locale.ROOT, "%.4f", v.coerceIn(0f, 1f)) }
                "{\"text\":${json(it.text)},\"misto\":[$m],\"pismo\":${it.px.coerceIn(12, 120)},\"zarovnani\":\"${if (it.right) "r" else "l"}\"," +
                    "\"pozadi\":${it.bg.coerceIn(0, 100)}" + (if (it.selected) ",\"vybrana\":true" else ",\"barva\":\"${GameMenu.TINT_HEX}\"") +
                    // ikona položky ze hry (od 1.5.32): malé PNG jako data: adresa, bílé na průhledném
                    (it.icon?.let { k -> icons[k] }?.takeIf { u -> u.startsWith("data:image/png;base64,") && u.length < 4000 }?.let { u -> ",\"ikona\":\"$u\"" } ?: "") +
                    // části s ikonou tlačítka (od 1.5.35, patička „ZPĚT ○“)
                    (if (it.parts.isEmpty()) "" else ",\"casti\":[" + it.parts.take(6).joinToString(",") { (pt, ic) ->
                        "{\"text\":${json(pt)}" + (ic?.takeIf { c -> c in ButtonIcons.KNOWN }?.let { c -> ",\"tlacitko\":\"$c\"" } ?: "") + "}"
                    } + "]") + "}"
            }
            return "{\"typ\":\"menupolozky\",\"polozky\":[$p]}"
        }

        /** Schování pokynů u předmětů ([worldMessage]). */
        const val WORLD_HIDE = "{\"typ\":\"predmety\",\"polozky\":[]}"

        /**
         * Pokyny u předmětů (aplikace v TV 1.5.20, `#predmety`): text od levého horního rohu `misto` (zlomky obrazu),
         * výška řádku = výška `misto`, písmo v px na obrazovce 1080. Starší TV zprávu přeskočí.
         */
        fun worldMessage(items: List<Pair<String, FloatArray>>, pismo: Int = 0, pozadi: Int = 85, barva: String = "#FFFFFF"): String {
            if (items.isEmpty() || items.any { it.second.size < 4 }) return WORLD_HIDE
            val p = items.joinToString(",") { (t, b) ->
                val m = b.take(4).joinToString(",") { String.format(java.util.Locale.ROOT, "%.4f", it.coerceIn(0f, 1f)) }
                "{\"text\":${json(t)},\"misto\":[$m]}"
            }
            return "{\"typ\":\"predmety\",\"pismo\":${pismo.coerceIn(12, 120)},\"pozadi\":${pozadi.coerceIn(0, 100)}," +
                "\"barva\":\"$barva\",\"polozky\":[$p]}"
        }

        /** Schování menu vpravo dole ([cornerMessage]). */
        const val CORNER_HIDE = "{\"typ\":\"roh\",\"polozky\":[]}"

        /**
         * Menu vpravo dole (nápovědy tlačítek, GTA VI; aplikace v TV 1.5.19) – vlastní prvek, nezávislý na pruhu i úkolu:
         * `{"typ":"roh","rezim":"nad"|"pres","pismo":px,"pozadi":0–100,"barva":"#RRGGBB","polozky":[{"text":…,
         * "misto":[vlevo,nahoře,vpravo,dole],"seda":false},…]}`. Každá položka zarovnaná doprava k `misto[2]`, nejméně
         * široká `misto[2] − misto[0]` (u „pres“ zakryje anglický text), výška řádku `misto`; režim „nad“ už má obdélníky
         * posunuté nad seznam ([CornerPrompts.boxes]). Schování [CORNER_HIDE]. Starší TV neznámý typ přeskočí.
         * Nepovinné `"ikona"` u položky (aplikace v TV 1.5.25, [CornerPrompts.icons]): ikona tlačítka za textem – „x“, „o“,
         * „s“, „t“ (kotouč se znakem), „L1“ / „R1“ / „L2“ / „R2“, víc mezerou („L2 R2“); starší TV ji přeskočí.
         */
        fun cornerMessage(mode: Int = 0, items: List<Pair<String, Boolean>> = emptyList(), boxes: List<FloatArray> = emptyList(),
                          pismo: Int = 0, pozadi: Int = 100, barva: String = "#FFFFFF", icons: List<String?> = emptyList()): String {
            if (items.isEmpty() || boxes.size != items.size || boxes.any { it.size < 4 }) return CORNER_HIDE
            val p = items.indices.joinToString(",") { i ->
                val m = boxes[i].take(4).joinToString(",") { String.format(java.util.Locale.ROOT, "%.4f", it.coerceIn(0f, 1f)) }
                val icon = icons.getOrNull(i)?.takeIf { it.split(' ').all { t -> t in ButtonIcons.KNOWN } }
                "{\"text\":${json(items[i].first)},\"misto\":[$m],\"seda\":${items[i].second}" +
                    (if (icon != null) ",\"ikona\":${json(icon)}" else "") + "}"
            }
            return "{\"typ\":\"roh\",\"rezim\":\"${if (mode == 1) "nad" else "pres"}\",\"pismo\":${pismo.coerceIn(12, 120)}," +
                "\"pozadi\":${pozadi.coerceIn(0, 100)},\"barva\":\"$barva\",\"polozky\":[$p]}"
        }

        /**
         * Stav pro televizi jako Domů v aplikaci: řádky (barva „ok“ / „pozor“ / „info“, text).
         * Aplikace v televizi od 1.5.0 je ukáže pod svými řádky (HDMI, spojení); starší zprávu přeskočí.
         */
        fun statusMessage(rows: List<Pair<String, String>>): String =
            rows.joinToString(",", "{\"typ\":\"stav\",\"radky\":[", "]}") { (b, t) -> "{\"barva\":${json(b)},\"text\":${json(t)}}" }

        /** Text jako řetězec JSON (uvozovky, zpětná lomítka a řídicí znaky se zapíšou bezpečně). */
        fun json(s: String): String {
            val sb = StringBuilder(s.length + 2).append('"')
            for (c in s) when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> {}
                c == '\t' -> sb.append(' ')
                c < ' ' || c == ' ' || c == ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
            return sb.append('"').toString()
        }

        /** Odpověď na klíč z žádosti o WebSocket (podle ní prohlížeč pozná, že mluví se správným protějškem). */
        fun acceptKey(key: String): String = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key.trim() + MAGIC).toByteArray(Charsets.US_ASCII)))

        /** Jedna textová zpráva WebSocketu směrem k televizi. */
        fun frame(text: String): ByteArray {
            val data = text.toByteArray(Charsets.UTF_8)
            val out = ByteArrayOutputStream(data.size + 4)
            out.write(0x81)
            if (data.size < 126) out.write(data.size)
            else if (data.size < 65536) { out.write(126); out.write(data.size shr 8 and 0xFF); out.write(data.size and 0xFF) }
            else { out.write(127); for (sh in 56 downTo 0 step 8) out.write(((data.size.toLong() shr sh) and 0xFF).toInt()) }   // obrázky telefonu
            out.write(data)
            return out.toByteArray()
        }

        /** Klíč z hlaviček žádosti o WebSocket; null = obyčejný požadavek. */
        fun keyFromHead(head: String): String? = head.split("\r\n").drop(1)
            .firstOrNull { it.substringBefore(':').trim().equals("Sec-WebSocket-Key", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() && it.length < 100 }
    }

    @Volatile var port = 0
        private set

    @Volatile private var server: ServerSocket? = null
    @Volatile private var acceptor: Thread? = null
    @Volatile private var pool: ExecutorService? = null
    /** Zprávy odcházejí z jednoho vlákna, aby pomalá televize nezdržela čtení titulků. */
    @Volatile private var sendQueue: ExecutorService? = null
    private val clients = CopyOnWriteArrayList<Socket>()
    private var nextId = 0
    /** Co je právě na obrazovce (null = pruh schovaný) – nově připojená televize to dostane hned. */
    private var shown: String? = null
    private var shownParts: String? = null
    private var shownLook: String? = null
    /** Zpráva `nastaveni` s volbami, které jdou měnit i v televizi ([TvSettings]); null = neposílat. */
    @Volatile private var settings: String? = null
    /** Poslední stav pro televizi (body s tečkou, aplikace v televizi od 1.5.0); nové televize ho dostanou po připojení. */
    @Volatile private var status: String? = null
    /** Právě zobrazený úkol ([objectiveMessage]); null = žádný. */
    @Volatile private var objective: String? = null
    /** Právě zobrazený popis volby Nastavení hry ([menuDescMessage]); null = žádný. */
    @Volatile private var menuDesc: String? = null
    /** Právě zobrazené menu vpravo dole ([cornerMessage]); null = žádné. */
    @Volatile private var corner: String? = null
    @Volatile private var world: String? = null
    @Volatile private var menuLabels: String? = null
    @Volatile private var phone: String? = null
    /** Televize požádala o změnu vzhledu ([TvSettings.parse] už ověřil volbu i hodnotu). */
    @Volatile var onChange: ((TvSettings.Change) -> Unit)? = null

    val running: Boolean get() = server?.isClosed == false

    /** Kolik televizí je právě připojeno. */
    val connected: Int get() = clients.size

    /** Začne čekat na televizi. Vrací false, když port nejde otevřít (už ho drží něco jiného). */
    /**
     * [retry] = když je port zrovna obsazený, zkoušet ho otevřít každé 2 s znovu, dokud se nepovede nebo se nezavolá [stop].
     * Služba překladu to potřebuje (od 0.97.35): po aktualizaci aplikace si port na okamžik vzala obrazovka aplikace
     * (TvIdle) a služba ho už nikdy neotevřela – televize pak jen „hledala v síti“, i když telefon překládal.
     */
    @Synchronized fun start(preferredPort: Int = PORT, bind: InetAddress? = null, retry: Boolean = false): Boolean {
        if (running) return true
        wanted = true
        val s = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bind, preferredPort), 8) }
        } catch (_: Throwable) {
            log("tv cannot listen" + if (retry) " – port $preferredPort obsazený, zkusím znovu" else "")
            if (retry && retrier == null) retrier = Thread({
                while (wanted && !running) {
                    try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
                    synchronized(this) { if (wanted && !running && tryOpen(preferredPort, bind)) log("tv port otevřen napodruhé") }
                }
                synchronized(this) { retrier = null }
            }, "tp-tv-retry").apply { isDaemon = true; start() }
            return false
        }
        listen(s)
        return true
    }

    /** Chce se poslouchat (start bez stop) – opakované pokusy o port běží jen do [stop]. */
    @Volatile private var wanted = false
    private var retrier: Thread? = null

    private fun tryOpen(port: Int, bind: InetAddress?): Boolean = try {
        listen(ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bind, port), 8) }); true
    } catch (_: Throwable) { false }

    private fun listen(s: ServerSocket) {
        server = s
        port = s.localPort
        val p = Executors.newCachedThreadPool { r -> Thread(r, "tp-tv").apply { isDaemon = true } }
        pool = p
        sendQueue = Executors.newSingleThreadExecutor { r -> Thread(r, "tp-tv-send").apply { isDaemon = true } }
        acceptor = Thread({
            while (!s.isClosed) {
                val c = try { s.accept() } catch (_: Throwable) { break }
                try { p.execute { handle(c) } } catch (_: Throwable) { close(c) }
            }
        }, "tp-tv-accept").apply { isDaemon = true; start() }
        log("tv on port $port")
    }

    @Synchronized fun stop() {
        wanted = false
        retrier?.interrupt()
        val s = server ?: return
        server = null
        try { s.close() } catch (_: Throwable) {}
        clients.forEach { close(it) }
        clients.clear()
        pool?.shutdownNow(); pool = null
        sendQueue?.shutdownNow(); sendQueue = null
        acceptor = null
        shown = null
        shownParts = null
        shownLook = null
        log("tv closed")
    }

    /**
     * Ukáže na televizi přeložený titulek. Stejný text podruhé se neposílá.
     * [parts] = řádky zvlášť s barvami jmen a volbami (viz [partsJson]); null = jen obyčejný text.
     * [look] = vzhled a poloha pruhu podle Nastavení (viz [TvLook]); null = televize kreslí po svém.
     */
    @Synchronized fun show(text: String, parts: String? = null, look: String? = null) {
        if (text == shown && parts == shownParts && look == shownLook) return
        shown = text
        shownParts = parts
        shownLook = look
        broadcast(showMessage(++nextId, text, parts, look))
    }

    /** Pošle televizím aktuální volby vzhledu (po spuštění a po každé změně); nové televize je dostanou po připojení. */
    fun sendSettings(message: String) {
        if (message == settings) return
        settings = message
        broadcast(message)
    }

    /** Pošle televizím stav (jen když se změnil); nové televize ho dostanou po připojení. */
    fun sendStatus(message: String) {
        if (message == status) return
        status = message
        broadcast(message)
    }

    /** Úkol pod titulky ([objectiveMessage]) – posílá se jen změna; nové televize ho dostanou po připojení. */
    @Synchronized fun sendObjective(message: String) {
        val m = if (message.contains("\"text\":\"\"")) null else message
        if (m == objective) return
        objective = m
        broadcast(message)
    }

    /** Popis volby Nastavení hry ([menuDescMessage]) – posílá se jen změna; nové televize ho dostanou po připojení. */
    @Synchronized fun sendMenuDesc(message: String) {
        val m = if (message == menuDescMessage(null, null)) null else message
        if (m == menuDesc) return
        menuDesc = m
        broadcast(message)
    }

    /** Menu vpravo dole ([cornerMessage]) – posílá se jen změna; nové televize ho dostanou po připojení. */
    @Synchronized fun sendCorner(message: String) {
        val m = if (message == CORNER_HIDE) null else message
        if (m == corner) return
        corner = m
        broadcast(message)
    }

    /** Pokyny u předmětů ([worldMessage]) – jen změna; nové televize je dostanou po připojení. */
    @Synchronized fun sendWorld(message: String) {
        val m = if (message == WORLD_HIDE) null else message
        if (m == world) return
        world = m
        broadcast(message)
    }

    /** Panel telefonu ([phoneMessage]) – jen změna; nové televize ho dostanou po připojení. */
    /** Popisky menu hry ([menuLabelsMessage]); stejná zpráva se znovu neposílá, nové TV ji dostane po připojení. */
    @Synchronized fun sendMenuLabels(message: String) {
        val m = if (message == MENU_LABELS_HIDE) null else message
        if (m == menuLabels) return
        menuLabels = m
        broadcast(message)
    }

    @Synchronized fun sendPhone(message: String) {
        val m = if (message == PHONE_HIDE) null else message
        if (m == phone) return
        phone = m
        broadcast(message)
    }

    /** Schová pruh na televizi. */
    @Synchronized fun hide() {
        if (shown == null) return
        shown = null
        shownParts = null
        shownLook = null
        broadcast(hideMessage(++nextId))
    }

    private fun broadcast(message: String) {
        if (clients.isEmpty()) return
        val bytes = frame(message)
        try { sendQueue?.execute { clients.forEach { write(it, bytes) } } } catch (_: Throwable) {}
    }

    private fun write(c: Socket, bytes: ByteArray) {
        try {
            synchronized(c) { c.getOutputStream().apply { write(bytes); flush() } }
        } catch (_: Throwable) { drop(c) }
    }

    private fun drop(c: Socket) {
        if (clients.remove(c)) log("tv gone, left ${clients.size}")
        close(c)
    }

    private fun close(c: Socket) { try { c.close() } catch (_: Throwable) {} }

    private fun handle(c: Socket) {
        try {
            // titulky patří jen do domácí sítě
            val from = c.inetAddress
            if (from == null || !(from.isLoopbackAddress || PhoneLogin.isHomeAddress(from.hostAddress ?: ""))) { close(c); return }
            c.soTimeout = 8000
            c.tcpNoDelay = true
            val input = c.getInputStream()
            val head = readHead(input) ?: run { close(c); return }
            val key = keyFromHead(head)
            if (key == null) {
                // obyčejný dotaz (např. z prohlížeče): jen dát vědět, že tu ReCaption je
                c.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: 10\r\nConnection: close\r\n\r\nTitulkyPS5".toByteArray(Charsets.US_ASCII))
                    flush()
                }
                close(c); return
            }
            if (clients.size >= MAX_CLIENTS) { close(c); return }
            c.getOutputStream().apply {
                write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: ${acceptKey(key)}\r\n\r\n").toByteArray(Charsets.US_ASCII))
                flush()
            }
            c.soTimeout = 0
            write(c, frame(HELLO))
            // přidání a poslání právě zobrazeného titulku jsou pod stejným zámkem jako show/hide,
            // aby se nová televize o žádnou změnu nepřipravila
            synchronized(this) {
                clients.add(c)
                settings?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                status?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                shown?.let { t -> val b = frame(showMessage(++nextId, t, shownParts, shownLook)); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                objective?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                menuDesc?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                corner?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                world?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                menuLabels?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                phone?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
                support?.let { m -> val b = frame(m); try { sendQueue?.execute { write(c, b) } } catch (_: Throwable) {} }
            }
            log("tv connected, now ${clients.size}")
            // Od televize je zajímavé jen ohlášení (platforma a verze); čte se i proto, aby se
            // poznalo zavření spojení.
            while (true) {
                val msg = readFrame(input) ?: break
                TvAnnounce.parse(msg)?.let { a ->
                    lastSeen = Seen(a, System.currentTimeMillis())
                    log("tv says ${a.platform ?: "?"} ${a.version ?: "?"}")
                    try { onAnnounce?.invoke(a) } catch (_: Throwable) {}
                }
                // záznam událostí z televize (od verze 1.2.0) – do souboru pro hlášení
                TvLog.parse(msg)?.let { TvLog.add(it) }
                // změna vzhledu z ovladače televize (od aplikace v televizi 1.4.0) – jen povolené volby a hodnoty
                TvSettings.parse(msg)?.let { ch ->
                    log("tv change ${ch.javaClass.simpleName}")
                    try { onChange?.invoke(ch) } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {
        } finally { drop(c) }
    }

    /** Hlavičky žádosti (po prázdný řádek); null, když jsou podezřele dlouhé nebo spojení skončilo. */
    private fun readHead(input: InputStream): String? {
        val end = "\r\n\r\n"
        val out = ByteArrayOutputStream()
        var match = 0
        while (out.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            match = if (b == end[match].code) match + 1 else if (b == end[0].code) 1 else 0
            if (match == end.length) return String(out.toByteArray(), 0, out.size() - end.length, Charsets.ISO_8859_1)
        }
        return null
    }
}
