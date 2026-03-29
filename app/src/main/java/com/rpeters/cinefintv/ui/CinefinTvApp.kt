package com.rpeters.cinefintv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.TabRow
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinDialogActions
import com.rpeters.cinefintv.ui.components.CinefinDialogSurface
import com.rpeters.cinefintv.ui.navigation.AuthRoutes
import com.rpeters.cinefintv.ui.navigation.CinefinTvNavGraph
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.navigation.navTabItems
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import com.rpeters.cinefintv.ui.theme.ThemeViewModel
import com.rpeters.cinefintv.update.UpdateInfo
import com.rpeters.cinefintv.update.UpdateInstallResult
import com.rpeters.cinefintv.update.UpdateManager
import com.rpeters.cinefintv.update.UpdateStatus
import com.rpeters.cinefintv.update.shouldCheckForUpdate
import kotlinx.coroutines.launch

/**
 * CompositionLocal to allow any screen to update the theme's seed color
 * for content-based dynamic theming (Material You).
 */
val LocalCinefinThemeController = compositionLocalOf<ThemeColorController> {
    error("No ThemeColorController provided")
}

class AppChromeFocusController {
    var topNavFocusRequester: FocusRequester? by mutableStateOf(null)
    var primaryContentFocusRequester: FocusRequester? by mutableStateOf(null)
}

val LocalAppChromeFocusController = compositionLocalOf<AppChromeFocusController?> { null }

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
fun CinefinTvApp(
    isAuthenticated: Boolean = false,
    updateManager: UpdateManager? = null,
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val themePrefs by themeViewModel.themePreferences.collectAsState()
    
    CinefinTvTheme(
        seedColor = themeViewModel.currentSeedColor,
        themeMode = themePrefs.themeMode,
        useDynamicColors = themePrefs.useDynamicColors,
        accentColor = themePrefs.accentColor,
        contrastLevel = themePrefs.contrastLevel,
    ) {
        CompositionLocalProvider(LocalCinefinThemeController provides themeViewModel) {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val expressiveColors = LocalCinefinExpressiveColors.current
            val spacing = LocalCinefinSpacing.current
            val coroutineScope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current

            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var isDownloading by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableStateOf(0f) }
            var updateError by remember { mutableStateOf<String?>(null) }
            var lastUpdateCheckAtMs by remember { mutableStateOf(0L) }
            var isCheckingForUpdate by remember { mutableStateOf(false) }

            fun launchUpdateCheck(force: Boolean = false) {
                if (updateManager == null || isCheckingForUpdate) return

                val nowMs = System.currentTimeMillis()
                if (!force && !shouldCheckForUpdate(lastUpdateCheckAtMs, nowMs)) return

                isCheckingForUpdate = true
                lastUpdateCheckAtMs = nowMs
                coroutineScope.launch {
                    val status = updateManager.checkForUpdate()
                    when (status) {
                        is UpdateStatus.UpdateAvailable -> {
                            updateInfo = status.info
                            updateError = null
                        }
                        is UpdateStatus.NoUpdate -> {
                            updateInfo = null
                            updateError = null
                        }
                        is UpdateStatus.Error -> {
                            updateError = status.message
                        }
                        is UpdateStatus.Downloading -> Unit
                    }
                    isCheckingForUpdate = false
                }
            }

            if (updateManager != null) {
                LaunchedEffect(updateManager) {
                    launchUpdateCheck(force = true)
                }

                DisposableEffect(lifecycleOwner, updateManager) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            launchUpdateCheck()
                        }
                    }

                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }
            }

            updateInfo?.let { info ->
                UpdateDialog(
                    info = info,
                    isDownloading = isDownloading,
                    progress = downloadProgress,
                    errorMessage = updateError,
                    onConfirm = {
                        if (updateManager != null) {
                            isDownloading = true
                            downloadProgress = 0f
                            updateError = null
                            coroutineScope.launch {
                                val result = updateManager.downloadAndInstallApk(info) { progress ->
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
                        if (!info.isCritical) {
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
                !currentRoute.startsWith("movie/detail/") &&
                !currentRoute.startsWith("tvshow/detail/") &&
                !currentRoute.startsWith("season/detail/") &&
                !currentRoute.startsWith("episode/detail/") &&
                !currentRoute.startsWith("stuff/detail/") &&
                !currentRoute.startsWith("detail/person/")

            val selectedTabIndex = navTabItems.indexOfFirst { item ->
                currentRoute != null && (currentRoute == item.route || 
                    (item.route.isNotEmpty() && currentRoute.startsWith(item.route)))
            }.let { if (it == -1) navTabItems.indexOfFirst { it.route == NavRoutes.HOME } else it }
            .coerceAtLeast(0)

            fun navigateToTab(route: String) {
                if (currentRoute != route) {
                    val homeDestinationId = navController.graph.findNode(NavRoutes.HOME)?.id
                    navController.navigate(route) {
                        if (homeDestinationId != null) {
                            popUpTo(homeDestinationId) {
                                saveState = true
                            }
                        } else {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }

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
                CinefinAppScaffold(
                    showNav = showNav,
                    selectedTabIndex = selectedTabIndex,
                    onNavigateToTab = ::navigateToTab,
                ) {
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
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun CinefinAppScaffold(
    showNav: Boolean,
    selectedTabIndex: Int,
    onNavigateToTab: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    val tabFocusRequesters = remember {
        List(navTabItems.size) { FocusRequester() }
    }
    val chromeFocusController = remember { AppChromeFocusController() }
    val selectedTabFocusRequester = tabFocusRequesters[selectedTabIndex]

    SideEffect {
        chromeFocusController.topNavFocusRequester = if (showNav) selectedTabFocusRequester else null
    }

    CompositionLocalProvider(LocalAppChromeFocusController provides chromeFocusController) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showNav) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.gutter, vertical = 12.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                    ),
                    border = androidx.tv.material3.Border(
                        border = BorderStroke(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
                        ),
                    ),
                    tonalElevation = 12.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.05f),
                                        Color.Transparent,
                                    ),
                                ),
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusProperties {
                                    enter = { selectedTabFocusRequester }
                                }
                                .testTag(AppTestTags.NavBar),
                            indicator = { tabPositions, doesTabRowHaveFocus ->
                                TabRowDefaults.UnderlinedIndicator(
                                    currentTabPosition = tabPositions[selectedTabIndex],
                                    doesTabRowHaveFocus = doesTabRowHaveFocus,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    inactiveColor = Color.Transparent
                                )
                            },
                            containerColor = Color.Transparent
                        ) {
                            navTabItems.forEachIndexed { index, item ->
                                var isFocused by remember { mutableStateOf(false) }
                                Tab(
                                    selected = index == selectedTabIndex,
                                    onFocus = { isFocused = true },
                                    modifier = Modifier
                                        .focusRequester(tabFocusRequesters[index])
                                        .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                        .focusProperties {
                                            chromeFocusController.primaryContentFocusRequester?.let {
                                                down = it
                                            }
                                        }
                                        .testTag(AppTestTags.tab(item.route)),
                                    onClick = { onNavigateToTab(item.route) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (index == selectedTabIndex || isFocused) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontSize = 16.sp,
                                                fontWeight = if (index == selectedTabIndex || isFocused) 
                                                    FontWeight.ExtraBold 
                                                else 
                                                    FontWeight.Medium,
                                                letterSpacing = 0.5.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (index == selectedTabIndex || isFocused) 
                                                MaterialTheme.colorScheme.onSurface 
                                            else 
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            content()
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
    CinefinDialogSurface(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(520.dp),
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
                CinefinDialogActions(
                    dismissLabel = if (info.isCritical) null else "Later",
                    confirmLabel = "Update Now",
                    onDismiss = if (info.isCritical) null else onDismiss,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}
