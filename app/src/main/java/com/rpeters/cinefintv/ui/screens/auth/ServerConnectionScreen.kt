package com.rpeters.cinefintv.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import com.rpeters.cinefintv.ui.components.RequestScreenFocus
import com.rpeters.cinefintv.ui.components.CinefinTextInputField
import com.rpeters.cinefintv.ui.components.TvScreenTopFocusAnchor
import com.rpeters.cinefintv.ui.components.rememberTvScreenFocusState
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ServerConnectionScreen(
    serverUrl: String,
    isLoading: Boolean,
    errorMessage: String?,
    onServerUrlChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val screenFocus = rememberTvScreenFocusState()

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
                .widthIn(max = 820.dp)
                .fillMaxWidth()
                .defaultMinSize(minWidth = 420.dp),
            shape = RoundedCornerShape(32.dp),
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
                    title = "Connect to Jellyfin",
                    description = "Enter your server URL. Local IPs and reverse-proxy URLs are supported.",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = expressiveColors.titleAccent,
                        )
                    },
                )

                AuthTextField(
                    label = "Server URL",
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    placeholder = "https://media.example.com",
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Uri,
                    modifier = Modifier
                        .focusRequester(screenFocus.primaryContentRequester)
                        .focusProperties {
                            up = screenFocus.topAnchorRequester
                        },
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                WideButton(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                ) {
                    Text(if (isLoading) "Connecting..." else "Continue")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    CinefinTextInputField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        imeAction = imeAction,
        keyboardType = keyboardType,
        visualTransformation = visualTransformation,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AuthHero(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(
                containerColor = expressiveColors.chromeSurface.copy(alpha = 0.74f),
            ),
            border = Border(
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    expressiveColors.borderSubtle.copy(alpha = 0.6f),
                ),
            ),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = expressiveColors.pillMuted,
                            shape = CircleShape,
                        )
                        .padding(12.dp)
                ) {
                    icon()
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
