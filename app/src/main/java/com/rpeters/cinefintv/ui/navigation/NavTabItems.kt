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
    val destination: NavDestination,
    val label: String,
    val icon: ImageVector,
)

val navTabItems = listOf(
    NavTabItem(Home, "Home", Icons.Default.Home),
    NavTabItem(LibraryMovies, "Movies", Icons.Default.Movie),
    NavTabItem(LibraryTvShows, "TV Shows", Icons.Default.Tv),
    NavTabItem(LibraryCollections, "Library", Icons.Default.Folder),
    NavTabItem(LibraryMusic, "Music", Icons.Default.MusicNote),
    NavTabItem(Search, "Search", Icons.Default.Search),
    NavTabItem(Settings, "Settings", Icons.Default.Settings),
)
