package com.atlasglobe.app.ui.screens.register

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

object Perms {
    val bluetooth: Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    val location: Array<String> = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    val notifications: Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

    fun has(c: Context, p: Array<String>) =
        p.all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }

    fun btOn(c: Context): Boolean =
        (c.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true

    fun locationOn(c: Context): Boolean {
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    fun batteryOk(c: Context): Boolean =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)

    fun appSettings(c: Context) = c.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun locationSettings(c: Context) = c.startActivity(
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun batteryRequest(c: Context) = c.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
