package com.rpeters.cinefintv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.LocalPerformanceProfile

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPersonCard(
    name: String,
    role: String? = null,
    imageUrl: String? = null,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val performanceProfile = LocalPerformanceProfile.current
    val expressiveColors = LocalCinefinExpressiveColors.current
    val spacing = LocalCinefinSpacing.current
    var isFocused by remember { mutableStateOf(false) }

    val nameColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 180),
        label = "PersonCardNameColor",
    )
    val roleColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 180),
        label = "PersonCardRoleColor",
    )
    val metaContainerColor by animateColorAsState(
        targetValue = if (isFocused) {
            expressiveColors.elevatedSurface.copy(alpha = 0.98f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "PersonCardMetaColor",
    )

    Column(
        modifier = modifier.width(180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .size(172.dp)
                .onFocusChanged {
                    val focused = it.isFocused || it.hasFocus
                    isFocused = focused
                    if (focused) onFocus()
                },
            scale = CardDefaults.scale(focusedScale = 1.08f),
            glow = CardDefaults.glow(
                focusedGlow = Glow(
                    elevation = 16.dp,
                    elevationColor = expressiveColors.focusGlow,
                ),
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = expressiveColors.focusRing,
                    ),
                    shape = CircleShape,
                ),
            ),
            shape = CardDefaults.shape(CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(imageUrl)
                            .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
                            .size(344, 344)
                            .build(),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(spacing.cornerCard),
            colors = SurfaceDefaults.colors(containerColor = metaContainerColor),
            tonalElevation = if (isFocused) 8.dp else 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .defaultMinSize(minHeight = 68.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                    color = nameColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!role.isNullOrBlank()) {
                    Text(
                        text = role,
                        style = MaterialTheme.typography.labelSmall,
                        color = roleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
