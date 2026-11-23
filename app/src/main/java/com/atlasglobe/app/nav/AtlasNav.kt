package com.atlasglobe.app.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.atlasglobe.app.ui.components.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.compose.foundation.shape.RoundedCornerShape
import com.atlasglobe.app.data.*
import com.atlasglobe.app.notify.AtlasService
import com.atlasglobe.app.security.AppLockState
import com.atlasglobe.app.ui.components.AtlasBottomBar
import com.atlasglobe.app.ui.components.HomeTopBar
import com.atlasglobe.app.ui.screens.LockScreen
import com.atlasglobe.app.ui.screens.PlaceholderScreen
import com.atlasglobe.app.ui.screens.RequirementsGate
import com.atlasglobe.app.ui.screens.SplashScreen
import com.atlasglobe.app.ui.screens.home.*
import com.atlasglobe.app.ui.screens.chat.*
import com.atlasglobe.app.ui.screens.register.RegisterFlow
import com.atlasglobe.app.ui.theme.Atlas

@Composable
fun AtlasNav() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    NavHost(nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen {
                val dest = if (ProfileStore(ctx).isRegistered) Routes.MAIN else Routes.REGISTER
                nav.navigate(dest) { popUpTo(Routes.SPLASH) { inclusive = true } }
            }
        }
        composable(Routes.REGISTER) {
            RegisterFlow {
                AppLockState.unlocked = true   // il vient de saisir son PIN
                nav.navigate(Routes.MAIN) { popUpTo(Routes.REGISTER) { inclusive = true } }
            }
        }
        composable(Routes.MAIN) {
            val store = remember { ProfileStore(ctx) }
            Box(Modifier.fillMaxSize()) {
                RequirementsGate { if (AccountState.blocked) BlockedScreen() else MainShell(store) }
                if (store.lockEnabled && !AppLockState.unlocked) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                    LockScreen(store) { AppLockState.unlocked = true } }
            }
        }
    }
}

/** Coque principale : barre du haut (accueil) + barre du bas + onglets + annonce officielle devant. */
@Composable
fun MainShell(store: ProfileStore) {
    val ctx = LocalContext.current
    val me = remember { store.load()!! }
    remember {
        FeedRepo.init(ctx); NotifStore.init(ctx); AnnouncementState.init(ctx); AccountState.init(ctx); com.atlasglobe.app.ui.components.Lang.init(ctx); ChatRepo.init(ctx); com.atlasglobe.app.mesh.MeshRouter.init(ctx); com.atlasglobe.app.connect.ConnectStore.init(ctx); com.atlasglobe.app.connect.ConnectionManager.init(ctx); com.atlasglobe.app.connect.AtlasConnectManager.init(ctx); com.atlasglobe.app.settings.SettingsStore.init(ctx); ResellerAccess.approved = store.resellerApproved
        AtlasService.start(ctx)          // reste actif application fermée
        true
    }
    val tabs = rememberNavController()
    val entry by tabs.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val fullscreen = current != null && (current.startsWith("publish") || current.startsWith("story") || current.startsWith("viewer") ||
        current.startsWith("chat") || current.startsWith("contacts") || current.startsWith("group"))
    var choose by remember { mutableStateOf(false) }

    // Toucher une notification ouvre la bonne page
    val pending = PendingOpen.route
    LaunchedEffect(pending) {
        when {
            pending == "notifications" -> tabs.go(Routes.NOTIFICATIONS)
            pending == "connectivity" -> tabs.go(Routes.CONNECTIVITY)
            pending?.startsWith("chat:") == true -> tabs.navigate("chat/" + pending.removePrefix("chat:"))
        }
        if (pending != null) PendingOpen.route = null
    }

    Box(Modifier.fillMaxSize().background(Atlas.Black)) {
        Column(Modifier.fillMaxSize()) {
            if (current == Routes.HOME) HomeTopBar(
                notifCount = NotifStore.unread, city = me.city,
                onPlus = { choose = true },
                onNotifications = { tabs.navigate(Routes.NOTIFICATIONS) })
            Box(Modifier.weight(1f)) {
                NavHost(tabs, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        HomeScreen(me,
                            onOpenStory = { tabs.navigate("story/$it") },
                            onOpenImage = { tabs.navigate("viewer/$it") },
                            onAddStory = { tabs.navigate("publish/story") })
                    }
                    composable(Routes.SEARCH) { PlaceholderScreen("Recherche") }
                    composable(Routes.MESSAGES) {
                        ChatListScreen(onOpen = { tabs.navigate("chat/$it") }, onContacts = { tabs.navigate(Routes.CONTACTS) },
                            onNewGroup = { tabs.navigate(Routes.GROUP_NEW) })
                    }
                    composable(Routes.CONTACTS) { ContactsScreen { tabs.navigate("chat/$it") { popUpTo(Routes.CONTACTS) { inclusive = true } } } }
                    composable(Routes.GROUP_NEW) {
                        GroupCreateScreen { gid -> tabs.navigate("chat/$gid") { popUpTo(Routes.GROUP_NEW) { inclusive = true } } }
                    }
                    composable(Routes.CHAT, listOf(navArgument("id") { type = NavType.StringType })) {
                        val id = it.arguments?.getString("id") ?: ""
                        ChatScreen(id) { tabs.navigate("group_info/$id") }
                    }
                    composable(Routes.GROUP_INFO, listOf(navArgument("id") { type = NavType.StringType })) {
                        GroupInfoScreen(it.arguments?.getString("id") ?: "") { tabs.popBackStack(Routes.MESSAGES, false) }
                    }
                    composable(Routes.SETTINGS) { com.atlasglobe.app.ui.screens.settings.SettingsScreen(onOpenChat = { tabs.navigate("chat/$it") }, onOpenReseller = { tabs.navigate(Routes.RESELLER) }) }
                    composable(Routes.CONNECTIVITY) { com.atlasglobe.app.ui.screens.connect.ConnectivityScreen { tabs.navigate("chat/$it") } }
                    composable(Routes.NOTIFICATIONS) { NotificationsScreen() }
                    composable(Routes.PUBLISH, listOf(navArgument("mode") { type = NavType.StringType })) {
                        PublishScreen(it.arguments?.getString("mode") ?: "post") { tabs.popBackStack() }
                    }
                    composable(Routes.STORY, listOf(navArgument("author") { type = NavType.StringType })) {
                        StoryViewer(it.arguments?.getString("author") ?: "", me.atlasId) { tabs.popBackStack() }
                    }
                    composable(Routes.VIEWER, listOf(navArgument("post") { type = NavType.StringType })) {
                        ImageViewer(it.arguments?.getString("post") ?: "")
                    }
                    if (ResellerAccess.approved) composable(Routes.RESELLER) { com.atlasglobe.app.reseller.ResellerHome() }
                }
            }
            if (!fullscreen) AtlasBottomBar(current, onNavigate = { tabs.go(it) })
        }

        // Annonce officielle : devant tout, fermée par le bouton Retour du téléphone
        AnnouncementState.current?.let { AnnouncementOverlay(it) }
        com.atlasglobe.app.ui.screens.call.CallOverlay()
    }

    if (choose) Dialog(onDismissRequest = { choose = false }) {
        Column(Modifier.fillMaxWidth().background(Atlas.Card, RoundedCornerShape(8.dp))) {
            listOf("Publication" to "post", "Vidéo" to "video", "Story" to "story", "Texte" to "text").forEach { (label, mode) ->
                Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.fillMaxWidth()
                    .clickable { choose = false; tabs.navigate("publish/$mode") }.padding(20.dp))
            }
        }
    }
}

private fun NavHostController.go(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}


@Composable
private fun BlockedScreen() {
    Column(Modifier.fillMaxSize().background(Atlas.Black).padding(32.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        com.atlasglobe.app.ui.components.SpinningGlobe(90.dp)
        Spacer(Modifier.height(24.dp))
        Text("Compte suspendu", color = Color.White, fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(AccountState.reason.ifBlank { "Votre compte a été suspendu par l'équipe Atlas." }, color = Atlas.Grey, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Contactez le support Atlas pour plus d'informations.", color = Atlas.Grey, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
