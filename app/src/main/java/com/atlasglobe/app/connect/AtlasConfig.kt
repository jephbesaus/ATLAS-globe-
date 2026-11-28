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
    const val ASSISTANT_PUB = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETiRgKrL0/jlB+SIVFc1bhPlySmrlZElwJOpqCl7AB2MF1rc6LaXTI7HQf6nCVGvIUkyY4suq/8xU9DXRz9GofA=="          // base64, fournie par le panel admin
    const val ADMIN_PUB = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETiRgKrL0/jlB+SIVFc1bhPlySmrlZElwJOpqCl7AB2MF1rc6LaXTI7HQf6nCVGvIUkyY4suq/8xU9DXRz9GofA=="              // clé qui signe les jetons d'accès (panel admin)
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

/** Avatar officiel de l'assistant Atlas : une silhouette noir et blanc générée une seule fois sur le disque. */
object AssistantAvatar {
    private var cached: String? = null
    fun path(c: Context): String {
        cached?.let { return it }
        val f = java.io.File(c.filesDir, "assistant_avatar.png")
        if (!f.exists()) {
            val size = 256
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(android.graphics.Color.BLACK)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
            canvas.drawCircle(size / 2f, size * 0.37f, size * 0.20f, p)
            val path = android.graphics.Path()
            path.addRoundRect(android.graphics.RectF(size * 0.22f, size * 0.58f, size * 0.78f, size * 1.05f),
                size * 0.28f, size * 0.28f, android.graphics.Path.Direction.CW)
            canvas.drawPath(path, p)
            runCatching { f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        }
        return f.absolutePath.also { cached = it }
    }
}
