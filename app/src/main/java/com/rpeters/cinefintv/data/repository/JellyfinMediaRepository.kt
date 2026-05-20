package com.rpeters.cinefintv.data.repository

import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import android.os.SystemClock
import com.rpeters.cinefintv.core.constants.Constants
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
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

import com.rpeters.cinefintv.data.common.DispatcherProvider

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
    dispatchers: DispatcherProvider,
    healthChecker: LibraryHealthChecker,
    private val updateBus: com.rpeters.cinefintv.data.common.MediaUpdateBus,
) : BaseJellyfinRepository(authRepository, sessionManager, cache, dispatchers, healthChecker) {
    private companion object {
        const val HOME_TAG = "HomeRepository"
    }

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
        val startedAt = SystemClock.elapsedRealtime()
        Log.d(HOME_TAG, "getUserLibraries start: forceRefresh=$forceRefresh")
        if (!forceRefresh) {
            val cached = cache.getCachedLibraries()
            if (cached != null) {
                Log.d(HOME_TAG, "getUserLibraries cache hit: count=${cached.size} in ${SystemClock.elapsedRealtime() - startedAt}ms")
                return ApiResult.Success(cached)
            }
        }

        // ✅ FIX: include both COLLECTION_FOLDER and USER_VIEW for complete library detection
        return withServerClient("getUserLibraries") { server, client ->
            val requestStartedAt = SystemClock.elapsedRealtime()
            Log.d(HOME_TAG, "getUserLibraries sdk request start")
            val userUuid = parseUuid(server.userId ?: "", "user")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                includeItemTypes = listOf(BaseItemKind.COLLECTION_FOLDER, BaseItemKind.USER_VIEW),
            )
            val items = response.content.items
            Log.d(HOME_TAG, "getUserLibraries sdk response: count=${items.size} in ${SystemClock.elapsedRealtime() - requestStartedAt}ms")
            val cacheStartedAt = SystemClock.elapsedRealtime()
            Log.d(HOME_TAG, "getUserLibraries cache write start: count=${items.size}")
            cache.cacheLibraries(items)
            Log.d(HOME_TAG, "getUserLibraries cache write complete in ${SystemClock.elapsedRealtime() - cacheStartedAt}ms")
            Log.d(HOME_TAG, "getUserLibraries complete in ${SystemClock.elapsedRealtime() - startedAt}ms")
            items
        }
    }

    suspend fun getCachedLibraries(): List<BaseItemDto>? = cache.getCachedLibraries()

    suspend fun getLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
        collectionType: String? = null,
        fields: List<ItemFields>? = null,
    ): ApiResult<List<BaseItemDto>> {
        // ✅ COMPREHENSIVE FIX: Use centralized parameter validation
        val validatedParams = ApiParameterValidator.validateLibraryParams(
            parentId = parentId,
            itemTypes = itemTypes,
            startIndex = startIndex,
            limit = limit,
            collectionType = collectionType,
        ) ?: return ApiResult.Error(
            message = "Invalid API parameters provided",
            errorType = ErrorType.VALIDATION,
        )

        // Check if library is blocked due to repeated failures
        if (validatedParams.parentId != null && healthChecker?.isLibraryBlocked(validatedParams.parentId) == true) {
            SecureLogger.w(
                "JellyfinMediaRepository",
                "Library ${validatedParams.parentId} is blocked due to repeated failures",
            )
            return ApiResult.Error(
                message = "Library temporarily unavailable",
                errorType = ErrorType.UNKNOWN, // HEALTH_CHECK type doesn't exist yet, using UNKNOWN as placeholder
            )
        }

        return withServerClient("getLibraryItems", validatedParams.parentId) { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val parentUuid = validatedParams.parentId?.let { parseUuid(it, "parent") }

            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = parentUuid,
                startIndex = validatedParams.startIndex,
                limit = validatedParams.limit,
                includeItemTypes = validatedParams.itemTypes?.split(",")?.mapNotNull {
                    runCatching { BaseItemKind.valueOf(it.trim().uppercase()) }.getOrNull()
                } ?: getDefaultTypesForCollection(validatedParams.collectionType),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = fields ?: listOf(
                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                    ItemFields.CAN_DELETE,
                    ItemFields.CAN_DOWNLOAD,
                    ItemFields.ITEM_COUNTS,
                    ItemFields.OVERVIEW,
                ),
                recursive = true,
            )
            response.content.items
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
        val startedAt = SystemClock.elapsedRealtime()
        Log.d(HOME_TAG, "getRecentlyAddedByType start: type=$itemType limit=$limit forceRefresh=$forceRefresh")
        if (!forceRefresh) {
            val cached = when (itemType) {
                BaseItemKind.MOVIE -> cache.getCachedRecentlyAddedMovies()
                BaseItemKind.EPISODE -> cache.getCachedRecentlyAddedEpisodes()
                else -> null
            }
            if (cached != null) {
                Log.d(HOME_TAG, "getRecentlyAddedByType cache hit: type=$itemType count=${cached.size} in ${SystemClock.elapsedRealtime() - startedAt}ms")
                return ApiResult.Success(cached)
            }
        }

        return withServerClient("getRecentlyAddedByType") { server, client ->
            val requestStartedAt = SystemClock.elapsedRealtime()
            Log.d(HOME_TAG, "getRecentlyAddedByType sdk request start: type=$itemType")
            val userUuid = parseUuid(server.userId ?: "", "user")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                recursive = true,
                includeItemTypes = listOf(itemType),
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.DESCENDING),
                limit = limit,
            )
            val items = response.content.items
            Log.d(HOME_TAG, "getRecentlyAddedByType sdk response: type=$itemType count=${items.size} in ${SystemClock.elapsedRealtime() - requestStartedAt}ms")
            when (itemType) {
                BaseItemKind.MOVIE -> {
                    val cacheStartedAt = SystemClock.elapsedRealtime()
                    Log.d(HOME_TAG, "getRecentlyAddedByType cache write start: type=$itemType count=${items.size}")
                    cache.cacheRecentlyAddedMovies(items)
                    Log.d(HOME_TAG, "getRecentlyAddedByType cache write complete: type=$itemType in ${SystemClock.elapsedRealtime() - cacheStartedAt}ms")
                }
                BaseItemKind.EPISODE -> {
                    val cacheStartedAt = SystemClock.elapsedRealtime()
                    Log.d(HOME_TAG, "getRecentlyAddedByType cache write start: type=$itemType count=${items.size}")
                    cache.cacheRecentlyAddedEpisodes(items)
                    Log.d(HOME_TAG, "getRecentlyAddedByType cache write complete: type=$itemType in ${SystemClock.elapsedRealtime() - cacheStartedAt}ms")
                }
                else -> {
                    Log.d(HOME_TAG, "getRecentlyAddedByType no cache configured: type=$itemType")
                }
            }
            Log.d(HOME_TAG, "getRecentlyAddedByType complete: type=$itemType in ${SystemClock.elapsedRealtime() - startedAt}ms")
            items
        }
    }

    suspend fun getCachedRecentlyAddedMovies(): List<BaseItemDto>? = cache.getCachedRecentlyAddedMovies()
    suspend fun getCachedRecentlyAddedEpisodes(): List<BaseItemDto>? = cache.getCachedRecentlyAddedEpisodes()

    suspend fun getRecentlyAddedFromLibrary(
        libraryId: String,
        limit: Int = 10,
    ): ApiResult<List<BaseItemDto>> {
        return withServerClient("getRecentlyAddedFromLibrary") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val parentUuid = parseUuid(libraryId, "library")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = parentUuid,
                recursive = true,
                sortBy = listOf(ItemSortBy.DATE_CREATED),
                sortOrder = listOf(SortOrder.DESCENDING),
                limit = limit,
            )
            response.content.items
        }
    }

    suspend fun getRecentlyAddedByTypes(limit: Int = Constants.RECENTLY_ADDED_BY_TYPE_LIMIT): ApiResult<Map<String, List<BaseItemDto>>> {
        val contentTypes = listOf(
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.EPISODE,
            BaseItemKind.AUDIO,
            BaseItemKind.BOOK,
            BaseItemKind.AUDIO_BOOK,
            BaseItemKind.VIDEO,
        )

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinMediaRepository", "getRecentlyAddedByTypes: Starting to fetch items for ${contentTypes.size} content types")
        }
        val results = mutableMapOf<String, List<BaseItemDto>>()

        for (contentType in contentTypes) {
            when (val result = getRecentlyAddedByType(contentType, limit)) {
                is ApiResult.Success -> {
                    if (result.data.isNotEmpty()) {
                        val typeName = when (contentType) {
                            BaseItemKind.MOVIE -> "Movies"
                            BaseItemKind.SERIES -> "TV Shows"
                            BaseItemKind.EPISODE -> "Episodes"
                            BaseItemKind.AUDIO -> "Music"
                            BaseItemKind.BOOK -> "Books"
                            BaseItemKind.AUDIO_BOOK -> "Audiobooks"
                            BaseItemKind.VIDEO -> "Videos"
                            else -> "Other"
                        }
                        results[typeName] = result.data
                        if (BuildConfig.DEBUG) {
                            Log.d("JellyfinMediaRepository", "getRecentlyAddedByTypes: Added ${result.data.size} items to category '$typeName'")
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.w("JellyfinMediaRepository", "getRecentlyAddedByTypes: Failed to load $contentType: ${result.message}")
                }
                else -> {}
            }
        }
        return ApiResult.Success(results)
    }

    suspend fun getContinueWatching(limit: Int = 20, forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        val startedAt = SystemClock.elapsedRealtime()
        Log.d(HOME_TAG, "getContinueWatching start: limit=$limit forceRefresh=$forceRefresh")
        if (!forceRefresh) {
            val cached = cache.getCachedContinueWatching()
            if (cached != null) {
                Log.d(HOME_TAG, "getContinueWatching cache hit: count=${cached.size} in ${SystemClock.elapsedRealtime() - startedAt}ms")
                return ApiResult.Success(cached)
            }
        }

        return withServerClient("getContinueWatching") { server, client ->
            val requestStartedAt = SystemClock.elapsedRealtime()
            Log.d(HOME_TAG, "getContinueWatching sdk request start")
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
            val items = response.content.items
            Log.d(HOME_TAG, "getContinueWatching sdk response: count=${items.size} in ${SystemClock.elapsedRealtime() - requestStartedAt}ms")
            val cacheStartedAt = SystemClock.elapsedRealtime()
            Log.d(HOME_TAG, "getContinueWatching cache write start: count=${items.size}")
            cache.cacheContinueWatching(items)
            Log.d(HOME_TAG, "getContinueWatching cache write complete in ${SystemClock.elapsedRealtime() - cacheStartedAt}ms")
            Log.d(HOME_TAG, "getContinueWatching complete in ${SystemClock.elapsedRealtime() - startedAt}ms")
            items
        }
    }

    suspend fun getCachedContinueWatching(): List<BaseItemDto>? = cache.getCachedContinueWatching()

    suspend fun getFavorites(): ApiResult<List<BaseItemDto>> {
        return withServerClient("getFavorites") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                recursive = true,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                filters = listOf(ItemFilter.IS_FAVORITE),
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

    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean): ApiResult<Boolean> {
        return withServerClient("toggleFavorite") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val itemUuid = parseUuid(itemId, "item")

            if (isFavorite) {
                client.userLibraryApi.markFavoriteItem(itemId = itemUuid, userId = userUuid)
            } else {
                client.userLibraryApi.unmarkFavoriteItem(itemId = itemUuid, userId = userUuid)
            }
            updateBus.refreshItem(itemId)
            !isFavorite // Return the new state
        }
    }

    suspend fun deleteItem(itemId: String): ApiResult<Boolean> {
        return withServerClient("deleteItem") { _, client ->
            val itemUuid = parseUuid(itemId, "item")
            client.libraryApi.deleteItem(itemId = itemUuid)
            updateBus.refreshAll()
            true
        }
    }

    /**
     * Checks if the currently authenticated user has administrator privileges
     * or permission to delete content.
     */
    private suspend fun hasAdminDeletePermission(
        server: com.rpeters.cinefintv.data.JellyfinServer,
        client: org.jellyfin.sdk.api.client.ApiClient,
    ): Boolean {
        val user = client.userApi.getCurrentUser().content
        return user.policy?.isAdministrator == true || user.policy?.enableContentDeletion == true
    }

    /**
     * Deletes an item only if the current user has administrator permissions.
     */
    suspend fun deleteItemAsAdmin(itemId: String): ApiResult<Boolean> {
        return withServerClient("deleteItemAsAdmin") { server, client ->
            if (!hasAdminDeletePermission(server, client)) {
                throw IllegalStateException("Administrator permissions required")
            }
            val itemUuid = parseUuid(itemId, "item")
            client.libraryApi.deleteItem(itemId = itemUuid)
            updateBus.refreshAll()
            true
        }
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
        }

    suspend fun getSimilarMovies(movieId: String, limit: Int = 20): ApiResult<List<BaseItemDto>> =
        withServerClient("getSimilarMovies") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val movieUuid = parseUuid(movieId, "movie")

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
        }

    suspend fun getEpisodesForSeason(seasonId: String): ApiResult<List<BaseItemDto>> =
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

    suspend fun getNextUp(limit: Int = 12, forceRefresh: Boolean = false): ApiResult<List<BaseItemDto>> {
        val startedAt = SystemClock.elapsedRealtime()
        Log.d(HOME_TAG, "getNextUp start: limit=$limit forceRefresh=$forceRefresh")
        if (!forceRefresh) {
            val cached = cache.getCachedNextUp()
            if (cached != null) {
                Log.d(HOME_TAG, "getNextUp cache hit: count=${cached.size} in ${SystemClock.elapsedRealtime() - startedAt}ms")
                return ApiResult.Success(cached)
            }
        }

        return withServerClient("getNextUp") { server, client ->
            val requestStartedAt = SystemClock.elapsedRealtime()
            Log.d(HOME_TAG, "getNextUp sdk request start")
            val userUuid = parseUuid(server.userId ?: "", "user")

            val response = client.tvShowsApi.getNextUp(
                userId = userUuid,
                limit = limit,
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.OVERVIEW),
                enableUserData = true,
            )

            val items = response.content.items
            Log.d(HOME_TAG, "getNextUp sdk response: count=${items.size} in ${SystemClock.elapsedRealtime() - requestStartedAt}ms")
            val cacheStartedAt = SystemClock.elapsedRealtime()
            Log.d(HOME_TAG, "getNextUp cache write start: count=${items.size}")
            cache.cacheNextUp(items)
            Log.d(HOME_TAG, "getNextUp cache write complete in ${SystemClock.elapsedRealtime() - cacheStartedAt}ms")
            Log.d(HOME_TAG, "getNextUp complete in ${SystemClock.elapsedRealtime() - startedAt}ms")
            items
        }
    }

    suspend fun getCachedNextUp(): List<BaseItemDto>? = cache.getCachedNextUp()

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
        includeTypes: List<BaseItemKind>? = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        limit: Int = 100,
    ): ApiResult<List<BaseItemDto>> =
        withServerClient("getItemsByPerson") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val personUuid = parseUuid(personId, "person")

            val response = client.itemsApi.getItems(
                userId = userUuid,
                personIds = listOf(personUuid),
                recursive = true,
                includeItemTypes = includeTypes,
                sortBy = listOf(ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.DESCENDING),
                fields = listOf(
                    org.jellyfin.sdk.model.api.ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                    org.jellyfin.sdk.model.api.ItemFields.OVERVIEW,
                    org.jellyfin.sdk.model.api.ItemFields.GENRES,
                    org.jellyfin.sdk.model.api.ItemFields.PEOPLE,
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
        SecureLogger.networkError(
            tag = "JellyfinMediaRepository",
            error = "HTTP $status ${body.orEmpty()}",
            url = requestUrl
        )
    }

    /**
     * Clear library health issues for known problematic collection types on app initialization.
     * This helps with libraries like 'homevideos' that may have compatibility issues.
     */
    fun clearKnownLibraryHealthIssues() {
        healthChecker?.cleanup()

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinMediaRepository", "Cleared known library health issues on initialization")
        }
    }
}
