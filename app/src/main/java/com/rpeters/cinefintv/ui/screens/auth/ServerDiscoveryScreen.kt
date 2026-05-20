@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.rpeters.cinefintv.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.WideButton
import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors

@Composable
fun ServerDiscoveryScreen(
    uiState: DiscoveryUiState,
    onServerSelected: (url: String) -> Unit,
    onEnterManually: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    val hasServers = uiState.servers.isNotEmpty()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        com.rpeters.cinefintv.ui.components.ImmersiveBackground(
            backdropUrl = null,
            baseColor = expressiveColors.backgroundTop,
            scrimColor = expressiveColors.backgroundBottom,
        )
        
        Box(modifier = Modifier.padding(horizontal = 64.dp, vertical = 48.dp)) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = expressiveColors.chromeSurface.copy(alpha = 0.55f),
                ),
                border = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(1.dp, expressiveColors.borderSubtle.copy(alpha = 0.3f))
                ),
                tonalElevation = 8.dp,
            ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                AuthHero(
                    title = "Servers on this network",
                    description = when {
                        !hasServers -> "Scanning your local network for Jellyfin servers..."
                        else -> "Select a server below, or enter the address manually."
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = expressiveColors.titleAccent,
                        )
                    },
                )

                if (!hasServers) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                uiState.servers.forEach { server ->
                    ServerCard(
                        server = server,
                        onSelect = { onServerSelected(server.url) },
                    )
                }

                WideButton(
                    onClick = onEnterManually,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Router,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("Enter server address manually")
                }
            }
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: DiscoveredServerUi,
    onSelect: () -> Unit,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current

    WideButton(
        onClick = onSelect,
        enabled = server.isVerified,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = server.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${server.host}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                server.isVerifying -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
                server.isVerified -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Verified",
                    tint = expressiveColors.watchedGreen,
                    modifier = Modifier.size(24.dp),
                )
                else -> Text(
                    text = "Unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
