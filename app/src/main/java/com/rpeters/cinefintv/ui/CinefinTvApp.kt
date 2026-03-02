package com.rpeters.cinefintv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.navigation.AuthRoutes
import com.rpeters.cinefintv.ui.navigation.CinefinTvNavGraph
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme

private data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

private val navItems = listOf(
    NavItem("Home", Icons.Default.Home, NavRoutes.HOME),
    NavItem("Search", Icons.Default.Search, NavRoutes.SEARCH),
    NavItem("Movies", Icons.Default.Movie, NavRoutes.LIBRARY_MOVIES),
    NavItem("TV Shows", Icons.Default.Tv, NavRoutes.LIBRARY_TVSHOWS),
    NavItem("Stuff", Icons.Default.VideoLibrary, NavRoutes.LIBRARY_STUFF),
    NavItem("Music", Icons.Default.MusicNote, NavRoutes.LIBRARY_MUSIC),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTvApp(isAuthenticated: Boolean = false) {
    CinefinTvTheme {
        val navController = rememberNavController()
        val currentBackStack by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStack?.destination?.route

        val showNav = currentRoute != null &&
            !currentRoute.startsWith("auth/") &&
            !currentRoute.startsWith("player/")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showNav) {
                // drawerContent receives DrawerValue but NavigationDrawerScope.hasFocus
                // drives the open/closed animation internally; we ignore the parameter.
                NavigationDrawer(
                    drawerContent = { drawerValue ->
                        navItems.forEach { item ->
                            NavigationDrawerItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                    )
                                },
                            ) {
                                Text(item.label)
                            }
                        }
                    }
                ) {
                    CinefinTvNavGraph(
                        navController = navController,
                        startDestination = if (isAuthenticated) NavRoutes.HOME else AuthRoutes.SERVER_CONNECTION,
                    )
                }
            } else {
                CinefinTvNavGraph(
                    navController = navController,
                    startDestination = if (isAuthenticated) NavRoutes.HOME else AuthRoutes.SERVER_CONNECTION,
                )
            }
        }
    }
}
