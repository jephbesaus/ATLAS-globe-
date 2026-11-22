package com.atlasglobe.app.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Réglages locaux : confidentialité, fond de discussion, certification du compte. */
object SettingsStore {
    private lateinit var p: android.content.SharedPreferences
    var presence by mutableStateOf(true); private set
    var showPhone by mutableStateOf(true); private set
    var showEmail by mutableStateOf(true); private set
    var readReceipts by mutableStateOf(true); private set
    var allowCalls by mutableStateOf(true); private set
    var chatBg by mutableStateOf("black"); private set        // black | grey | white | blue | photo:<chemin>
    var certLevel by mutableStateOf(0); private set            // 0 aucune, 1 Standard, 2 Premium, 3 Pro
    var certNote by mutableStateOf(""); private set             // "pending:2" ou "rejected:motif"

    fun init(c: Context) {
        if (::p.isInitialized) return
        p = c.applicationContext.getSharedPreferences("atlas_settings", Context.MODE_PRIVATE)
        presence = p.getBoolean("presence", true); showPhone = p.getBoolean("show_phone", true); showEmail = p.getBoolean("show_email", true)
        readReceipts = p.getBoolean("read", true); allowCalls = p.getBoolean("calls", true)
        chatBg = p.getString("chat_bg", "black") ?: "black"; certLevel = p.getInt("cert", 0); certNote = p.getString("cert_note", "") ?: ""
    }
    fun changePresence(v: Boolean) { presence = v; p.edit().putBoolean("presence", v).apply() }
    fun changeShowPhone(v: Boolean) { showPhone = v; p.edit().putBoolean("show_phone", v).apply() }
    fun changeShowEmail(v: Boolean) { showEmail = v; p.edit().putBoolean("show_email", v).apply() }
    fun changeReadReceipts(v: Boolean) { readReceipts = v; p.edit().putBoolean("read", v).apply() }
    fun changeAllowCalls(v: Boolean) { allowCalls = v; p.edit().putBoolean("calls", v).apply() }
    fun changeChatBg(v: String) { chatBg = v; p.edit().putString("chat_bg", v).apply() }
    /** Reçu d'Atlas Assistant après paiement et vérification. */
    fun changeCertNote(v: String) { certNote = v; p.edit().putString("cert_note", v).apply() }
    fun setCert(level: Int) { certLevel = level; p.edit().putInt("cert", level).apply() }
}

object Certification {
    val names = listOf("", "Standard", "Premium", "Pro")
    val prices = listOf("", "2,5 $/mois", "10 $/mois", "20 $/mois")
    val perks = listOf(emptyList(),
        listOf("Badge de certification, identité vérifiée", "Protection contre l'usurpation de votre nom", "Groupes jusqu'à 100 membres (gratuit : 30)",
            "Stories visibles 48 h (gratuit : 24 h)", "1 forfait Wi-Fi de 30 min offert par mois", "Support sous 48 h"),
        listOf("Tout Standard, plus :", "Groupes jusqu'à 256 membres", "Statistiques de vos publications (vues, abonnés)", "Publication épinglée sur votre profil",
            "Priorité dans la recherche et les suggestions", "2 forfaits Wi-Fi de 2 h offerts par mois", "Publications programmées, thèmes de discussion exclusifs", "Support sous 24 h"),
        listOf("Tout Premium, plus :", "Profil professionnel : commerce, adresse, horaires, bouton WhatsApp", "Catalogue de 20 produits",
            "4 publications boostées par mois dans votre quartier", "Canal de diffusion : annonces à des abonnés sans limite", "Groupes jusqu'à 1000 membres",
            "Statistiques détaillées", "2 forfaits Wi-Fi de 8 h offerts par mois", "Support prioritaire sous 4 h"))
}
