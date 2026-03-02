package com.rpeters.cinefintv.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.screens.auth.AuthViewModel
import com.rpeters.cinefintv.ui.screens.auth.LoginScreen
import com.rpeters.cinefintv.ui.screens.auth.ServerConnectionScreen
import com.rpeters.cinefintv.ui.screens.detail.DetailScreen
import com.rpeters.cinefintv.ui.screens.home.HomeScreen
import com.rpeters.cinefintv.ui.screens.library.LibraryCategory
import com.rpeters.cinefintv.ui.screens.library.LibraryScreen
import com.rpeters.cinefintv.ui.screens.music.MusicScreen
import com.rpeters.cinefintv.ui.screens.search.SearchScreen
import com.rpeters.cinefintv.ui.player.PlayerScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTvNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AuthRoutes.SERVER_CONNECTION,
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

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

    NavHost(navController = navController, startDestination = startDestination) {
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
                onLogin = { username, password ->
                    authViewModel.login(username, password)
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
            LibraryScreen(
                category = LibraryCategory.STUFF,
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.detail(itemId))
                },
            )
        }
        composable(NavRoutes.LIBRARY_MUSIC) {
            MusicScreen(
                onOpenItem = { itemId ->
                    navController.navigate(NavRoutes.player(itemId))
                },
            )
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
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaceholderScreen(name: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name)
    }
}
