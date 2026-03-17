package com.rpeters.cinefintv.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.player.PlayerScreen
import com.rpeters.cinefintv.ui.player.audio.AudioPlayerScreen
import com.rpeters.cinefintv.ui.screens.auth.AuthViewModel
import com.rpeters.cinefintv.ui.screens.auth.LoginScreen
import com.rpeters.cinefintv.ui.screens.auth.ServerConnectionScreen
import com.rpeters.cinefintv.ui.screens.detail.DetailScreen
import com.rpeters.cinefintv.ui.screens.home.HomeScreen
import com.rpeters.cinefintv.ui.screens.library.LibraryCategory
import com.rpeters.cinefintv.ui.screens.library.LibraryScreen
import com.rpeters.cinefintv.ui.screens.music.MusicScreen
import com.rpeters.cinefintv.ui.screens.person.PersonScreen
import com.rpeters.cinefintv.ui.screens.search.SearchScreen
import com.rpeters.cinefintv.ui.screens.settings.SettingsScreen
import com.rpeters.cinefintv.ui.screens.collections.CollectionsDetailScreen
import com.rpeters.cinefintv.ui.screens.collections.CollectionsLibraryScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
fun CinefinTvNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AuthRoutes.SERVER_CONNECTION,
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val resolvedStartDestination = if (authUiState.isSessionActive) NavRoutes.HOME else startDestination

    if (!authUiState.isSessionChecked) {
        AuthBootstrapScreen()
        return
    }

    LaunchedEffect(authUiState.connectedServerUrl) {
        if (authUiState.connectedServerUrl != null &&
            navController.currentDestination?.route == AuthRoutes.SERVER_CONNECTION
        ) {
            navController.navigate(AuthRoutes.LOGIN)
        }
    }

    LaunchedEffect(authUiState.loginSucceeded) {
        if (!authUiState.loginSucceeded || navController.currentDestination?.route == NavRoutes.HOME) {
            return@LaunchedEffect
        }

        authViewModel.resetLoginSuccess()
        navController.navigate(NavRoutes.HOME) {
            launchSingleTop = true
            popUpTo(AuthRoutes.SERVER_CONNECTION) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = resolvedStartDestination,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        composable(AuthRoutes.SERVER_CONNECTION) {
            ServerConnectionScreen(
                serverUrl = authUiState.serverUrlInput,
                isLoading = authUiState.isTestingConnection,
                errorMessage = authUiState.connectionError,
                onServerUrlChange = { authViewModel.updateServerUrlInput(it) },
                onContinue = { authViewModel.testServerConnection() },
            )
        }
        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                serverUrl = authUiState.connectedServerUrl ?: authUiState.serverUrlInput,
                isAuthenticating = authUiState.isAuthenticating,
                errorMessage = authUiState.loginError,
                isQuickConnectEnabled = authUiState.isQuickConnectEnabled,
                isQuickConnectLoading = authUiState.isQuickConnectLoading,
                quickConnectCode = authUiState.quickConnectCode,
                quickConnectPollStatus = authUiState.quickConnectPollStatus,
                quickConnectError = authUiState.quickConnectError,
                onLogin = { username, password ->
                    authViewModel.login(username, password)
                },
                onUseQuickConnect = {
                    authViewModel.startQuickConnect()
                },
                onGenerateNewCode = {
                    authViewModel.generateNewQuickConnectCode()
                },
                onLeaveScreen = {
                    authViewModel.stopQuickConnect()
                },
                onBack = {
                    authViewModel.returnToServerEntry()
                    navController.popBackStack()
                },
            )
        }
        composable(NavRoutes.HOME) {
            HomeScreen(
                onOpenItem = { item ->
                    when {
                        item.itemType == "CollectionFolder" -> {
                            when (item.collectionType?.lowercase()) {
                                "movies" -> navController.navigate(NavRoutes.LIBRARY_MOVIES)
                                "tvshows" -> navController.navigate(NavRoutes.LIBRARY_TVSHOWS)
                                "music" -> navController.navigate(NavRoutes.LIBRARY_MUSIC)
                                "homevideos" -> navController.navigate(NavRoutes.LIBRARY_COLLECTIONS)
                                else -> navController.navigate(NavRoutes.detail(item.id))
                            }
                        }
                        item.itemType == "Video" || item.collectionType == "homevideos" -> {
                            navController.navigate(NavRoutes.collectionsDetail(item.id))
                        }
                        else -> {
                            navController.navigate(NavRoutes.detail(item.id))
                        }
                    }
                },
                onPlayItem = { itemId ->
                    navController.navigate(NavRoutes.player(itemId))
                },
            )
        }
        composable(NavRoutes.SEARCH) {
            SearchScreen(
                onOpenItem = { item ->
                    when {
                        item.itemType == "CollectionFolder" -> {
                            when (item.collectionType?.lowercase()) {
                                "movies" -> navController.navigate(NavRoutes.LIBRARY_MOVIES)
                                "tvshows" -> navController.navigate(NavRoutes.LIBRARY_TVSHOWS)
                                "music" -> navController.navigate(NavRoutes.LIBRARY_MUSIC)
                                "homevideos" -> navController.navigate(NavRoutes.LIBRARY_COLLECTIONS)
                                else -> navController.navigate(NavRoutes.detail(item.id))
                            }
                        }
                        item.itemType == "Video" || item.collectionType == "homevideos" -> {
                            navController.navigate(NavRoutes.collectionsDetail(item.id))
                        }
                        else -> {
                            navController.navigate(NavRoutes.detail(item.id))
                        }
                    }
                },
            )
        }
        composable(NavRoutes.LIBRARY_MOVIES) {
            LibraryScreen(
                category = LibraryCategory.MOVIES,
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.detail(itemId))
                },
            )
        }
        composable(NavRoutes.LIBRARY_TVSHOWS) {
            LibraryScreen(
                category = LibraryCategory.TV_SHOWS,
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.detail(itemId))
                },
            )
        }
        composable(NavRoutes.LIBRARY_COLLECTIONS) {
            CollectionsLibraryScreen(
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.collectionsDetail(itemId))
                },
            )
        }
        composable(NavRoutes.LIBRARY_MUSIC) {
            MusicScreen(
                onPlayTrack = { request ->
                    navController.navigate(NavRoutes.audioPlayer(request.trackId, request.queueIds))
                },
            )
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen()
        }
        composable(
            NavRoutes.DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) {
            DetailScreen(
                onPlay = { itemId, startPos ->
                    navController.navigate(NavRoutes.player(itemId, startPos))
                },
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.detail(itemId))
                },
                onOpenPerson = { personId ->
                    navController.navigate(NavRoutes.personDetail(personId))
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(
            NavRoutes.PERSON_DETAIL,
            arguments = listOf(navArgument("personId") { type = NavType.StringType }),
        ) {
            PersonScreen(
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.detail(itemId))
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(
            NavRoutes.COLLECTIONS_DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) {
            CollectionsDetailScreen(
                onPlay = { itemId ->
                    navController.navigate(NavRoutes.player(itemId))
                },
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.collectionsDetail(itemId))
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(
            NavRoutes.PLAYER,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("start") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            ),
        ) {
            PlayerScreen(
                onBack = {
                    navController.popBackStack()
                },
                onOpenItem = { nextItemId ->
                    val playerDestinationId = navController.graph.findNode(NavRoutes.PLAYER)?.id
                    navController.navigate(NavRoutes.player(nextItemId)) {
                        if (playerDestinationId != null) {
                            popUpTo(playerDestinationId) { inclusive = true }
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = NavRoutes.AUDIO_PLAYER,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("queue") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            AudioPlayerScreen(
                onBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AuthBootstrapScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = "Restoring session...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaceholderScreen(name: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "This feature is coming soon.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onBack) {
                Text("Go Back")
            }
        }
    }
}
