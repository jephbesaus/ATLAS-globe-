package com.atlasglobe.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.atlasglobe.app.data.ChatRepo

/** Réponse directe depuis la notification. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val text = RemoteInput.getResultsFromIntent(i)?.getCharSequence("reply")?.toString()?.trim().orEmpty()
        val conv = i.getStringExtra("conv") ?: return
        ChatRepo.init(c)
        if (text.isNotEmpty()) ChatRepo.send(conv, "text", text, null)
        NotificationManagerCompat.from(c).cancel(i.getIntExtra("nid", 0))
    }
}
