package com.atlasglobe.app.data

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Profile(
    val nom: String, val prenom: String, val phone: String,
    val continent: String, val country: String, val province: String, val city: String,
    val photoPath: String?, val atlasId: String
)

/** Profil local (aucun serveur, 0 Mo). Le PIN n'est jamais stocké en clair. */
class ProfileStore(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("atlas_profile", Context.MODE_PRIVATE)

    val isRegistered: Boolean get() = p.getBoolean("registered", false)

    fun save(pr: Profile, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.edit()
            .putString("nom", pr.nom).putString("prenom", pr.prenom).putString("phone", pr.phone)
            .putString("continent", pr.continent).putString("country", pr.country)
            .putString("province", pr.province).putString("city", pr.city)
            .putString("photo", pr.photoPath).putString("atlas_id", pr.atlasId)
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", hash(pin, salt))
            .putBoolean("registered", true)
            .apply()
    }

    fun load(): Profile? = if (!isRegistered) null else Profile(
        p.getString("nom", "") ?: "", p.getString("prenom", "") ?: "", p.getString("phone", "") ?: "",
        p.getString("continent", "") ?: "", p.getString("country", "") ?: "",
        p.getString("province", "") ?: "", p.getString("city", "") ?: "",
        p.getString("photo", null), p.getString("atlas_id", "") ?: ""
    )

    // ---- Sécurité (réglages modifiables dans Paramètres > Sécurité) ----
    var lockEnabled: Boolean
        get() = p.getBoolean("lock_enabled", true)
        set(v) { p.edit().putBoolean("lock_enabled", v).apply() }
    var biometricEnabled: Boolean
        get() = p.getBoolean("biometric_enabled", true)
        set(v) { p.edit().putBoolean("biometric_enabled", v).apply() }
    var failCount: Int
        get() = p.getInt("fail_count", 0)
        set(v) { p.edit().putInt("fail_count", v).apply() }
    var lockUntil: Long
        get() = p.getLong("lock_until", 0L)
        set(v) { p.edit().putLong("lock_until", v).apply() }

    // ---- Revendeur : AUCUNE interface tant que le panel admin n'a pas approuvé ----
    /** Reçu uniquement par la synchronisation serveur (approbation admin). Jamais modifiable par l'utilisateur. */
    val resellerApproved: Boolean get() = p.getBoolean("reseller_approved", false)
    fun setResellerApprovedFromServer(v: Boolean) { p.edit().putBoolean("reseller_approved", v).apply(); ResellerAccess.approved = v }

    var bio: String
        get() = p.getString("bio", "") ?: ""
        set(v) { p.edit().putString("bio", v).apply() }
    var email: String
        get() = p.getString("email", "") ?: ""
        set(v) { p.edit().putString("email", v).apply() }
    var numberConfirmed: Boolean
        get() = p.getBoolean("number_confirmed", false)
        set(v) { p.edit().putBoolean("number_confirmed", v).apply() }

    fun updateProfile(nom: String, prenom: String, phone: String, photoPath: String?) {
        p.edit().putString("nom", nom).putString("prenom", prenom).putString("phone", phone)
            .apply { if (photoPath != null) putString("photo", photoPath) }.apply()
    }
    fun changePin(old: String, new: String): Boolean {
        if (!verifyPin(old)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.edit().putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP)).putString("pin_hash", hash(new, salt)).apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val s = p.getString("pin_salt", null) ?: return false
        val h = p.getString("pin_hash", null) ?: return false
        return hash(pin, Base64.decode(s, Base64.NO_WRAP)) == h
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 20000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    companion object {
        /** ID Atlas lisible : ATL-XXXX-XXXX (la vraie paire de clés arrive à l'étape 6). */
        fun newAtlasId(): String {
            val a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; val r = SecureRandom()
            fun part() = (1..4).map { a[r.nextInt(a.length)] }.joinToString("")
            return "ATL-${part()}-${part()}"
        }
    }
}

/** Accès Revendeur Wi-Fi (réactif pour l'interface). Rien n'est visible tant que ce n'est pas approuvé par le panel admin. */
object ResellerAccess {
    var approved by androidx.compose.runtime.mutableStateOf(false)
}
