package com.rpeters.cinefintv

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.ui.CinefinTvApp
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.update.UpdateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var updateManager: UpdateManager

    @Inject
    lateinit var authRepository: JellyfinAuthRepository
    
    @OptIn(ExperimentalTvMaterial3Api::class)
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isSessionRestored by authRepository.isSessionRestored.collectAsStateWithLifecycle()
            val isConnected by authRepository.isConnected.collectAsStateWithLifecycle()
            
            if (isSessionRestored == null) {
                // Show a simple restoration screen while the session is being checked
                // Wrap in CinefinTvTheme to ensure consistent look even during restoration
                CinefinTvTheme {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Restoring session...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                CinefinTvApp(
                    isAuthenticated = isConnected,
                    updateManager = updateManager
                )
            }
        }
    }
}
