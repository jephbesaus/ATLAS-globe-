package com.atlasglobe.app.call

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.atlasglobe.app.R
import com.atlasglobe.app.data.Api
import com.atlasglobe.app.data.Net
import com.atlasglobe.app.notify.NotificationHelper
import com.atlasglobe.app.settings.SettingsStore
import org.json.JSONObject
import java.net.URI

/** Appels vocaux (internet seulement) : signalisation par le serveur Atlas, audio relayé en UDP. */
object CallManager {
    enum class State { IDLE, OUTGOING, INCOMING, ACTIVE }
    private val main = Handler(Looper.getMainLooper())
    var state by mutableStateOf(State.IDLE); private set
    var peer by mutableStateOf(""); private set
    var info by mutableStateOf(""); private set
    var muted by mutableStateOf(false); private set
    var speaker by mutableStateOf(false); private set
    var seconds by mutableIntStateOf(0); private set
    private var id = ""
    private var audio: AudioEngine? = null
    private var ring: MediaPlayer? = null
    private var startedAt = 0L
    private val tickRun: Runnable = object : Runnable { override fun run() { if (state == State.ACTIVE) { seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt(); main.postDelayed(this, 1000) } } }

    fun canCall(c: Context): String? = when {
        state != State.IDLE -> "Un appel est déjà en cours"
        !Net.isOnline(c) -> "Vous devez être connecté à Internet pour appeler"
        !Api.configured() -> "Le serveur Atlas n'est pas encore configuré"
        else -> null
    }

    fun start(c: Context, to: String) {
        val app = c.applicationContext
        main.post { peer = to; info = "Appel en cours…"; state = State.OUTGOING; seconds = 0; muted = false; speaker = false; playRing(app, soft = true) }
        Thread {
            val r = Api.signed("POST", "/v1/call/start", JSONObject().put("to", to))
            if (r.ok) id = r.json?.optString("id") ?: "" else main.post { endLocal(app, if (r.json?.optString("error") == "busy") "Ligne occupée" else "Ce contact n'est pas joignable") }
        }.start()
    }

    fun accept(c: Context) { val app = c.applicationContext; val cid = id; NotificationManagerCompat.from(app).cancel(NOTIF)
        stopRing(); info = "Connexion…"; Thread { Api.signed("POST", "/v1/call/accept", JSONObject().put("id", cid)) }.start() }
    fun reject(c: Context) { val app = c.applicationContext; val cid = id; Thread { Api.signed("POST", "/v1/call/reject", JSONObject().put("id", cid)) }.start(); endLocal(app, "") }
    fun end(c: Context) { val app = c.applicationContext; val cid = id; Thread { Api.signed("POST", "/v1/call/end", JSONObject().put("id", cid)) }.start(); endLocal(app, "") }
    fun toggleMute() { muted = !muted; audio?.muted = muted }
    fun toggleSpeaker() { speaker = !speaker; audio?.setSpeaker(speaker) }

    /** Événements du serveur (attente longue). */
    fun onEvent(c: Context, e: JSONObject) {
        val app = c.applicationContext
        main.post {
            when (e.optString("type")) {
                "incoming" -> {
                    SettingsStore.init(app)
                    if (state != State.IDLE || !SettingsStore.allowCalls) { Thread { Api.signed("POST", "/v1/call/reject", JSONObject().put("id", e.optString("id"))) }.start(); return@post }
                    id = e.optString("id"); peer = e.optString("from"); info = "Appel entrant"; state = State.INCOMING; seconds = 0; muted = false; speaker = false
                    playRing(app, soft = false); NotificationHelper.incomingCall(app, com.atlasglobe.app.data.ChatRepo.nameOf(peer))
                }
                "accepted" -> if (e.optString("id") == id) {
                    stopRing(); NotificationManagerCompat.from(app).cancel(NOTIF)
                    val host = runCatching { URI(Api.SERVER_URL).host }.getOrNull() ?: return@post
                    val eng = AudioEngine(app, host, e.optInt("port"), e.optString("token")); audio = eng
                    state = State.ACTIVE; info = ""; startedAt = System.currentTimeMillis(); seconds = 0; main.postDelayed(tickRun, 1000)
                    runCatching { ContextCompat.startForegroundService(app, Intent(app, CallService::class.java)) }
                    Thread { runCatching { eng.start() }.onFailure { main.post { endLocal(app, "Audio indisponible") } } }.start()
                }
                "ended" -> if (e.optString("id") == id) endLocal(app, when (e.optString("reason")) { "rejected" -> "Appel refusé"; "timeout" -> "Pas de réponse"; "lost" -> "Connexion perdue"; else -> "" })
            }
        }
    }

    private fun endLocal(c: Context, why: String) {
        stopRing(); audio?.stop(); audio = null; runCatching { c.stopService(Intent(c, CallService::class.java)) }
        NotificationManagerCompat.from(c).cancel(NOTIF); main.removeCallbacks(tickRun)
        info = why; state = State.IDLE; id = ""
        if (why.isNotEmpty()) android.widget.Toast.makeText(c, why, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun playRing(c: Context, soft: Boolean) {
        stopRing()
        ring = runCatching { MediaPlayer.create(c, R.raw.atlas_call, AudioAttributes.Builder().setUsage(if (soft) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(), (c.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).generateAudioSessionId())
            ?.apply { isLooping = true; setVolume(if (soft) 0.4f else 1f, if (soft) 0.4f else 1f); start() } }.getOrNull()
    }
    private fun stopRing() { runCatching { ring?.stop(); ring?.release() }; ring = null }

    const val NOTIF = 7002
}
