package com.rpeters.cinefintv.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector

data class NavTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val navTabItems = listOf(
    NavTabItem(NavRoutes.HOME, "Home", Icons.Default.Home),
    NavTabItem(NavRoutes.LIBRARY_MOVIES, "Movies", Icons.Default.Movie),
    NavTabItem(NavRoutes.LIBRARY_TVSHOWS, "TV Shows", Icons.Default.Tv),
    NavTabItem(NavRoutes.LIBRARY_COLLECTIONS, "Stuff", Icons.Default.Folder),
    NavTabItem(NavRoutes.LIBRARY_MUSIC, "Music", Icons.Default.MusicNote),
    NavTabItem(NavRoutes.SEARCH, "Search", Icons.Default.Search),
    NavTabItem(NavRoutes.SETTINGS, "Settings", Icons.Default.Settings),
)
