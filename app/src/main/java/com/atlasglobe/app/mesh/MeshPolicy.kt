package com.atlasglobe.app.mesh

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

enum class MeshMode(val label: String, val scanOnMs: Long, val scanOffMs: Long, val sessionMs: Long) {
    ACTIVE("Actif", 8_000, 7_000, 40_000),
    BALANCED("Équilibré", 5_000, 25_000, 25_000),
    ECONOMY("Économie", 3_000, 60_000, 15_000)
}

/** Choisit le rythme du mesh selon le mode, la batterie, la chaleur et l'heure. */
object MeshPolicy {
    var chosen by mutableStateOf(MeshMode.BALANCED); private set
    var effective by mutableStateOf(MeshMode.BALANCED); private set
    var relay by mutableStateOf(true); private set        // sous 15 % : on ne relaie plus les messages des autres
    var paused by mutableStateOf(false); private set      // téléphone trop chaud : pause
    var batteryPct by mutableIntStateOf(100); private set
    var tempC by mutableStateOf(0f); private set
    var charging by mutableStateOf(false); private set

    fun load(c: Context) {
        val s = c.getSharedPreferences("atlas_mesh", Context.MODE_PRIVATE).getString("mode", "BALANCED")
        chosen = runCatching { MeshMode.valueOf(s!!) }.getOrDefault(MeshMode.BALANCED)
    }
    fun setMode(c: Context, m: MeshMode) {
        chosen = m; c.getSharedPreferences("atlas_mesh", Context.MODE_PRIVATE).edit().putString("mode", m.name).apply(); refresh(c)
    }

    fun refresh(c: Context) {
        val b: Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val charging = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) ?: 250) / 10f
        val thermal = if (Build.VERSION.SDK_INT >= 29)
            (c.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else 0
        batteryPct = level * 100 / scale.coerceAtLeast(1); tempC = temp; this.charging = charging

        var m = chosen
        val night = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 0..4
        if (night && m != MeshMode.ECONOMY) m = MeshMode.values()[m.ordinal + 1]          // la nuit : un cran plus lent
        if (batteryPct < 20 && !charging) m = MeshMode.ECONOMY
        if (temp >= 40f || thermal >= PowerManager.THERMAL_STATUS_MODERATE) m = MeshMode.ECONOMY
        effective = m
        relay = charging || batteryPct >= 15
        paused = temp >= 45f || thermal >= PowerManager.THERMAL_STATUS_SEVERE
    }
}

/** Ce que l'écran peut afficher : "12 appareils Atlas à proximité". */
object MeshStats {
    var nearby by mutableIntStateOf(0)
    var links by mutableIntStateOf(0)
    var running by mutableStateOf(false)
}
