package com.atlasglobe.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.atlasglobe.app.data.ProfileStore

/** Relance Atlas en arrière-plan après redémarrage du téléphone. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (ProfileStore(c).isRegistered) AtlasService.start(c)
    }
}
