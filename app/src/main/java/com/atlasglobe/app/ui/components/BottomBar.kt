package com.atlasglobe.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.R
import com.atlasglobe.app.nav.Routes
import com.atlasglobe.app.ui.theme.Atlas

/** Barre du bas : Accueil | Recherche | (Wi-Fi = Connectivité) | Messages | Paramètres */
@Composable
fun AtlasBottomBar(current: String?, onNavigate: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().background(Atlas.Black).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Item("Accueil", Icons.Filled.Home, Icons.Outlined.Home, current == Routes.HOME,
                Modifier.weight(1f)) { onNavigate(Routes.HOME) }
            Item("Recherche", Icons.Filled.Search, Icons.Outlined.Search, current == Routes.SEARCH,
                Modifier.weight(1f)) { onNavigate(Routes.SEARCH) }
            Spacer(Modifier.weight(1f))
            Item("Messages", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, current == Routes.MESSAGES,
                Modifier.weight(1f)) { onNavigate(Routes.MESSAGES) }
            Item("Paramètres", Icons.Filled.Person, Icons.Outlined.Person, current == Routes.SETTINGS,
                Modifier.weight(1f)) { onNavigate(Routes.SETTINGS) }
        }
        val sel = current == Routes.CONNECTIVITY
        Image(painterResource(R.drawable.hotspot_btn), "Connectivité",
            Modifier.align(Alignment.TopCenter).offset(y = (-22).dp).size(58.dp).clip(CircleShape)
                .border(if (sel) 2.5.dp else 1.dp, if (sel) Color.White else Atlas.Grey, CircleShape)
                .background(Color.Black).clickable { onNavigate(Routes.CONNECTIVITY) },
            contentScale = ContentScale.Crop)
    }
}

@Composable
private fun Item(label: String, on: ImageVector, off: ImageVector, selected: Boolean,
                 modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (selected) on else off, label, tint = if (selected) Color.White else Atlas.Grey,
            modifier = Modifier.size(24.dp))
        Text(label, fontSize = 10.sp, color = if (selected) Color.White else Atlas.Grey)
    }
}
