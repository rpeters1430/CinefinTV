package com.rpeters.cinefintv.data.repository

import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.model.CurrentUserDetails
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.repository.common.BaseJellyfinRepository
import com.rpeters.cinefintv.data.repository.common.ErrorType
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that contains operations specific to the user or session.
 * Functions here were previously scattered across [JellyfinRepository].
 */
@Singleton
class JellyfinUserRepository @Inject constructor(
    authRepository: JellyfinAuthRepository,
    sessionManager: com.rpeters.cinefintv.data.session.JellyfinSessionManager,
    cache: JellyfinCache,
    private val offlineProgressRepository: OfflineProgressRepository,
) : BaseJellyfinRepository(authRepository, sessionManager, cache) {

    suspend fun logout() {
        authRepository.logout()
    }

    /**
     * Flushes all queued offline progress updates to the server.
     */
    suspend fun syncOfflineProgress(): ApiResult<Int> {
        val updates = offlineProgressRepository.getQueuedUpdates()
            .sortedWith(compareBy<QueuedProgressUpdate> { it.timestamp }.thenByDescending { eventPriority(it.eventType) })
        if (updates.isEmpty()) return ApiResult.Success(0)

        SecureLogger.i("JellyfinUserRepository", "Syncing ${updates.size} offline progress updates")
        var successCount = 0
        var networkRetryCount = 0
        var nonRetryFailureCount = 0
        val syncedIds = mutableSetOf<String>()

        for (update in updates) {
            try {
                val effectiveSessionId = resolveSessionId(update)
                val result = when (update.eventType) {
                    OfflinePlaybackEventType.PROGRESS -> {
                        reportPlaybackProgress(
                            itemId = update.itemId,
                            sessionId = effectiveSessionId,
                            positionTicks = update.positionTicks,
                            mediaSourceId = update.mediaSourceId,
                            playMethod = update.playMethod,
                            isPaused = update.isPaused,
                            isMuted = update.isMuted,
                            queueOfflineOnNetworkError = false,
                        )
                    }
                    OfflinePlaybackEventType.STOPPED -> {
                        reportPlaybackStopped(
                            itemId = update.itemId,
                            sessionId = effectiveSessionId,
                            positionTicks = update.positionTicks,
                            mediaSourceId = update.mediaSourceId,
                            failed = false,
                            queueOfflineOnNetworkError = false,
                        )
                    }
                    OfflinePlaybackEventType.MARK_PLAYED -> {
                        when (
                            val watchedResult = markAsWatched(
                            itemId = update.itemId,
                            queueOfflineOnNetworkError = false,
                        )
                        ) {
                            is ApiResult.Success -> ApiResult.Success(Unit)
                            is ApiResult.Error -> ApiResult.Error(
                                watchedResult.message,
                                watchedResult.cause,
                                watchedResult.errorType,
                            )
                            is ApiResult.Loading -> ApiResult.Loading()
                        }
                    }
                    OfflinePlaybackEventType.MARK_UNPLAYED -> {
                        when (
                            val unwatchedResult = markAsUnwatched(
                            itemId = update.itemId,
                            queueOfflineOnNetworkError = false,
                        )
                        ) {
                            is ApiResult.Success -> ApiResult.Success(Unit)
                            is ApiResult.Error -> ApiResult.Error(
                                unwatchedResult.message,
                                unwatchedResult.cause,
                                unwatchedResult.errorType,
                            )
                            is ApiResult.Loading -> ApiResult.Loading()
                        }
                    }
                }
                if (result is ApiResult.Success) {
                    successCount++
                    syncedIds.add(update.id)
                } else {
                    // Item remains in queue for next sync attempt
                    if (result is ApiResult.Error && result.errorType == ErrorType.NETWORK) {
                        networkRetryCount++
                    } else {
                        // Non-network failures (e.g. 404) are still removed to prevent blocking the queue
                        nonRetryFailureCount++
                        syncedIds.add(update.id)
                    }
                }
            } catch (e: Exception) {
                // Unexpected error - keep in queue for one retry then likely prune on age
                SecureLogger.e("JellyfinUserRepository", "Failed to sync progress for ${update.itemId}", e)
            }
        }

        // Only remove the ones that actually synced (or failed permanently)
        offlineProgressRepository.removeUpdates(syncedIds)

        SecureLogger.i(
            "JellyfinUserRepository",
            "Offline sync summary: total=${updates.size}, success=$successCount, networkRetry=$networkRetryCount, failed=$nonRetryFailureCount",
        )
        return ApiResult.Success(successCount)
    }

    suspend fun pendingOfflineProgressCount(): Int = offlineProgressRepository.pendingCountSnapshot()

    suspend fun getCurrentUser(): ApiResult<CurrentUserDetails> =
        withServerClient("getCurrentUser") { server, client ->
            val user = client.userApi.getCurrentUser().content
            CurrentUserDetails(
                name = user.name?.takeIf { it.isNotBlank() } ?: server.username.orEmpty(),
                primaryImageTag = user.primaryImageTag,
                lastLoginDate = user.lastLoginDate?.toString(),
                isAdministrator = user.policy?.isAdministrator == true,
            )
        }

    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean): ApiResult<Boolean> =
        withServerClient("toggleFavorite") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val itemUuid = parseUuid(itemId, "item")
            if (isFavorite) {
                client.userLibraryApi.unmarkFavoriteItem(itemId = itemUuid, userId = userUuid)
            } else {
                client.userLibraryApi.markFavoriteItem(itemId = itemUuid, userId = userUuid)
            }
            !isFavorite
        }

    suspend fun markAsWatched(
        itemId: String,
        queueOfflineOnNetworkError: Boolean = true,
    ): ApiResult<Boolean> {
        val result = withServerClient("markAsWatched") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val itemUuid = parseUuid(itemId, "item")
            client.playStateApi.markPlayedItem(itemId = itemUuid, userId = userUuid)
            true
        }
        if (queueOfflineOnNetworkError && result is ApiResult.Error && result.errorType == ErrorType.NETWORK) {
            offlineProgressRepository.addUpdate(
                QueuedProgressUpdate(
                    eventType = OfflinePlaybackEventType.MARK_PLAYED,
                    itemId = itemId,
                    sessionId = "",
                    positionTicks = null,
                ),
            )
        }
        return result
    }

    suspend fun markAsUnwatched(
        itemId: String,
        queueOfflineOnNetworkError: Boolean = true,
    ): ApiResult<Boolean> {
        val result = withServerClient("markAsUnwatched") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val itemUuid = parseUuid(itemId, "item")
            client.playStateApi.markUnplayedItem(itemId = itemUuid, userId = userUuid)
            true
        }
        if (queueOfflineOnNetworkError && result is ApiResult.Error && result.errorType == ErrorType.NETWORK) {
            offlineProgressRepository.addUpdate(
                QueuedProgressUpdate(
                    eventType = OfflinePlaybackEventType.MARK_UNPLAYED,
                    itemId = itemId,
                    sessionId = "",
                    positionTicks = null,
                ),
            )
        }
        return result
    }

    suspend fun getItemUserData(itemId: String): ApiResult<UserItemDataDto> =
        withServerClient("getItemUserData") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val itemUuid = parseUuid(itemId, "item")
            val response = client.itemsApi.getItemUserData(itemId = itemUuid, userId = userUuid)
            response.content
        }

    suspend fun reportPlaybackStart(
        itemId: String,
        sessionId: String,
        positionTicks: Long?,
        mediaSourceId: String? = null,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
        isPaused: Boolean = false,
        isMuted: Boolean = false,
        canSeek: Boolean = true,
    ): ApiResult<Unit> =
        withServerClient("reportPlaybackStart") { _, client ->
            val itemUuid = parseUuid(itemId, "item")
            val info = PlaybackStartInfo(
                canSeek = canSeek,
                itemId = itemUuid,
                sessionId = sessionId,
                mediaSourceId = mediaSourceId,
                isPaused = isPaused,
                isMuted = isMuted,
                positionTicks = positionTicks,
                playMethod = playMethod,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
                playSessionId = sessionId,
            )
            client.playStateApi.reportPlaybackStart(info)
            Unit
        }

    suspend fun reportPlaybackProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long?,
        mediaSourceId: String? = null,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
        isPaused: Boolean = false,
        isMuted: Boolean = false,
        canSeek: Boolean = true,
        queueOfflineOnNetworkError: Boolean = true,
    ): ApiResult<Unit> {
        val effectiveSessionId = sessionId.ifBlank { UUID.randomUUID().toString() }
        val result = withServerClient("reportPlaybackProgress") { _, client ->
            val itemUuid = parseUuid(itemId, "item")
            val info = PlaybackProgressInfo(
                canSeek = canSeek,
                itemId = itemUuid,
                sessionId = effectiveSessionId,
                mediaSourceId = mediaSourceId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                isMuted = isMuted,
                playMethod = playMethod,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
                playSessionId = effectiveSessionId,
            )
            client.playStateApi.reportPlaybackProgress(info)
            Unit
        }

        // If network error, queue for later sync
        if (queueOfflineOnNetworkError && result is ApiResult.Error && result.errorType == ErrorType.NETWORK) {
            offlineProgressRepository.addUpdate(
                QueuedProgressUpdate(
                    eventType = OfflinePlaybackEventType.PROGRESS,
                    itemId = itemId,
                    sessionId = effectiveSessionId,
                    positionTicks = positionTicks,
                    mediaSourceId = mediaSourceId,
                    playMethod = playMethod,
                    isPaused = isPaused,
                    isMuted = isMuted,
                ),
            )
        }

        return result
    }

    suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long?,
        mediaSourceId: String? = null,
        failed: Boolean = false,
        queueOfflineOnNetworkError: Boolean = true,
    ): ApiResult<Unit> {
        val effectiveSessionId = sessionId.ifBlank { UUID.randomUUID().toString() }
        val result = withServerClient("reportPlaybackStopped") { _, client ->
            val itemUuid = parseUuid(itemId, "item")
            val info = PlaybackStopInfo(
                itemId = itemUuid,
                sessionId = effectiveSessionId,
                mediaSourceId = mediaSourceId,
                positionTicks = positionTicks,
                playSessionId = effectiveSessionId,
                failed = failed,
            )
            client.playStateApi.reportPlaybackStopped(info)
            Unit
        }
        if (queueOfflineOnNetworkError && result is ApiResult.Error && result.errorType == ErrorType.NETWORK) {
            offlineProgressRepository.addUpdate(
                QueuedProgressUpdate(
                    eventType = OfflinePlaybackEventType.STOPPED,
                    itemId = itemId,
                    sessionId = effectiveSessionId,
                    positionTicks = positionTicks,
                    mediaSourceId = mediaSourceId,
                ),
            )
        }
        return result
    }

    private fun resolveSessionId(update: QueuedProgressUpdate): String {
        if (update.sessionId.isNotBlank()) return update.sessionId
        val seed = "${update.itemId}-${update.timestamp}-${update.eventType}"
        return UUID.nameUUIDFromBytes(seed.toByteArray()).toString()
    }

    private fun eventPriority(type: OfflinePlaybackEventType): Int {
        return when (type) {
            OfflinePlaybackEventType.MARK_PLAYED -> 4
            OfflinePlaybackEventType.MARK_UNPLAYED -> 3
            OfflinePlaybackEventType.STOPPED -> 2
            OfflinePlaybackEventType.PROGRESS -> 1
        }
    }

    suspend fun getFavorites(): ApiResult<List<BaseItemDto>> =
        // ✅ FIX: Use withServerClient helper to ensure fresh server/client on token refresh
        withServerClient("getFavorites") { server, client ->
            val userUuid = parseUuid(server.userId ?: "", "user")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                recursive = true,
                sortBy = listOf(org.jellyfin.sdk.model.api.ItemSortBy.SORT_NAME),
                filters = listOf(org.jellyfin.sdk.model.api.ItemFilter.IS_FAVORITE),
            )
            response.content.items
        }

    suspend fun deleteItem(itemId: String): ApiResult<Boolean> =
        withServerClient("deleteItem") { server, client ->
            val itemUuid = parseUuid(itemId, "item")
            client.libraryApi.deleteItem(itemId = itemUuid)
            true
        }

    suspend fun deleteItemAsAdmin(itemId: String): ApiResult<Boolean> =
        withServerClient("deleteItemAsAdmin") { server, client ->
            val itemUuid = parseUuid(itemId, "item")

            // Check admin permissions first
            val hasPermission = hasAdminDeletePermission(server)
            if (!hasPermission) {
                throw SecurityException("Administrator permissions required")
            }

            client.libraryApi.deleteItem(itemId = itemUuid)
            true
        }

    suspend fun refreshItemMetadata(itemId: String): ApiResult<Boolean> =
        ApiResult.Error(
            "Metadata refresh not yet implemented - requires Jellyfin SDK update",
            errorType = ErrorType.BAD_REQUEST,
        )

    private suspend fun hasAdminDeletePermission(
        server: com.rpeters.cinefintv.data.JellyfinServer,
    ): Boolean {
        return try {
            // ✅ FIX: Use withServerClient helper to ensure fresh server/client on token refresh
            val result = withServerClient("hasAdminDeletePermission") { server, client ->
                val userUuid = parseUuid(server.userId ?: "", "user")
                val user = client.userApi.getCurrentUser().content
                user.policy?.isAdministrator == true || user.policy?.enableContentDeletion == true
            }
            when (result) {
                is ApiResult.Success -> result.data
                else -> false
            }
        } catch (e: CancellationException) {
            throw e
        }
    }
}
