package com.rpeters.cinefintv.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns

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

    Column(
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
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
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
            value = serverUrl,
            onValueChange = onServerUrlChange,
            placeholder = "https://media.example.com",
            imeAction = ImeAction.Done,
            keyboardType = KeyboardType.Uri,
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onContinue,
                enabled = !isLoading,
            ) {
                Text(if (isLoading) "Connecting..." else "Continue")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val shape = RoundedCornerShape(12.dp)
    val expressiveColors = LocalCinefinExpressiveColors.current

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onBackground,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = imeAction,
            keyboardType = keyboardType,
        ),
        visualTransformation = visualTransformation,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = expressiveColors.borderSubtle,
                shape = shape,
            )
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        expressiveColors.chromeSurface,
                        expressiveColors.accentSurface.copy(alpha = 0.92f),
                    ),
                ),
                shape,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp),
            ) {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
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
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        expressiveColors.heroStart.copy(alpha = 0.92f),
                        expressiveColors.heroEnd.copy(alpha = 0.95f),
                    ),
                ),
            )
            .border(
                BorderStroke(1.dp, expressiveColors.borderSubtle.copy(alpha = 0.75f)),
                RoundedCornerShape(28.dp),
            )
            .padding(28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(expressiveColors.pillMuted)
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
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
