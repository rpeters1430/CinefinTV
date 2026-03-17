package com.rpeters.cinefintv.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * ✅ Performance: Local provider for the device's performance profile
 */
val LocalPerformanceProfile = staticCompositionLocalOf {
    DevicePerformanceProfile(
        tier = DevicePerformanceProfile.Tier.LOW,
        memoryCachePercent = 0.12,
        diskCacheSizeMb = 80L,
    )
}

/**
 * ✅ Performance: State-based debouncing for UI inputs
 */
@Composable
fun <T> rememberDebouncedState(
    value: T,
    delayMs: Long = 300L,
): T {
    var debouncedValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        kotlinx.coroutines.delay(delayMs)
        debouncedValue = value
    }

    return debouncedValue
}

/**
 * ✅ Performance: Memory-efficient list item keys (moved to Extensions.kt)
 */

/**
 * ✅ Performance: Viewport-aware loading helper
 * Hook into LazyListState to check if an item is within the visible viewport plus a buffer.
 */
@Composable
fun rememberViewportAwareLoader(
    state: LazyListState,
    index: Int,
    buffer: Int = 3,
): Boolean {
    val isVisible by remember(state, index) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf false
            
            val firstVisible = visibleItems.first().index
            val lastVisible = visibleItems.last().index
            
            index >= (firstVisible - buffer) && index <= (lastVisible + buffer)
        }
    }
    return isVisible
}

/**
 * ✅ Performance: Flow extensions for efficient data handling
 */
fun <T> Flow<List<T>>.distinctByKey(keySelector: (T) -> Any): Flow<List<T>> {
    return this.map { list ->
        list.distinctBy { keySelector(it) }
    }.distinctUntilChanged()
}

fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> = flow {
    require(periodMs > 0) { "periodMs must be > 0" }

    var lastEmitTimeMs = 0L
    collect { value ->
        val now = System.currentTimeMillis()
        if (now - lastEmitTimeMs >= periodMs) {
            lastEmitTimeMs = now
            emit(value)
        }
    }
}
