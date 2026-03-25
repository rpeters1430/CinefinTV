package com.rpeters.cinefintv.data.repository

import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.repository.common.ApiParameterValidator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.repository.common.BaseJellyfinRepository
import com.rpeters.cinefintv.data.repository.common.ErrorType
import com.rpeters.cinefintv.data.repository.common.LibraryHealthChecker
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository containing media-related operations that were previously
 * part of the large [JellyfinRepository]. The implementations here are
 * intentionally lightweight and focused on simple request execution.
 */
@Singleton
class JellyfinMediaRepository @Inject constructor(
    authRepository: JellyfinAuthRepository,
    sessionManager: com.rpeters.cinefintv.data.session.JellyfinSessionManager,
    cache: JellyfinCache,
    private val healthChecker: LibraryHealthChecker,
) : BaseJellyfinRepository(authRepository, sessionManager, cache) {

    // Helper function to get default item types for a collection
    private fun getDefaultTypesForCollection(collectionType: String?): List<BaseItemKind>? = when (collectionType?.lowercase()) {
        "movies" -> listOf(BaseItemKind.MOVIE)
        "tvshows" -> listOf(BaseItemKind.SERIES)
        "music" -> listOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.AUDIO, BaseItemKind.MUSIC_ARTIST)
        "homevideos" -> null // Don't specify types for home videos - let server decide
        "photos" -> listOf(BaseItemKind.PHOTO)
        "books" -> listOf(BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK)
        else -> null
    }

    suspend fun getUserLibraries(forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        // ✅ FIX: Use withServerClient helper to ensure fresh server/client on token refresh
        return withServerClient("getUserLibraries") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                includeItemTypes = listOf(BaseItemKind.COLLECTION_FOLDER),
            )
            response.content.items
        }
    }

    suspend fun getLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
        collectionType: String? = null,
    ): ApiResult<List<BaseItemDto>> {
        // ✅ COMPREHENSIVE FIX: Use centralized parameter validation
        val validatedParams = ApiParameterValidator.validateLibraryParams(
            parentId = parentId,
            itemTypes = itemTypes,
            startIndex = startIndex,
            limit = limit,
            collectionType = collectionType,
        ) ?: throw IllegalArgumentException("Invalid API parameters provided")

        // Check if library is blocked due to repeated failures
        if (validatedParams.parentId != null && healthChecker.isLibraryBlocked(validatedParams.parentId)) {
            SecureLogger.w(
                "JellyfinMediaRepository",
                "Library ${validatedParams.parentId} is blocked due to repeated failures",
            )
            return ApiResult.Error(
                "This library is temporarily unavailable due to repeated errors. Please try again in a moment.",
                errorType = ErrorType.VALIDATION,
            )
        }

        return withServerClient("getLibraryItems") { server, client ->
            // ✅ CRITICAL FIX: Server is provided as parameter by withServerClient
            // This ensures fresh token access on retry after 401 re-authentication
            val userUuid = parseUuid(server.userId ?: "", "user")

            // Parse parentId if provided
            val parent = validatedParams.parentId?.let { parseUuid(it, "parent") }

            // Parse item types from validated string
            var itemKinds = validatedParams.itemTypes?.split(",")?.mapNotNull { type ->
                when (type.trim()) {
                    "Movie" -> BaseItemKind.MOVIE
                    "Series" -> BaseItemKind.SERIES
                    "Episode" -> BaseItemKind.EPISODE
                    "Audio" -> BaseItemKind.AUDIO
                    "MusicAlbum" -> BaseItemKind.MUSIC_ALBUM
                    "MusicArtist" -> BaseItemKind.MUSIC_ARTIST
                    "Book" -> BaseItemKind.BOOK
                    "AudioBook" -> BaseItemKind.AUDIO_BOOK
                    "Video" -> BaseItemKind.VIDEO
                    "Photo" -> BaseItemKind.PHOTO
                    else -> null
                }
            }

            // Home videos and photos libraries: include both video and photo to surface all content
            val isHomeVideos = collectionType?.equals("homevideos", ignoreCase = true) == true
            val isPhotos = collectionType?.equals("photos", ignoreCase = true) == true
            if (isHomeVideos && (itemKinds == null || itemKinds.isEmpty())) {
                itemKinds = listOf(BaseItemKind.VIDEO, BaseItemKind.PHOTO)
            }
            if (isPhotos && (itemKinds == null || itemKinds.isEmpty())) {
                itemKinds = listOf(BaseItemKind.PHOTO)
            }

            SecureLogger.v(
                "JellyfinMediaRepository",
                "Making validated API call with parentId=${parent?.toString()}, itemTypes=${itemKinds?.joinToString { it.name }}, startIndex=${validatedParams.startIndex}, limit=${validatedParams.limit}",
            )

            try {
                // Choose sensible defaults per collection for sorting and fields
                val coll = validatedParams.collectionType?.lowercase()
                val sortBy = when (coll) {
                    "movies" -> listOf(ItemSortBy.SORT_NAME)
                    "tvshows" -> listOf(ItemSortBy.SORT_NAME)
                    "music" -> listOf(ItemSortBy.SORT_NAME)
                    "homevideos" -> listOf(ItemSortBy.SORT_NAME)
                    else -> listOf(ItemSortBy.SORT_NAME)
                }
                val sortOrder = listOf(SortOrder.ASCENDING)
                val fields = listOf(
                    org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                    org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                    org.jellyfin.sdk.model.api.ItemFields.GENRES,
                    org.jellyfin.sdk.model.api.ItemFields.DATE_CREATED,
                    org.jellyfin.sdk.model.api.ItemFields.STUDIOS,
                    org.jellyfin.sdk.model.api.ItemFields.TAGS,
                )

                val response = client.itemsApi.getItems(
                    userId = userUuid,
                    parentId = parent,
                    recursive = true,
                    includeItemTypes = itemKinds,
                    sortBy = sortBy,
                    sortOrder = sortOrder,
                    fields = fields,
                    startIndex = validatedParams.startIndex,
                    limit = validatedParams.limit,
                )
                val items = response.content.items

                // Report success to health checker
                validatedParams.parentId?.let { libraryId ->
                    healthChecker.reportSuccess(libraryId)
                }

                items
            } catch (e: org.jellyfin.sdk.api.client.exception.InvalidStatusException) {
                val errorMsg = try { e.message } catch (_: Throwable) { "Bad Request" }
                SecureLogger.e(
                    "JellyfinMediaRepository",
                    "getLibraryItems ${e.status}: ${errorMsg ?: e.message}",
                )

                // ✅ FIX: All 401 errors are now handled automatically by executeWithTokenRefresh
                // No manual 401 handling needed - fresh server/client created on retry
                // Just let the error propagate up to be handled by the framework

                // If we get a 400, try multiple fallback strategies
                if (e.message?.contains("400") == true) {
                    SecureLogger.w(
                        "JellyfinMediaRepository",
                        "HTTP 400 error detected, attempting fallback strategies for parentId=$parentId, collectionType=$collectionType",
                    )
                    val allowBroadFallback = isHomeVideos || isPhotos || itemKinds.isNullOrEmpty()

                    // Strategy 1: Try collection-type defaults if we had explicit types
                    if (!collectionType.isNullOrBlank() && !itemTypes.isNullOrBlank()) {
                        try {
                            val fallbackTypes = getDefaultTypesForCollection(collectionType)
                            SecureLogger.v(
                                "JellyfinMediaRepository",
                                "Fallback strategy 1: Using collection type defaults: ${fallbackTypes?.joinToString()}",
                            )

                            val response = client.itemsApi.getItems(
                                userId = userUuid,
                                parentId = parent,
                                recursive = true,
                                includeItemTypes = fallbackTypes,
                                startIndex = validatedParams.startIndex,
                                limit = validatedParams.limit,
                            )
                            SecureLogger.v(
                                "JellyfinMediaRepository",
                                "Fallback strategy 1 succeeded: ${response.content.items.size} items",
                            )
                            return@withServerClient response.content.items
                        } catch (fallbackException: Exception) {
                            SecureLogger.w(
                                "JellyfinMediaRepository",
                                "Fallback strategy 1 failed: ${fallbackException.message}",
                            )
                        }
                    }

                    // Strategy 2: Try without any includeItemTypes (let server decide)
                    // Only use this broad fallback for libraries that intentionally support mixed content.
                    if (allowBroadFallback) {
                        try {
                            SecureLogger.v(
                                "JellyfinMediaRepository",
                                "Fallback strategy 2: Requesting without includeItemTypes filter",
                            )

                            val response = client.itemsApi.getItems(
                                userId = userUuid,
                                parentId = parent,
                                recursive = true,
                                includeItemTypes = null, // Let server return all types
                                startIndex = validatedParams.startIndex,
                                limit = validatedParams.limit,
                            )
                            SecureLogger.v(
                                "JellyfinMediaRepository",
                                "Fallback strategy 2 succeeded: ${response.content.items.size} items",
                            )
                            return@withServerClient response.content.items
                        } catch (fallbackException2: Exception) {
                            SecureLogger.w(
                                "JellyfinMediaRepository",
                                "Fallback strategy 2 also failed: ${fallbackException2.message}",
                            )
                        }
                    }

                    // Never remove the library constraint as a fallback. Returning cross-library
                    // content is worse than returning an empty state for the selected library.
                    SecureLogger.w(
                        "JellyfinMediaRepository",
                        "Library-scoped fallback strategies failed for library ${validatedParams.parentId}, returning empty list",
                    )

                    // Report failure to health checker unless it's a known fragile type
                    if (!(isHomeVideos || isPhotos)) {
                        validatedParams.parentId?.let { libraryId ->
                            healthChecker.reportFailure(libraryId, errorMsg ?: "HTTP 400 error")
                        }
                    }

                    return@withServerClient emptyList()
                }

                // Report failure to health checker for any unhandled exceptions
                validatedParams.parentId?.let { libraryId ->
                    healthChecker.reportFailure(libraryId, errorMsg ?: "HTTP error")
                }

                throw e
            }
        }
    }

    suspend fun getAllLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        collectionType: String? = null,
        pageSize: Int = 250,
        maxItems: Int = 10_000,
    ): ApiResult<List<BaseItemDto>> {
        val allItems = mutableListOf<BaseItemDto>()
        var startIndex = 0

        while (allItems.size < maxItems) {
            val remaining = maxItems - allItems.size
            val requestLimit = minOf(pageSize, remaining)
            when (
                val result = getLibraryItems(
                    parentId = parentId,
                    itemTypes = itemTypes,
                    startIndex = startIndex,
                    limit = requestLimit,
                    collectionType = collectionType,
                )
            ) {
                is ApiResult.Success -> {
                    val pageItems = result.data
                    if (pageItems.isEmpty()) {
                        break
                    }

                    allItems += pageItems

                    if (pageItems.size < requestLimit) {
                        break
                    }

                    startIndex += pageItems.size
                }
                is ApiResult.Error -> return result
                is ApiResult.Loading -> Unit
            }
        }

        return ApiResult.Success(allItems)
    }

    suspend fun getRecentlyAdded(limit: Int = 50, forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        // ✅ FIX: Use withServerClient helper to ensure fresh server/client on token refresh
        return withServerClient("getRecentlyAdded") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                recursive = true,
                includeItemTypes = listOf(
                    BaseItemKind.MOVIE,
                    BaseItemKind.SERIES,
                    BaseItemKind.EPISODE,
                    BaseItemKind.AUDIO,
                    BaseItemKind.MUSIC_ALBUM,
                    BaseItemKind.MUSIC_ARTIST,
                    BaseItemKind.BOOK,
                    BaseItemKind.AUDIO_BOOK,
                    BaseItemKind.VIDEO,
                ),
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.DESCENDING),
                limit = limit,
            )
            response.content.items
        }
    }

    suspend fun getRecentlyAddedByType(itemType: BaseItemKind, limit: Int = 20, forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        // ✅ FIX: Use withServerClient helper to ensure fresh server/client on token refresh
        return withServerClient("getRecentlyAddedByType") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                recursive = true,
                includeItemTypes = listOf(itemType),
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.DESCENDING),
                limit = limit,
            )
            response.content.items
        }
    }

    suspend fun getContinueWatching(limit: Int = 20, forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        return withServerClient("getContinueWatching") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                recursive = true,
                includeItemTypes = listOf(
                    BaseItemKind.MOVIE,
                    BaseItemKind.EPISODE,
                    BaseItemKind.VIDEO,
                ),
                sortBy = listOf(ItemSortBy.DATE_PLAYED),
                sortOrder = listOf(SortOrder.DESCENDING),
                filters = listOf(ItemFilter.IS_RESUMABLE),
                limit = limit,
            )
            response.content.items
        }
    }

    suspend fun getMovieDetails(movieId: String): ApiResult<BaseItemDto> =
        withServerClient("getMovieDetails") { server, client ->
            getItemDetailsById(movieId, "movie", server, client)
        }

    suspend fun getSeriesDetails(seriesId: String): ApiResult<BaseItemDto> =
        withServerClient("getSeriesDetails") { server, client ->
            getItemDetailsById(seriesId, "series", server, client)
        }

    suspend fun getEpisodeDetails(episodeId: String): ApiResult<BaseItemDto> =
        withServerClient("getEpisodeDetails") { server, client ->
            getItemDetailsById(episodeId, "episode", server, client)
        }

    suspend fun getAlbumDetails(albumId: String): ApiResult<BaseItemDto> =
        withServerClient("getAlbumDetails") { server, client ->
            getItemDetailsById(albumId, "album", server, client)
        }

    suspend fun getAlbumTracks(albumId: String): ApiResult<List<BaseItemDto>> =
        withServerClient("getAlbumTracks") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val albumUuid = parseUuid(albumId, "album")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = albumUuid,
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = listOf(
                    org.jellyfin.sdk.model.api.ItemFields.MEDIA_SOURCES,
                    org.jellyfin.sdk.model.api.ItemFields.MEDIA_STREAMS,
                    org.jellyfin.sdk.model.api.ItemFields.DATE_CREATED,
                ),
            )
            response.content.items
        }

    suspend fun getItemDetails(itemId: String): ApiResult<BaseItemDto> =
        withServerClient("getItemDetails") { server, client ->
            getItemDetailsById(itemId, "item", server, client)
        }

    suspend fun getAlbumsForArtist(artistId: String): ApiResult<List<BaseItemDto>> =
        withServerClient("getAlbumsForArtist") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val artistUuid = parseUuid(artistId, "artist")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = artistUuid,
                includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = listOf(
                    org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                    org.jellyfin.sdk.model.api.ItemFields.DATE_CREATED,
                    org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                ),
            )
            response.content.items
        }

    suspend fun getSeasonsForSeries(seriesId: String): ApiResult<List<BaseItemDto>> =
        // ✅ FIX: Use withServerClient helper to ensure fresh server/client on token refresh
        withServerClient("getSeasonsForSeries") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val seriesUuid = parseUuid(seriesId, "series")

            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = seriesUuid,
                includeItemTypes = listOf(BaseItemKind.SEASON),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = listOf(
                    org.jellyfin.sdk.model.api.ItemFields.DATE_CREATED,
                    org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                ),
            )
            response.content.items
        }

    suspend fun getSimilarSeries(seriesId: String, limit: Int = 20): ApiResult<List<BaseItemDto>> =
        withServerClient("getSimilarSeries") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val seriesUuid = parseUuid(seriesId, "series")

            try {
                val response = client.libraryApi.getSimilarItems(
                    itemId = seriesUuid,
                    userId = userUuid,
                    limit = limit,
                    fields = listOf(
                        org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                        org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                        org.jellyfin.sdk.model.api.ItemFields.GENRES,
                    ),
                )
                response.content.items
            } catch (e: CancellationException) {
                throw e
            }
        }

    suspend fun getSimilarMovies(movieId: String, limit: Int = 20): ApiResult<List<BaseItemDto>> =
        withServerClient("getSimilarMovies") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val movieUuid = parseUuid(movieId, "movie")

            try {
                val response = client.libraryApi.getSimilarItems(
                    itemId = movieUuid,
                    userId = userUuid,
                    limit = limit,
                    fields = listOf(
                        org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                        org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                        org.jellyfin.sdk.model.api.ItemFields.GENRES,
                    ),
                )
                response.content.items
            } catch (e: CancellationException) {
                throw e
            }
        }

    suspend fun getEpisodesForSeason(seasonId: String): ApiResult<List<BaseItemDto>> =
        // ✅ FIX: Use withServerClient helper to ensure fresh server/client on token refresh
        withServerClient("getEpisodesForSeason") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val seasonUuid = parseUuid(seasonId, "season")
            val fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.MEDIA_SOURCES,
                org.jellyfin.sdk.model.api.ItemFields.DATE_CREATED,
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
            )
            val seasonItem = getItemDetailsById(seasonId, "season", server, client)
            val seriesUuid = seasonItem.seriesId

            if (seriesUuid != null) {
                val tvEpisodes = client.tvShowsApi.getEpisodes(
                    seriesId = seriesUuid,
                    userId = userUuid,
                    seasonId = seasonUuid,
                    fields = fields,
                    enableUserData = true,
                ).content.items
                if (tvEpisodes.isNotEmpty()) {
                    return@withServerClient tvEpisodes
                }
            }

            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = seasonUuid,
                recursive = true,
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = fields,
            )
            response.content.items
        }

    suspend fun getNextUpForSeries(seriesId: String): ApiResult<BaseItemDto?> =
        withServerClient("getNextUpForSeries") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val seriesUuid = parseUuid(seriesId, "series")

            val response = client.tvShowsApi.getNextUp(
                userId = userUuid,
                seriesId = seriesUuid,
                limit = 1,
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.OVERVIEW),
                enableUserData = true,
            )

            response.content.items.firstOrNull()
        }

    suspend fun getNextEpisode(episodeId: String): ApiResult<BaseItemDto?> =
        withServerClient("getNextEpisode") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val currentEpisode = getItemDetailsById(episodeId, "episode", server, client)
            val seriesUuid = currentEpisode.seriesId ?: return@withServerClient null

            val response = client.tvShowsApi.getNextUp(
                userId = userUuid,
                seriesId = seriesUuid,
                limit = 2,
                fields = listOf(ItemFields.MEDIA_SOURCES),
                enableUserData = true,
            )

            response.content.items.firstOrNull { it.id != currentEpisode.id }
        }

    /**
     * Get details for a specific person.
     */
    suspend fun getPersonDetails(personId: String): ApiResult<BaseItemDto> =
        withServerClient("getPersonDetails") { server, client ->
            getItemDetailsById(personId, "person", server, client)
        }

    /**
     * Get all movies and TV shows for a specific person (actor/director/etc)
     */
    suspend fun getItemsByPerson(
        personId: String,
        limit: Int = 50,
    ): ApiResult<List<BaseItemDto>> =
        withServerClient("getItemsByPerson") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val personUuid = parseUuid(personId, "person")

            val response = client.itemsApi.getItems(
                userId = userUuid,
                personIds = listOf(personUuid),
                recursive = true,
                includeItemTypes = listOf(
                    BaseItemKind.MOVIE,
                    BaseItemKind.SERIES,
                    BaseItemKind.EPISODE,
                ),
                sortBy = listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.DESCENDING),
                fields = listOf(
                    org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                    org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                    org.jellyfin.sdk.model.api.ItemFields.GENRES,
                ),
                limit = limit,
            )
            response.content.items
        }

    private suspend fun getItemDetailsById(
        itemId: String,
        itemTypeName: String,
        server: com.rpeters.cinefintv.data.JellyfinServer,
        client: org.jellyfin.sdk.api.client.ApiClient,
    ): BaseItemDto {
        val userUuid = parseUuid(server.userId ?: "", "user")
        val itemUuid = parseUuid(itemId, itemTypeName)

        val response = client.itemsApi.getItems(
            userId = userUuid,
            ids = listOf(itemUuid),
            limit = 1,
            fields = listOf(
                org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                org.jellyfin.sdk.model.api.ItemFields.GENRES,
                org.jellyfin.sdk.model.api.ItemFields.PEOPLE,
                org.jellyfin.sdk.model.api.ItemFields.MEDIA_SOURCES,
                org.jellyfin.sdk.model.api.ItemFields.MEDIA_STREAMS,
                org.jellyfin.sdk.model.api.ItemFields.DATE_CREATED,
                org.jellyfin.sdk.model.api.ItemFields.STUDIOS,
                org.jellyfin.sdk.model.api.ItemFields.TAGS,
                org.jellyfin.sdk.model.api.ItemFields.CHAPTERS,
            ),
        )

        return response.content.items.firstOrNull()
            ?: throw IllegalStateException("$itemTypeName not found")
    }

    suspend fun logHttpError(requestUrl: String, status: Int, body: String?) {
        Log.e("JellyfinMediaRepository", "HTTP error for $requestUrl\n$status ${body.orEmpty()}")
    }

    /**
     * Clear library health issues for known problematic collection types on app initialization.
     * This helps with libraries like 'homevideos' that may have compatibility issues.
     */
    fun clearKnownLibraryHealthIssues() {
        // Clean up any previous health issues for homevideos libraries
        // since we now handle them gracefully
        healthChecker.cleanup()

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinMediaRepository", "Cleared known library health issues on initialization")
        }
    }
}
