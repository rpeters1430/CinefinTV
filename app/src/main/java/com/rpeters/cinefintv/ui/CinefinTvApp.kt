package com.rpeters.cinefintv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinDialogActions
import com.rpeters.cinefintv.ui.components.CinefinDialogSurface
import com.rpeters.cinefintv.ui.navigation.AuthRoutes
import com.rpeters.cinefintv.ui.navigation.CinefinTvNavGraph
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.navigation.appChromeRouteSpec
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

val LocalCinefinThemeController = compositionLocalOf<ThemeColorController> {
    error("No ThemeColorController provided")
}

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

            val appChromeRouteSpec = appChromeRouteSpec(currentRoute)

            fun navigateToTab(route: String) {
                val activeRoute = navController.currentDestination?.route
                if (activeRoute != route) {
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
                    showNav = appChromeRouteSpec.showNav,
                    selectedTabIndex = appChromeRouteSpec.selectedTabIndex,
                    onNavigateToTab = ::navigateToTab,
                ) {
                    CinefinTvNavGraph(
                        navController = navController,
                        startDestination = if (isAuthenticated) NavRoutes.HOME else AuthRoutes.SERVER_CONNECTION,
                    )
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
    content: @Composable () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val tabFocusRequesters = remember {
        List(navTabItems.size) { FocusRequester() }
    }
    val chromeFocusController = remember { AppChromeFocusController() }
    val selectedTabFocusRequester = tabFocusRequesters[selectedTabIndex]
    var navHasFocus by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (showNav && navHasFocus) 224.dp else 104.dp,
        animationSpec = tween(durationMillis = 280),
        label = "railWidth",
    )
    val logoSize by animateDpAsState(
        targetValue = if (navHasFocus) 46.dp else 50.dp,
        animationSpec = tween(durationMillis = 280),
        label = "logoSize",
    )
    val iconSize by animateDpAsState(
        targetValue = if (navHasFocus) 22.dp else 28.dp,
        animationSpec = tween(durationMillis = 280),
        label = "iconSize",
    )
    val buttonPaddingVertical by animateDpAsState(
        targetValue = if (navHasFocus) 10.dp else 14.dp,
        animationSpec = tween(durationMillis = 280),
        label = "buttonPaddingVertical",
    )
    val buttonPaddingHorizontal by animateDpAsState(
        targetValue = if (navHasFocus) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 280),
        label = "buttonPaddingHorizontal",
    )

    SideEffect {
        chromeFocusController.topNavFocusRequester = if (showNav) selectedTabFocusRequester else null
    }

    CompositionLocalProvider(LocalAppChromeFocusController provides chromeFocusController) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showNav) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(railWidth)
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = expressiveColors.chromeSurface.copy(alpha = 0.46f),
                    ),
                    border = androidx.tv.material3.Border(
                        border = BorderStroke(
                            width = 1.dp,
                            color = expressiveColors.borderSubtle.copy(alpha = 0.28f),
                        ),
                    ),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .onFocusChanged { navHasFocus = it.hasFocus || it.isFocused }
                            .focusProperties {
                                onEnter = {
                                    selectedTabFocusRequester.requestFocus()
                                }
                            }
                            .focusGroup()
                            .testTag(AppTestTags.NavBar)
                            .padding(vertical = 16.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(logoSize)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                                            expressiveColors.titleAccent.copy(alpha = 0.42f),
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "C",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        navTabItems.forEachIndexed { index, item ->
                            var isFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = { onNavigateToTab(item.route) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(tabFocusRequesters[index])
                                    .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                    .focusProperties {
                                        chromeFocusController.primaryContentFocusRequester?.let {
                                            right = it
                                        }
                                    }
                                    .testTag(AppTestTags.tab(item.route)),
                                shape = ButtonDefaults.shape(RoundedCornerShape(18.dp)),
                                scale = ButtonDefaults.scale(
                                    focusedScale = 1.04f,
                                ),
                                colors = ButtonDefaults.colors(
                                    containerColor = if (index == selectedTabIndex || isFocused) {
                                        expressiveColors.detailPanelFocused.copy(alpha = if (navHasFocus) 0.82f else 0.72f)
                                    } else {
                                        Color.Transparent
                                    },
                                    focusedContainerColor = expressiveColors.detailPanelFocused.copy(alpha = 0.82f),
                                ),
                                border = ButtonDefaults.border(
                                    border = if (index == selectedTabIndex && !isFocused) {
                                        androidx.tv.material3.Border(
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = expressiveColors.focusRing.copy(alpha = 0.38f),
                                            ),
                                        )
                                    } else {
                                        androidx.tv.material3.Border(
                                            border = BorderStroke(1.dp, Color.Transparent),
                                        )
                                    },
                                    focusedBorder = androidx.tv.material3.Border(
                                        border = BorderStroke(
                                            width = 1.5.dp,
                                            color = expressiveColors.focusRing.copy(alpha = 0.72f),
                                        ),
                                    ),
                                ),
                                glow = ButtonDefaults.glow(
                                    focusedGlow = androidx.tv.material3.Glow(
                                        elevationColor = expressiveColors.focusGlow.copy(alpha = 0.16f),
                                        elevation = 6.dp,
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = buttonPaddingVertical,
                                            horizontal = buttonPaddingHorizontal,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = if (navHasFocus) {
                                        Arrangement.spacedBy(12.dp)
                                    } else {
                                        Arrangement.Center
                                    },
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(iconSize),
                                        tint = if (index == selectedTabIndex || isFocused) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                                        }
                                    )
                                    AnimatedVisibility(
                                        visible = navHasFocus,
                                        enter = fadeIn(animationSpec = tween(durationMillis = 200)) +
                                            expandHorizontally(animationSpec = tween(durationMillis = 280)),
                                        exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                                            shrinkHorizontally(animationSpec = tween(durationMillis = 280)),
                                    ) {
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontSize = 13.sp,
                                                fontWeight = if (index == selectedTabIndex || isFocused) {
                                                    FontWeight.SemiBold
                                                } else {
                                                    FontWeight.Medium
                                                },
                                                letterSpacing = 0.sp,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (index == selectedTabIndex || isFocused) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = if (showNav) 8.dp else 0.dp),
            ) {
                content()
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
