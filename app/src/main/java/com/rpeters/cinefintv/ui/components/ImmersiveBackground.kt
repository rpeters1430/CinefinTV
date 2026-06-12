package com.rpeters.cinefintv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay

@Composable
fun ImmersiveBackground(
    backdropUrl: String?,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.Black,
    scrimColor: Color = Color.Black,
) {
    var stableBackdropUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(backdropUrl) {
        if (backdropUrl == null) {
            stableBackdropUrl = null
        } else if (stableBackdropUrl == null) {
            // First load: set immediately to avoid a blank screen
            stableBackdropUrl = backdropUrl
        } else {
            // Subsequent loads: wait until focus settles to prevent rapid flashing and lag
            delay(500L)
            stableBackdropUrl = backdropUrl
        }
    }

    Box(modifier = modifier.fillMaxSize().background(baseColor)) {
        Crossfade(
            targetState = stableBackdropUrl,
            animationSpec = tween(1000),
            label = "backdropCrossfade"
        ) { imageUrl ->
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        // Complex premium scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to scrimColor.copy(alpha = 0.38f),
                            0.35f to Color.Transparent,
                            0.75f to scrimColor.copy(alpha = 0.52f),
                            1.0f to scrimColor
                        )
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to scrimColor.copy(alpha = 0.62f),
                            0.25f to scrimColor.copy(alpha = 0.08f),
                            0.75f to Color.Transparent,
                        )
                    )
                )
        )
    }
}
