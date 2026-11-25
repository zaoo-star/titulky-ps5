package cz.recaption.pc

import java.io.File

/**
 * Čtečka pro TV LG / Samsung (⚙ → Použití → Čtečka pro TV) – PC místo telefonu s Androidem.
 *
 * Hra jde z PS5 kabelem HDMI do TV a hráč hraje na PS5 svým herním účtem. PC se k PS5 připojí přes
 * Remote Play **účtem pro ReCaption** (druhý uživatel na PS5, jako čtečka na Androidu – herním účtem by
 * PS5 hráči vypnula ovladač), čte obraz z okna chiaki-ng ([Ctecka]) a hotové věty posílá do aplikace
 * ReCaption v TV. chiaki-ng běží ve vlastním profilu ([Chiaki.PROFIL_CTECKY]) v okně, bez zvuku.
 *
 * PS5 se **nebudí**: probuzení by ji zapnulo na účet pro ReCaption. Každých pár sekund se jen zjistí,
 * jestli je zapnutá, a připojí se, až ji hráč zapne ovladačem. Když PS5 během hry přejde do klidového
 * režimu, chiaki-ng skončí a znovu se nepřipojuje, dokud PS5 není zase zapnutá (jako Android od 0.97.37).
 * Opakované krátké spojení (4× pod minutu) zastaví pokusy – opakované připojování ruší ovladače u konzole.
 */
class CteckaTv(
    private val exe: File,
    private val konzole: Chiaki.Konzole,
    /** Čtení obrazu (po změně nastavení se spouští znovu) – dostane okno chiaki-ng po každém připojení. */
    private val ctecka: () -> Ctecka?,
    private val log: (String) -> Unit,
    private val zmena: () -> Unit,
) {
    enum class Faze { HLEDANI, NENALEZENA, KLID, PRIPOJOVANI, BEZI, CHYBA }

    @Volatile var faze = Faze.HLEDANI
        private set(v) { if (field != v) { field = v; zmena() } }
    @Volatile var chyba = ""
        private set
    /** Okno chiaki-ng právě připojeného (null = nepřipojeno). */
    @Volatile var okno: OknoHry? = null
        private set
    /** Okno pro čtení („pid:1234“, bez spojení [CEKA_NA_OKNO]) – i pro čtení spuštěné znovu. */
    @Volatile var oknoNazev: String = CEKA_NA_OKNO
        private set
    @Volatile private var konec = false
    @Volatile private var proces: Process? = null

    fun zastav() {
        konec = true
        proces?.destroy()
    }

    private fun cekej(ms: Long) {
        val do_ = System.currentTimeMillis() + ms
        while (!konec && System.currentTimeMillis() < do_) Thread.sleep(200)
    }

    private fun najdi(): Chiaki.Nalez? {
        val nalezene = runCatching { Chiaki.hledej(1500) }.getOrDefault(emptyList())
        return nalezene.firstOrNull { it.jmeno.equals(konzole.jmeno, ignoreCase = true) } ?: nalezene.singleOrNull()
    }

    /** Běží, dokud se nezavolá [zastav]. */
    fun bez() {
        var kratkych = 0
        var poSpojeni = false
        try {
            while (!konec) {
                val n = najdi()
                if (konec) break
                when {
                    n == null -> { faze = Faze.NENALEZENA; cekej(4000); continue }
                    !n.pripravena -> {
                        if (faze != Faze.KLID) log("PS5 · ${konzole.jmeno} · klidový režim · čeká se na zapnutí ovladačem")
                        faze = Faze.KLID; cekej(4000); continue
                    }
                }
                // po skončeném spojení: PS5 mohla právě usínat – připojit až po druhém „zapnutá“
                if (poSpojeni) {
                    cekej(3000)
                    if (najdi()?.pripravena != true) continue
                    poSpojeni = false
                }
                if (konec) break
                faze = Faze.PRIPOJOVANI
                val puvodni = Chiaki.aktualniProfil()
                runCatching { Chiaki.pripravProfil() }.onFailure { log("chiaki-ng: profil čtečky – ${it.message}") }
                val p = Chiaki.hraj(exe, konzole.jmeno, n!!.adresa, Chiaki.PROFIL_CTECKY, celaObrazovka = false)
                proces = p
                if (konec) p.destroy()
                val zacatek = System.currentTimeMillis()
                log("chiaki-ng: ${konzole.jmeno} (${n.adresa}) · účet pro Titulky PS5 · bez zvuku")
                val o = OknoHry("pid:${p.pid()}")
                for (i in 1..60) {
                    if (o.existuje() || !p.isAlive || konec) break
                    Thread.sleep(500)
                }
                Chiaki.obnovProfil(puvodni)
                if (p.isAlive && !konec) {
                    okno = o
                    oknoNazev = "pid:${p.pid()}"
                    ctecka()?.okno(oknoNazev)
                    faze = Faze.BEZI
                }
                p.waitFor()
                okno = null
                proces = null
                oknoNazev = CEKA_NA_OKNO
                ctecka()?.okno(CEKA_NA_OKNO)
                Chiaki.obnovProfil(puvodni)
                if (konec) break
                val s = (System.currentTimeMillis() - zacatek) / 1000
                log("chiaki-ng skončil po $s s")
                runCatching { File(Nastroje.slozka, "chiaki-vystup.txt").readLines().takeLast(5).forEach { log("chiaki-ng: $it") } }
                if (s < 60) kratkych++ else kratkych = 0
                if (kratkych >= 4) {
                    chyba = "chiaki-ng · spojení s PS5 se nepovedlo"
                    faze = Faze.CHYBA
                    log("Spojení s PS5 4× za sebou skončilo do minuty · další pokusy zastaveny")
                    return
                }
                poSpojeni = true
                faze = Faze.HLEDANI
                cekej(15_000)
            }
        } catch (e: Exception) {
            chyba = "chiaki-ng · ${e.message ?: e.javaClass.simpleName}"
            faze = Faze.CHYBA
            log("Čtečka pro TV: ${e.message}")
        } finally {
            proces?.destroy()
            proces = null
            okno = null
            oknoNazev = CEKA_NA_OKNO
        }
    }
}
