package com.atlasglobe.app.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.atlasglobe.app.ui.screens.register.PermissionStep
import com.atlasglobe.app.ui.screens.register.Reqs

/** Si le Bluetooth, la localisation ou les notifications sont coupés, Atlas redemande de les activer. */
const val BLOCK_WHOLE_APP = true   // false = ne bloquer que les fonctions mesh

@Composable
fun RequirementsGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    // Réagit aussi quand l'utilisateur coupe le Bluetooth / la localisation depuis la barre rapide
    DisposableEffect(Unit) {
        val rx = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { tick++ } }
        val f = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(ctx, rx, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(rx) }
    }

    val missing = remember(tick) { Reqs.firstMissing(ctx) }
    if (missing != null && BLOCK_WHOLE_APP) {
        key(missing.id) { PermissionStep(missing) { tick++ } }
    } else content()
}
