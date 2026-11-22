package com.atlasglobe.app.ui.screens.home

import androidx.compose.foundation.background
import com.atlasglobe.app.ui.components.Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.data.NotifStore
import com.atlasglobe.app.ui.components.timeAgo
import com.atlasglobe.app.ui.theme.Atlas

@Composable
fun NotificationsScreen() {
    // Ouvrir la page remet le compteur à zéro
    LaunchedEffect(Unit) { NotifStore.markAllRead() }
    val items = NotifStore.items
    Column(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding()) {
        Text("Notifications", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        if (items.isEmpty()) Text("Aucune notification", color = Atlas.Grey, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(40.dp))
        else LazyColumn {
            items(items, key = { it.id }) { n ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    Icon(when (n.type) { "announce" -> Icons.Filled.Campaign; "connect" -> Icons.Filled.Wifi
                        "post" -> Icons.Filled.Image; else -> Icons.Filled.Info }, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(n.text, color = Atlas.Grey, fontSize = 14.sp, maxLines = 3)
                    }
                    Text(timeAgo(n.createdAt), color = Atlas.Grey, fontSize = 11.sp)
                }
                HorizontalDivider(color = Atlas.Line)
            }
        }
    }
}
