package com.rpeters.cinefintv.data.repository

import android.content.Context
import com.rpeters.cinefintv.core.constants.Constants
import com.rpeters.cinefintv.data.DeviceCapabilities
import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.SecureCredentialManager
import com.rpeters.cinefintv.data.ServerInfo
import com.rpeters.cinefintv.data.model.QuickConnectResult
import com.rpeters.cinefintv.data.model.QuickConnectState
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.repository.common.ErrorType
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import com.rpeters.cinefintv.data.utils.RepositoryUtils
import com.rpeters.cinefintv.utils.AnalyticsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.PublicSystemInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinRepository @Inject constructor(
    private val sessionManager: JellyfinSessionManager,
    private val secureCredentialManager: SecureCredentialManager,
    @param:ApplicationContext private val context: Context,
    private val deviceCapabilities: DeviceCapabilities,
    private val authRepository: JellyfinAuthRepository,
    private val streamRepository: JellyfinStreamRepository,
    private val mediaRepository: JellyfinMediaRepository,
    private val searchRepository: JellyfinSearchRepository,
    val connectivityChecker: com.rpeters.cinefintv.network.ConnectivityChecker,
    private val analyticsHelper: AnalyticsHelper,
) {
    companion object {
        private const val TAG = "JellyfinRepository"

        // Default codecs
        private const val DEFAULT_VIDEO_CODEC = "h264"
        private const val DEFAULT_AUDIO_CODEC = "aac"
        private const val DEFAULT_CONTAINER = "mp4"

        // Search constants
        private const val SEARCH_LIMIT = 50

        // Pagination constants
        private const val DEFAULT_LIMIT = 100
        private const val DEFAULT_START_INDEX = 0
        private const val RECENTLY_ADDED_LIMIT = Constants.RECENTLY_ADDED_LIMIT
        private const val RECENTLY_ADDED_BY_TYPE_LIMIT = Constants.RECENTLY_ADDED_BY_TYPE_LIMIT
    }

    // ===== STATE FLOWS - Delegated to JellyfinAuthRepository =====
    val currentServer: Flow<JellyfinServer?> = authRepository.currentServer
    val isConnected: Flow<Boolean> = authRepository.isConnected

    // ===== AUTHENTICATION METHODS - Delegated to JellyfinAuthRepository =====

    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> =
        authRepository.testServerConnection(serverUrl)

    suspend fun getServerInfo(): ApiResult<ServerInfo> {
        val server = authRepository.getCurrentServer() ?: return ApiResult.Error("No server configured", errorType = ErrorType.AUTHENTICATION)
        return try {
            val client = sessionManager.getClientForUrl(server.url)
            val info = client.systemApi.getSystemInfo().content
            @Suppress("DEPRECATION")
            ApiResult.Success(ServerInfo(
                id = info.id ?: "",
                name = info.serverName ?: "Jellyfin Server",
                version = info.version ?: "Unknown",
                operatingSystem = info.operatingSystem ?: "",
                localAddress = info.localAddress ?: "",
                productName = info.productName ?: "Jellyfin",
                startupWizardCompleted = info.startupWizardCompleted,
            ))
        } catch (e: Exception) {
            ApiResult.Error("Failed to get server info: ${e.message}", cause = e, errorType = ErrorType.NETWORK)
        }
    }

    suspend fun authenticateUser(
        serverUrl: String?,
        username: String?,
        password: String?,
    ): ApiResult<AuthenticationResult> {
        val safeServerUrl = serverUrl ?: return ApiResult.Error("Server URL is required", errorType = ErrorType.AUTHENTICATION)
        val safeUsername = username ?: return ApiResult.Error("Username is required", errorType = ErrorType.AUTHENTICATION)
        val safePassword = password ?: return ApiResult.Error("Password is required", errorType = ErrorType.AUTHENTICATION)

        return authRepository.authenticateUser(
            serverUrl = safeServerUrl,
            username = safeUsername,
            password = safePassword,
        )
    }

    suspend fun initiateQuickConnect(serverUrl: String): ApiResult<QuickConnectResult> =
        authRepository.initiateQuickConnect(serverUrl)

    suspend fun isQuickConnectEnabled(serverUrl: String): ApiResult<Boolean> =
        authRepository.isQuickConnectEnabled(serverUrl)

    suspend fun getQuickConnectState(serverUrl: String, secret: String): ApiResult<QuickConnectState> =
        authRepository.getQuickConnectState(serverUrl, secret)

    suspend fun authenticateWithQuickConnect(
        serverUrl: String,
        secret: String,
    ): ApiResult<AuthenticationResult> =
        authRepository.authenticateWithQuickConnect(serverUrl, secret)

    fun restorePersistedSession(server: JellyfinServer) {
        authRepository.seedCurrentServer(server.copy(isConnected = true))
    }

    fun isSessionTokenExpired(): Boolean = authRepository.isTokenExpired()

    suspend fun logout() {
        authRepository.logout()
    }

    // ===== LIBRARY METHODS - Delegated to JellyfinMediaRepository =====

    suspend fun getUserLibraries(): ApiResult<List<BaseItemDto>> =
        mediaRepository.getUserLibraries()

    suspend fun getLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = DEFAULT_START_INDEX,
        limit: Int = DEFAULT_LIMIT,
        fields: List<ItemFields>? = null,
    ): ApiResult<List<BaseItemDto>> =
        mediaRepository.getLibraryItems(
            parentId = parentId,
            itemTypes = itemTypes,
            startIndex = startIndex,
            limit = limit,
            fields = fields,
        )

    suspend fun getItemsByPerson(
        personId: String,
        includeTypes: List<BaseItemKind>? = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        limit: Int = 100,
    ): ApiResult<List<BaseItemDto>> =
        mediaRepository.getItemsByPerson(
            personId = personId,
            includeTypes = includeTypes,
            limit = limit,
        )

    suspend fun getRecentlyAdded(limit: Int = RECENTLY_ADDED_LIMIT): ApiResult<List<BaseItemDto>> =
        mediaRepository.getRecentlyAdded(limit = limit)

    suspend fun getRecentlyAddedByType(itemType: BaseItemKind, limit: Int = RECENTLY_ADDED_BY_TYPE_LIMIT): ApiResult<List<BaseItemDto>> =
        mediaRepository.getRecentlyAddedByType(itemType = itemType, limit = limit)

    suspend fun getRecentlyAddedFromLibrary(
        libraryId: String,
        limit: Int = 10,
    ): ApiResult<List<BaseItemDto>> =
        mediaRepository.getRecentlyAddedFromLibrary(libraryId = libraryId, limit = limit)

    suspend fun getRecentlyAddedByTypes(limit: Int = RECENTLY_ADDED_BY_TYPE_LIMIT): ApiResult<Map<String, List<BaseItemDto>>> =
        mediaRepository.getRecentlyAddedByTypes(limit = limit)

    suspend fun getFavorites(): ApiResult<List<BaseItemDto>> =
        mediaRepository.getFavorites()

    suspend fun getSeasonsForSeries(seriesId: String): ApiResult<List<BaseItemDto>> =
        mediaRepository.getSeasonsForSeries(seriesId = seriesId)

    suspend fun getEpisodesForSeason(seasonId: String): ApiResult<List<BaseItemDto>> =
        mediaRepository.getEpisodesForSeason(seasonId = seasonId)

    suspend fun getSeriesDetails(seriesId: String): ApiResult<BaseItemDto> =
        mediaRepository.getSeriesDetails(seriesId = seriesId)

    suspend fun getMovieDetails(movieId: String): ApiResult<BaseItemDto> =
        mediaRepository.getMovieDetails(movieId = movieId)

    suspend fun getEpisodeDetails(episodeId: String): ApiResult<BaseItemDto> =
        mediaRepository.getEpisodeDetails(episodeId = episodeId)

    suspend fun getItemDetails(itemId: String): ApiResult<BaseItemDto> =
        mediaRepository.getItemDetails(itemId = itemId)

    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean): ApiResult<Boolean> =
        mediaRepository.toggleFavorite(itemId, isFavorite)

    suspend fun deleteItem(itemId: String): ApiResult<Boolean> =
        mediaRepository.deleteItem(itemId)

    suspend fun deleteItemAsAdmin(itemId: String): ApiResult<Boolean> =
        mediaRepository.deleteItemAsAdmin(itemId)

    // ===== SEARCH METHODS - Delegated to JellyfinSearchRepository =====

    suspend fun searchItems(
        query: String,
        includeItemTypes: List<BaseItemKind>? = null,
        limit: Int = SEARCH_LIMIT,
    ): ApiResult<List<BaseItemDto>> =
        searchRepository.searchItems(
            query = query,
            includeItemTypes = includeItemTypes,
            limit = limit,
        )

    // ===== IMAGE METHODS - Delegated to JellyfinStreamRepository =====

    fun getImageUrl(itemId: String, imageType: String = "Primary", tag: String? = null): String? =
        streamRepository.getImageUrl(itemId, imageType, tag)

    fun getSeriesImageUrl(item: BaseItemDto): String? =
        streamRepository.getSeriesImageUrl(item)

    fun getBackdropUrl(item: BaseItemDto): String? =
        streamRepository.getBackdropUrl(item)

    fun getLogoUrl(item: BaseItemDto): String? =
        streamRepository.getLogoUrl(item)

    // ===== STREAMING METHODS - Delegated to JellyfinStreamRepository =====

    fun getStreamUrl(itemId: String): String? =
        streamRepository.getStreamUrl(itemId)

    fun getTranscodedStreamUrl(
        itemId: String,
        maxBitrate: Int? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        videoCodec: String = DEFAULT_VIDEO_CODEC,
        audioCodec: String = DEFAULT_AUDIO_CODEC,
        container: String = DEFAULT_CONTAINER,
        audioBitrate: Int? = null,
        audioChannels: Int = 8,
        allowVideoStreamCopy: Boolean = true,
    ): String? =
        streamRepository.getTranscodedStreamUrl(
            itemId = itemId,
            maxBitrate = maxBitrate,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            container = container,
            audioBitrate = audioBitrate,
            audioChannels = audioChannels,
            allowVideoStreamCopy = allowVideoStreamCopy,
        )

    fun getHlsStreamUrl(itemId: String): String? =
        streamRepository.getHlsStreamUrl(itemId)

    fun getDashStreamUrl(itemId: String): String? =
        streamRepository.getDashStreamUrl(itemId)

    suspend fun getPlaybackInfo(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        startPositionMs: Long = 0L,
    ): PlaybackInfoResponse =
        streamRepository.getPlaybackInfo(
            itemId = itemId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            startPositionMs = startPositionMs,
        )

    fun getCurrentServer(): JellyfinServer? = authRepository.getCurrentServer()

    fun isUserAuthenticated(): Boolean = authRepository.isUserAuthenticated()

    fun getDownloadUrl(itemId: String): String? =
        streamRepository.getDownloadUrl(itemId)

    suspend fun getTranscodingProgress(
        deviceId: String,
        jellyfinItemId: String? = null,
    ): TranscodingProgressInfo? =
        streamRepository.getTranscodingProgress(deviceId, jellyfinItemId)

    fun getDirectStreamUrl(itemId: String, container: String? = null): String? =
        streamRepository.getDirectStreamUrl(itemId, container)

    private fun parseUuid(id: String, idType: String): UUID = RepositoryUtils.parseUuid(id, idType)
}
