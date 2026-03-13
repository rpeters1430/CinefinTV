package com.rpeters.cinefintv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

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

    Column(
        modifier = modifier
            .width(120.dp)
            .onFocusChanged { if (it.isFocused) onFocus() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = CardDefaults.shape(CircleShape),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            border = CardDefaults.border(
                focusedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.border
                    )
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(imageUrl)
                            .crossfade(performanceProfile.tier != DevicePerformanceProfile.Tier.LOW)
                            // People thumbnails are small: 120dp @ ~2x density
                            .size(240, 240)
                            .build(),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (!role.isNullOrBlank()) {
                Text(
                    text = role,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
