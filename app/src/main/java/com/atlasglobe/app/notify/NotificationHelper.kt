package com.atlasglobe.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.atlasglobe.app.MainActivity
import com.atlasglobe.app.R
import com.atlasglobe.app.data.Announcement
import com.atlasglobe.app.data.AnnouncementState
import com.atlasglobe.app.data.NotifStore

/** Notifications propres à Atlas : 5 catégories, chacune avec le son choisi par l'utilisateur. */
object NotificationHelper {
    const val CH_SERVICE = "atlas_service"

    class Category(val id: String, val label: String, val defaultSound: String, val importance: Int)
    val categories = listOf(
        Category("messages", "Messages", "atlas_message", NotificationManager.IMPORTANCE_HIGH),
        Category("calls", "Appels", "atlas_call", NotificationManager.IMPORTANCE_HIGH),
        Category("posts", "Publications et stories", "atlas_soft", NotificationManager.IMPORTANCE_DEFAULT),
        Category("connect", "Connectivité", "atlas_soft", NotificationManager.IMPORTANCE_DEFAULT),
        Category("announce", "Annonces Atlas", "atlas_alert", NotificationManager.IMPORTANCE_HIGH))
    val sounds = listOf("atlas_message" to "Atlas Message", "atlas_soft" to "Atlas Doux", "atlas_alert" to "Atlas Alerte",
        "atlas_call" to "Atlas Appel", "system" to "Son du téléphone", "silent" to "Silencieux")

    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private fun prefs(c: Context) = c.getSharedPreferences("atlas_notif", Context.MODE_PRIVATE)
    private fun cat(id: String) = categories.first { it.id == id }

    fun soundOf(c: Context, catId: String): String = prefs(c).getString("snd_$catId", cat(catId).defaultSound) ?: cat(catId).defaultSound
    private fun channelId(c: Context, catId: String) = "atlas_${catId}_${soundOf(c, catId)}"

    private fun soundUri(c: Context, s: String): Uri? = when (s) {
        "silent" -> null
        "system" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else -> Uri.parse("android.resource://${c.packageName}/raw/$s")
    }

    /** Un canal Android ne change plus de son une fois créé : un canal par (catégorie, son). */
    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_SERVICE) == null) nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Atlas actif", NotificationManager.IMPORTANCE_MIN).apply { setSound(null, null); setShowBadge(false) })
        categories.forEach { k ->
            val id = channelId(c, k.id)
            if (nm.getNotificationChannel(id) == null) nm.createNotificationChannel(NotificationChannel(id, k.label, k.importance).apply {
                val u = soundUri(c, soundOf(c, k.id)); if (u != null) setSound(u, attrs) else setSound(null, null)
                enableVibration(u != null)
            })
        }
    }

    fun setSound(c: Context, catId: String, sound: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val old = channelId(c, catId)
        prefs(c).edit().putString("snd_$catId", sound).apply()
        runCatching { nm.deleteNotificationChannel(old) }
        createChannels(c)
    }

    /** Écoute d'un son dans les réglages. */
    fun preview(c: Context, sound: String) {
        val u = soundUri(c, sound) ?: return
        runCatching { MediaPlayer().apply { setAudioAttributes(attrs); setDataSource(c, u); prepare(); setOnCompletionListener { it.release() }; start() } }
    }

    fun openIntent(c: Context, open: String, req: Int): PendingIntent = PendingIntent.getActivity(
        c, req, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open", open), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun show(c: Context, catId: String, id: Int, title: String, text: String, open: String) {
        createChannels(c)
        val n = NotificationCompat.Builder(c, channelId(c, catId))
            .setSmallIcon(R.drawable.ic_stat_atlas).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openIntent(c, open, id)).build()
        runCatching { if (NotificationManagerCompat.from(c).areNotificationsEnabled()) NotificationManagerCompat.from(c).notify(id, n) }
    }

    /** Message reçu : visible même application fermée, avec réponse directe. */
    fun message(c: Context, sender: String, text: String, conversationId: String) {
        createChannels(c)
        val id = ("m" + conversationId).hashCode()
        val ri = androidx.core.app.RemoteInput.Builder("reply").setLabel("Répondre").build()
        val replyPi = PendingIntent.getBroadcast(c, id,
            Intent(c, ReplyReceiver::class.java).putExtra("conv", conversationId).putExtra("nid", id),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_atlas, "Répondre", replyPi)
            .addRemoteInput(ri).setAllowGeneratedReplies(false).build()
        val n = NotificationCompat.Builder(c, channelId(c, "messages"))
            .setSmallIcon(R.drawable.ic_stat_atlas).setContentTitle(sender).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openIntent(c, "chat:$conversationId", id)).addAction(action).build()
        runCatching { if (NotificationManagerCompat.from(c).areNotificationsEnabled()) NotificationManagerCompat.from(c).notify(id, n) }
    }

    /** Appel entrant : s'affiche même écran verrouillé (intention plein écran), avec la sonnerie Atlas. */
    fun incomingCall(c: Context, name: String) {
        createChannels(c)
        val pi = openIntent(c, "call", 9001)
        val n = NotificationCompat.Builder(c, channelId(c, "calls")).setSmallIcon(R.drawable.ic_stat_atlas).setContentTitle(name).setContentText("Appel entrant Atlas Globe")
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true).setAutoCancel(false)
            .setContentIntent(pi).setFullScreenIntent(pi, true).setTimeoutAfter(45_000).build()
        runCatching { if (NotificationManagerCompat.from(c).areNotificationsEnabled()) NotificationManagerCompat.from(c).notify(com.atlasglobe.app.call.CallManager.NOTIF, n) }
    }

    fun post(c: Context, title: String, text: String) {
        NotifStore.init(c); NotifStore.add("post", title, text)
        show(c, "posts", System.nanoTime().toInt(), title, text, "notifications")
    }
    fun connectivity(c: Context, title: String, text: String) {
        NotifStore.init(c); NotifStore.add("connect", title, text)
        show(c, "connect", System.nanoTime().toInt(), title, text, "connectivity")
    }
    /** Annonce officielle : notification + affichage devant l'application à l'ouverture. */
    fun announcement(c: Context, a: Announcement) {
        NotifStore.init(c); NotifStore.add("announce", a.title, a.body)
        AnnouncementState.receive(c, a)
        show(c, "announce", 7001, a.title, a.body, "announcement")
    }
}
