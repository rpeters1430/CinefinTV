@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.player.syncplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.rpeters.cinefintv.data.syncplay.SyncPlayGroup
import com.rpeters.cinefintv.data.syncplay.SyncPlaySessionState
import com.rpeters.cinefintv.ui.components.CinefinDialogSurface

@Composable
fun SyncPlayGroupDialog(
    sessionState: SyncPlaySessionState,
    availableGroups: List<SyncPlayGroup>,
    isLoading: Boolean,
    onCreateGroup: (name: String) -> Unit,
    onJoinGroup: (groupId: String) -> Unit,
    onLeaveGroup: () -> Unit,
    onDismiss: () -> Unit,
) {
    CinefinDialogSurface(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.55f),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Watch Together",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            when (sessionState) {
                is SyncPlaySessionState.Idle -> {
                    IdleContent(
                        availableGroups = availableGroups,
                        isLoading = isLoading,
                        onCreateGroup = onCreateGroup,
                        onJoinGroup = onJoinGroup,
                    )
                }
                is SyncPlaySessionState.InGroup -> {
                    ActiveGroupContent(
                        group = sessionState.group,
                        onLeaveGroup = onLeaveGroup,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    availableGroups: List<SyncPlayGroup>,
    isLoading: Boolean,
    onCreateGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Start a new session or join an existing one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = { onCreateGroup("Watch Together") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Default.GroupAdd, contentDescription = null)
                Text("Create New Session")
            }
        }

        if (!isLoading && availableGroups.isNotEmpty()) {
            Text(
                text = "Active Sessions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(availableGroups, key = { it.groupId }) { group ->
                    ListItem(
                        selected = false,
                        onClick = { onJoinGroup(group.groupId) },
                        headlineContent = {
                            Text(group.groupName, style = MaterialTheme.typography.titleMedium)
                        },
                        supportingContent = {
                            Text(
                                "${group.participants.size} watching",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveGroupContent(
    group: SyncPlayGroup,
    onLeaveGroup: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "You are watching with ${group.participants.size} ${if (group.participants.size == 1) "person" else "people"}.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Session: ${group.groupName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onLeaveGroup,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Leave Session")
        }
    }
}
