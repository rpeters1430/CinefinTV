package com.rpeters.cinefintv.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTvNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AuthRoutes.SERVER_CONNECTION,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(AuthRoutes.SERVER_CONNECTION) {
            PlaceholderScreen("Server Connection") {
                navController.navigate(AuthRoutes.LOGIN) {
                    popUpTo(AuthRoutes.SERVER_CONNECTION) { inclusive = true }
                }
            }
        }
        composable(AuthRoutes.LOGIN) {
            PlaceholderScreen("Login") {
                navController.navigate(NavRoutes.HOME) {
                    popUpTo(AuthRoutes.LOGIN) { inclusive = true }
                }
            }
        }
        composable(NavRoutes.HOME) {
            PlaceholderScreen("Home")
        }
        composable(NavRoutes.SEARCH) {
            PlaceholderScreen("Search")
        }
        composable(NavRoutes.LIBRARY_MOVIES) {
            PlaceholderScreen("Movies")
        }
        composable(NavRoutes.LIBRARY_TVSHOWS) {
            PlaceholderScreen("TV Shows")
        }
        composable(NavRoutes.LIBRARY_STUFF) {
            PlaceholderScreen("Stuff")
        }
        composable(NavRoutes.LIBRARY_MUSIC) {
            PlaceholderScreen("Music")
        }
        composable(
            NavRoutes.DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            PlaceholderScreen("Detail: $itemId")
        }
        composable(
            NavRoutes.PLAYER,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            PlaceholderScreen("Player: $itemId")
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
