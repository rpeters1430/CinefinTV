package com.rpeters.cinefintv.ui.player.audio

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface AudioControllerConnector {
    suspend fun connect(context: Context): AudioPlaybackController
}

class Media3AudioControllerConnector @Inject constructor() : AudioControllerConnector {
    override suspend fun connect(context: Context): AudioPlaybackController {
        val token = SessionToken(context, ComponentName(context, AudioService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()

        return suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    runCatching { future.get() }
                        .map(::Media3AudioPlaybackController)
                        .onSuccess(continuation::resume)
                        .onFailure(continuation::resumeWithException)
                },
                ContextCompat.getMainExecutor(context),
            )

            continuation.invokeOnCancellation {
                future.cancel(true)
            }
        }
    }
}
