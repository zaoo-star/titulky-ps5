package cz.recaption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import cz.recaption.core.LgDevTime
import cz.recaption.core.LgIpk
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Co si aplikace pamatuje o televizi LG ve vývojářském režimu (podle adresy televize):
 *  - otisk klíče televize (veřejný údaj; změna se odmítne, dokud ji uživatel nepotvrdí),
 *  - soukromý klíč SSH a klíč relace vývojářského režimu – jen zašifrované klíčem z Android
 *    Keystore (AES-GCM, klíč neopustí zařízení; zálohování aplikace je vypnuté),
 *  - poslední zjištěný zbývající čas vývojářského režimu.
 * Passphrase se nikam neukládá.
 *
 * Podle LgDevUloziste.kt z projektu PlayCaption (AGPL-3.0), upraveno pro ReCaption.
 */
class LgVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lg_tv", Context.MODE_PRIVATE)
    private fun k(ip: String) = LgIpk.sha256(ip.toByteArray()).take(24)

    var lastIp: String
        get() = prefs.getString("ip", "") ?: ""
        set(v) = prefs.edit().putString("ip", v).apply()

    fun hostKey(ip: String): String? = prefs.getString(k(ip) + ".host", null)
    fun paired(ip: String) = prefs.contains(k(ip) + ".ssh") && hostKey(ip) != null

    fun sshKey(ip: String): ByteArray? = prefs.getString(k(ip) + ".ssh", null)?.let { runCatching { open(key(), "$ip:ssh", it) }.getOrNull() }

    fun savePairing(ip: String, host: String, sshKey: ByteArray) {
        require(sshKey.size in 256..8192)
        prefs.edit().putString(k(ip) + ".ssh", seal(key(), "$ip:ssh", sshKey)).putString(k(ip) + ".host", host).apply()
    }

    fun forget(ip: String) {
        val p = k(ip)
        prefs.edit().remove("$p.ssh").remove("$p.host").remove("$p.token").remove("$p.secs").remove("$p.checked").remove("$p.renew").apply()
    }

    fun token(ip: String): String? = prefs.getString(k(ip) + ".token", null)?.let { runCatching { String(open(key(), "$ip:token", it), Charsets.UTF_8) }.getOrNull() }

    /** Nový klíč relace (po prodloužení nebo novém zapnutí režimu) zneplatní poslední zjištěný čas. */
    fun saveToken(ip: String, token: String) {
        if (token(ip) == token) return
        val p = k(ip)
        prefs.edit().putString("$p.token", seal(key(), "$ip:token", token.toByteArray(Charsets.UTF_8)))
            .remove("$p.secs").remove("$p.checked").remove("$p.try").apply()
    }

    fun devTime(ip: String): LgDevTime? {
        val p = k(ip)
        return if (prefs.contains("$p.secs")) LgDevTime(prefs.getLong("$p.secs", 0), prefs.getLong("$p.checked", 0)) else null
    }
    fun saveDevTime(ip: String, t: LgDevTime) { val p = k(ip); prefs.edit().putLong("$p.secs", t.seconds).putLong("$p.checked", t.checkedAt).apply() }
    fun lastTry(ip: String) = prefs.getLong(k(ip) + ".try", 0)
    fun setLastTry(ip: String, t: Long) = prefs.edit().putLong(k(ip) + ".try", t).apply()
    /** Poslední pokus o prodloužení vývojářského režimu (volba „Prodlužovat vývojářský režim“). */
    fun renewTry(ip: String) = prefs.getLong(k(ip) + ".renew", 0)
    fun setRenewTry(ip: String, t: Long) = prefs.edit().putLong(k(ip) + ".renew", t).apply()

    /** Verze ReCaption v televizi při posledním spojení (jen pro informaci). */
    fun tvVersion(ip: String): String? = prefs.getString(k(ip) + ".ver", null)
    fun setTvVersion(ip: String, v: String) = prefs.edit().putString(k(ip) + ".ver", v).apply()

    private fun key(): SecretKey = synchronized(LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    companion object {
        private const val ALIAS = "recaption-lg-v1"
        private val LOCK = Any()

        /** AES-GCM; aad váže zašifrovaný údaj na televizi a účel, takže se nedá přehodit jinam. */
        fun seal(key: SecretKey, aad: String, data: ByteArray): String {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key)
            c.updateAAD(aad.toByteArray())
            return Base64.getEncoder().encodeToString(c.iv + c.doFinal(data))
        }

        fun open(key: SecretKey, aad: String, text: String): ByteArray {
            val b = Base64.getDecoder().decode(text)
            require(b.size > 28)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b, 0, 12))
            c.updateAAD(aad.toByteArray())
            return c.doFinal(b, 12, b.size - 12)
        }
    }
}
