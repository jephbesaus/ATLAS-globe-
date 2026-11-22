package com.atlasglobe.app.connect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.atlasglobe.app.notify.NotificationHelper

/** Notification "il vous reste 5 minutes", puis "votre temps est terminé". */
class ExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        ConnectStore.init(c)
        when (i.getStringExtra("kind")) {
            "soon" -> NotificationHelper.connectivity(c, "Atlas Connect", "Il vous reste 5 minutes de connexion.")
            "end" -> { ConnectStore.end(); ConnectionManager.disconnect()
                NotificationHelper.connectivity(c, "Atlas Connect", "Votre temps est terminé. Veuillez renouveler votre connexion.") }
        }
    }
}
