package cz.recaption

import cz.recaption.core.TvSettings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import cz.recaption.core.Console
import cz.recaption.core.TvSender
import cz.recaption.core.TvType

/**
 * Typ televize na jednom místě, aby ho šlo nabídnout odkudkoli (Domů, Nastavení, průvodce prvním
 * spuštěním): Android TV (pruh ukazuje toto zařízení, výchozí), LG (webOS) nebo Samsung (Tizen) –
 * tam toto zařízení jen čte a překládá a věty posílá aplikaci ReCaption v televizi (core/TvSender.kt).
 *
 *  - [show] – nabídka typů; vybraný typ uloží ([set]) a zavolá `onPicked`,
 *  - [set] – uloží typ a pošle ho běžícímu překladu (i z voleb v průvodci),
 *  - [short] – televize do souhrnného řádku na Domů („LG OLED55B7 · připojeno“),
 *  - [needsInstall] – má se nabízet nahrání aplikace do televize (aplikace se z ní ještě nikdy neohlásila)?
 *  - [openInstall] – průvodce nahráním pro LG / Samsung.
 */
object TvChoice {
    fun label(c: Context, type: TvType): String = c.tx("tv_type_" + type.key)

    /** Uloží typ televize a hned ho pošle běžícímu překladu. */
    fun set(activity: Activity, t: TvType) {
        Prefs.setTvType(activity, t)
        if (TranslatorService.running) {
            activity.startService(Intent(activity, TranslatorService::class.java).setAction(TranslatorService.ACTION_RELOAD))
        }
    }

    /** Nabídka typů televize. Vybraný typ se hned uloží a projeví v běžícím překladu. */
    fun show(activity: Activity, onPicked: (TvType) -> Unit = {}) {
        val types = TvType.values().toList()
        val current = types.indexOf(Prefs.tvType(activity))
        fun pick(i: Int) {
            set(activity, types[i])
            onPicked(types[i])
        }
        try {
            AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(activity.tx("tv_type"))
                .setSingleChoiceItems(types.map { label(activity, it) }.toTypedArray(), current) { d, i -> d.dismiss(); pick(i) }
                .show()
        } catch (_: Exception) {
            // nouzově: další možnost v pořadí
            pick((current + 1) % types.size)
        }
    }

    /**
     * Zapamatuje si, že se z televize daného typu někdy ohlásila aplikace ReCaption (model a verze),
     * aby Domů přestalo nabízet nahrání. Volá se při každém vykreslení – stačí levné čtení.
     */
    private fun remember(c: Context, type: TvType) {
        val a = TvSender.lastSeen?.announce ?: return
        val p = type.platform ?: return
        if (a.platform != p) return
        val v = listOfNotNull(a.model, a.version).joinToString(" · ")
        if (Prefs.tvSeen(c, p) != v) Prefs.setTvSeen(c, p, v)
    }

    /** Ohlásila se už někdy aplikace z televize tohoto typu? */
    fun seen(c: Context, type: TvType): Boolean {
        remember(c, type)
        return type.platform?.let { Prefs.tvSeen(c, it) } != null
    }

    fun needsInstall(c: Context, type: TvType = Prefs.tvType(c)): Boolean = type.sends && !seen(c, type)

    /**
     * Verze aplikace pro televizi, kterou telefon do TV nahraje: z APK (receiver-manifest.json), nebo novější stažená
     * z vydání (od 1.0.3, [TvPackages]); null = nejde zjistit.
     */
    fun bundledVersion(c: Context, type: TvType): String? = bundled.getOrPut(type) { TvPackages.version(c, type) ?: "" }.ifEmpty { null }

    /** Čte se jen jednou (Domů ji potřebuje při každém překreslení); znovu až po stažení novějšího balíčku. */
    private val bundled = java.util.concurrent.ConcurrentHashMap<TvType, String>()

    /** Stažen novější balíček pro TV ([TvPackages]) → verzi přečíst znovu. */
    fun forgetBundled() = bundled.clear()

    /**
     * Verze z uloženého ohlášení: „model · verze“, nebo jen „verze“ (po nahrání do TV, která se od spuštění
     * aplikace ještě neohlásila – do 0.97.30 se to četlo jako „bez verze“ a Domů hlásilo „LG ? · nová …“);
     * null = aplikace v televizi před 1.1.0 verzi neposílala (jen model).
     */
    internal fun storedVersion(v: String): String? {
        val last = v.substringAfterLast(" · ").trim()
        return last.takeIf { VERSION.matches(it) }
    }
    private val VERSION = Regex("\\d+(\\.\\d+)+")

    /**
     * V televizi je starší ReCaption než to, které nese tohle APK → (verze v televizi, nová verze), jinak null.
     * Verzi v televizi bere z posledního ohlášení (i z dřívějška, `Prefs.tvSeen`).
     */
    fun outdated(c: Context, type: TvType = Prefs.tvType(c)): Pair<String, String>? {
        val p = type.platform ?: return null
        val bundled = bundledVersion(c, type) ?: return null
        val live = TvSender.lastSeen?.announce?.takeIf { it.platform == p }
        // uloženo „model · verze“; aplikace v televizi před 1.1.0 verzi neposílala → bez verze (= zastaralá)
        val seen = live ?: Prefs.tvSeen(c, p)?.let { cz.recaption.core.TvAnnounce(p, storedVersion(it), null, null, null) } ?: return null
        val check = cz.recaption.core.TvCheck.of(seen, p, bundled)
        return if (check == cz.recaption.core.TvCheck.OLDER || check == cz.recaption.core.TvCheck.NO_VERSION)
            (seen.version ?: "?") to bundled else null
    }

    /**
     * Verze ReCaptionu v televizi, když je stejná nebo novější než přibalená (= není co nahrávat);
     * null = televize se neohlásila, nebo je v ní starší verze.
     */
    fun upToDate(c: Context, type: TvType = Prefs.tvType(c)): String? {
        val p = type.platform ?: return null
        val bundled = bundledVersion(c, type) ?: return null
        val live = TvSender.lastSeen?.announce?.takeIf { it.platform == p }
        // uloženo „model · verze“; aplikace v televizi před 1.1.0 verzi neposílala → bez verze (= zastaralá)
        val seen = live ?: Prefs.tvSeen(c, p)?.let { cz.recaption.core.TvAnnounce(p, storedVersion(it), null, null, null) } ?: return null
        return if (cz.recaption.core.TvCheck.of(seen, p, bundled).ok) seen.version else null
    }

    /**
     * Televize do souhrnného řádku na Domů: „Android TV“, nebo u LG/Samsungu značka, model (když se
     * televize už ohlásila) a stav spojení („LG OLED55B7 · připojeno“). Druhá hodnota = připojeno.
     */
    fun short(c: Context): Pair<String, Boolean> {
        val type = Prefs.tvType(c)
        val name = when (type) { TvType.ANDROID -> "Android TV"; TvType.LG -> "LG"; TvType.SAMSUNG -> "Samsung" }
        if (!type.sends) return name to false
        val model = TvSender.lastSeen?.announce?.takeIf { it.platform == type.platform }?.model
        val connected = TranslatorService.tvConnected > 0
        val state = when {
            connected -> c.tx("tv_st_on")
            seen(c, type) -> c.tx("tv_st_off")
            else -> c.tx("tv_st_never")
        }
        return (name + (model?.let { " $it" } ?: "") + " · " + state) to connected
    }

    /**
     * [update] = z Domů u starší aplikace v TV LG: rovnou aktualizovat, stránka s přípravou jen při chybě (od 0.97.39).
     * [wizard] = z kroku Televize v průvodci: po úspěchu se stránka sama zavře (krok pak ukáže ✔).
     */
    fun openInstall(activity: Activity, type: TvType = Prefs.tvType(activity), update: Boolean = false, wizard: Boolean = false) {
        val cls = when (type) {
            TvType.LG -> LgInstallActivity::class.java
            TvType.SAMSUNG -> SamsungInstallActivity::class.java
            TvType.ANDROID -> return
        }
        activity.startActivity(Intent(activity, cls).putExtra(LgInstallActivity.EXTRA_UPDATE, update && type == TvType.LG)
            .putExtra(LgInstallActivity.EXTRA_WIZARD, wizard))
    }

    /** Popisek tlačítka k nahrání aplikace do televize daného typu; u LG se starší verzí „Aktualizovat…“. */
    fun installLabel(c: Context, type: TvType): String = c.tx(when {
        type != TvType.LG -> "ss_open"
        outdated(c, type) != null -> "lg_open_upd"
        else -> "lg_open"
    })
}

/**
 * Spojení s aplikací v televizi LG/Samsung i tehdy, když překlad neběží (jen dokud je ReCaption vidět):
 * Domů pak ukazuje skutečný stav „LG … · připojeno“ ještě před Spustit. Při spuštění překladu se
 * zavře a port převezme služba ([TranslatorService] volá [stop] před vlastním TvSender).
 */
/** Volby vzhledu z ovladače televize ([TvSettings]) – stejně jako v Titulkách v telefonu. */
object TvLookPrefs {
    fun message(c: android.content.Context): String = TvSettings.message(Prefs.opacity(c), Prefs.textSize(c), Prefs.matchSize(c),
        Prefs.layout(c).position, Prefs.showOriginal(c), Prefs.barPad(c), Prefs.coloredSpeaker(c), Prefs.origSp(c), Prefs.textColorIndex(c),
        Prefs.sporicLuna(c), Prefs.sporicVideo(c))

    fun apply(c: android.content.Context, ch: TvSettings.Change) {
        when (ch) {
            is TvSettings.Change.Opacity -> Prefs.setOpacity(c, ch.percent)
            is TvSettings.Change.TextSize -> if (ch.sp == 0) Prefs.setMatchSize(c, true)
                else { Prefs.setMatchSize(c, false); Prefs.setTextSize(c, ch.sp) }
            is TvSettings.Change.Position -> Prefs.setLayout(c, Prefs.layout(c).withPosition(ch.position))
            is TvSettings.Change.Original -> Prefs.setShowOriginal(c, ch.show)
            is TvSettings.Change.Pad -> Prefs.setBarPad(c, ch.pad)
            is TvSettings.Change.Names -> Prefs.setColoredSpeaker(c, ch.colored)
            is TvSettings.Change.OrigSize -> Prefs.setOrigSp(c, ch.sp)
            is TvSettings.Change.TextColor -> Prefs.setTextColorIndex(c, ch.index)
        }
    }
}

object TvIdle {
    @Volatile private var sender: TvSender? = null

    /** Kolik televizí je připojeno k čekajícímu spojení; null = neběží. */
    val connected: Int? get() = sender?.takeIf { it.running }?.connected

    @Synchronized fun ensure(c: android.content.Context) {
        stopTimer.removeCallbacks(stopTask)
        if (TranslatorService.running || !Prefs.tvSend(c)) { stop(); return }
        if (sender?.running != true) {
            val s = TvSender { android.util.Log.i("TP_TEST", "TV|$it"); cz.recaption.core.DiagLog.add("TV|$it") }
            // „Vzhled titulků“ z televize i bez spuštěného překladu: uložit jako z telefonu a poslat zpět
            val app = c.applicationContext
            s.onChange = { ch -> android.os.Handler(android.os.Looper.getMainLooper()).post { TvLookPrefs.apply(app, ch); ensure(app) } }
            sender = if (s.start()) s else null
        }
        // stav pro televizi (překlad neběží, konzole, jazyk, hra) a volby vzhledu – posílá se jen změna
        if (sender != null) Ps5Probe.poke(c)
        sender?.sendStatus(TvStatus.message(c))
        sender?.sendSettings(TvLookPrefs.message(c))
    }

    @Synchronized fun stop() { stopTimer.removeCallbacks(stopTask); sender?.stop(); sender = null }

    /**
     * Aplikace se schovala (zavřená, jiná aplikace): spojení s TV ještě [GRACE_MS] drží (přání majitele 22. 11. 2026 – po
     * zavření a otevření aplikace byla TV zase „nepřipojeno“ a musela telefon v síti hledat znovu). Otevřený port nic nestojí,
     * stav se do TV mezitím neposílá; po návratu ([ensure]) se zavření zruší. Proces může systém ukončit dřív – pak se TV
     * připojí znovu jako dřív.
     */
    fun stopLater() { stopTimer.removeCallbacks(stopTask); stopTimer.postDelayed(stopTask, GRACE_MS) }

    private const val GRACE_MS = 10 * 60_000L
    private val stopTimer = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopTask = Runnable { if (!UiState.visible && !TranslatorService.running) stop() }

    /**
     * Překlad se zastavil a aplikace je vidět: spojení služby s televizí převezme obrazovka aplikace (od 0.97.71, přání majitele) –
     * televize zůstane připojená a nemusí telefon znovu hledat („ReCaption · připojeno“, oranžová jen konzole).
     */
    @Synchronized fun adopt(c: android.content.Context, s: TvSender) {
        if (!s.running || TranslatorService.running || !Prefs.tvSend(c)) { s.stop(); return }
        sender?.takeIf { it !== s }?.stop()
        val app = c.applicationContext
        s.onChange = { ch -> android.os.Handler(android.os.Looper.getMainLooper()).post { TvLookPrefs.apply(app, ch); ensure(app) } }
        s.onAnnounce = { a -> TvProfile.onAnnounce(app, a) }
        sender = s
        s.sendStatus(TvStatus.message(c))
    }

    /** Spuštění překladu: služba si vezme otevřené spojení obrazovky aplikace (stejný port), televize se znovu nepřipojuje. */
    @Synchronized fun take(): TvSender? { val s = sender?.takeIf { it.running }; sender = null; return s }
}

/**
 * Stav pro aplikaci v televizi LG/Samsung (od 0.97.14, televize od 1.5.0): stejné body jako Domů –
 * konzole a obraz, překlad (běží / neběží), jazyk a DeepL, hra. Krátké stavové řádky, žádné věty.
 */
object TvStatus {
    fun message(c: android.content.Context): String = TvSender.statusMessage(rows(c))

    /**
     * Má stav přes hru v režimu „dynamicky“ zůstat? Jen když hráč bez zásahu nic neuvidí: konzole spí, obraz nejde,
     * spojení selhalo, nebo chybí jazykový balíček. Jazyk, DeepL a odhad hry se ukážou jen na chvíli po změně.
     */
    fun sticky(c: android.content.Context): Boolean {
        if (!TranslatorService.running || TranslatorService.demo) return false
        if (Prefs.console(c) == Console.PLAYSTATION && Ps5Link.asleep) return true
        val st = TranslatorService.linkStatus().stage
        if (st == cz.recaption.core.LinkStatus.Stage.STALLED || st == cz.recaption.core.LinkStatus.Stage.NO_IMAGE ||
            st == cz.recaption.core.LinkStatus.Stage.FAILED) return true
        return !OfflineTranslator.isReady(Prefs.lang(c))
    }

    fun rows(c: android.content.Context): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val console = Prefs.console(c)
        val name = when (console) {
            Console.PLAYSTATION -> Prefs.ps5Host(c)?.name
            Console.XBOX -> if (XboxAccount.ready(c)) XboxAccount.consoleName(c) else null
        } ?: if (console == Console.XBOX) "Xbox" else "PS5"
        // první řádek: na čem ReCaption čte a jestli překládá (přání majitele 22. 11. 2026 – TV ukazovala jen
        // „ReCaption · připojeno“ a zelené řádky, takže to vypadalo, že všechno běží, i když překlad v telefonu stál)
        val running = TranslatorService.running && !TranslatorService.demo
        val device = c.tx(if (NetInfo.isHandheld(c)) "tvs_dev_phone" else "tvs_dev_box")
        // jen pro TV LG / Samsung – na Android TV čte a ukazuje totéž zařízení
        if (Prefs.tvSend(c)) out += (if (running) "ok" else "pozor") to "$device · " + c.tx(if (running) "tvs_run" else "tvs_off")
        // čtení náhle zpomalilo (24. 11. 2026, core/ReadSlowdown): typicky telefon přepnutý do jiné aplikace nebo zhasnutý –
        // Android pak čtení přibrzdí a hráč u TV neví proč. Oranžový řádek drží stav v TV na obrazovce, dokud se to nespraví.
        // Když obraz z konzole nejde vůbec a aplikace je vidět, stačí řádek konzole pod tím.
        if (running && Prefs.tvSend(c) && TranslatorService.readSlow) {
            val hidden = NetInfo.isHandheld(c) && !UiState.visible
            if (hidden || TranslatorService.linkStatus().stage == cz.recaption.core.LinkStatus.Stage.IMAGE)
                out += "pozor" to "$device · " + c.tx(if (hidden) "tvs_slow_bg" else "tvs_slow") +
                    // telefon Samsung uspává aplikace na pozadí sám – krátce kde to vypnout (podrobně pomoc/telefon.md#pozadi)
                    (if (hidden && NetInfo.isSamsungPhone(c)) " · " + c.tx("tvs_slow_ss") else "")
        }
        if (running) {
            val (b, st) = if (console == Console.PLAYSTATION && Ps5Link.asleep) "pozor" to c.tx("h_con_sleep") else when (TranslatorService.linkStatus().stage) {
                cz.recaption.core.LinkStatus.Stage.IMAGE -> "ok" to c.tx("tv_st_on")
                cz.recaption.core.LinkStatus.Stage.STALLED, cz.recaption.core.LinkStatus.Stage.NO_IMAGE -> "pozor" to c.tx("h_con_noimg")
                cz.recaption.core.LinkStatus.Stage.FAILED -> "pozor" to c.tx("h_con_fail")
                cz.recaption.core.LinkStatus.Stage.PAUSED -> "info" to c.tx("st_paused").lowercase()
                else -> "info" to c.tx("h_con_wait")
            }
            out += b to "$name · $st"
        } else {
            // překlad neběží: řádek konzole oranžově (od 0.97.90 nad ním i „Telefon · překlad neběží“);
            // od 0.97.75 „spárováno“ / „v klidovém režimu“ / „zapnutá“ podle hledání v síti (Ps5Probe), ne „nepřipojeno“
            val probe = if (console == Console.PLAYSTATION) Ps5Probe.state else null
            // zapnutá PS5 zeleně jako na Domů (od 0.97.101 – dřív v TV oranžově, v aplikaci zeleně); že překlad neběží,
            // říká řádek „Telefon · překlad neběží“ nad tím
            out += (if (Prefs.consoleReady(c) && probe == Ps5Probe.State.ON) "ok" else "pozor") to "$name · " + c.tx(when {
                !Prefs.consoleReady(c) -> "sum_nopair"
                probe == Ps5Probe.State.ON -> "h_con_on"
                probe == Ps5Probe.State.REST -> "h_con_sleep"
                else -> "h_con_ready"
            })
        }
        // bez běžícího překladu jsou jazyk a hra jen informace (šedě), ne zelené „jede“
        val okOrInfo = if (running) "ok" else "info"
        val lang = Prefs.lang(c)
        out += if (!OfflineTranslator.isReady(lang)) "pozor" to c.tx("w_model").replace("%s", Prefs.langPair(c))
            else okOrInfo to Prefs.langPair(c) + " · " + c.tx(if (DeeplTranslator.active(c)) "dl_mode_on" else "h_offline")
        val game = cz.recaption.core.GameProfiles.of(Prefs.gameId(c))
        val guess = Prefs.gameGuess(c)?.let { cz.recaption.core.GameProfiles.of(it) }
        if (guess != null) out += "pozor" to (if (game.id != "all") game.title + " · " else "") + guess.title + "?"
        else if (game.id != "all") out += okOrInfo to game.title
        return out
    }
}
