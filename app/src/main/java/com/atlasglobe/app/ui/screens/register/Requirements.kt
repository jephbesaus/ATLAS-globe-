package com.atlasglobe.app.ui.screens.register

import android.content.Context
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import com.atlasglobe.app.R

/** Une autorisation / un réglage exigé par Atlas. Utilisé par l'inscription ET par le blocage permanent. */
class Req(
    val id: String, val icon: ImageVector, val imageRes: Int?, val title: String, val message: String,
    val isActive: (Context) -> Boolean,
    val onActivate: (Context, (Array<String>) -> Unit, () -> Unit) -> Unit,
    val afterPerms: (Context, () -> Unit) -> Unit
)

object Reqs {
    val BT = Req("bt", Icons.Default.Bluetooth, R.drawable.bluetooth_glass,
        "Atlas Globe demande l'autorisation",
        "Pour que la messagerie fonctionne sans Internet, activez votre Bluetooth. Sans Bluetooth, vous ne recevez pas les messages de vos voisins.",
        { Perms.has(it, Perms.bluetooth) && Perms.btOn(it) },
        { c, askPerms, askBt -> when {
            !Perms.has(c, Perms.bluetooth) -> askPerms(Perms.bluetooth)
            !Perms.btOn(c) -> askBt()
            else -> Unit } },
        { c, askBt -> if (Perms.has(c, Perms.bluetooth) && !Perms.btOn(c)) askBt() })

    val LOC = Req("loc", Icons.Default.LocationOn, null,
        "Activez la localisation",
        "Android exige la localisation pour chercher les appareils proches. Atlas ne partage pas votre position sans votre accord.",
        { Perms.has(it, Perms.location) && Perms.locationOn(it) },
        { c, askPerms, _ -> when {
            !Perms.has(c, Perms.location) -> askPerms(Perms.location)
            !Perms.locationOn(c) -> Perms.locationSettings(c)
            else -> Unit } },
        { c, _ -> if (Perms.has(c, Perms.location) && !Perms.locationOn(c)) Perms.locationSettings(c) })

    val NOTIF = Req("notif", Icons.Default.Notifications, null,
        "Activez les notifications",
        "Pour entendre le son Atlas quand vous recevez un message.",
        { Perms.has(it, Perms.notifications) },
        { _, askPerms, _ -> askPerms(Perms.notifications) },
        { _, _ -> })

    val BATT = Req("batt", Icons.Default.BatteryChargingFull, null,
        "Gardez Atlas actif",
        "Pour recevoir vos messages en arrière-plan, autorisez Atlas à ne pas être arrêté par l'économie de batterie. Atlas règle lui-même sa consommation.",
        { Perms.batteryOk(it) },
        { c, _, _ -> Perms.batteryRequest(c) },
        { _, _ -> })

    /** Premier réglage manquant parmi ceux imposés en permanence (null = tout est bon). */
    fun firstMissing(c: Context): Req? {
        if (!BT.isActive(c)) return BT
        if (!LOC.isActive(c)) return LOC
        if (Build.VERSION.SDK_INT >= 33 && !NOTIF.isActive(c)) return NOTIF
        return null
    }
}
