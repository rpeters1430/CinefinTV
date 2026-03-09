package com.rpeters.cinefintv.data.repository.common

import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import java.util.UUID

/**
 * Comprehensive API parameter validation to prevent HTTP 400 errors.
 * Addresses the core issue of malformed or incompatible API parameters
 * that cause library loading failures.
 */
object ApiParameterValidator {
    private const val TAG = "ApiParameterValidator"

    // Valid parameter ranges
    private const val MIN_START_INDEX = 0
    private const val MAX_START_INDEX = 100_000
    private const val MIN_LIMIT = 1
    private const val MAX_LIMIT = 10_000
    private const val DEFAULT_LIMIT = 50

    /**
     * Validates and sanitizes library loading parameters.
     * Returns null if parameters are invalid and cannot be fixed.
     */
    fun validateLibraryParams(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = 0,
        limit: Int = DEFAULT_LIMIT,
        collectionType: String? = null,
    ): ValidatedApiLibraryParams? {
        try {
            // Validate and sanitize parentId
            val validParentId = validateParentId(parentId)

            // Validate pagination parameters
            val validStartIndex = validateStartIndex(startIndex)
            val validLimit = validateLimit(limit)

            // Validate item types
            val validItemTypes = validateItemTypes(itemTypes)

            // Validate collection type
            val validCollectionType = validateCollectionType(collectionType)

            // Smart parameter compatibility checking
            val finalParams = ensureParameterCompatibility(
                parentId = validParentId,
                itemTypes = validItemTypes,
                collectionType = validCollectionType,
                startIndex = validStartIndex,
                limit = validLimit,
            )

            if (BuildConfig.DEBUG && finalParams != null) {
                Log.d(
                    TAG,
                    "Validated params: parentId=${finalParams.parentId}, " +
                        "itemTypes=${finalParams.itemTypes}, collectionType=${finalParams.collectionType}, " +
                        "startIndex=${finalParams.startIndex}, limit=${finalParams.limit}",
                )
            }

            return finalParams
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Validates parentId parameter.
     */
    private fun validateParentId(parentId: String?): String? {
        if (parentId.isNullOrBlank()) return null

        // Filter out invalid values
        val invalidValues = setOf("null", "undefined", "", "0", "-1")
        if (parentId.lowercase() in invalidValues) return null

        // Try to parse as UUID to ensure it's valid
        return try {
            UUID.fromString(parentId)
            parentId
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Validates startIndex parameter.
     */
    private fun validateStartIndex(startIndex: Int): Int {
        return when {
            startIndex < MIN_START_INDEX -> {
                Log.w(TAG, "startIndex $startIndex too small, using $MIN_START_INDEX")
                MIN_START_INDEX
            }
            startIndex > MAX_START_INDEX -> {
                Log.w(TAG, "startIndex $startIndex too large, using $MAX_START_INDEX")
                MAX_START_INDEX
            }
            else -> startIndex
        }
    }

    /**
     * Validates limit parameter.
     */
    private fun validateLimit(limit: Int): Int {
        return when {
            limit < MIN_LIMIT -> {
                Log.w(TAG, "limit $limit too small, using $MIN_LIMIT")
                MIN_LIMIT
            }
            limit > MAX_LIMIT -> {
                Log.w(TAG, "limit $limit too large, using $MAX_LIMIT")
                MAX_LIMIT
            }
            else -> limit
        }
    }

    /**
     * Validates and sanitizes itemTypes parameter.
     */
    private fun validateItemTypes(itemTypes: String?): String? {
        if (itemTypes.isNullOrBlank()) return null

        val validItemTypes = setOf(
            "Movie", "Series", "Episode", "Audio", "MusicAlbum", "MusicArtist",
            "Book", "AudioBook", "Video", "Photo", "BoxSet", "CollectionFolder",
            "Playlist", "Person", "Genre", "MusicGenre", "Studio", "Year",
        )

        val requestedTypes = itemTypes.split(",").map { it.trim() }
        val filteredTypes = requestedTypes.filter { type ->
            val isValid = type in validItemTypes
            if (!isValid && BuildConfig.DEBUG) {
                Log.w(TAG, "Unknown item type: $type")
            }
            isValid
        }

        return if (filteredTypes.isNotEmpty()) {
            filteredTypes.joinToString(",")
        } else {
            Log.w(TAG, "No valid item types found in: $itemTypes")
            null
        }
    }

    /**
     * Validates collectionType parameter.
     */
    private fun validateCollectionType(collectionType: String?): String? {
        if (collectionType.isNullOrBlank()) return null

        val validCollectionTypes = setOf(
            "movies", "tvshows", "music", "homevideos", "photos", "books",
            "playlists", "livetv", "folders",
        )

        val normalizedType = collectionType.lowercase().trim()
        return if (normalizedType in validCollectionTypes) {
            normalizedType
        } else {
            Log.w(TAG, "Unknown collection type: $collectionType")
            null
        }
    }

    /**
     * Ensures parameter compatibility to prevent HTTP 400 errors.
     * This is where we apply smart logic to fix common parameter conflicts.
     */
    private fun ensureParameterCompatibility(
        parentId: String?,
        itemTypes: String?,
        collectionType: String?,
        startIndex: Int,
        limit: Int,
    ): ValidatedApiLibraryParams? {
        // Rule 1: If no parentId and no itemTypes, we need at least collection type or default types
        if (parentId == null && itemTypes == null && collectionType == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "No specific parameters provided, using default mixed content strategy")
            }
            return ValidatedApiLibraryParams(
                parentId = null,
                itemTypes = null, // Let server decide
                collectionType = null,
                startIndex = startIndex,
                limit = limit,
            )
        }

        // Rule 2: Collection type compatibility with item types
        val compatibleItemTypes = when (collectionType) {
            "movies" -> {
                // For movies, only allow Movie type or let server decide
                when {
                    itemTypes == null -> "Movie"
                    itemTypes.contains("Movie") -> "Movie"
                    else -> {
                        Log.w(TAG, "Incompatible item types '$itemTypes' for movies collection, using Movie")
                        "Movie"
                    }
                }
            }
            "tvshows" -> {
                // For TV shows, allow Series/Episode or let server decide
                when {
                    itemTypes == null -> "Series"
                    itemTypes.contains("Series") || itemTypes.contains("Episode") -> itemTypes
                    else -> {
                        Log.w(TAG, "Incompatible item types '$itemTypes' for tvshows collection, using Series")
                        "Series"
                    }
                }
            }
            "music" -> {
                // For music, allow music-related types
                when {
                    itemTypes == null -> "MusicAlbum,MusicArtist,Audio"
                    itemTypes.contains("Audio") || itemTypes.contains("MusicAlbum") || itemTypes.contains("MusicArtist") -> itemTypes
                    else -> {
                        Log.w(TAG, "Incompatible item types '$itemTypes' for music collection, using default music types")
                        "MusicAlbum,MusicArtist,Audio"
                    }
                }
            }
            "homevideos" -> {
                // ✅ FIX: For home videos, respect null itemTypes to let server decide and prevent HTTP 400 errors
                when {
                    itemTypes == null -> null // Let server decide item types to prevent HTTP 400 errors
                    itemTypes.contains("Video") -> itemTypes
                    else -> {
                        Log.w(TAG, "Incompatible item types '$itemTypes' for homevideos collection, letting server decide")
                        null // Let server decide instead of forcing Video type
                    }
                }
            }
            "photos" -> {
                when {
                    itemTypes == null -> "Photo"
                    itemTypes.contains("Photo") -> itemTypes
                    else -> {
                        Log.w(TAG, "Incompatible item types '$itemTypes' for photos collection, using Photo")
                        "Photo"
                    }
                }
            }
            "books" -> {
                when {
                    itemTypes == null -> "Book,AudioBook"
                    itemTypes.contains("Book") || itemTypes.contains("AudioBook") -> itemTypes
                    else -> {
                        Log.w(TAG, "Incompatible item types '$itemTypes' for books collection, using Book,AudioBook")
                        "Book,AudioBook"
                    }
                }
            }
            else -> itemTypes // Keep original for unknown/mixed collections
        }

        // Rule 3: ParentId validation - if provided, it should be valid
        val finalParentId = parentId?.let { id ->
            try {
                UUID.fromString(id)
                id
            } catch (e: CancellationException) {
                throw e
            }
        }

        // Rule 4: Ensure we don't have empty itemTypes string
        val finalItemTypes = compatibleItemTypes?.takeIf { it.isNotBlank() }

        return ValidatedApiLibraryParams(
            parentId = finalParentId,
            itemTypes = finalItemTypes,
            collectionType = collectionType,
            startIndex = startIndex,
            limit = limit,
        )
    }

    /**
     * Creates safe default parameters for a given collection type.
     */
    fun createSafeDefaults(collectionType: CollectionType?): ValidatedApiLibraryParams {
        return when (collectionType) {
            CollectionType.MOVIES -> ValidatedApiLibraryParams(
                parentId = null,
                itemTypes = "Movie",
                collectionType = "movies",
                startIndex = 0,
                limit = DEFAULT_LIMIT,
            )
            CollectionType.TVSHOWS -> ValidatedApiLibraryParams(
                parentId = null,
                itemTypes = "Series",
                collectionType = "tvshows",
                startIndex = 0,
                limit = DEFAULT_LIMIT,
            )
            CollectionType.MUSIC -> ValidatedApiLibraryParams(
                parentId = null,
                itemTypes = "MusicAlbum,MusicArtist,Audio",
                collectionType = "music",
                startIndex = 0,
                limit = DEFAULT_LIMIT,
            )
            CollectionType.HOMEVIDEOS -> ValidatedApiLibraryParams(
                parentId = null,
                itemTypes = null, // Let server decide item types to prevent HTTP 400 errors
                collectionType = "homevideos",
                startIndex = 0,
                limit = DEFAULT_LIMIT,
            )
            CollectionType.PHOTOS -> ValidatedApiLibraryParams(
                parentId = null,
                itemTypes = "Photo",
                collectionType = "photos",
                startIndex = 0,
                limit = DEFAULT_LIMIT,
            )
            CollectionType.BOOKS -> ValidatedApiLibraryParams(
                parentId = null,
                itemTypes = "Book,AudioBook",
                collectionType = "books",
                startIndex = 0,
                limit = DEFAULT_LIMIT,
            )
            else -> ValidatedApiLibraryParams(
                parentId = null,
                itemTypes = null, // Let server decide
                collectionType = null,
                startIndex = 0,
                limit = DEFAULT_LIMIT,
            )
        }
    }

    /**
     * Validates search parameters.
     */
    fun validateSearchParams(
        query: String?,
        itemTypes: List<BaseItemKind>? = null,
        limit: Int = 50,
    ): ValidatedSearchParams? {
        // Validate query
        val validQuery = query?.trim()?.takeIf { it.isNotBlank() && it.length >= 2 }
        if (validQuery == null) {
            Log.w(TAG, "Search query is too short or empty")
            return null
        }

        // Validate item types
        val validItemTypes = itemTypes?.mapNotNull { kind ->
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
                else -> null
            }
        }

        val validLimit = validateLimit(limit)

        return ValidatedSearchParams(
            query = validQuery,
            itemTypes = validItemTypes?.joinToString(","),
            limit = validLimit,
        )
    }
}

/**
 * Validated parameter containers
 */
data class ValidatedApiLibraryParams(
    val parentId: String?,
    val itemTypes: String?,
    val collectionType: String?,
    val startIndex: Int,
    val limit: Int,
)

data class ValidatedSearchParams(
    val query: String,
    val itemTypes: String?,
    val limit: Int,
)
