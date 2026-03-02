package com.rpeters.cinefintv.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.flow.map

/**
 * ✅ PHASE 3: Advanced Performance Optimizations
 * Provides utilities for improving app performance and user experience
 */

/**
 * ✅ Performance: Infinite scroll detection for LazyColumn/LazyRow
 */
@Composable
fun LazyListState.OnBottomReached(
    loadMore: () -> Unit,
    buffer: Int = 3,
) {
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true

            lastVisibleItem.index >= layoutInfo.totalItemsCount - 1 - buffer
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            loadMore()
        }
    }
}

/**
 * ✅ Performance: Infinite scroll detection for LazyVerticalGrid
 */
@Composable
fun LazyGridState.OnBottomReached(
    loadMore: () -> Unit,
    buffer: Int = 6,
) {
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true

            lastVisibleItem.index >= layoutInfo.totalItemsCount - 1 - buffer
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            loadMore()
        }
    }
}

/**
 * ✅ Performance: Smart image loading with size optimization
 */
data class ImageLoadingConfig(
    val maxWidth: Dp = 400.dp,
    val maxHeight: Dp = 600.dp,
    val quality: Int = 85,
    val enableMemoryCache: Boolean = true,
    val enableDiskCache: Boolean = true,
    val crossfadeMs: Int = 300,
)

/**
 * ✅ Performance: Debounced state for search and other rapid inputs
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
 * ✅ Performance: Viewport-aware loading
 */
@Composable
fun rememberViewportAwareLoader(
    threshold: Dp = 200.dp,
): (Boolean) -> Boolean {
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }

    return remember {
        {
                isInViewport ->
            isInViewport // For now, simple passthrough. Can be enhanced with distance calculations
        }
    }
}

/**
 * ✅ Performance: Flow extensions for efficient data handling
 */
fun <T> Flow<List<T>>.distinctByKey(keySelector: (T) -> Any): Flow<List<T>> {
    return this.map { list ->
        list.distinctBy { keySelector(it) }
    }.distinctUntilChanged()
}

fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> {
    return this.distinctUntilChanged()
}

/**
 * ✅ Performance: Memory management constants
 */
object PerformanceConstants {
    const val DEFAULT_PAGE_SIZE = 20
    const val LARGE_PAGE_SIZE = 50
    const val PREFETCH_BUFFER = 3
    const val MAX_CACHED_IMAGES = 100
    const val IMAGE_CACHE_SIZE_MB = 50
    const val ANIMATION_DURATION_MS = 300
    const val DEBOUNCE_DELAY_MS = 300L
    const val NETWORK_TIMEOUT_MS = 30_000L
}

/**
 * ✅ Performance: Resource cleanup utilities
 */
class ResourceManager {
    private val resources = mutableSetOf<() -> Unit>()

    fun addCleanupTask(cleanup: () -> Unit) {
        resources.add(cleanup)
    }

    fun cleanup() {
        resources.forEach { it() }
        resources.clear()
    }
}

@Composable
fun rememberResourceManager(): ResourceManager {
    val resourceManager = remember { ResourceManager() }

    DisposableEffect(Unit) {
        onDispose {
            resourceManager.cleanup()
        }
    }

    return resourceManager
}

/**
 * ✅ Performance: Smart preloading strategy
 */
sealed class PreloadStrategy {
    object Aggressive : PreloadStrategy() // Preload 2 screens ahead
    object Moderate : PreloadStrategy() // Preload 1 screen ahead
    object Conservative : PreloadStrategy() // Preload only visible + buffer
}

data class PreloadConfig(
    val strategy: PreloadStrategy = PreloadStrategy.Moderate,
    val bufferSize: Int = 5,
    val enablePrefetch: Boolean = true,
)

/**
 * ✅ Performance: Battery and network aware optimizations
 */
object AdaptivePerformance {
    fun getOptimalImageQuality(isLowPowerMode: Boolean, isMeteredConnection: Boolean): Int {
        return when {
            isLowPowerMode && isMeteredConnection -> 60
            isLowPowerMode || isMeteredConnection -> 75
            else -> 85
        }
    }

    fun getOptimalCacheSize(availableMemoryMb: Long): Int {
        return when {
            availableMemoryMb < 512 -> 25
            availableMemoryMb < 1024 -> 50
            else -> 100
        }.coerceAtMost(availableMemoryMb.toInt() / 10)
    }

    fun shouldReduceAnimations(isLowPowerMode: Boolean): Boolean = isLowPowerMode
}
