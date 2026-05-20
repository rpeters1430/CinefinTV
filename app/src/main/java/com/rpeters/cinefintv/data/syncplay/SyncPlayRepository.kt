package com.rpeters.cinefintv.data.syncplay

import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.common.DispatcherProvider
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.repository.common.BaseJellyfinRepository
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import com.rpeters.cinefintv.di.ApplicationScope
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.syncPlayApi
import org.jellyfin.sdk.api.sockets.subscribeSyncPlayCommands
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.NewGroupRequestDto
import org.jellyfin.sdk.model.api.PlayRequestDto
import org.jellyfin.sdk.model.api.ReadyRequestDto
import org.jellyfin.sdk.model.api.SeekRequestDto
import org.jellyfin.sdk.model.api.SendCommandType
import javax.inject.Inject
import javax.inject.Singleton

data class SyncPlayGroup(
    val groupId: String,
    val groupName: String,
    val participants: List<String>,
)

sealed class SyncPlaySessionState {
    data object Idle : SyncPlaySessionState()
    data class InGroup(val group: SyncPlayGroup) : SyncPlaySessionState()
}

/** Commands received from other SyncPlay members via WebSocket. */
sealed class SyncPlayCommand {
    data class Play(val positionMs: Long) : SyncPlayCommand()
    data object Pause : SyncPlayCommand()
    data class Seek(val positionMs: Long) : SyncPlayCommand()
}

@Singleton
class SyncPlayRepository @Inject constructor(
    authRepository: JellyfinAuthRepository,
    sessionManager: JellyfinSessionManager,
    cache: JellyfinCache,
    dispatchers: DispatcherProvider,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : BaseJellyfinRepository(authRepository, sessionManager, cache, dispatchers) {

    private val _sessionState = MutableStateFlow<SyncPlaySessionState>(SyncPlaySessionState.Idle)
    val sessionState: StateFlow<SyncPlaySessionState> = _sessionState.asStateFlow()

    private val _incomingCommands = MutableSharedFlow<SyncPlayCommand>(extraBufferCapacity = 8)
    val incomingCommands: SharedFlow<SyncPlayCommand> = _incomingCommands.asSharedFlow()

    private var socketJob: Job? = null

    suspend fun getAvailableGroups(): ApiResult<List<SyncPlayGroup>> =
        withServerClient("syncPlayGetGroups") { _, client ->
            val response = client.syncPlayApi.syncPlayGetGroups()
            response.content.map { it.toSyncPlayGroup() }
        }

    suspend fun createGroup(groupName: String, itemId: String, startPositionMs: Long = 0L): ApiResult<SyncPlayGroup> =
        withServerClient("syncPlayCreateGroup") { _, client ->
            val groupInfo = client.syncPlayApi.syncPlayCreateGroup(
                NewGroupRequestDto(groupName = groupName),
            ).content
            // Set this item as the group's queue
            runCatching {
                client.syncPlayApi.syncPlaySetNewQueue(
                    PlayRequestDto(
                        playingQueue = listOf(java.util.UUID.fromString(itemId)),
                        playingItemPosition = 0,
                        startPositionTicks = startPositionMs * 10_000L,
                    ),
                )
            }
            val group = groupInfo.toSyncPlayGroup()
            _sessionState.value = SyncPlaySessionState.InGroup(group)
            startSocketSubscription(client)
            group
        }

    suspend fun joinGroup(groupId: String): ApiResult<Unit> =
        withServerClient("syncPlayJoinGroup") { _, client ->
            client.syncPlayApi.syncPlayJoinGroup(
                JoinGroupRequestDto(groupId = java.util.UUID.fromString(groupId)),
            )
            val group = runCatching {
                client.syncPlayApi.syncPlayGetGroup(
                    java.util.UUID.fromString(groupId),
                ).content.toSyncPlayGroup()
            }.getOrElse { SyncPlayGroup(groupId, "Group", emptyList()) }
            _sessionState.value = SyncPlaySessionState.InGroup(group)
            startSocketSubscription(client)
        }

    suspend fun leaveGroup(): ApiResult<Unit> =
        withServerClient("syncPlayLeaveGroup") { _, client ->
            client.syncPlayApi.syncPlayLeaveGroup()
            _sessionState.value = SyncPlaySessionState.Idle
            socketJob?.cancel()
            socketJob = null
        }

    suspend fun sendUnpause(positionMs: Long): ApiResult<Unit> =
        withServerClient("syncPlayUnpause") { _, client ->
            client.syncPlayApi.syncPlayUnpause()
        }

    suspend fun sendPause(): ApiResult<Unit> =
        withServerClient("syncPlayPause") { _, client ->
            client.syncPlayApi.syncPlayPause()
        }

    suspend fun sendSeek(positionMs: Long): ApiResult<Unit> =
        withServerClient("syncPlaySeek") { _, client ->
            client.syncPlayApi.syncPlaySeek(SeekRequestDto(positionTicks = positionMs * 10_000L))
        }

    private fun startSocketSubscription(client: ApiClient) {
        socketJob?.cancel()
        socketJob = appScope.launch {
            client.webSocket.subscribeSyncPlayCommands()
                .catch { e ->
                    if (e !is CancellationException) {
                        SecureLogger.w("SyncPlayRepository", "WebSocket subscription error", e)
                    }
                }
                .collect { message ->
                    val cmd = message.data ?: return@collect
                    val positionMs = (cmd.positionTicks ?: 0L) / 10_000L
                    when (cmd.command) {
                        SendCommandType.UNPAUSE -> _incomingCommands.emit(SyncPlayCommand.Play(positionMs))
                        SendCommandType.PAUSE -> _incomingCommands.emit(SyncPlayCommand.Pause)
                        SendCommandType.SEEK -> _incomingCommands.emit(SyncPlayCommand.Seek(positionMs))
                        else -> Unit
                    }
                }
        }
    }

    private fun GroupInfoDto.toSyncPlayGroup() = SyncPlayGroup(
        groupId = groupId.toString(),
        groupName = groupName,
        participants = participants,
    )
}
