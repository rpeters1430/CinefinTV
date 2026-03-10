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
import com.rpeters.cinefintv.ui.screens.auth.AuthViewModel
import com.rpeters.cinefintv.ui.screens.auth.LoginScreen
import com.rpeters.cinefintv.ui.screens.auth.ServerConnectionScreen
import com.rpeters.cinefintv.ui.screens.detail.DetailScreen
import com.rpeters.cinefintv.ui.screens.home.HomeScreen
import com.rpeters.cinefintv.ui.screens.library.LibraryCategory
import com.rpeters.cinefintv.ui.screens.library.LibraryScreen
import com.rpeters.cinefintv.ui.screens.music.MusicScreen
import com.rpeters.cinefintv.ui.screens.person.PersonScreen
import com.rpeters.cinefintv.ui.screens.stuff.StuffDetailScreen
import com.rpeters.cinefintv.ui.screens.stuff.StuffLibraryScreen
import com.rpeters.cinefintv.ui.screens.search.SearchScreen
import com.rpeters.cinefintv.ui.player.PlayerScreen
import com.rpeters.cinefintv.ui.player.audio.AudioPlayerScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
fun CinefinTvNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AuthRoutes.SERVER_CONNECTION,
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(authUiState.isSessionChecked) {
        if (authUiState.isSessionChecked && authUiState.isSessionActive) {
            navController.navigate(NavRoutes.HOME) {
                popUpTo(AuthRoutes.SERVER_CONNECTION) { inclusive = true }
            }
        }
    }

    LaunchedEffect(authUiState.connectedServerUrl) {
        if (authUiState.connectedServerUrl != null &&
            navController.currentDestination?.route == AuthRoutes.SERVER_CONNECTION
        ) {
            navController.navigate(AuthRoutes.LOGIN)
        }
    }

    LaunchedEffect(authUiState.loginSucceeded) {
        if (authUiState.loginSucceeded) {
            navController.navigate(NavRoutes.HOME) {
                popUpTo(AuthRoutes.SERVER_CONNECTION) { inclusive = true }
            }
            authViewModel.resetLoginSuccess()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
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
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.detail(itemId))
                },
                onPlayItem = { itemId ->
                    navController.navigate(NavRoutes.player(itemId))
                },
            )
        }
        composable(NavRoutes.SEARCH) {
            SearchScreen(
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.detail(itemId))
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
        composable(NavRoutes.LIBRARY_STUFF) {
            StuffLibraryScreen(
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.stuffDetail(itemId))
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
            PlaceholderScreen("Settings", onBack = { navController.popBackStack() })
        }
        composable(
            NavRoutes.DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) {
            DetailScreen(
                onPlay = { itemId ->
                    navController.navigate(NavRoutes.player(itemId))
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
            NavRoutes.STUFF_DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) {
            StuffDetailScreen(
                onPlay = { itemId ->
                    navController.navigate(NavRoutes.player(itemId))
                },
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.stuffDetail(itemId))
                },
                onBack = {
                    navController.popBackStack()
                },
            )
        }
        composable(
            NavRoutes.PLAYER,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) {
            PlayerScreen(
                onBack = {
                    navController.popBackStack()
                },
                onOpenItem = { nextItemId ->
                    navController.navigate(NavRoutes.player(nextItemId)) {
                        popUpTo(NavRoutes.PLAYER) { inclusive = true }
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
                    defaultValue = ""
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
