package com.rpeters.cinefintv.data.repository

import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.common.DispatcherProvider
import com.rpeters.cinefintv.data.common.MediaUpdateBus
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.repository.common.BaseJellyfinRepository
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UpdatePlaylistDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps the Jellyfin SDK's [org.jellyfin.sdk.api.operations.PlaylistsApi].
 *
 * Playlist item order must always come from [getPlaylistItems] (which reflects the
 * playlist's own ordering), never from [JellyfinMediaRepository.getLibraryItems] which
 * forces alphabetical sorting.
 */
@Singleton
class JellyfinPlaylistRepository @Inject constructor(
    authRepository: JellyfinAuthRepository,
    sessionManager: JellyfinSessionManager,
    cache: JellyfinCache,
    dispatchers: DispatcherProvider,
    private val updateBus: MediaUpdateBus,
) : BaseJellyfinRepository(authRepository, sessionManager, cache, dispatchers) {

    suspend fun getAllPlaylists(): ApiResult<List<BaseItemDto>> =
        withServerClient("getAllPlaylists") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                recursive = true,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = listOf(
                    ItemFields.OVERVIEW,
                    ItemFields.ITEM_COUNTS,
                    ItemFields.CAN_DELETE,
                ),
            )
            response.content.items
        }

    suspend fun getPlaylistItems(playlistId: String): ApiResult<List<BaseItemDto>> =
        withServerClient("getPlaylistItems", playlistId) { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val playlistUuid = parseUuid(playlistId, "playlist")
            val response = client.playlistsApi.getPlaylistItems(
                playlistId = playlistUuid,
                userId = userUuid,
                fields = listOf(
                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                    ItemFields.OVERVIEW,
                ),
                enableUserData = true,
            )
            response.content.items
        }

    suspend fun createPlaylist(name: String, itemIds: List<String> = emptyList()): ApiResult<String> =
        withServerClient("createPlaylist") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val result = client.playlistsApi.createPlaylist(
                CreatePlaylistDto(
                    name = name,
                    ids = itemIds.map { parseUuid(it, "item") },
                    userId = userUuid,
                    users = emptyList(),
                    isPublic = true,
                ),
            ).content
            updateBus.refreshAll()
            result.id
        }

    suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): ApiResult<Boolean> =
        withServerClient("addItemsToPlaylist", playlistId) { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val playlistUuid = parseUuid(playlistId, "playlist")
            client.playlistsApi.addItemToPlaylist(
                playlistId = playlistUuid,
                ids = itemIds.map { parseUuid(it, "item") },
                userId = userUuid,
            )
            updateBus.refreshItem(playlistId)
            true
        }

    /**
     * @param entryIds [BaseItemDto.playlistItemId] values from items returned by [getPlaylistItems] —
     * NOT the underlying media items' own ids.
     */
    suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): ApiResult<Boolean> =
        withServerClient("removeItemsFromPlaylist", playlistId) { _, client ->
            client.playlistsApi.removeItemFromPlaylist(
                playlistId = playlistId,
                entryIds = entryIds,
            )
            updateBus.refreshItem(playlistId)
            true
        }

    /**
     * @param entryId a [BaseItemDto.playlistItemId] value from [getPlaylistItems] — NOT the
     * underlying media item's own id.
     */
    suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): ApiResult<Boolean> =
        withServerClient("movePlaylistItem", playlistId) { _, client ->
            client.playlistsApi.moveItem(
                playlistId = playlistId,
                itemId = entryId,
                newIndex = newIndex,
            )
            updateBus.refreshItem(playlistId)
            true
        }

    suspend fun renamePlaylist(playlistId: String, newName: String): ApiResult<Boolean> =
        withServerClient("renamePlaylist", playlistId) { _, client ->
            val playlistUuid = parseUuid(playlistId, "playlist")
            client.playlistsApi.updatePlaylist(
                playlistId = playlistUuid,
                data = UpdatePlaylistDto(name = newName),
            )
            updateBus.refreshItem(playlistId)
            true
        }

    suspend fun deletePlaylist(playlistId: String): ApiResult<Boolean> =
        withServerClient("deletePlaylist") { _, client ->
            val playlistUuid = parseUuid(playlistId, "playlist")
            client.libraryApi.deleteItem(itemId = playlistUuid)
            updateBus.refreshAll()
            true
        }
}
