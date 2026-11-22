package com.atlasglobe.app.connect

import android.util.Base64
import com.atlasglobe.app.data.Crypto
import org.json.JSONObject

/**
 * Jeton d'accès signé par Atlas (panel admin) : "payload.signature" en base64.
 * payload = {"tid","u"(ID Atlas de l'acheteur),"p"(offre),"dur"(ms),"mb","iat"}  signature = ECDSA SHA-256 (DER) du payload.
 * Un revendeur le vérifie SANS Internet avec la clé publique d'Atlas.
 */
data class AccessToken(val tid: String, val user: String, val plan: String, val durMs: Long, val mb: Int, val iat: Long)

object TokenCodec {
    fun parse(raw: String): AccessToken? = try {
        val parts = raw.split(".")
        if (parts.size != 2) null else {
            val payload = Base64.decode(parts[0], Base64.NO_WRAP)
            val sig = Base64.decode(parts[1], Base64.NO_WRAP)
            val ok = AtlasConfig.ALLOW_UNSIGNED_TOKENS || (AtlasConfig.pub().isNotEmpty() && Crypto.verify(AtlasConfig.pub(), payload, sig))
            if (!ok) null else JSONObject(String(payload)).let {
                AccessToken(it.getString("tid"), it.getString("u"), it.optString("p", ""), it.getLong("dur"), it.optInt("mb", 0), it.optLong("iat", 0)) }
        }
    } catch (e: Exception) { null }
}
