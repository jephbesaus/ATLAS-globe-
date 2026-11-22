package com.atlasglobe.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.ui.theme.Atlas

/** Haut de l'accueil : logo | + (publier) | cloche + compteur | localisation + ville. */
@Composable
fun HomeTopBar(
    notifCount: Int, city: String,
    onPlus: () -> Unit, onNotifications: () -> Unit
) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SpinningGlobe(34.dp)
        Spacer(Modifier.width(8.dp))
        Text("Atlas", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(32.dp).border(1.5.dp, Color.White, CircleShape).clickable { onPlus() },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, "Publier", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.clickable { onNotifications() }) {
            Icon(Icons.Default.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(26.dp))
            if (notifCount > 0) Box(
                Modifier.align(Alignment.TopEnd).offset(6.dp, (-4).dp).size(16.dp)
                    .background(Atlas.Alert, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (notifCount > 9) "9+" else "$notifCount", color = Color.White, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LocationOn, "Localisation", tint = Color.White, modifier = Modifier.size(22.dp))
            Text(city, color = Atlas.Grey, fontSize = 8.sp, maxLines = 1)
        }
    }
}
