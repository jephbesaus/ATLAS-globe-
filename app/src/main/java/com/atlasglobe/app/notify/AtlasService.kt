package com.atlasglobe.app.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.atlasglobe.app.R
import com.atlasglobe.app.mesh.BleMesh
import com.atlasglobe.app.sync.ServerSync

/**
 * Service d'arrière-plan d'Atlas (sans Firebase). Il garde Atlas vivant application fermée pour :
 *  - recevoir les messages et annonces par notre connexion serveur (quand il y a Internet),
 *  - le mesh Bluetooth (étape 6),
 * La petite notification silencieuse "Atlas actif" est obligatoire sous Android.
 */
class AtlasService : Service() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        val n = NotificationCompat.Builder(this, NotificationHelper.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_atlas).setContentTitle("Atlas Globe")
            .setContentText("Actif : vous recevez vos messages")
            .setOngoing(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(NotificationHelper.openIntent(this, "home", 1)).build()
        try {
            ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: Exception) { stopSelf(); return }   // permission Bluetooth manquante : la barrière d'autorisations la redemande
        runCatching { BleMesh.start(this) }
        runCatching { ServerSync.start(this) }
    }

    override fun onDestroy() { ServerSync.stop(); BleMesh.stop(); super.onDestroy() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(c: Context) = runCatching {
            ContextCompat.startForegroundService(c, Intent(c, AtlasService::class.java)) }
    }
}
