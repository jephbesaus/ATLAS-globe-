package com.atlasglobe.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Annonce officielle affichée DEVANT l'application jusqu'à ce que l'utilisateur appuie sur Retour. */
object AnnouncementState {
    private var ctx: Context? = null
    var current by mutableStateOf<Announcement?>(null); private set

    fun init(c: Context) {
        ctx = c.applicationContext
        val p = prefs() ?: return
        if (!p.getBoolean("seen", true)) current = Announcement(
            p.getString("id", "") ?: "", p.getString("title", "") ?: "",
            p.getString("body", "") ?: "", p.getString("wa", "") ?: "", p.getString("tg", "") ?: "", p.getString("sp", "") ?: "")
    }

    /** Appelé par la connexion serveur (panel admin) ou le mesh. */
    fun receive(c: Context, a: Announcement) {
        ctx = c.applicationContext
        prefs()?.edit()?.putString("id", a.id)?.putString("title", a.title)?.putString("body", a.body)
            ?.putString("wa", a.whatsapp)?.putString("tg", a.telegram)?.putString("sp", a.support)?.putBoolean("seen", false)?.apply()
        current = a
    }

    fun dismiss() { prefs()?.edit()?.putBoolean("seen", true)?.apply(); current = null }

    private fun prefs() = ctx?.getSharedPreferences("atlas_announcement", Context.MODE_PRIVATE)
}

/** Page à ouvrir quand l'utilisateur touche une notification. */
object PendingOpen { var route by mutableStateOf<String?>(null) }


/** Compte bloqué ou débloqué par l'administration (message signé d'Atlas Assistant). */
object AccountState {
    private var ctx: Context? = null
    var blocked by androidx.compose.runtime.mutableStateOf(false); private set
    var reason by androidx.compose.runtime.mutableStateOf(""); private set
    fun init(c: Context) { ctx = c.applicationContext; val p = prefs(); blocked = p?.getBoolean("blocked", false) ?: false; reason = p?.getString("reason", "") ?: "" }
    fun set(c: Context, on: Boolean, why: String) { ctx = c.applicationContext
        prefs()?.edit()?.putBoolean("blocked", on)?.putString("reason", why)?.apply(); blocked = on; reason = why }
    private fun prefs() = ctx?.getSharedPreferences("atlas_account", Context.MODE_PRIVATE)
}
