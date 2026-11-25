package cz.recaption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import cz.recaption.core.AtvInstall
import cz.recaption.core.LgIpk
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Co si telefon pamatuje o nahrávání do Android TV (core/AtvInstall.kt):
 *  - vlastní klíč ADB tohoto telefonu (RSA 2048) – soukromá část jen zašifrovaná klíčem z Android
 *    Keystore (AES-GCM, stejně jako [LgVault]; zálohování aplikace je vypnuté), veřejná část v adbkey.pub tvaru,
 *  - poslední adresu, a pro každou televizi (podle adresy) jestli klíč už jednou přijala, model a verzi ReCaptionu.
 */
class AtvVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("atv_tv", Context.MODE_PRIVATE)
    private fun k(ip: String) = LgIpk.sha256(ip.toByteArray()).take(24)

    var lastIp: String
        get() = prefs.getString("ip", "") ?: ""
        set(v) = prefs.edit().putString("ip", v).apply()

    /** Televize už „Povolit ladění“ jednou potvrdila (tento klíč zná). */
    fun allowed(ip: String) = prefs.getBoolean(k(ip) + ".ok", false)
    fun setAllowed(ip: String, ok: Boolean) = prefs.edit().putBoolean(k(ip) + ".ok", ok).apply()

    fun model(ip: String): String? = prefs.getString(k(ip) + ".model", null)
    fun setModel(ip: String, m: String) = prefs.edit().putString(k(ip) + ".model", m).apply()
    fun tvVersion(ip: String): String? = prefs.getString(k(ip) + ".ver", null)
    /** Číslo verze ReCaptionu v TV při posledním spojení (0 = neznámé). */
    fun tvCode(ip: String): Long = prefs.getLong(k(ip) + ".code", 0)
    fun setTvVersion(ip: String, v: String, code: Long) = prefs.edit().putString(k(ip) + ".ver", v).putLong(k(ip) + ".code", code).apply()

    /** Klíč ADB tohoto telefonu (při prvním použití se vytvoří): soukromý klíč a obsah adbkey.pub. */
    fun adbKey(): Pair<PrivateKey, String> = synchronized(LOCK) {
        val sealed = prefs.getString("key", null)
        val pub = prefs.getString("pub", null)
        if (sealed != null && pub != null) {
            val der = LgVault.open(secret(), "atv:key", sealed)
            try { return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der)) to pub } finally { der.fill(0) }
        }
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val rsa = pair.public as RSAPublicKey
        val pubText = AtvInstall.adbPublicKey(rsa.modulus, rsa.publicExponent)
        val der = pair.private.encoded
        try {
            val e = prefs.edit().putString("key", LgVault.seal(secret(), "atv:key", der)).putString("pub", pubText)
            // nový klíč = žádná televize ho ještě nezná
            prefs.all.keys.filter { it.endsWith(".ok") }.forEach { e.remove(it) }
            e.apply()
        } finally { der.fill(0) }
        return pair.private to pubText
    }

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    companion object {
        private const val ALIAS = "recaption-atv-v1"
        private val LOCK = Any()
    }
}
