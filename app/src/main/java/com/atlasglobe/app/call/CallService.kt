package com.atlasglobe.app.call

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.atlasglobe.app.R
import com.atlasglobe.app.notify.NotificationHelper

/** Garde le micro actif quand l'application passe en arrière-plan pendant un appel. */
class CallService : Service() {
    override fun onCreate() {
        super.onCreate(); NotificationHelper.createChannels(this)
        val n = NotificationCompat.Builder(this, NotificationHelper.CH_SERVICE).setSmallIcon(R.drawable.ic_stat_atlas).setContentTitle("Atlas Globe").setContentText("Appel en cours")
            .setOngoing(true).setSilent(true).setContentIntent(NotificationHelper.openIntent(this, "call", 3)).build()
        try { ServiceCompat.startForeground(this, 2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) } catch (e: Exception) { stopSelf() }
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
