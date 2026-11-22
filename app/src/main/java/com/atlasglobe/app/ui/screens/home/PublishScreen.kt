package com.atlasglobe.app.ui.screens.home

import android.widget.Toast
import com.atlasglobe.app.ui.components.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.data.FeedRepo
import com.atlasglobe.app.data.ImageUtil
import com.atlasglobe.app.ui.components.rememberLocalBitmap
import com.atlasglobe.app.ui.screens.register.LineField
import com.atlasglobe.app.ui.screens.register.WhiteButton
import com.atlasglobe.app.ui.theme.Atlas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** mode : "post" (photo + légende), "story" (photo), "text" (texte seul). Retour = bouton du téléphone. */
@Composable
fun PublishScreen(mode: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var media by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            media = withContext(Dispatchers.IO) {
                ImageUtil.saveScaled(ctx, uri, 1080, File(ctx.filesDir, "media/${UUID.randomUUID()}.jpg")) }
            busy = false
        }
    }
    val needsImage = mode != "text"
    val canPublish = !busy && when (mode) { "text" -> caption.isNotBlank(); else -> media != null }

    Column(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding().navigationBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(when (mode) { "story" -> "Nouvelle story"; "text" -> "Nouveau texte"; else -> "Nouvelle publication" },
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        if (needsImage) {
            val bmp by rememberLocalBitmap(media)
            Box(Modifier.fillMaxWidth().aspectRatio(1f).border(1.dp, Atlas.Grey)
                .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center) {
                val b = bmp
                if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp)); Text("Choisir une photo", color = Atlas.Grey)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (mode != "story") {
            if (mode == "text") TextField(caption, { caption = it.take(600) }, placeholder = { Text("Écrivez votre message", color = Atlas.Grey) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.White, unfocusedIndicatorColor = Atlas.Grey, cursorColor = Color.White,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White))
            else LineField(caption, { caption = it.take(600) }, "Légende (facultatif)")
            Spacer(Modifier.height(24.dp))
        }
        WhiteButton(if (busy) "Préparation…" else "Publier", canPublish) {
            when (mode) {
                "story" -> FeedRepo.addStory(media!!)
                "text" -> FeedRepo.addPost("text", caption.trim(), null)
                else -> FeedRepo.addPost("image", caption.trim(), media)
            }
            Toast.makeText(ctx, "Publié. L'envoi se fera dès qu'il y aura une connexion.", Toast.LENGTH_SHORT).show()
            onDone()
        }
    }
}
