@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.screens.detail.DetailChipRow
import com.rpeters.cinefintv.ui.screens.detail.blockBringIntoView
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import kotlinx.coroutines.launch

data class HeroIconAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

@Composable
fun DetailStripTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun FlatDetailHero(
    backdropUrl: String?,
    logoUrl: String?,
    title: String,
    eyebrow: String,
    ratingText: String?,
    badges: List<String>,
    tagline: String?,
    description: String,
    creditLine: String?,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryIconActions: List<HeroIconAction>,
    primaryActionFocusRequester: FocusRequester,
    primaryActionDownFocusRequester: FocusRequester? = null,
    onDownNavigation: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 520.dp),
    ) {
        if (backdropUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(backdropUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = 0.9f),
                        0.32f to Color.Black.copy(alpha = 0.68f),
                        0.58f to Color.Black.copy(alpha = 0.3f),
                        0.82f to Color.Transparent,
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.14f),
                        0.58f to Color.Black.copy(alpha = 0.18f),
                        0.8f to MaterialTheme.colorScheme.background.copy(alpha = 0.48f),
                        1.0f to MaterialTheme.colorScheme.background,
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.66f)
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = 0.3f),
                        0.72f to Color.Black.copy(alpha = 0.08f),
                        1.0f to Color.Transparent,
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .padding(start = spacing.gutter, top = 48.dp, end = spacing.gutter)
                .background(
                    color = Color.Black.copy(alpha = 0.34f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!logoUrl.isNullOrBlank()) {
                Box(modifier = Modifier.heightIn(max = 120.dp)) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .testTag(DetailTestTags.HeroLogo),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayMedium.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.72f),
                            offset = Offset(0f, 4f),
                            blurRadius = 22f,
                        ),
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.testTag(DetailTestTags.HeroTitle),
                )
            }

            Text(
                text = listOfNotNull(eyebrow.takeIf { it.isNotBlank() }, ratingText).joinToString("  •  "),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f),
            )

            if (badges.isNotEmpty()) {
                DetailChipRow(
                    labels = badges,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            tagline?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    fontStyle = FontStyle.Italic,
                    color = Color.White.copy(alpha = 0.98f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.96f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.95f),
                )
            }

            creditLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE3C08C),
                    fontWeight = FontWeight.Medium,
                )
            }

            HeroActionStrip(
                primaryLabel = primaryActionLabel,
                onPrimaryClick = onPrimaryAction,
                secondaryActions = secondaryIconActions,
                primaryFocusRequester = primaryActionFocusRequester,
                primaryDownFocusRequester = primaryActionDownFocusRequester,
                onDownNavigation = onDownNavigation,
            )
        }
    }
}

@Composable
private fun HeroActionStrip(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryActions: List<HeroIconAction>,
    primaryFocusRequester: FocusRequester,
    primaryDownFocusRequester: FocusRequester?,
    onDownNavigation: (() -> Unit)?,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val secondaryFocusRequesters = remember(secondaryActions.size) {
        List(secondaryActions.size) { FocusRequester() }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Button(
            onClick = onPrimaryClick,
            modifier = Modifier
                .testTag(DetailTestTags.PrimaryAction)
                .focusRequester(primaryFocusRequester)
                .blockBringIntoView()
                .focusProperties {
                    if (primaryDownFocusRequester != null && onDownNavigation == null) {
                        down = primaryDownFocusRequester
                    }
                    if (secondaryActions.isNotEmpty()) {
                        right = secondaryFocusRequesters.first()
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    val nativeEvent = keyEvent.nativeKeyEvent
                    if (
                        onDownNavigation != null &&
                        nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                        nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    ) {
                        onDownNavigation()
                        true
                    } else {
                        false
                    }
                }
                .defaultMinSize(minWidth = 220.dp, minHeight = 58.dp),
            scale = ButtonDefaults.scale(focusedScale = 1.03f),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            glow = ButtonDefaults.glow(
                focusedGlow = Glow(
                    elevationColor = expressiveColors.focusGlow.copy(alpha = 0.48f),
                    elevation = 12.dp,
                ),
            ),
            border = ButtonDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = expressiveColors.focusRing,
                    ),
                ),
            ),
        ) {
            Text(primaryLabel)
        }

        secondaryActions.forEachIndexed { index, action ->
            OutlinedButton(
                onClick = action.onClick,
                modifier = Modifier
                    .size(58.dp)
                    .focusRequester(secondaryFocusRequesters[index])
                    .blockBringIntoView()
                    .focusProperties {
                        left = when (index) {
                            0 -> primaryFocusRequester
                            else -> secondaryFocusRequesters[index - 1]
                        }
                        if (index < secondaryFocusRequesters.lastIndex) {
                            right = secondaryFocusRequesters[index + 1]
                        }
                        if (primaryDownFocusRequester != null && onDownNavigation == null) {
                            down = primaryDownFocusRequester
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        val nativeEvent = keyEvent.nativeKeyEvent
                        if (
                            onDownNavigation != null &&
                            nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                        ) {
                            onDownNavigation()
                            true
                        } else {
                            false
                        }
                    },
                scale = ButtonDefaults.scale(focusedScale = 1.08f),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = expressiveColors.detailPanelFocused,
                    focusedContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                glow = ButtonDefaults.glow(
                    focusedGlow = Glow(
                        elevationColor = expressiveColors.focusGlow.copy(alpha = 0.34f),
                        elevation = 10.dp,
                    ),
                ),
                border = ButtonDefaults.border(
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = expressiveColors.borderSubtle.copy(alpha = 0.58f),
                        ),
                    ),
                    focusedBorder = Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = expressiveColors.focusRing,
                        ),
                    ),
                ),
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.contentDescription,
                )
            }
        }
    }
}
