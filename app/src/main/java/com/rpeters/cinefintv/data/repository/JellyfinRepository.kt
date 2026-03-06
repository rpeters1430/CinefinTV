package com.rpeters.cinefintv.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.R
import com.rpeters.cinefintv.core.constants.Constants
import com.rpeters.cinefintv.data.DeviceCapabilities
import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.SecureCredentialManager
import com.rpeters.cinefintv.data.ServerInfo
import com.rpeters.cinefintv.data.model.JellyfinDeviceProfile
import com.rpeters.cinefintv.data.model.QuickConnectResult
import com.rpeters.cinefintv.data.model.QuickConnectState
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.repository.common.ErrorType
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import com.rpeters.cinefintv.data.utils.RepositoryUtils
import com.rpeters.cinefintv.core.ErrorHandler
import com.rpeters.cinefintv.core.OfflineManager
import com.rpeters.cinefintv.utils.AppResources
import com.rpeters.cinefintv.utils.SecureLogger
import com.rpeters.cinefintv.utils.AnalyticsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.SortOrder
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
    val connectivityChecker: com.rpeters.cinefintv.network.ConnectivityChecker,
    private val analyticsHelper: AnalyticsHelper,
) {
    companion object {
        private const val TAG = "JellyfinRepository"

        // ✅ PHASE 4: Use centralized constants
        private const val TOKEN_VALIDITY_DURATION_MS = Constants.TOKEN_VALIDITY_DURATION_MS

        // API retry constants
        private const val DEFAULT_MAX_RETRIES = Constants.MAX_RETRY_ATTEMPTS - 1 // Convert to retry count
        private const val RE_AUTH_DELAY_MS = Constants.RE_AUTH_DELAY_MS

        // Stream quality constants
        private const val DEFAULT_MAX_BITRATE = 140_000_000
        private const val DEFAULT_MAX_AUDIO_CHANNELS = 2

        // Image size constants
        private const val DEFAULT_IMAGE_MAX_HEIGHT = 400
        private const val DEFAULT_IMAGE_MAX_WIDTH = 400
        private const val BACKDROP_MAX_HEIGHT = 400
        private const val BACKDROP_MAX_WIDTH = 800

        // API pagination constants
        private const val DEFAULT_LIMIT = 100
        private const val DEFAULT_START_INDEX = 0
        private const val RECENTLY_ADDED_LIMIT = Constants.RECENTLY_ADDED_LIMIT
        private const val RECENTLY_ADDED_BY_TYPE_LIMIT = Constants.RECENTLY_ADDED_BY_TYPE_LIMIT
        private const val SEARCH_LIMIT = 50

        // Default codecs
        private const val DEFAULT_VIDEO_CODEC = "h264"
        private const val DEFAULT_AUDIO_CODEC = "aac"
        private const val DEFAULT_CONTAINER = "mp4"
    }

    // ===== STATE FLOWS - Delegated to JellyfinAuthRepository =====
    val currentServer: Flow<JellyfinServer?> = authRepository.currentServer
    val isConnected: Flow<Boolean> = authRepository.isConnected

    // ✅ FIX: Removed authMutex - authentication is now handled by AuthRepository

    // Helper function for debug logging that only logs in debug builds
    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", message)
        }
    }

    // Helper function to get string resources
    private fun getString(resId: Int): String = context.getString(resId)

    // Helper function to get default item types for a collection
    private fun getDefaultTypesForCollection(collectionType: String?): List<BaseItemKind>? = when (collectionType?.lowercase()) {
        "movies" -> listOf(BaseItemKind.MOVIE)
        "tvshows" -> listOf(BaseItemKind.SERIES)
        "music" -> listOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.AUDIO, BaseItemKind.MUSIC_ARTIST)
        "homevideos" -> listOf(BaseItemKind.VIDEO)
        "photos" -> listOf(BaseItemKind.PHOTO)
        "books" -> listOf(BaseItemKind.BOOK, BaseItemKind.AUDIO_BOOK)
        else -> null
    }

    /**
     * Get Jellyfin API client on background thread to avoid StrictMode violations.
     */
    private suspend fun getClient(serverUrl: String, accessToken: String? = null): ApiClient =
        sessionManager.getClientForUrl(serverUrl)

    private suspend fun <T> withIo(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { block() }

    // ✅ FIX: Helper method to get current authenticated client
    private suspend fun getCurrentAuthenticatedClient(): ApiClient? {
        val currentServer = authRepository.getCurrentServer()
        return currentServer?.let {
            getClient(it.url, it.accessToken)
        }
    }

    /**
     * Execute a repository operation with a fresh ApiClient and current server context.
     * Authentication refresh and 401-aware retry are centralized in JellyfinSessionManager.
     */
    private suspend fun <T> withServerClient(
        operationName: String,
        block: suspend (server: JellyfinServer, client: ApiClient) -> T,
    ): ApiResult<T> =
        try {
            val result = withIo {
                sessionManager.executeWithAuth(operationName) { server, client ->
                    block(server, client)
                }
            }
            ApiResult.Success(result)
        } catch (e: Exception) {
            handleExceptionSafely(e)
        }

    // ✅ PHASE 4: Simplified error handling using centralized utilities
    private fun <T> handleExceptionSafely(e: Exception, defaultMessage: String = getString(R.string.error_occurred)): ApiResult.Error<T> {
        // Let cancellation exceptions bubble up instead of converting to ApiResult.Error
        if (e is java.util.concurrent.CancellationException || e is kotlinx.coroutines.CancellationException) {
            throw e
        }
        return handleException(e, defaultMessage)
    }

    private fun <T> handleException(e: Exception, defaultMessage: String = getString(R.string.error_occurred)): ApiResult.Error<T> {
        val processedError = ErrorHandler.processError(e, operation = defaultMessage)
        val currentServer = authRepository.getCurrentServer()
        ErrorHandler.logErrorAnalytics(
            analyticsHelper = analyticsHelper,
            error = processedError,
            operation = defaultMessage,
            userId = currentServer?.userId,
        )

        return ApiResult.Error(
            message = processedError.userMessage,
            cause = e,
            errorType = RepositoryUtils.getErrorType(e),
        )
    }

    // ===== AUTHENTICATION METHODS - Delegated to JellyfinAuthRepository =====

    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> =
        authRepository.testServerConnection(serverUrl)

    suspend fun getServerInfo(): ApiResult<ServerInfo> =
        withServerClient("getServerInfo") { _, client ->
            val info = client.systemApi.getSystemInfo().content
            @Suppress("DEPRECATION")
            ServerInfo(
                id = info.id ?: "",
                name = info.serverName ?: "Jellyfin Server",
                version = info.version ?: "Unknown",
                operatingSystem = info.operatingSystem ?: "",
                localAddress = info.localAddress ?: "",
                productName = info.productName ?: "Jellyfin",
                startupWizardCompleted = info.startupWizardCompleted,
            )
        }

    suspend fun authenticateUser(
        serverUrl: String?,
        username: String?,
        password: String?,
    ): ApiResult<AuthenticationResult> {
        val safeServerUrl = serverUrl ?: return ApiResult.Error("Server URL is required", errorType = ErrorType.AUTHENTICATION)
        val safeUsername = username ?: return ApiResult.Error("Username is required", errorType = ErrorType.AUTHENTICATION)
        val safePassword = password ?: return ApiResult.Error("Password is required", errorType = ErrorType.AUTHENTICATION)

        // Delegate to auth repository, ensuring non-null values
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

    // ===== LIBRARY METHODS - Simplified for better maintainability =====

    suspend fun getUserLibraries(): ApiResult<List<BaseItemDto>> {
        // ✅ FIX: Validate token before making requests
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "getUserLibraries: Token expired, attempting proactive refresh")
            if (!forceReAuthenticate()) {
                return ApiResult.Error("Authentication expired", errorType = ErrorType.AUTHENTICATION)
            }
        }

        return withServerClient("getUserLibraries") { server, client ->
            val userUuid = runCatching { UUID.fromString(server.userId ?: "") }.getOrNull()
                ?: throw IllegalStateException("Invalid user ID")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.COLLECTION_FOLDER),
            )
            response.content.items
        }
    }

    suspend fun getLibraryItems(
        parentId: String? = null,
        itemTypes: String? = null,
        startIndex: Int = DEFAULT_START_INDEX,
        limit: Int = DEFAULT_LIMIT,
        fields: List<ItemFields>? = null,
    ): ApiResult<List<BaseItemDto>> {
        // ✅ FIX: Validate token before making requests
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "getLibraryItems: Token expired, attempting proactive refresh")
            if (!forceReAuthenticate()) {
                return ApiResult.Error("Authentication expired", errorType = ErrorType.AUTHENTICATION)
            }
        }

        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        return try {
            withIo {
                val client = getClient(server.url, server.accessToken)
                val itemKinds = itemTypes?.split(",")?.mapNotNull { type ->
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

                // Guard against empty list - can cause 400 errors
                val includeTypes = itemKinds?.takeIf { it.isNotEmpty() }

                val response = client.itemsApi.getItems(
                    userId = userUuid,
                    recursive = true,
                    includeItemTypes = includeTypes,
                    startIndex = startIndex,
                    limit = limit,
                    fields = fields,
                )
                ApiResult.Success(response.content.items)
            }
        } catch (e: Exception) {
            handleExceptionSafely(e, "getLibraryItems")
        }
    }

    /**
     * Get all movies and TV shows for a specific person (actor/director/etc)
     */
    suspend fun getItemsByPerson(
        personId: String,
        includeTypes: List<BaseItemKind>? = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
        limit: Int = 100,
    ): ApiResult<List<BaseItemDto>> {
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "getItemsByPerson: Token expired, attempting proactive refresh")
            if (!forceReAuthenticate()) {
                return ApiResult.Error("Authentication expired", errorType = ErrorType.AUTHENTICATION)
            }
        }

        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        return try {
            withIo {
                val client = getClient(server.url, server.accessToken)
                val response = client.itemsApi.getItems(
                    userId = userUuid,
                    recursive = true,
                    personIds = listOf(UUID.fromString(personId)),
                    includeItemTypes = includeTypes,
                    limit = limit,
                    sortBy = listOf(ItemSortBy.PRODUCTION_YEAR, ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = listOf(
                        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                        ItemFields.OVERVIEW,
                        ItemFields.GENRES,
                        ItemFields.PEOPLE,
                    ),
                )
                ApiResult.Success(response.content.items)
            }
        } catch (e: Exception) {
            handleExceptionSafely(e, "getItemsByPerson")
        }
    }

    suspend fun getRecentlyAdded(limit: Int = RECENTLY_ADDED_LIMIT): ApiResult<List<BaseItemDto>> {
        // ✅ FIX: Validate token before making requests
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "getRecentlyAdded: Token expired, attempting proactive refresh")
            if (!forceReAuthenticate()) {
                return ApiResult.Error("Authentication expired", errorType = ErrorType.AUTHENTICATION)
            }
        }

        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        return try {
            logDebug("getRecentlyAdded: Requesting $limit items from server")

            val items = executeWithRetry {
                withIo {
                    // ✅ FIX: Always get current server state inside the retry closure to use fresh token
                    val currentServer = authRepository.getCurrentServer()
                        ?: throw IllegalStateException("Server not available")
                    val currentUserUuid = runCatching { UUID.fromString(currentServer.userId ?: "") }.getOrNull()
                        ?: throw IllegalStateException("Invalid user ID")

                    val client = getClient(currentServer.url, currentServer.accessToken)
                    val response = client.itemsApi.getItems(
                        userId = currentUserUuid,
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

            logDebug("getRecentlyAdded: Retrieved ${items.size} items")

            // Log details of each item
            items.forEachIndexed { index, item ->
                val dateFormatted = item.dateCreated?.toString() ?: AppResources.getString(R.string.unknown)
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinRepository", "getRecentlyAdded[$index]: ${item.type} - '${item.name}' (Created: $dateFormatted)")
                }
            }

            ApiResult.Success(items)
        } catch (e: Exception) {
            handleExceptionSafely(e, "getRecentlyAdded")
        }
    }

    private suspend fun reAuthenticate(): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "reAuthenticate: Delegating to AuthRepository")
        }

        // ✅ FIX: Delegate to AuthRepository to prevent duplicate authentication logic and race conditions
        return authRepository.reAuthenticate()
    }

    private suspend fun forceReAuthenticate(): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "forceReAuthenticate: Forcing token refresh via AuthRepository")
        }

        // ✅ FIX: Use force refresh when server reports 401 errors
        return authRepository.forceReAuthenticate()
    }

    suspend fun logout() {
        authRepository.logout()
    }

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 2,
        operation: suspend () -> T,
    ): T {
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                return operation()
            } catch (e: CancellationException) {
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinRepository", "Operation was cancelled, not retrying")
                }
                throw e
            }
        }

        throw lastException ?: Exception(AppResources.getString(R.string.unknown_error))
    }

    /**
     * Execute an operation with automatic re-authentication on 401 errors
     */
    private suspend fun <T> executeWithAuthRetry(
        operationName: String,
        maxRetries: Int = 2,
        operation: suspend () -> ApiResult<T>,
    ): ApiResult<T> {
        for (attempt in 0..maxRetries) {
            try {
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinRepository", "$operationName: Attempt ${attempt + 1}/${maxRetries + 1}")
                }

                val result = operation()

                when (result) {
                    is ApiResult.Success -> {
                        if (attempt > 0) {
                            if (BuildConfig.DEBUG) {
                                Log.i("JellyfinRepository", "$operationName: Succeeded on attempt ${attempt + 1}")
                            }
                        }
                        return result
                    }
                    is ApiResult.Loading -> return result
                    is ApiResult.Error -> {
                        Log.w("JellyfinRepository", "$operationName: Got error on attempt ${attempt + 1}: ${result.message} (type: ${result.errorType})")

                        // Check if this is a 401 error that we should retry with re-authentication
                        if (result.errorType == ErrorType.UNAUTHORIZED && attempt < maxRetries) {
                            Log.w("JellyfinRepository", "$operationName: Got 401 error, attempting force re-authentication")

                            if (forceReAuthenticate()) {
                                if (BuildConfig.DEBUG) {
                                    Log.d("JellyfinRepository", "$operationName: Force re-authentication successful, retrying")
                                }
                                // Additional delay for token propagation
                                kotlinx.coroutines.delay(RE_AUTH_DELAY_MS)
                                continue
                            } else {
                                Log.w("JellyfinRepository", "$operationName: Force re-authentication failed")
                                return result
                            }
                        } else {
                            if (BuildConfig.DEBUG) {
                                Log.d("JellyfinRepository", "$operationName: Error type ${result.errorType} not retryable or max attempts reached")
                            }
                            return result
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinRepository", "$operationName: Operation cancelled on attempt ${attempt + 1}")
                }
                throw e
            }
        }

        return ApiResult.Error("$operationName failed after $maxRetries retry attempts", errorType = ErrorType.UNKNOWN)
    }

    suspend fun getRecentlyAddedByType(itemType: BaseItemKind, limit: Int = RECENTLY_ADDED_BY_TYPE_LIMIT): ApiResult<List<BaseItemDto>> {
        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "getRecentlyAddedByType: Requesting $limit items of type $itemType")
        }

        return executeWithAuthRetry("getRecentlyAddedByType") {
            withIo {
                val currentServer = authRepository.getCurrentServer()
                    ?: return@withIo ApiResult.Error("Server not available", errorType = ErrorType.AUTHENTICATION)
                val client = getClient(currentServer.url, currentServer.accessToken)
                val currentUserUuid = runCatching { UUID.fromString(currentServer.userId ?: "") }.getOrNull()
                    ?: return@withIo ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)

                val response = client.itemsApi.getItems(
                    userId = currentUserUuid,
                    recursive = true,
                    includeItemTypes = listOf(itemType),
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = limit,
                )
                val items = response.content.items

                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinRepository", "getRecentlyAddedByType: Retrieved ${items.size} items of type $itemType")
                }

                // Log details of each item
                items.forEachIndexed { index, item ->
                    val dateFormatted = item.dateCreated?.toString() ?: AppResources.getString(R.string.unknown)
                    if (BuildConfig.DEBUG) {
                        Log.d("JellyfinRepository", "getRecentlyAddedByType[$itemType][$index]: '${item.name}' (Created: $dateFormatted)")
                    }
                }

                ApiResult.Success(items)
            }
        }
    }

    suspend fun getRecentlyAddedFromLibrary(
        libraryId: String,
        limit: Int = 10,
    ): ApiResult<List<BaseItemDto>> {
        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        val parentUuid = runCatching { UUID.fromString(libraryId) }.getOrNull()
        if (parentUuid == null) {
            return ApiResult.Error("Invalid library ID", errorType = ErrorType.NETWORK)
        }

        return try {
            withIo {
                val client = getClient(server.url, server.accessToken)
                val response = client.itemsApi.getItems(
                    userId = userUuid,
                    parentId = parentUuid,
                    recursive = true,
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = limit,
                )
                ApiResult.Success(response.content.items)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun getRecentlyAddedByTypes(limit: Int = RECENTLY_ADDED_BY_TYPE_LIMIT): ApiResult<Map<String, List<BaseItemDto>>> {
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
            Log.d("JellyfinRepository", "getRecentlyAddedByTypes: Starting to fetch items for ${contentTypes.size} content types")
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
                            Log.d("JellyfinRepository", "getRecentlyAddedByTypes: Added ${result.data.size} items to category '$typeName'")
                        }
                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.d("JellyfinRepository", "getRecentlyAddedByTypes: No items found for type $contentType")
                        }
                    }
                }
                is ApiResult.Error -> {
                    Log.w("JellyfinRepository", "getRecentlyAddedByTypes: Failed to load $contentType: ${result.message}")
                    // Continue with other types even if one fails
                }
                else -> { /* Loading state not relevant here */ }
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "getRecentlyAddedByTypes: Completed with ${results.size} categories: ${results.keys.joinToString(", ")}")
        }
        return ApiResult.Success(results)
    }

    suspend fun getFavorites(): ApiResult<List<BaseItemDto>> {
        // ✅ FIX: Validate token before making requests
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "getFavorites: Token expired, attempting proactive refresh")
            if (!forceReAuthenticate()) {
                return ApiResult.Error("Authentication expired", errorType = ErrorType.AUTHENTICATION)
            }
        }

        return withServerClient("getFavorites") { server, client ->
            val userUuid = runCatching { UUID.fromString(server.userId ?: "") }.getOrNull()
                ?: throw IllegalStateException("Invalid user ID")
            val response = client.itemsApi.getItems(
                userId = userUuid,
                recursive = true,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                filters = listOf(ItemFilter.IS_FAVORITE),
            )
            response.content.items
        }
    }

    /**
     * Fetches all seasons for a given series.
     *
     * @param seriesId The ID of the series to fetch seasons for.
     * @return [ApiResult] containing a list of seasons or an error.
     */
    suspend fun getSeasonsForSeries(seriesId: String): ApiResult<List<BaseItemDto>> {
        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "getSeasonsForSeries: Fetching seasons for seriesId=$seriesId")
        }
        return withServerClient("getSeasonsForSeries") { server, client ->
            val userUuid = runCatching { UUID.fromString(server.userId ?: "") }.getOrNull()
                ?: throw IllegalStateException("Invalid user ID")
            val seriesUuid = parseUuid(seriesId, "series")

            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = seriesUuid,
                includeItemTypes = listOf(BaseItemKind.SEASON),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.DATE_CREATED, ItemFields.OVERVIEW),
            )
            if (BuildConfig.DEBUG) {
                Log.d("JellyfinRepository", "getSeasonsForSeries: Successfully fetched ${response.content.items.size} seasons for seriesId=$seriesId")
            }
            response.content.items
        }
    }

    /**
     * Fetches all episodes for a given season.
     *
     * @param seasonId The ID of the season to fetch episodes for.
     * @return [ApiResult] containing a list of episodes or an error.
     */
    suspend fun getEpisodesForSeason(seasonId: String): ApiResult<List<BaseItemDto>> {
        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "getEpisodesForSeason: Fetching episodes for seasonId=$seasonId")
        }
        return withServerClient("getEpisodesForSeason") { server, client ->
            val userUuid = runCatching { UUID.fromString(server.userId ?: "") }.getOrNull()
                ?: throw IllegalStateException("Invalid user ID")
            val seasonUuid = parseUuid(seasonId, "season")

            val response = client.itemsApi.getItems(
                userId = userUuid,
                parentId = seasonUuid,
                includeItemTypes = listOf(BaseItemKind.EPISODE),
                sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                sortOrder = listOf(SortOrder.ASCENDING),
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.DATE_CREATED, ItemFields.OVERVIEW),
            )
            if (BuildConfig.DEBUG) {
                Log.d("JellyfinRepository", "getEpisodesForSeason: Successfully fetched ${response.content.items.size} episodes for seasonId=$seasonId")
            }
            response.content.items
        }
    }

    private suspend fun getItemDetailsById(itemId: String, itemTypeName: String): ApiResult<BaseItemDto> {
        return withServerClient("getItemDetailsById") { server, client ->
            val userUuid = runCatching { UUID.fromString(server.userId ?: "") }.getOrNull()
                ?: throw IllegalStateException("Invalid user ID")
            val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
                ?: throw IllegalArgumentException("Invalid $itemTypeName ID")

            val response = client.itemsApi.getItems(
                userId = userUuid,
                ids = listOf(itemUuid),
                limit = 1,
                fields = listOf(
                    ItemFields.OVERVIEW,
                    ItemFields.GENRES,
                    ItemFields.PEOPLE,
                    ItemFields.MEDIA_SOURCES,
                    ItemFields.MEDIA_STREAMS,
                    ItemFields.DATE_CREATED,
                    ItemFields.STUDIOS,
                    ItemFields.TAGS,
                    ItemFields.CHAPTERS,
                ),
            )
            val item = response.content.items.firstOrNull()
            item ?: throw IllegalStateException("Item not found")
        }
    }

    suspend fun getSeriesDetails(seriesId: String): ApiResult<BaseItemDto> {
        return getItemDetailsById(seriesId, "series")
    }

    suspend fun getMovieDetails(movieId: String): ApiResult<BaseItemDto> {
        return getItemDetailsById(movieId, "movie")
    }

    suspend fun getEpisodeDetails(episodeId: String): ApiResult<BaseItemDto> {
        return getItemDetailsById(episodeId, "episode")
    }

    /**
     * Fetches a single item by ID without requiring knowledge of its type.
     */
    suspend fun getItemDetails(itemId: String): ApiResult<BaseItemDto> {
        return getItemDetailsById(itemId, "item")
    }

    // ===== SEARCH METHODS - Simplified implementation =====

    suspend fun searchItems(
        query: String,
        includeItemTypes: List<BaseItemKind>? = null,
        limit: Int = SEARCH_LIMIT,
    ): ApiResult<List<BaseItemDto>> {
        if (query.isBlank()) {
            return ApiResult.Success(emptyList())
        }

        // ✅ FIX: Validate token before making requests
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "searchItems: Token expired, attempting proactive refresh")
            if (!forceReAuthenticate()) {
                return ApiResult.Error("Authentication expired", errorType = ErrorType.AUTHENTICATION)
            }
        }

        return withServerClient("searchItems") { server, client ->
            val userUuid = runCatching { UUID.fromString(server.userId ?: "") }.getOrNull()
                ?: throw IllegalStateException("Invalid user ID")

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

    // ===== IMAGE METHODS - Delegated to JellyfinStreamRepository =====

    fun getImageUrl(itemId: String, imageType: String = "Primary", tag: String? = null): String? =
        streamRepository.getImageUrl(itemId, imageType, tag)

    fun getSeriesImageUrl(item: BaseItemDto): String? =
        streamRepository.getSeriesImageUrl(item)

    fun getBackdropUrl(item: BaseItemDto): String? =
        streamRepository.getBackdropUrl(item)

    fun getLogoUrl(item: BaseItemDto): String? =
        streamRepository.getLogoUrl(item)

    private suspend fun <T> safeApiCall(operation: suspend () -> T): T = operation()

    private fun isTokenExpired(): Boolean {
        val server = authRepository.getCurrentServer() ?: return true
        val loginTimestamp = server.loginTimestamp ?: return true
        val currentTime = System.currentTimeMillis()

        // Consider token expired after 50 minutes (10 minutes before actual expiry)
        // This gives us time to refresh before hitting 401 errors
        val isExpired = (currentTime - loginTimestamp) > TOKEN_VALIDITY_DURATION_MS

        if (isExpired) {
            // Log only non-sensitive information for debugging
            Log.w("JellyfinRepository", "Token expired. Server ID: ${server.id.take(8)}")
        }

        return isExpired
    }

    private suspend fun validateAndRefreshToken() {
        if (isTokenExpired()) {
            Log.w("JellyfinRepository", "Token expired, attempting proactive refresh")
            if (forceReAuthenticate()) {
                if (BuildConfig.DEBUG) {
                    Log.d("JellyfinRepository", "Proactive token refresh successful")
                }
            } else {
                Log.w("JellyfinRepository", "Proactive token refresh failed, user will be logged out")
            }
        }
    }

    /**
     * Manually validate and refresh token - exposed for manual refresh
     */
    suspend fun validateAndRefreshTokenManually() {
        validateAndRefreshToken()
    }

    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean): ApiResult<Boolean> {
        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val userUuid = runCatching { UUID.fromString(server.userId) }.getOrNull()
        if (userUuid == null) {
            return ApiResult.Error("Invalid user ID", errorType = ErrorType.AUTHENTICATION)
        }

        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
        if (itemUuid == null) {
            return ApiResult.Error("Invalid item ID", errorType = ErrorType.NOT_FOUND)
        }

        return try {
            withIo {
                val client = getClient(server.url, server.accessToken)
                if (isFavorite) {
                    client.userLibraryApi.markFavoriteItem(itemId = itemUuid, userId = userUuid)
                } else {
                    client.userLibraryApi.unmarkFavoriteItem(itemId = itemUuid, userId = userUuid)
                }
                ApiResult.Success(!isFavorite) // Return the new state
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun deleteItem(itemId: String): ApiResult<Boolean> {
        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
        if (itemUuid == null) {
            return ApiResult.Error("Invalid item ID", errorType = ErrorType.NOT_FOUND)
        }

        return try {
            withIo {
                val client = getClient(server.url, server.accessToken)
                client.libraryApi.deleteItem(itemId = itemUuid)
                ApiResult.Success(true)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Checks if the currently authenticated user has administrator privileges
     * or permission to delete content.
     */
    private suspend fun hasAdminDeletePermission(server: JellyfinServer): ApiResult<Boolean> {
        val userId = server.userId ?: return ApiResult.Success(false)
        return try {
            withIo {
                val userUuid = parseUuid(userId, "user")
                val client = getClient(server.url, server.accessToken)
                val user = client.userApi.getCurrentUser().content
                ApiResult.Success(user.policy?.isAdministrator == true || user.policy?.enableContentDeletion == true)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Deletes an item only if the current user has administrator permissions.
     */
    suspend fun deleteItemAsAdmin(itemId: String): ApiResult<Boolean> {
        val server = authRepository.getCurrentServer()
        if (server?.accessToken == null || server.userId == null) {
            return ApiResult.Error("Not authenticated", errorType = ErrorType.AUTHENTICATION)
        }

        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
        if (itemUuid == null) {
            return ApiResult.Error("Invalid item ID", errorType = ErrorType.NOT_FOUND)
        }

        val adminPermissionResult = hasAdminDeletePermission(server)
        if (adminPermissionResult is ApiResult.Error) {
            return adminPermissionResult
        }
        if (adminPermissionResult is ApiResult.Success && !adminPermissionResult.data) {
            return ApiResult.Error("Administrator permissions required", errorType = ErrorType.FORBIDDEN)
        }

        return try {
            withIo {
                val client = getClient(server.url, server.accessToken)
                client.libraryApi.deleteItem(itemId = itemUuid)
                ApiResult.Success(true)
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

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
        audioChannels: Int = DEFAULT_MAX_AUDIO_CHANNELS,
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
    ): PlaybackInfoResponse {
        val server = validateServer()
        val client = sessionManager.getClientForUrl(server.url)

        // Create playbook info DTO with direct play enabled
        val userUuid = runCatching { UUID.fromString(server.userId ?: "") }.getOrNull()
            ?: throw IllegalStateException("Invalid user UUID: ${server.userId}")
        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid item UUID: $itemId")

        // Get device capabilities to create proper device profile
        val capabilities = deviceCapabilities.getDirectPlayCapabilities()
        Log.d("JellyfinRepository", "Device capabilities: maxResolution=${capabilities.maxResolution}, supports4K=${capabilities.supports4K}")
        val deviceProfile = JellyfinDeviceProfile.createAndroidDeviceProfile(capabilities)
        Log.d("JellyfinRepository", "DeviceProfile created with codecProfiles: ${deviceProfile.codecProfiles.size}")

        // Log the actual codec profiles being sent
        deviceProfile.codecProfiles.forEachIndexed { index, codecProfile ->
            Log.d("JellyfinRepository", "  CodecProfile[$index]: type=${codecProfile.type}, codec=${codecProfile.codec}, conditions=${codecProfile.conditions.size}")
            codecProfile.conditions.forEach { condition ->
                Log.d("JellyfinRepository", "    Condition: ${condition.property} ${condition.condition} ${condition.value}")
            }
        }

        // Set maxStreamingBitrate based on network quality to guide server transcoding decisions
        // This helps the server choose appropriate transcoding quality instead of using a very high default
        val maxBitrate = getNetworkBasedMaxBitrate()

        val playbackInfoDto = PlaybackInfoDto(
            userId = userUuid,
            maxStreamingBitrate = maxBitrate,
            startTimeTicks = null,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxAudioChannels = null,
            mediaSourceId = null,
            liveStreamId = null,
            deviceProfile = deviceProfile,
            enableDirectPlay = true,
            enableDirectStream = true,
            enableTranscoding = true,
            allowVideoStreamCopy = true,
            allowAudioStreamCopy = true,
            autoOpenLiveStream = null,
        )

        if (BuildConfig.DEBUG) {
            SecureLogger.d(
                "JellyfinRepository",
                "PlaybackInfo request for item $itemId: " +
                    "maxBitrate=${playbackInfoDto.maxStreamingBitrate} (${maxBitrate / 1_000_000}Mbps), " +
                    "directPlay=${playbackInfoDto.enableDirectPlay}, " +
                    "directStream=${playbackInfoDto.enableDirectStream}, " +
                    "transcode=${playbackInfoDto.enableTranscoding}",
            )
            Log.d("JellyfinRepository", "Network-based maxStreamingBitrate: ${maxBitrate / 1_000_000}Mbps")

            // Log DeviceProfile details
            val transcodingProfiles = deviceProfile.transcodingProfiles.orEmpty()
            Log.d("JellyfinRepository", "DeviceProfile: ${deviceProfile.name}, maxStreamBitrate=${deviceProfile.maxStreamingBitrate}, maxStaticBitrate=${deviceProfile.maxStaticBitrate}")

            // Log DirectPlayProfiles
            deviceProfile.directPlayProfiles.forEachIndexed { index, profile ->
                Log.d("JellyfinRepository", "  DirectPlayProfile[$index]: container=${profile.container}, type=${profile.type}, videoCodec=${profile.videoCodec}, audioCodec=${profile.audioCodec}")
            }
            transcodingProfiles.forEachIndexed { index, profile ->
                Log.d("JellyfinRepository", "  TranscodingProfile[$index]: codec=${profile.videoCodec}, container=${profile.container}, protocol=${profile.protocol}, conditions=${profile.conditions.orEmpty().size}")
                profile.conditions.orEmpty().forEach { condition ->
                    Log.d("JellyfinRepository", "    Condition: ${condition.property} ${condition.condition} ${condition.value}")
                }
            }
        }

        val response = withIo {
            client.mediaInfoApi.getPostedPlaybackInfo(
                itemId = itemUuid,
                data = playbackInfoDto,
            ).content
        }

        if (BuildConfig.DEBUG) {
            // Log the response from the server
            Log.d("JellyfinRepository", "Server response:")
            Log.d("JellyfinRepository", "  PlaySessionId: ${response.playSessionId}")
            Log.d("JellyfinRepository", "  MediaSources: ${response.mediaSources.size}")

            response.mediaSources.forEachIndexed { index, mediaSource ->
                Log.d("JellyfinRepository", "  MediaSource[$index]:")
                Log.d("JellyfinRepository", "    Name: ${mediaSource.name}")
                Log.d("JellyfinRepository", "    Container: ${mediaSource.container}")
                Log.d("JellyfinRepository", "    Size: ${mediaSource.size}")
                Log.d("JellyfinRepository", "    IsRemote: ${mediaSource.isRemote}")

                // Log video stream info
                mediaSource.mediaStreams?.filter { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }?.forEach { stream ->
                    Log.d("JellyfinRepository", "    Video Stream: ${stream.codec}, ${stream.width}x${stream.height}, bitrate=${stream.bitRate}")
                }

                // Log direct play and transcoding support
                Log.d("JellyfinRepository", "    SupportsDirectPlay: ${mediaSource.supportsDirectPlay}")
                Log.d("JellyfinRepository", "    SupportsDirectStream: ${mediaSource.supportsDirectStream}")
                Log.d("JellyfinRepository", "    SupportsTranscoding: ${mediaSource.supportsTranscoding}")

                // Log transcoding URL if present
                mediaSource.transcodingUrl?.let { url ->
                    Log.d("JellyfinRepository", "    TranscodingUrl: $url")
                    // Extract resolution from transcoding URL
                    val maxWidth = Regex("MaxWidth=(\\d+)").find(url)?.groupValues?.get(1)
                    val maxHeight = Regex("MaxHeight=(\\d+)").find(url)?.groupValues?.get(1)
                    Log.d("JellyfinRepository", "    Transcoding resolution: ${maxWidth}x$maxHeight")
                }
            }
            val sourceSummaries = response.mediaSources.orEmpty().map { source ->
                "id=${source.id}, " +
                    "directPlay=${source.supportsDirectPlay}, " +
                    "directStream=${source.supportsDirectStream}, " +
                    "transcode=${source.supportsTranscoding}, " +
                    "container=${source.container}, " +
                    "transcodingUrl=${!source.transcodingUrl.isNullOrBlank()}"
            }
            SecureLogger.d(
                "JellyfinRepository",
                "PlaybackInfo response: playSessionId=${response.playSessionId}, " +
                    "mediaSources=${sourceSummaries.size}",
            )
            if (sourceSummaries.isNotEmpty()) {
                SecureLogger.v("JellyfinRepository", "PlaybackInfo sources: ${sourceSummaries.joinToString(" | ")}")
            }
        }

        return response
    }

    /**
     * Get playback info specifically for Chromecast/Cast devices.
     * Uses device-specific profiles based on the Cast receiver's capabilities.
     * @param itemId The item ID to get playback info for
     * @param isShieldOrAndroidTV Whether the Cast receiver is a SHIELD/Android TV (more capable)
     */
    suspend fun getCastPlaybackInfo(
        itemId: String,
        isShieldOrAndroidTV: Boolean = false,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackInfoResponse {
        val server = validateServer()
        val client = sessionManager.getClientForUrl(server.url)

        val userUuid = runCatching { UUID.fromString(server.userId ?: "") }.getOrNull()
            ?: throw IllegalStateException("Invalid user UUID: ${server.userId}")
        val itemUuid = runCatching { UUID.fromString(itemId) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid item UUID: $itemId")

        // Use device-specific profile based on Cast receiver capabilities
        val deviceProfile = if (isShieldOrAndroidTV) {
            JellyfinDeviceProfile.createShieldCastDeviceProfile()
        } else {
            JellyfinDeviceProfile.createChromecastDeviceProfile()
        }

        // Use adaptive bitrate for Cast based on network quality, but capped
        // for reliability on the receiver side.
        val maxBitrate = if (isShieldOrAndroidTV) {
            minOf(getNetworkBasedMaxBitrate(), 60_000_000)
        } else {
            20_000_000
        }
        Log.d("JellyfinRepository", "Cast: Using ${if (isShieldOrAndroidTV) "SHIELD" else "Chromecast"} device profile with ${maxBitrate / 1_000_000}Mbps limit")

        // Always allow DirectStream and StreamCopy for all Cast devices.
        // Remuxing is low-overhead and supported by almost all Cast receivers.
        // DirectPlay is still restricted to SHIELD/Android TV for maximum compatibility.
        val enableDirectPlay = isShieldOrAndroidTV

        val playbackInfoDto = PlaybackInfoDto(
            userId = userUuid,
            maxStreamingBitrate = maxBitrate,
            startTimeTicks = null,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxAudioChannels = null,
            mediaSourceId = null,
            liveStreamId = null,
            deviceProfile = deviceProfile,
            enableDirectPlay = enableDirectPlay,
            enableDirectStream = true, // Always allow remuxing
            enableTranscoding = true, // Always allow transcoding as fallback
            allowVideoStreamCopy = true, // Always allow remuxing
            allowAudioStreamCopy = true, // Always allow remuxing
            autoOpenLiveStream = null,
        )

        if (BuildConfig.DEBUG) {
            Log.d(
                "JellyfinRepository",
                "Cast PlaybackInfo request for item $itemId: maxBitrate=${maxBitrate / 1_000_000}Mbps",
            )
        }

        val response = withIo {
            client.mediaInfoApi.getPostedPlaybackInfo(
                itemId = itemUuid,
                data = playbackInfoDto,
            ).content
        }

        if (BuildConfig.DEBUG) {
            Log.d("JellyfinRepository", "Cast PlaybackInfo response:")
            Log.d("JellyfinRepository", "  PlaySessionId: ${response.playSessionId}")
            Log.d("JellyfinRepository", "  MediaSources: ${response.mediaSources.size}")

            response.mediaSources.forEachIndexed { index, mediaSource ->
                Log.d("JellyfinRepository", "  Cast MediaSource[$index]:")
                Log.d("JellyfinRepository", "    Container: ${mediaSource.container}")
                Log.d("JellyfinRepository", "    SupportsTranscoding: ${mediaSource.supportsTranscoding}")
                mediaSource.transcodingUrl?.let { url ->
                    Log.d("JellyfinRepository", "    TranscodingUrl: $url")
                }
            }
        }

        return response
    }

    /**
     * Get maximum streaming bitrate based on current network quality.
     * This provides a realistic bitrate limit for the server to use when making transcoding decisions.
     * Returns higher values for better networks to allow direct play or high-quality transcoding.
     */
    private fun getNetworkBasedMaxBitrate(): Int {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return 25_000_000 // Default to 25 Mbps if network service unavailable

        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return when {
            // Ethernet: Best quality, allow high bitrates
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> {
                120_000_000 // 120 Mbps - excellent quality for direct play or 4K transcoding
            }
            // WiFi: Good quality, allow good bitrates
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                80_000_000 // 80 Mbps - very good quality for 1080p/4K
            }
            // Cellular: Medium quality, conservative bitrate
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                25_000_000 // 25 Mbps - good quality 1080p transcoding
            }
            // Unknown network: Low quality, very conservative
            else -> {
                10_000_000 // 10 Mbps - basic 720p/1080p transcoding
            }
        }
    }

    fun getCurrentServer(): JellyfinServer? = authRepository.getCurrentServer()

    fun isUserAuthenticated(): Boolean = authRepository.isUserAuthenticated()

    fun getDownloadUrl(itemId: String): String? =
        streamRepository.getDownloadUrl(itemId)

    /**
     * Get transcoding progress for active sessions on this device.
     *
     * @param deviceId The device ID to filter sessions by
     * @param jellyfinItemId Optional item ID to match specific transcoding session
     * @return TranscodingProgressInfo if an active transcoding session is found, null otherwise
     */
    suspend fun getTranscodingProgress(
        deviceId: String,
        jellyfinItemId: String? = null,
    ): TranscodingProgressInfo? {
        return try {
            val client = getCurrentAuthenticatedClient() ?: return null
            val response = withIo {
                client.sessionApi.getSessions(deviceId = deviceId)
            }
            val sessions = response.content

            val session = if (jellyfinItemId != null) {
                sessions.find { session ->
                    session.transcodingInfo != null &&
                        session.nowPlayingItem?.id?.toString() == jellyfinItemId
                }
            } else {
                sessions.find { it.transcodingInfo != null }
            }

            session?.transcodingInfo?.let { info ->
                TranscodingProgressInfo(
                    completionPercentage = info.completionPercentage ?: 0.0,
                    bitrate = info.bitrate,
                    width = info.width,
                    height = info.height,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Failed to get transcoding progress: ${e.message}")
            }
            null
        }
    }

    fun getDirectStreamUrl(itemId: String, container: String? = null): String? =
        streamRepository.getDirectStreamUrl(itemId, container)

    fun getBestStreamUrl(itemId: String, offlineManager: OfflineManager, container: String? = null): String? {
        // This would require BaseItemDto to determine offline availability
        // For now, fall back to regular stream URL
        return getStreamUrl(itemId)
    }

    /**
     * Determines if the repository should use offline mode for operations.
     *
     * @param offlineManager The offline manager to check connectivity
     * @return True if should operate in offline mode
     */
    fun shouldUseOfflineMode(offlineManager: OfflineManager): Boolean {
        return !offlineManager.isCurrentlyOnline()
    }

    /**
     * Gets offline-compatible error messages when operations fail.
     *
     * @param offlineManager The offline manager for connectivity info
     * @param operation The operation that failed
     * @return User-friendly error message with offline context
     */
    fun getOfflineContextualError(offlineManager: OfflineManager, operation: String): String {
        return if (!offlineManager.isCurrentlyOnline()) {
            offlineManager.getOfflineErrorMessage(operation)
        } else {
            "$operation failed. Please check your connection and try again."
        }
    }

    // ✅ PHASE 4: Utility methods replaced with centralized utilities
    private fun validateServer(): JellyfinServer = RepositoryUtils.validateServer(authRepository.getCurrentServer())
    private fun parseUuid(id: String, idType: String): UUID = RepositoryUtils.parseUuid(id, idType)
}

data class TranscodingProgressInfo(
    val completionPercentage: Double,
    val bitrate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
)
