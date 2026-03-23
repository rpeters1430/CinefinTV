@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.detail.cinematic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
import com.rpeters.cinefintv.ui.screens.detail.MetaFactItem
import com.rpeters.cinefintv.ui.screens.detail.MetaFactStyle
import com.rpeters.cinefintv.ui.theme.CinefinMotion
import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

/**
 * A row showing a one-line summary of key facts collapsed by default.
 * D-pad select or click expands to a full FlowRow of MetaFactItem cards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandableFactsSection(
    items: List<DetailLabeledMetaItem>,
    summaryText: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalCinefinSpacing.current
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusable()
                .onFocusChanged { isFocused = it.isFocused }
                .clickable { expanded = !expanded }
                .onKeyEvent { event ->
                    if (event.key == Key.DirectionCenter && event.type == KeyEventType.KeyUp) {
                        expanded = !expanded
                        true
                    } else false
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 250, easing = CinefinMotion.Emphasized)
            ) + fadeIn(tween(200)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 200, easing = CinefinMotion.Emphasized)
            ) + fadeOut(tween(150)),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.cardGap),
                horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
            ) {
                items.forEach { item ->
                    MetaFactItem(
                        icon = item.icon,
                        label = item.label,
                        value = item.value,
                        style = MetaFactStyle.Card,
                    )
                }
            }
        }
    }
}
