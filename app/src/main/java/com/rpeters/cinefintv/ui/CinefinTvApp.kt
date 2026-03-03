package com.rpeters.cinefintv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.navigation.AuthRoutes
import com.rpeters.cinefintv.ui.navigation.CinefinTvNavGraph
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme

private data class NavTabItem(
    val label: String,
    val route: String,
)

private val navTabItems = listOf(
    NavTabItem("Search", NavRoutes.SEARCH),
    NavTabItem("Home", NavRoutes.HOME),
    NavTabItem("TV Shows", NavRoutes.LIBRARY_TVSHOWS),
    NavTabItem("Movies", NavRoutes.LIBRARY_MOVIES),
    NavTabItem("Music", NavRoutes.LIBRARY_MUSIC),
    NavTabItem("Stuff", NavRoutes.LIBRARY_STUFF),
    NavTabItem("Settings", NavRoutes.SETTINGS),
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
            !currentRoute.startsWith("player/") &&
            !currentRoute.startsWith("detail/")

        val selectedTabIndex = navTabItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showNav) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 12.dp),
                    ) {
                        navTabItems.forEachIndexed { index, item ->
                            Tab(
                                selected = index == selectedTabIndex,
                                onFocus = {},
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                            ) {
                                Text(item.label)
                            }
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    CinefinTvNavGraph(
                        navController = navController,
                        startDestination = if (isAuthenticated) NavRoutes.HOME else AuthRoutes.SERVER_CONNECTION,
                    )
                }
            }
        }
    }
}

