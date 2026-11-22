package com.atlasglobe.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

object Net {
    fun isOnline(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** État Internet observé en direct (pour les messages "cette fonction demande des mégas"). */
@Composable
fun rememberOnline(): State<Boolean> {
    val c = LocalContext.current
    val st = remember { mutableStateOf(Net.isOnline(c)) }
    DisposableEffect(Unit) {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) { st.value = Net.isOnline(c) }
            override fun onLost(n: Network) { st.value = Net.isOnline(c) }
            override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) { st.value = Net.isOnline(c) }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
    return st
}
