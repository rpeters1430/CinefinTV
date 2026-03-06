package com.rpeters.cinefintv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import com.rpeters.cinefintv.ui.CinefinTvApp
import com.rpeters.cinefintv.update.UpdateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var updateManager: UpdateManager
    
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CinefinTvApp(updateManager = updateManager)
        }
    }
}
