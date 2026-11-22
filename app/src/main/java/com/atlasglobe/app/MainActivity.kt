package com.atlasglobe.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.atlasglobe.app.data.PendingOpen
import com.atlasglobe.app.nav.AtlasNav
import com.atlasglobe.app.notify.NotificationHelper
import com.atlasglobe.app.security.AppLockState
import com.atlasglobe.app.ui.theme.AtlasTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createChannels(this)
        handle(intent)
        setContent { AtlasTheme { AtlasNav() } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }
    private fun handle(i: Intent?) { PendingOpen.route = i?.getStringExtra("open") }
    override fun onStop() { super.onStop(); AppLockState.onStop(SystemClock.elapsedRealtime()) }
    override fun onStart() { super.onStart(); AppLockState.onStart(SystemClock.elapsedRealtime()) }
}
