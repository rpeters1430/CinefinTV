package com.rpeters.cinefintv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.navigation.AuthRoutes
import com.rpeters.cinefintv.ui.navigation.CinefinTvNavGraph
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.update.UpdateInfo
import com.rpeters.cinefintv.update.UpdateInstallResult
import com.rpeters.cinefintv.update.UpdateManager
import com.rpeters.cinefintv.update.UpdateStatus
import kotlinx.coroutines.launch

private data class NavTabItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
)

private val navTabItems = listOf(
    NavTabItem("Home", NavRoutes.HOME, Icons.Default.Home),
    NavTabItem("TV Shows", NavRoutes.LIBRARY_TVSHOWS, Icons.Default.Tv),
    NavTabItem("Movies", NavRoutes.LIBRARY_MOVIES, Icons.Default.Movie),
    NavTabItem("Music", NavRoutes.LIBRARY_MUSIC, Icons.Default.MusicNote),
    NavTabItem("Stuff", NavRoutes.LIBRARY_STUFF, Icons.Default.VideoLibrary),
    NavTabItem("Search", NavRoutes.SEARCH, Icons.Default.Search),
    NavTabItem("Settings", NavRoutes.SETTINGS, Icons.Default.Settings),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
fun CinefinTvApp(
    isAuthenticated: Boolean = false,
    updateManager: UpdateManager? = null
) {
    CinefinTvTheme {
        val navController = rememberNavController()
        val currentBackStack by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStack?.destination?.route
        val coroutineScope = rememberCoroutineScope()

        var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        var isDownloading by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableStateOf(0f) }
        var updateError by remember { mutableStateOf<String?>(null) }

        // Check for updates on startup
        if (updateManager != null) {
            LaunchedEffect(Unit) {
                val status = updateManager.checkForUpdate()
                if (status is UpdateStatus.UpdateAvailable) {
                    updateInfo = status.info
                    updateError = null
                }
            }
        }

        if (updateInfo != null) {
            UpdateDialog(
                info = updateInfo!!,
                isDownloading = isDownloading,
                progress = downloadProgress,
                errorMessage = updateError,
                onConfirm = {
                    if (updateManager != null) {
                        isDownloading = true
                        downloadProgress = 0f
                        updateError = null
                        coroutineScope.launch {
                            val result = updateManager.downloadAndInstallApk(updateInfo!!) { progress ->
                                downloadProgress = progress
                            }
                            isDownloading = false
                            result.fold(
                                onSuccess = { installResult ->
                                    when (installResult) {
                                        UpdateInstallResult.InstallerLaunched -> {
                                            updateInfo = null
                                            updateError = null
                                        }
                                        UpdateInstallResult.PermissionRequired -> {
                                            updateError = "Allow installs from this app, then choose Update Now again."
                                        }
                                    }
                                },
                                onFailure = { error ->
                                    updateError = error.message ?: "Update failed."
                                }
                            )
                        }
                    }
                },
                onDismiss = {
                    if (!updateInfo!!.isCritical) {
                        updateInfo = null
                        updateError = null
                    }
                }
            )
        }

        val showNav = currentRoute != null &&
            !currentRoute.startsWith("auth/") &&
            !currentRoute.startsWith("player/") &&
            !currentRoute.startsWith("audio-player/") &&
            !currentRoute.startsWith("detail/") &&
            !currentRoute.startsWith("stuff/detail/")

        // Determine selected tab based on route prefix to keep it highlighted during sub-navigation
        val selectedTabIndex = navTabItems.indexOfFirst { item ->
            currentRoute != null && (currentRoute == item.route || 
                (item.route.isNotEmpty() && currentRoute.startsWith(item.route)))
        }.let { if (it == -1) navTabItems.indexOfFirst { it.route == NavRoutes.HOME } else it }
        .coerceAtLeast(0)

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
                            .padding(horizontal = 48.dp, vertical = 24.dp),
                    ) {
                        val activeRoute = currentRoute.orEmpty()
                        navTabItems.forEachIndexed { index, item ->
                            Tab(
                                selected = index == selectedTabIndex,
                                onFocus = {},
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                colors = TabDefaults.pillIndicatorTabColors(
                                    contentColor = MaterialTheme.colorScheme.onBackground,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContentColor = Color(0xFF0D1117),
                                    focusedContentColor = Color(0xFFE50914),
                                    focusedSelectedContentColor = Color(0xFF0D1117),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    disabledInactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    disabledSelectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    isDownloading: Boolean,
    progress: Float,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(450.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "New Update Available",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Version ${info.versionName} is ready to install.",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (!info.releaseNotes.isNullOrBlank()) {
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = "Downloading... ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
                    ) {
                        if (!info.isCritical) {
                            androidx.tv.material3.OutlinedButton(onClick = onDismiss) {
                                Text("Later")
                            }
                        }
                        Button(onClick = onConfirm) {
                            Text("Update Now")
                        }
                    }
                }
            }
        }
    }
}
