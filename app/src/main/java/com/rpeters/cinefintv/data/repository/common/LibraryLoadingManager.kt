package com.rpeters.cinefintv.data.repository.common

import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.repository.JellyfinMediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized manager for coordinating library loading operations.
 * Fixes issues with duplicate requests, race conditions, and inefficient loading patterns.
 */
@Singleton
class LibraryLoadingManager @Inject constructor(
    private val mediaRepository: JellyfinMediaRepository,
) {
    companion object {
        private const val TAG = "LibraryLoadingManager"
        private const val MAX_CONCURRENT_LOADS = 3
        private const val LOAD_BATCH_SIZE = 50
        private const val LOAD_TIMEOUT_MS = 30_000L
    }

    // Coordinated loading scope with supervisor job for error isolation
    private val loadingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track ongoing operations to prevent duplicates
    private val ongoingOperations = mutableMapOf<String, kotlinx.coroutines.Deferred<ApiResult<List<BaseItemDto>>>>()
    private val operationsMutex = Mutex()

    // Library loading state
    private val _libraryLoadingState = MutableStateFlow<Map<String, LibraryLoadingState>>(emptyMap())
    val libraryLoadingState: StateFlow<Map<String, LibraryLoadingState>> = _libraryLoadingState.asStateFlow()

    /**
     * Loads libraries with intelligent deduplication and error handling.
     */
    suspend fun loadLibraries(forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        val operationKey = "load_libraries"

        return operationsMutex.withLock {
            // Check if operation is already in progress
            ongoingOperations[operationKey]?.let { ongoing ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Joining ongoing library loading operation")
                }
                return@withLock ongoing.await()
            }

            // Start new operation
            val operation = loadingScope.async {
                try {
                    updateLoadingState(operationKey, LibraryLoadingState.Loading)

                    val result = mediaRepository.getUserLibraries(forceRefresh)

                    when (result) {
                        is ApiResult.Success -> {
                            updateLoadingState(operationKey, LibraryLoadingState.Success(result.data))
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Successfully loaded ${result.data.size} libraries")
                            }
                        }
                        is ApiResult.Error -> {
                            updateLoadingState(operationKey, LibraryLoadingState.Error(result.message))
                            if (BuildConfig.DEBUG) {
                                Log.e(TAG, "Failed to load libraries: ${result.message}")
                            }
                        }
                        else -> { /* Loading state already set */ }
                    }

                    result
                } catch (e: CancellationException) {
                    throw e
                } finally {
                    operationsMutex.withLock {
                        ongoingOperations.remove(operationKey)
                    }
                }
            }

            ongoingOperations[operationKey] = operation
            operation.await()
        }
    }

    /**
     * Loads library items with intelligent parameter validation and fallback strategies.
     */
    suspend fun loadLibraryItems(
        libraryId: String?,
        collectionType: CollectionType?,
        itemTypes: List<BaseItemKind>? = null,
        startIndex: Int = 0,
        limit: Int = LOAD_BATCH_SIZE,
        forceRefresh: Boolean = false,
    ): ApiResult<List<BaseItemDto>> {
        val operationKey = "load_library_${libraryId}_${startIndex}_$limit"

        return operationsMutex.withLock {
            // Check for ongoing operation
            ongoingOperations[operationKey]?.let { ongoing ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Joining ongoing library items operation: $operationKey")
                }
                return@withLock ongoing.await()
            }

            // Validate and sanitize parameters
            val validatedParams = validateLibraryParams(libraryId, collectionType, itemTypes, startIndex, limit)
            if (validatedParams == null) {
                return@withLock ApiResult.Error(
                    "Invalid library parameters",
                    null,
                    ErrorType.VALIDATION,
                )
            }

            val operation = loadingScope.async {
                try {
                    updateLoadingState(operationKey, LibraryLoadingState.Loading)

                    val result = mediaRepository.getLibraryItems(
                        parentId = validatedParams.parentId,
                        itemTypes = validatedParams.itemTypes,
                        startIndex = validatedParams.startIndex,
                        limit = validatedParams.limit,
                        collectionType = validatedParams.collectionType,
                    )

                    when (result) {
                        is ApiResult.Success -> {
                            updateLoadingState(operationKey, LibraryLoadingState.Success(result.data))
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Successfully loaded ${result.data.size} library items for $operationKey")
                            }
                        }
                        is ApiResult.Error -> {
                            updateLoadingState(operationKey, LibraryLoadingState.Error(result.message))
                            if (BuildConfig.DEBUG) {
                                Log.e(TAG, "Failed to load library items: ${result.message}")
                            }
                        }
                        else -> { /* Loading state already set */ }
                    }

                    result
                } catch (e: CancellationException) {
                    throw e
                } finally {
                    operationsMutex.withLock {
                        ongoingOperations.remove(operationKey)
                    }
                }
            }

            ongoingOperations[operationKey] = operation
            operation.await()
        }
    }

    /**
     * Loads multiple library types in parallel with proper error isolation.
     */
    suspend fun loadLibraryTypesBatch(
        requests: List<LibraryTypeLoadRequest>,
    ): Map<String, ApiResult<List<BaseItemDto>>> {
        if (requests.isEmpty()) return emptyMap()

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Loading ${requests.size} library types in parallel")
        }

        // Limit concurrent operations
        val batches = requests.chunked(MAX_CONCURRENT_LOADS)
        val results = mutableMapOf<String, ApiResult<List<BaseItemDto>>>()

        for (batch in batches) {
            val batchResults = batch.map { request ->
                loadingScope.async {
                    request.key to loadLibraryItems(
                        libraryId = request.libraryId,
                        collectionType = request.collectionType,
                        itemTypes = request.itemTypes,
                        startIndex = request.startIndex,
                        limit = request.limit,
                        forceRefresh = request.forceRefresh,
                    )
                }
            }.awaitAll()

            results.putAll(batchResults)
        }

        if (BuildConfig.DEBUG) {
            val successCount = results.values.count { it is ApiResult.Success }
            Log.d(TAG, "Batch loading completed: $successCount/${results.size} successful")
        }

        return results
    }

    /**
     * Validates and sanitizes library loading parameters.
     */
    private fun validateLibraryParams(
        libraryId: String?,
        collectionType: CollectionType?,
        itemTypes: List<BaseItemKind>?,
        startIndex: Int,
        limit: Int,
    ): ValidatedLibraryParams? {
        try {
            // Validate basic parameters
            val validStartIndex = maxOf(0, startIndex)
            val validLimit = when {
                limit <= 0 -> LOAD_BATCH_SIZE
                limit > 200 -> 200 // Cap for performance
                else -> limit
            }

            // Validate and sanitize library ID
            val parentId = libraryId?.takeIf {
                it.isNotBlank() && it != "null" && it != "undefined"
            }

            // Convert collection type to string
            val collectionTypeString = collectionType?.let { type ->
                when (type) {
                    CollectionType.MOVIES -> "movies"
                    CollectionType.TVSHOWS -> "tvshows"
                    CollectionType.MUSIC -> "music"
                    CollectionType.HOMEVIDEOS -> "homevideos"
                    CollectionType.PHOTOS -> "photos"
                    CollectionType.BOOKS -> "books"
                    else -> type.name.lowercase()
                }
            }

            // Convert item types to API string format
            val itemTypesString = itemTypes?.let { types ->
                types.mapNotNull { kind ->
                    when (kind) {
                        BaseItemKind.MOVIE -> "Movie"
                        BaseItemKind.SERIES -> "Series"
                        BaseItemKind.EPISODE -> "Episode"
                        BaseItemKind.AUDIO -> "Audio"
                        BaseItemKind.MUSIC_ALBUM -> "MusicAlbum"
                        BaseItemKind.MUSIC_ARTIST -> "MusicArtist"
                        BaseItemKind.BOOK -> "Book"
                        BaseItemKind.AUDIO_BOOK -> "AudioBook"
                        BaseItemKind.VIDEO -> "Video"
                        BaseItemKind.PHOTO -> "Photo"
                        else -> {
                            Log.w(TAG, "Unknown item type: $kind")
                            null
                        }
                    }
                }.takeIf { it.isNotEmpty() }?.joinToString(",")
            }

            return ValidatedLibraryParams(
                parentId = parentId,
                collectionType = collectionTypeString,
                itemTypes = itemTypesString,
                startIndex = validStartIndex,
                limit = validLimit,
            )
        } catch (e: CancellationException) {
            throw e
        }
    }

    private fun updateLoadingState(operationKey: String, state: LibraryLoadingState) {
        val currentState = _libraryLoadingState.value.toMutableMap()
        currentState[operationKey] = state
        _libraryLoadingState.value = currentState
    }

    /**
     * Clears loading state for completed operations.
     */
    fun clearLoadingState(operationKey: String) {
        val currentState = _libraryLoadingState.value.toMutableMap()
        currentState.remove(operationKey)
        _libraryLoadingState.value = currentState
    }

    /**
     * Cancels all ongoing operations.
     */
    suspend fun cancelAllOperations() {
        operationsMutex.withLock {
            ongoingOperations.values.forEach { it.cancel() }
            ongoingOperations.clear()
        }
        _libraryLoadingState.value = emptyMap()
    }
}

/**
 * Data classes for library loading operations
 */
data class LibraryTypeLoadRequest(
    val key: String,
    val libraryId: String?,
    val collectionType: CollectionType?,
    val itemTypes: List<BaseItemKind>? = null,
    val startIndex: Int = 0,
    val limit: Int = 50,
    val forceRefresh: Boolean = false,
)

private data class ValidatedLibraryParams(
    val parentId: String?,
    val collectionType: String?,
    val itemTypes: String?,
    val startIndex: Int,
    val limit: Int,
)

sealed class LibraryLoadingState {
    object Loading : LibraryLoadingState()
    data class Success(val data: List<BaseItemDto>) : LibraryLoadingState()
    data class Error(val message: String) : LibraryLoadingState()
}
