package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.CinefinChip
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailHeroSection(
    item: DetailHeroModel,
    focusedDescription: String?,
    modifier: Modifier = Modifier
) {
    val spacing = LocalCinefinSpacing.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(spacing.cornerCard),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        if (!item.subtitle.isNullOrBlank()) {
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val displayOverview = focusedDescription ?: item.overview
        if (!displayOverview.isNullOrBlank()) {
            Text(
                text = displayOverview,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .heightIn(min = 100.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(100.dp))
        }

        // Unified Metadata Row
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.chipGap),
            verticalArrangement = Arrangement.spacedBy(spacing.chipGap),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            if (item.watchStatus != WatchStatus.NONE) {
                WatchStatusBadge(
                    status = item.watchStatus,
                    progress = item.playbackProgress
                )
            }

            item.metaBadges.forEach { badge ->
                CinefinChip(label = badge)
            }

            item.technicalDetails?.let { details ->
                details.videoQuality?.let { CinefinChip(label = it, strong = true, icon = Icons.Default.HighQuality) }
                details.bitrate?.let { CinefinChip(label = it, icon = Icons.Default.Speed) }
                details.framerate?.let { CinefinChip(label = it, icon = Icons.Default.Timer) }
                details.audioCodec?.let { CinefinChip(label = it, icon = Icons.Default.GraphicEq) }
                details.audioType?.let { CinefinChip(label = it, icon = Icons.Default.Speaker) }
                details.language?.let { CinefinChip(label = it, icon = Icons.Default.Language) }
            }
        }
    }
}
