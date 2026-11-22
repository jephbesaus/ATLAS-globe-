package com.atlasglobe.app.data

import android.content.Context
import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement de bout en bout.
 *  - Identité : paire de clés ECDH P-256 créée sur le téléphone (aucun serveur).
 *  - Entre deux personnes : secret partagé ECDH + HMAC-SHA256 -> clé AES-256-GCM.
 *  - Groupe : clé AES-256 de groupe, distribuée à chaque membre dans un message chiffré individuellement.
 * Les relais voient seulement l'en-tête (expéditeur, destinataire), jamais le contenu.
 */
object Crypto {
    private var priv: PrivateKey? = null
    private var pub: PublicKey? = null
    private val rnd = SecureRandom()
    private val cache = HashMap<String, ByteArray>()

    @Synchronized fun init(c: Context) {
        if (priv != null) return
        val p = c.applicationContext.getSharedPreferences("atlas_keys", Context.MODE_PRIVATE)
        val kf = KeyFactory.getInstance("EC")
        val s = p.getString("priv", null); val u = p.getString("pub", null)
        if (s != null && u != null) {
            priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(s, Base64.NO_WRAP)))
            pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(u, Base64.NO_WRAP)))
        } else {
            val g = KeyPairGenerator.getInstance("EC"); g.initialize(ECGenParameterSpec("secp256r1"))
            val kp = g.generateKeyPair(); priv = kp.private; pub = kp.public
            p.edit().putString("priv", Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP))
                .putString("pub", Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)).apply()
        }
    }

    fun publicB64(): String = Base64.encodeToString(pub!!.encoded, Base64.NO_WRAP)

    @Synchronized fun shared(peerPubB64: String): ByteArray = cache.getOrPut(peerPubB64) {
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(peerPubB64, Base64.NO_WRAP)))
        val ka = KeyAgreement.getInstance("ECDH"); ka.init(priv); ka.doPhase(peer, true)
        val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec("atlas-v1".toByteArray(), "HmacSHA256"))
        mac.doFinal(ka.generateSecret())
    }

    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    fun open(key: ByteArray, blob: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        c.doFinal(blob, 12, blob.size - 12)
    } catch (e: Exception) { null }

    fun sign(data: ByteArray): ByteArray {
        val s = Signature.getInstance("SHA256withECDSA"); s.initSign(priv); s.update(data); return s.sign()
    }
    fun verify(pubB64: String, data: ByteArray, sig: ByteArray): Boolean = try {
        val pk = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(pubB64, Base64.NO_WRAP)))
        val s = Signature.getInstance("SHA256withECDSA"); s.initVerify(pk); s.update(data); s.verify(sig)
    } catch (e: Exception) { false }

    fun validKey(b64: String): Boolean = try { KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(b64 + "=".repeat((4 - b64.length % 4) % 4), Base64.NO_WRAP))); true } catch (e: Exception) { false }

    fun newGroupKey(): String = Base64.encodeToString(ByteArray(32).also { rnd.nextBytes(it) }, Base64.NO_WRAP)
    fun keyBytes(b64: String): ByteArray = Base64.decode(b64, Base64.NO_WRAP)
}
