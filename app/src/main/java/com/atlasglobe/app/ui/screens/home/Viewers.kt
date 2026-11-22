package com.atlasglobe.app.ui.screens.home

import android.widget.Toast
import com.atlasglobe.app.ui.components.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.data.FeedRepo
import com.atlasglobe.app.data.ImageUtil
import com.atlasglobe.app.ui.components.Avatar
import com.atlasglobe.app.ui.components.rememberLocalBitmap
import com.atlasglobe.app.ui.components.timeAgo
import com.atlasglobe.app.ui.theme.Atlas

/** Image en grand, zoom au pincement. Retour = bouton du téléphone. */
@Composable
fun ImageViewer(postId: String) {
    val path = FeedRepo.posts.firstOrNull { it.id == postId }?.mediaPath
    val bmp by rememberLocalBitmap(path, 2048)
    var scale by remember { mutableFloatStateOf(1f) }
    val st = rememberTransformableState { z, _, _ -> scale = (scale * z).coerceIn(1f, 5f) }
    Box(Modifier.fillMaxSize().background(Color.Black).transformable(st), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale), contentScale = ContentScale.Fit) }
    }
}

/** Stories d'une personne, l'une après l'autre. Touche gauche = précédente, droite = suivante. */
@Composable
fun StoryViewer(authorId: String, myId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val list = remember { FeedRepo.stories.filter { it.authorId == authorId }.sortedBy { it.createdAt } }
    if (list.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    var index by remember { mutableIntStateOf(list.indexOfFirst { !it.viewed }.coerceAtLeast(0)) }
    val story = FeedRepo.stories.firstOrNull { it.id == list[index].id } ?: list[index]
    val own = authorId == myId
    var reply by remember { mutableStateOf("") }
    val progress = remember(index) { Animatable(0f) }
    var paused by remember { mutableStateOf(false) }
    val bmp by rememberLocalBitmap(story.mediaPath, 1920)

    fun next() { if (index < list.size - 1) index++ else onClose() }
    fun prev() { if (index > 0) index-- }

    LaunchedEffect(index) { FeedRepo.markViewed(list[index].id) }
    LaunchedEffect(index, paused) {
        if (!paused) { progress.animateTo(1f, tween(((1f - progress.value) * 5000).toInt().coerceAtLeast(1), easing = LinearEasing)); next() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().imePadding()) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().pointerInput(index) {
            detectTapGestures(onTap = { o -> if (o.x < size.width / 3f) prev() else next() })
        }, contentScale = ContentScale.Fit) }

        Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                list.indices.forEach { i ->
                    val v = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                    LinearProgressIndicator(progress = { v }, modifier = Modifier.weight(1f).height(2.dp),
                        color = Color.White, trackColor = Atlas.Line, gapSize = 0.dp, drawStopIndicator = {})
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(story.authorName, story.authorPhoto, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column { Text(story.authorName, color = Color.White, fontSize = 14.sp)
                    Text(timeAgo(story.createdAt), color = Atlas.Grey, fontSize = 11.sp) }
            }
        }

        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color(0x99000000)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (own) {
                Text("Supprimer la story", color = Atlas.Alert, fontSize = 14.sp, modifier = Modifier.clickable {
                    FeedRepo.deleteStory(story.id); onClose() }.padding(8.dp))
            } else {
                TextField(reply, { reply = it; paused = it.isNotEmpty() }, singleLine = true,
                    placeholder = { Text("Répondre à ${story.authorName.substringBefore(" ")}", color = Atlas.Grey) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.White, unfocusedIndicatorColor = Atlas.Grey, cursorColor = Color.White,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                if (reply.isNotBlank()) Icon(Icons.AutoMirrored.Filled.Send, "Envoyer", tint = Color.White,
                    modifier = Modifier.padding(start = 10.dp).size(26.dp).clickable {
                        FeedRepo.replyToStory(story, reply.trim()); reply = ""; paused = false
                        Toast.makeText(ctx, "Réponse envoyée", Toast.LENGTH_SHORT).show() })
                Icon(if (story.likedByMe) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline, "J'aime",
                    tint = Color.White, modifier = Modifier.padding(start = 12.dp).size(28.dp).clickable { FeedRepo.likeStory(story.id) })
                Icon(Icons.Filled.Download, "Télécharger", tint = Color.White, modifier = Modifier.padding(start = 12.dp).size(26.dp).clickable {
                    val ok = ImageUtil.saveToGallery(ctx, story.mediaPath)
                    Toast.makeText(ctx, if (ok) "Story enregistrée dans la galerie" else "Échec de l'enregistrement", Toast.LENGTH_SHORT).show() })
            }
        }
    }
}
