@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.theme.BackgroundDark
import com.rpeters.cinefintv.ui.theme.CinefinRed
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

/**
 * Full-bleed cinematic hero section shared by MovieDetailLayout and TvShowDetailLayout.
 */
@Composable
fun CinematicHero(
    backdropUrl: String?,
    logoUrl: String?,
    title: String,
    eyebrow: String,
    ratingText: String?,
    genres: List<String>,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActions: List<Pair<String, () -> Unit>> = emptyList(),
    primaryActionFocusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    val themeController = LocalCinefinThemeController.current
    val context = LocalContext.current

    // logoLoaded: start true if there's no logo (show text immediately)
    var logoLoaded by remember { mutableStateOf(logoUrl == null) }

    // Clear seed color when leaving this screen
    DisposableEffect(Unit) {
        onDispose { themeController.updateSeedColor(null) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = screenHeight * 0.52f),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Backdrop image with Palette extraction
        if (backdropUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(backdropUrl)
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onSuccess = { state ->
                    val bitmap = state.result.image.toBitmap()
                    Palette.from(bitmap).generate { palette ->
                        val color = palette?.vibrantSwatch?.rgb ?: palette?.dominantSwatch?.rgb
                        color?.let {
                            themeController.updateSeedColor(Color(it))
                        }
                    }
                },
            )
        }

        // Gradient overlay: transparent → BackgroundDark
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.4f to BackgroundDark.copy(alpha = 0.3f),
                        1.0f to BackgroundDark,
                    )
                )
        )

        // Content: eyebrow + logo/title + meta + action bar
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Eyebrow line
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = spacing.elementGap),
            )

            // Logo or title
            AnimatedVisibility(
                visible = logoLoaded,
                enter = fadeIn(tween(300)),
            ) {
                if (logoUrl != null) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .heightIn(max = 120.dp)
                            .wrapContentWidth(),
                        onSuccess = { logoLoaded = true },
                        onError = { logoLoaded = true },
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(Modifier.height(spacing.elementGap))

            // Rating + genre chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.chipGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = spacing.elementGap),
            ) {
                ratingText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = expressiveColors.titleAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                genres.take(3).forEach { genre ->
                    CinefinChip(label = genre)
                }
            }

            // Action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(expressiveColors.chromeSurface.copy(alpha = 0.85f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.border,
                        shape = RectangleShape,
                    )
                    .padding(horizontal = spacing.gutter, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.elementGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.colors(
                        containerColor = CinefinRed,
                        contentColor = Color.White,
                        focusedContainerColor = CinefinRed,
                        focusedContentColor = Color.White,
                    ),
                    modifier = Modifier.focusRequester(primaryActionFocusRequester),
                ) {
                    Text(primaryActionLabel, fontWeight = FontWeight.Bold)
                }

                secondaryActions.forEach { (label, action) ->
                    OutlinedButton(onClick = action) {
                        Text(label)
                    }
                }
            }
        }
    }
}
