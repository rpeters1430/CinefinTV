package com.rpeters.cinefintv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.lerp
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.key
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinDialogActions
import com.rpeters.cinefintv.ui.components.CinefinDialogSurface
import com.rpeters.cinefintv.ui.navigation.CinefinTvNavGraph
import com.rpeters.cinefintv.ui.navigation.NavDestination
import com.rpeters.cinefintv.ui.navigation.ServerConnection
import com.rpeters.cinefintv.ui.navigation.Home
import com.rpeters.cinefintv.ui.navigation.appChromeRouteSpec
import com.rpeters.cinefintv.ui.navigation.isTopLevelTabDestination
import com.rpeters.cinefintv.ui.navigation.navigateToTopLevelDestination
import com.rpeters.cinefintv.ui.navigation.navTabItems
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinMotion
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import com.rpeters.cinefintv.ui.theme.ThemeViewModel
import com.rpeters.cinefintv.update.UpdateInfo
import com.rpeters.cinefintv.update.UpdateInstallResult
import com.rpeters.cinefintv.update.UpdateManager
import com.rpeters.cinefintv.update.UpdateStatus
import com.rpeters.cinefintv.update.shouldCheckForUpdate
import kotlinx.coroutines.delay
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
    val motionSpec by themeViewModel.motionSpec.collectAsState()
    
    CinefinTvTheme(
        seedColor = themeViewModel.currentSeedColor,
        themeMode = themePrefs.themeMode,
        useDynamicColors = themePrefs.useDynamicColors,
        accentColor = themePrefs.accentColor,
        contrastLevel = themePrefs.contrastLevel,
    ) {
        CompositionLocalProvider(
            LocalCinefinThemeController provides themeViewModel,
            LocalCinefinMotion provides motionSpec,
        ) {
            val backStack: NavBackStack<NavKey> = key(isAuthenticated) {
                rememberNavBackStack(
                    if (isAuthenticated) Home else ServerConnection
                )
            }
            val currentDestination = backStack.lastOrNull() as? NavDestination
            
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

            val appChromeRouteSpec = appChromeRouteSpec(currentDestination)

            fun onNavigate(destination: NavDestination) {
                if (destination.isTopLevelTabDestination()) {
                    backStack.navigateToTopLevelDestination(destination)
                } else {
                    backStack.add(destination)
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
                    onNavigateToTab = ::onNavigate,
                ) {
                    CinefinTvNavGraph(
                        backStack = backStack,
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
    onNavigateToTab: (NavDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val tabFocusRequesters = remember {
        List(navTabItems.size) { FocusRequester() }
    }
    val chromeFocusController = remember { AppChromeFocusController() }
    var lastShowNav by remember { mutableStateOf(showNav) }

    // When we return to a screen that shows the nav bar after it was hidden (e.g. from Player or Details),
    // automatically trigger focus restoration to the primary content area.
    LaunchedEffect(showNav) {
        if (showNav && !lastShowNav) {
            chromeFocusController.shouldRestoreFocusToContent = true
        }
        lastShowNav = showNav
    }

    val selectedTabFocusRequester = tabFocusRequesters.getOrElse(selectedTabIndex) { FocusRequester() }
    var focusedTabIndex by remember { mutableStateOf<Int?>(null) }
    val navHasFocus = focusedTabIndex != null
    val railWidth = 196.dp
    val railSlotStartPadding = 12.dp
    val railSlotWidth = railWidth + railSlotStartPadding
    val railProgress by animateFloatAsState(
        targetValue = if (navHasFocus) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "railProgress",
    )
    val logoSize = lerp(50.dp, 46.dp, railProgress)
    val iconSize = lerp(28.dp, 22.dp, railProgress)
    val buttonPaddingVertical = lerp(14.dp, 10.dp, railProgress)
    val buttonPaddingHorizontal = 12.dp

    SideEffect {
        chromeFocusController.topNavFocusRequester = if (showNav) selectedTabFocusRequester else null
    }

    // Reset focused-tab state when the rail hides so it doesn't flash expanded on return.
    if (!showNav && focusedTabIndex != null) {
        focusedTabIndex = null
    }
    if (!showNav && chromeFocusController.primaryContentFocusRequester != null) {
        chromeFocusController.primaryContentFocusRequester = null
    }

    CompositionLocalProvider(LocalAppChromeFocusController provides chromeFocusController) {
        // After a delete-and-back action the content area may be reloading, leaving focus stranded
        // in the nav rail. Watch for the flag and redirect focus to content once it is available.
        LaunchedEffect(
            chromeFocusController.shouldRestoreFocusToContent,
            chromeFocusController.primaryContentFocusRequester,
        ) {
            if (chromeFocusController.shouldRestoreFocusToContent) {
                val target = chromeFocusController.primaryContentFocusRequester
                if (target != null) {
                    chromeFocusController.shouldRestoreFocusToContent = false

                    // Focus often settles on the nav rail for a few frames after restoring
                    // a top-level destination, so retry briefly instead of firing once.
                    repeat(5) { attempt ->
                        withFrameNanos { }
                        withFrameNanos { }
                        delay(if (attempt == 0) 120L else 60L)
                        runCatching { target.requestFocus() }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            if (showNav) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(railSlotWidth)
                        .padding(start = railSlotStartPadding, top = 12.dp, bottom = 12.dp)
                        .zIndex(1f),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(railWidth),
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
                                .focusProperties {
                                    onEnter = {
                                        selectedTabFocusRequester.requestFocus()
                                    }
                                }
                                .focusGroup()
                                .testTag(AppTestTags.NavBar),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(modifier = Modifier.padding(vertical = 16.dp, horizontal = 10.dp)) {
                                Column(
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
                                val showLabel = navHasFocus || index == selectedTabIndex
                                Button(
                                    onClick = { onNavigateToTab(item.destination) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(tabFocusRequesters[index])
                                        .onFocusChanged {
                                            val focused = it.isFocused || it.hasFocus
                                            isFocused = focused
                                            when {
                                                focused -> focusedTabIndex = index
                                                focusedTabIndex == index -> focusedTabIndex = null
                                            }
                                        }
                                        .focusProperties {
                                            chromeFocusController.primaryContentFocusRequester?.let {
                                                right = it
                                            }
                                        }
                                        .testTag(AppTestTags.tab(item.label)),
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
                                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                                    ) {
                                        Icon(
                                            item.icon,
                                            contentDescription = item.label,
                                            modifier = Modifier.size(iconSize),
                                            tint = if (index == selectedTabIndex || isFocused) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                                            },
                                        )
                                        AnimatedVisibility(
                                            visible = showLabel,
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
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = if (showNav) 8.dp else 0.dp)
                    .testTag(AppTestTags.ContentHost),
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
