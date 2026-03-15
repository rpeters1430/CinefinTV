package com.rpeters.cinefintv.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import androidx.tv.material3.WideButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import com.rpeters.cinefintv.ui.components.RequestScreenFocus
import com.rpeters.cinefintv.ui.components.TvScreenTopFocusAnchor
import com.rpeters.cinefintv.ui.components.rememberTvScreenFocusState
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    serverUrl: String,
    isAuthenticating: Boolean,
    errorMessage: String?,
    isQuickConnectEnabled: Boolean,
    isQuickConnectLoading: Boolean,
    quickConnectCode: String?,
    quickConnectPollStatus: String?,
    quickConnectError: String?,
    onLogin: (username: String, password: String) -> Unit,
    onUseQuickConnect: () -> Unit,
    onGenerateNewCode: () -> Unit,
    onLeaveScreen: () -> Unit,
    onBack: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showQuickConnectPanel by rememberSaveable { mutableStateOf(false) }
    val screenFocus = rememberTvScreenFocusState()

    val canSignIn = username.isNotBlank() && password.isNotBlank() && !isAuthenticating

    val helperText = remember(serverUrl) {
        if (serverUrl.isBlank()) "Connect to a server first." else "Signing in to $serverUrl"
    }

    if (showQuickConnectPanel) {
        QuickConnectPanel(
            isLoading = isQuickConnectLoading,
            code = quickConnectCode,
            pollStatus = quickConnectPollStatus,
            error = quickConnectError,
            onNewCode = onGenerateNewCode,
            onCancel = {
                onLeaveScreen()
                showQuickConnectPanel = false
            },
        )
    } else {
        RequestScreenFocus(
            key = serverUrl,
            requester = screenFocus.primaryContentRequester,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            expressiveColors.backgroundTop,
                            expressiveColors.backgroundBottom,
                        ),
                    ),
                )
                .padding(horizontal = 64.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 860.dp)
                    .fillMaxWidth()
                    .defaultMinSize(minWidth = 460.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = expressiveColors.chromeSurface.copy(alpha = 0.84f),
                ),
                tonalElevation = 8.dp,
            ) {
                val scrollState = rememberScrollState()
                val coroutineScope = rememberCoroutineScope()
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .padding(horizontal = 28.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    TvScreenTopFocusAnchor(
                        state = screenFocus,
                        onFocused = {
                            coroutineScope.launch {
                                scrollState.scrollTo(0)
                            }
                        },
                    )

                    AuthHero(
                        title = "Sign In",
                        description = helperText,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = expressiveColors.titleAccent,
                            )
                        },
                    )

                    AuthTextField(
                        label = "Username",
                        value = username,
                        onValueChange = { username = it },
                        placeholder = "Username",
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Text,
                        modifier = Modifier
                            .focusRequester(screenFocus.primaryContentRequester)
                            .focusProperties {
                                up = screenFocus.topAnchorRequester
                            },
                    )

                    AuthPasswordField(
                        value = password,
                        onValueChange = { password = it },
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    WideButton(
                        onClick = { onLogin(username.trim(), password) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canSignIn,
                    ) {
                        Text(if (isAuthenticating) "Signing In..." else "Sign In")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = onBack) {
                            Text("Back")
                        }

                        if (isQuickConnectEnabled) {
                            Button(
                                onClick = {
                                    showQuickConnectPanel = true
                                    onUseQuickConnect()
                                },
                                enabled = !isAuthenticating,
                            ) {
                                Text("Use Quick Connect")
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onLeaveScreen() }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickConnectPanel(
    isLoading: Boolean,
    code: String?,
    pollStatus: String?,
    error: String?,
    onNewCode: () -> Unit,
    onCancel: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val screenFocus = rememberTvScreenFocusState()

    RequestScreenFocus(
        key = code ?: error ?: pollStatus,
        requester = screenFocus.primaryContentRequester,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        expressiveColors.backgroundTop,
                        expressiveColors.backgroundBottom,
                    ),
                ),
            )
            .padding(horizontal = 64.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 860.dp)
                .fillMaxWidth()
                .defaultMinSize(minWidth = 460.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
            colors = SurfaceDefaults.colors(
                containerColor = expressiveColors.chromeSurface.copy(alpha = 0.84f),
            ),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = 28.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                TvScreenTopFocusAnchor(
                    state = screenFocus,
                    onFocused = {
                        coroutineScope.launch {
                            scrollState.scrollTo(0)
                        }
                    },
                )

                AuthHero(
                    title = "Quick Connect",
                    description = "Enter this code in the Jellyfin app on your phone or computer:",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Password,
                            contentDescription = null,
                            tint = expressiveColors.titleAccent,
                        )
                    },
                )

                // error is checked first — the ViewModel does not clear quickConnectCode on denial/expiry,
                // so error must take priority over code to avoid showing a stale code with no explanation.
                when {
                    error != null -> {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    isLoading && code == null -> {
                        Text(
                            text = "Generating code...",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    code != null -> {
                        Text(
                            text = code.toCharArray().joinToString("    "),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                if (pollStatus != null) {
                    Text(
                        text = pollStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    WideButton(
                        onClick = onNewCode,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(screenFocus.primaryContentRequester)
                            .focusProperties {
                                up = screenFocus.topAnchorRequester
                            },
                        enabled = !isLoading,
                    ) {
                        Text("New Code")
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthTextField(
        label = "Password",
        value = value,
        onValueChange = onValueChange,
        placeholder = "Password",
        imeAction = ImeAction.Done,
        keyboardType = KeyboardType.Password,
        modifier = modifier,
        visualTransformation = PasswordVisualTransformation(),
    )
}
