package com.atlasglobe.app.connect

import android.content.Context
import com.atlasglobe.app.data.ChatRepo
import com.atlasglobe.app.mesh.MeshStore

/**
 * Compte officiel "Atlas Assistant" (administrateur). Son e-mail n'apparaît jamais dans l'application.
 * À REMPLIR après la création du compte admin : ASSISTANT_PUB = clé publique affichée par le panel admin.
 */
object AtlasConfig {
    const val ASSISTANT_ID = "ATL-ASST-0001"
    const val ASSISTANT_NAME = "Atlas Assistant"
    const val ASSISTANT_PUB = ""          // base64, fournie par le panel admin
    const val ADMIN_PUB = ""              // clé qui signe les jetons d'accès (panel admin)
    const val SPEEDTEST_URL = "https://speed.cloudflare.com/__down?bytes=3000000"   // à remplacer par le serveur Atlas
    const val ALLOW_UNSIGNED_TOKENS = false   // true seulement pour les essais avant la création du panel admin

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("atlas_server", Context.MODE_PRIVATE)
    private var saved = ""
    /** Clé publique d'Atlas : celle écrite dans l'application, sinon celle reçue du serveur à l'inscription. */
    fun pub(): String = if (ASSISTANT_PUB.isNotEmpty()) ASSISTANT_PUB else saved
    fun savePub(c: Context, k: String) { saved = k; sp(c).edit().putString("pub", k).apply(); MeshStore.setKey(ASSISTANT_ID, k, ASSISTANT_NAME) }

    fun install(c: Context) {
        ChatRepo.init(c)
        ChatRepo.addContact(ASSISTANT_ID, ASSISTANT_NAME)
        if (saved.isEmpty()) saved = sp(c).getString("pub", "") ?: ""
        if (pub().isNotEmpty()) MeshStore.setKey(ASSISTANT_ID, pub(), ASSISTANT_NAME)
    }
}
