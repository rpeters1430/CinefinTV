package com.rpeters.cinefintv

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide bus for voice/system search queries. MainActivity posts here;
 * SearchViewModel and CinefinTvNavGraph both collect to pre-fill search and navigate.
 */
@Singleton
class VoiceSearchCoordinator @Inject constructor() {
    private val _pendingQuery = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val pendingQuery: SharedFlow<String> = _pendingQuery.asSharedFlow()

    fun submit(query: String) {
        if (query.isNotBlank()) _pendingQuery.tryEmit(query.trim())
    }
}
