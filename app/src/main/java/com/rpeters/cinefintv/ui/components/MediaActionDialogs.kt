package com.rpeters.cinefintv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

data class MediaActionDialogItem(
    val label: String,
    val supportingText: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun MediaActionDialog(
    title: String,
    actions: List<MediaActionDialogItem>,
    onDismissRequest: () -> Unit,
) {
    CinefinDialogSurface(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            actions.forEach { action ->
                CinefinSettingListItem(
                    headline = action.label,
                    supporting = action.supportingText,
                    modifier = Modifier.fillMaxWidth(),
                    trailingText = if (action.isDestructive) "Delete" else null,
                    onClick = {
                        action.onClick()
                        onDismissRequest()
                    },
                )
            }
            CinefinDialogActions(
                dismissLabel = null,
                confirmLabel = "Close",
                onDismiss = null,
                onConfirm = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    CinefinDialogSurface(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CinefinDialogActions(
                dismissLabel = "Cancel",
                confirmLabel = "Delete",
                onDismiss = onDismissRequest,
                onConfirm = onConfirmDelete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
