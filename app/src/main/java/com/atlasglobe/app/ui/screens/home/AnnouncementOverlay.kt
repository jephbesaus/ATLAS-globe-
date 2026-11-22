package com.atlasglobe.app.ui.screens.home

import android.content.Intent
import com.atlasglobe.app.ui.components.Text
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.data.Announcement
import com.atlasglobe.app.data.AnnouncementState
import com.atlasglobe.app.ui.components.SpinningGlobe
import com.atlasglobe.app.ui.screens.register.WhiteButton
import com.atlasglobe.app.ui.theme.Atlas

/** Annonce officielle, devant tout. Aucun bouton de fermeture : le bouton Retour du téléphone la ferme. */
@Composable
fun AnnouncementOverlay(a: Announcement) {
    val ctx = LocalContext.current
    BackHandler { AnnouncementState.dismiss() }
    Column(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        SpinningGlobe(88.dp)
        Spacer(Modifier.height(20.dp))
        Text("ANNONCE OFFICIELLE", color = Atlas.Grey, fontSize = 12.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(14.dp))
        Text(a.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(a.body, color = Color.White, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        fun open(u: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        if (a.whatsapp.isNotBlank()) { WhiteButton("Rejoindre le groupe WhatsApp") { open(a.whatsapp) }; Spacer(Modifier.height(12.dp)) }
        if (a.telegram.isNotBlank()) { WhiteButton("Rejoindre sur Telegram") { open(a.telegram) }; Spacer(Modifier.height(12.dp)) }
        if (a.support.isNotBlank()) Text("Support : ${a.support}", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).clickable { val s = a.support
                if (s.startsWith("http") || s.startsWith("mailto:")) open(s) else if (s.contains("@")) open("mailto:$s") else open("https://wa.me/" + s.filter { it.isDigit() }) })
        Spacer(Modifier.height(20.dp))
        Text("Appuyez sur Retour pour fermer", color = Atlas.Grey, fontSize = 12.sp)
    }
}
