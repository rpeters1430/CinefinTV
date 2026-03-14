package com.rpeters.cinefintv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.rpeters.cinefintv.ui.navigation.navTabItems
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.update.UpdateInfo
import com.rpeters.cinefintv.update.UpdateInstallResult
import com.rpeters.cinefintv.update.UpdateManager
import com.rpeters.cinefintv.update.UpdateStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
fun CinefinTvApp(
    isAuthenticated: Boolean = false,
    updateManager: UpdateManager? = null
) {
    CinefinTvTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val expressiveColors = LocalCinefinExpressiveColors.current
        val spacing = LocalCinefinSpacing.current
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
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            expressiveColors.backgroundTop,
                            expressiveColors.backgroundBottom,
                        ),
                    ),
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (showNav) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.gutter, vertical = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(spacing.cornerContainer))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            expressiveColors.chromeSurface,
                                            expressiveColors.accentSurface.copy(alpha = 0.92f),
                                        ),
                                    ),
                                )
                                .border(
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = expressiveColors.borderSubtle.copy(alpha = 0.75f),
                                    ),
                                    shape = RoundedCornerShape(spacing.cornerContainer),
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            TabRow(
                                selectedTabIndex = selectedTabIndex,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                navTabItems.forEachIndexed { index, item ->
                                    val isSelected = index == selectedTabIndex
                                    Tab(
                                        selected = isSelected,
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
                                            contentColor = Color.White.copy(alpha = 0.8f),
                                            inactiveContentColor = Color.White.copy(alpha = 0.5f),
                                            selectedContentColor = Color(0xFF0D1117),
                                            focusedContentColor = expressiveColors.focusRing,
                                            focusedSelectedContentColor = Color(0xFF0D1117),
                                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            disabledInactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            disabledSelectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        ),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = item.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
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
    val spacing = LocalCinefinSpacing.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(spacing.cornerCard),
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
                            color = ComposeMaterialTheme.colorScheme.primary,
                            trackColor = ComposeMaterialTheme.colorScheme.surfaceContainerHigh
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
