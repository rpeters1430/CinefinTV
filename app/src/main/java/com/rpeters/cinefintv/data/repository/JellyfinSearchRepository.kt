package com.rpeters.cinefintv.data.repository

import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.repository.common.BaseJellyfinRepository
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for search functionality across Jellyfin content.
 * Provides intelligent search with caching and various filter options.
 */
@Singleton
class JellyfinSearchRepository @Inject constructor(
    authRepository: JellyfinAuthRepository,
    sessionManager: com.rpeters.cinefintv.data.session.JellyfinSessionManager,
    cache: JellyfinCache,
) : BaseJellyfinRepository(authRepository, sessionManager, cache) {

    /**
     * Search for items across all content types with optional filtering.
     */
    suspend fun searchItems(
        query: String,
        includeItemTypes: List<BaseItemKind>? = null,
        limit: Int = 50,
    ): ApiResult<List<BaseItemDto>> {
        if (query.isBlank()) {
            return ApiResult.Success(emptyList())
        }

        return executeWithRetry("searchItems") {
            executeWithTokenRefresh {
                val server = validateServer()
                val userUuid = parseUuid(server.userId ?: "", "user")
                val client = getClient(server.url, server.accessToken)

                val response = client.itemsApi.getItems(
                    userId = userUuid,
                    searchTerm = query.trim(),
                    recursive = true,
                    includeItemTypes = includeItemTypes ?: listOf(
                        BaseItemKind.MOVIE,
                        BaseItemKind.SERIES,
                        BaseItemKind.EPISODE,
                        BaseItemKind.AUDIO,
                        BaseItemKind.MUSIC_ALBUM,
                        BaseItemKind.MUSIC_ARTIST,
                        BaseItemKind.BOOK,
                        BaseItemKind.AUDIO_BOOK,
                    ),
                    limit = limit,
                )
                response.content.items
            }
        }
    }

    /**
     * Search specifically for movies.
     */
    suspend fun searchMovies(query: String, limit: Int = 20): ApiResult<List<BaseItemDto>> {
        return searchItems(query, listOf(BaseItemKind.MOVIE), limit)
    }

    /**
     * Search specifically for TV shows and series.
     */
    suspend fun searchTVShows(query: String, limit: Int = 20): ApiResult<List<BaseItemDto>> {
        return searchItems(query, listOf(BaseItemKind.SERIES), limit)
    }

    /**
     * Search specifically for music content.
     */
    suspend fun searchMusic(query: String, limit: Int = 20): ApiResult<List<BaseItemDto>> {
        return searchItems(
            query,
            listOf(BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST),
            limit,
        )
    }

    /**
     * Search specifically for books and audiobooks.
     */
    suspend fun searchBooks(query: String, limit: Int = 20): ApiResult<List<BaseItemDto>> {
        return searchItems(query, listOf(BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK), limit)
    }
}
