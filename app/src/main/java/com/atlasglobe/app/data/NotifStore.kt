package com.atlasglobe.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Liste des notifications (cloche). Le compteur se remet à zéro à l'ouverture de la page. */
object NotifStore {
    private var file: File? = null
    var items by mutableStateOf<List<AppNotification>>(emptyList()); private set
    val unread: Int get() = items.count { !it.read }

    @Synchronized fun init(ctx: Context) {
        if (file != null) return
        file = File(ctx.applicationContext.filesDir, "notifs.json")
        items = runCatching {
            val a = JSONArray(file!!.readText())
            (0 until a.length()).map { val o = a.getJSONObject(it)
                AppNotification(o.getString("id"), o.getString("type"), o.getString("title"),
                    o.getString("text"), o.getLong("t"), o.getBoolean("read")) }
        }.getOrDefault(emptyList())
    }

    @Synchronized fun add(type: String, title: String, text: String) {
        items = (listOf(AppNotification(UUID.randomUUID().toString(), type, title, text,
            System.currentTimeMillis(), false)) + items).take(100)
        save()
    }

    @Synchronized fun markAllRead() { items = items.map { it.copy(read = true) }; save() }

    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("type", it.type).put("title", it.title)
            .put("text", it.text).put("t", it.createdAt).put("read", it.read)) }
        runCatching { file?.writeText(a.toString()) }
    }
}
