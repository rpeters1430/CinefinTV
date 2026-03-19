package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailHeroSection(
    item: DetailHeroModel,
    focusedDescription: String?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.ExtraBold,
        )

        if (!item.subtitle.isNullOrBlank()) {
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val displayOverview = focusedDescription ?: item.overview
        if (!displayOverview.isNullOrBlank()) {
            Text(
                text = displayOverview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
            )
        } else {
            Spacer(modifier = Modifier.height(88.dp))
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.chipGap),
            verticalArrangement = Arrangement.spacedBy(spacing.chipGap),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.watchStatus != WatchStatus.NONE) {
                WatchStatusBadge(
                    status = item.watchStatus,
                    progress = item.playbackProgress,
                )
            }

            item.metaBadges.forEach { badge ->
                CinefinChip(label = badge)
            }

            item.technicalDetails?.videoQuality?.let {
                CinefinChip(label = it, strong = true, icon = Icons.Default.HighQuality)
            }
        }

        item.technicalDetails?.let { details ->
            DetailTechnicalPanel(details = details)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailTechnicalPanel(
    details: DetailTechnicalDetails,
    modifier: Modifier = Modifier,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val technicalSpecs = buildList {
        details.videoQuality?.let { add(TechSpec("Video", it, Icons.Default.HighQuality)) }
        details.videoCodec?.let { add(TechSpec("Codec", it, Icons.Default.HighQuality)) }
        details.container?.let { add(TechSpec("Container", it, Icons.Default.CollectionsBookmark)) }
        details.bitrate?.let { add(TechSpec("Bitrate", it, Icons.Default.Speed)) }
        details.framerate?.let { add(TechSpec("Frame Rate", it, Icons.Default.Timer)) }
        details.audioCodec?.let { add(TechSpec("Audio Codec", it, Icons.Default.GraphicEq)) }
        details.audioType?.let { add(TechSpec("Audio Mix", it, Icons.Default.Speaker)) }
        details.language?.let { add(TechSpec("Language", it, Icons.Default.Language)) }
        details.subtitleSummary?.let { add(TechSpec("Subtitles", it, Icons.Default.Subtitles)) }
    }
    if (technicalSpecs.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(
            containerColor = expressiveColors.surfaceContainerHigh.copy(alpha = 0.88f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Technical Details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                technicalSpecs.forEach { spec ->
                    TechSpecChip(spec = spec)
                }
            }
        }
    }
}

@Composable
private fun TechSpecChip(
    spec: TechSpec,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = spec.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private data class TechSpec(
    val label: String,
    val value: String,
    val icon: ImageVector,
)
