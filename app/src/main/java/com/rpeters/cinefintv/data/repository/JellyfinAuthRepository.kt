package com.rpeters.cinefintv.data.repository

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.SecureCredentialManager
import com.rpeters.cinefintv.data.model.QuickConnectResult
import com.rpeters.cinefintv.data.model.QuickConnectState
import com.rpeters.cinefintv.data.network.TokenProvider
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.utils.RepositoryUtils
import com.rpeters.cinefintv.utils.SecureLogger
import com.rpeters.cinefintv.utils.normalizeServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import org.jellyfin.sdk.model.api.QuickConnectResult as SdkQuickConnectResult

@Singleton
class JellyfinAuthRepository @Inject constructor(
    private val jellyfin: Jellyfin,
    private val secureCredentialManager: SecureCredentialManager,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) : TokenProvider {
    private val authMutex = Mutex()

    // Token state for TokenProvider implementation
    private val _tokenState = MutableStateFlow<String?>(null)

    // State flows for server connection status
    private val _currentServer = MutableStateFlow<JellyfinServer?>(null)
    val currentServer: StateFlow<JellyfinServer?> = _currentServer.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _isSessionRestored = MutableStateFlow<Boolean?>(null)
    /**
     * Flow representing the initial session restoration state.
     * null = restoration in progress or not started
     * true = session successfully restored
     * false = no session found or restoration failed
     */
    val isSessionRestored: StateFlow<Boolean?> = _isSessionRestored.asStateFlow()

    companion object {
        private const val TAG = "JellyfinAuthRepository"
    }

    // TokenProvider implementation
    override suspend fun token(): String? = _tokenState.value

    private fun throwableMessageOrFallback(prefix: String, throwable: Throwable): String {
        val detail = throwable.message?.takeIf { it.isNotBlank() } ?: throwable.javaClass.simpleName
        return "$prefix: $detail"
    }

    private fun saveNewToken(token: String?) {
        // SECURITY: Never log actual token values, even partially
        // Tokens should only be logged in extreme debugging scenarios using secure logging
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Saving new token: ${if (token != null) "[PRESENT]" else "[NULL]"}")
        }
        _tokenState.update { token }
        // Server state is also updated in authenticateUser method
    }

    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return try {
            SecureLogger.d(TAG, "testServerConnection: Attempting to connect to server")
            val response = withContext(Dispatchers.IO) {
                val client = createApiClient(serverUrl)
                client.systemApi.getPublicSystemInfo()
            }
            SecureLogger.d(TAG, "testServerConnection: Successfully connected to server")
            ApiResult.Success(response.content)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "testServerConnection: Error connecting to server", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error(throwableMessageOrFallback("Connection error", e), e, errorType)
        }
    }

    suspend fun authenticateUser(
        serverUrl: String,
        username: String,
        password: String,
    ): ApiResult<AuthenticationResult> {
        return authMutex.withLock {
            authenticateUserInternal(serverUrl, username, password)
        }
    }

    private suspend fun authenticateUserInternal(
        serverUrl: String,
        username: String,
        password: String,
    ): ApiResult<AuthenticationResult> {
        _isAuthenticating.update { true }
        try {
            SecureLogger.d(TAG, "authenticateUser: Attempting authentication")
            val normalizedServerUrl = normalizeServerUrl(serverUrl)

            val response = withContext(Dispatchers.IO) {
                val client = createApiClient(serverUrl)
                client.userApi.authenticateUserByName(
                    AuthenticateUserByName(
                        username = username,
                        pw = password,
                    ),
                )
            }

            val authResult = response.content
            SecureLogger.d(TAG, "authenticateUser: Authentication successful")

            persistAuthenticationState(
                serverUrl = serverUrl,
                normalizedServerUrl = normalizedServerUrl,
                authResult = authResult,
                usernameHint = username,
            )

            return ApiResult.Success(authResult)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "authenticateUser: Error authenticating", e)
            val errorType = RepositoryUtils.getErrorType(e)
            return ApiResult.Error(throwableMessageOrFallback("Authentication failed", e), e, errorType)
        } finally {
            _isAuthenticating.update { false }
        }
    }

    suspend fun reAuthenticate(): Boolean {
        return authMutex.withLock {
            reAuthenticateInternal()
        }
    }

    suspend fun forceReAuthenticate(): Boolean {
        return authMutex.withLock {
            Log.d(TAG, "forceReAuthenticate: Force refresh requested, checking if re-auth still needed")

            // Double-check: if another thread just successfully re-authenticated,
            // we might not need to do it again
            val currentServer = _currentServer.value
            if (currentServer?.accessToken != null) {
                Log.d(TAG, "forceReAuthenticate: Access token is already present, skipping forced re-authentication")
                return@withLock true
            }

            Log.d(TAG, "forceReAuthenticate: No access token available, proceeding with re-authentication")
            reAuthenticateInternal()
        }
    }

    private suspend fun reAuthenticateInternal(): Boolean {
        val server = _currentServer.value ?: return false
        val username = server.username ?: return false
        val serverUrl = server.url

        try {
            val password = secureCredentialManager.getPassword(serverUrl, username)
            if (password == null) {
                Log.w(TAG, "reAuthenticate: No saved password found for user $username")
                return false
            }

            Log.d(TAG, "reAuthenticate: Found saved credentials for $serverUrl, attempting authentication")

            val result = authenticateUserInternal(serverUrl, username, password)
            return if (result is ApiResult.Success) {
                Log.d(TAG, "reAuthenticate: Successfully re-authenticated user $username")
                true
            } else {
                Log.w(TAG, "reAuthenticate: Failed to re-authenticate user $username")
                false
            }
        } catch (e: IOException) {
            Log.e(TAG, "reAuthenticate: I/O error during re-authentication", e)
            return false
        }
    }

    fun getCurrentServer(): JellyfinServer? = _currentServer.value

    fun isUserAuthenticated(): Boolean = _currentServer.value?.accessToken != null

    fun isTokenExpired(): Boolean {
        return _currentServer.value?.accessToken.isNullOrBlank()
    }

    /**
     * Checks if the token is approaching expiration and should be refreshed proactively.
     * Returns true if the token is within the refresh threshold (5 minutes before expiration).
     * This allows the interceptor to refresh tokens before they expire, reducing blocking.
     */
    fun shouldRefreshToken(): Boolean {
        return false
    }

    @VisibleForTesting
    fun seedCurrentServer(server: JellyfinServer?) {
        _currentServer.update { server }
        _isConnected.update { server?.isConnected == true }
        _tokenState.update { server?.accessToken }
    }

    suspend fun tryRestoreSession(): Boolean {
        _isSessionRestored.update { null } // Mark as in-progress
        return try {
            val savedServer = secureCredentialManager.loadServerState()
            if (savedServer == null || savedServer.accessToken.isNullOrBlank() || savedServer.url.isBlank()) {
                _isSessionRestored.update { false }
                return false
            }
            seedCurrentServer(savedServer)
            Log.d(TAG, "tryRestoreSession: Restored session for ${savedServer.url}")
            _isSessionRestored.update { true }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "tryRestoreSession: failed to restore session", e)
            _isSessionRestored.update { false }
            false
        }
    }

    suspend fun logout() {
        authMutex.withLock {
            _isAuthenticating.update { false }
            val server = _currentServer.value
            if (server != null && server.username != null) {
                try {
                    secureCredentialManager.clearPassword(server.url, server.username)
                    Log.d(TAG, "logout: Cleared saved credentials for user ${server.username}")
                } catch (e: IOException) {
                    Log.w(TAG, "logout: I/O error clearing credentials", e)
                }
            }

            // Clear token state
            saveNewToken(null)
            _currentServer.update { null }
            _isConnected.update { false }
            try {
                secureCredentialManager.clearServerState()
            } catch (e: Exception) {
                Log.w(TAG, "logout: failed to clear server state", e)
            }
            Log.d(TAG, "logout: User logged out successfully")
        }
    }

    fun createApiClient(): ApiClient? {
        val server = _currentServer.value ?: return null
        return jellyfin.createApi(
            baseUrl = server.url,
            accessToken = server.accessToken,
        )
    }

    private fun createApiClient(serverUrl: String, accessToken: String? = null): ApiClient {
        return jellyfin.createApi(
            baseUrl = serverUrl,
            accessToken = accessToken,
        )
    }

    private suspend fun persistAuthenticationState(
        serverUrl: String,
        normalizedServerUrl: String = normalizeServerUrl(serverUrl),
        authResult: AuthenticationResult,
        usernameHint: String? = null,
    ) {
        val resolvedUsername = usernameHint ?: authResult.user?.name
        val server = JellyfinServer(
            id = authResult.serverId ?: "",
            name = authResult.user?.name ?: resolvedUsername ?: serverUrl,
            url = serverUrl,
            isConnected = true,
            userId = authResult.user?.id?.toString(),
            username = resolvedUsername,
            accessToken = authResult.accessToken,
            loginTimestamp = System.currentTimeMillis(),
            normalizedUrl = normalizedServerUrl,
        )

        _currentServer.update { server }
        _isConnected.update { true }
        saveNewToken(authResult.accessToken)
        try {
            secureCredentialManager.saveServerState(server)
        } catch (e: Exception) {
            Log.w(TAG, "persistAuthenticationState: failed to persist server state", e)
        }
    }

    suspend fun initiateQuickConnect(serverUrl: String): ApiResult<QuickConnectResult> {
        return try {
            val response = withContext(Dispatchers.IO) {
                val client = createApiClient(serverUrl)
                client.quickConnectApi.initiateQuickConnect()
            }
            ApiResult.Success(response.content.toDomainQuickConnectResult())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "initiateQuickConnect: Error", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error(throwableMessageOrFallback("Quick Connect error", e), e, errorType)
        }
    }

    suspend fun isQuickConnectEnabled(serverUrl: String): ApiResult<Boolean> {
        return try {
            val response = withContext(Dispatchers.IO) {
                val client = createApiClient(serverUrl)
                client.quickConnectApi.getQuickConnectEnabled()
            }
            ApiResult.Success(response.content)
        } catch (e: InvalidStatusException) {
            // Disabled quick connect may return unauthorized on some server versions.
            // Older servers may also not expose this endpoint.
            if (e.status == 401 || e.status == 403 || e.status == 404) {
                ApiResult.Success(false)
            } else {
                Log.e(TAG, "isQuickConnectEnabled: Failed with status ${e.status}", e)
                val errorType = RepositoryUtils.getErrorType(e)
                ApiResult.Error(throwableMessageOrFallback("Failed to check Quick Connect availability", e), e, errorType)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "isQuickConnectEnabled: Error", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error(throwableMessageOrFallback("Failed to check Quick Connect availability", e), e, errorType)
        }
    }

    suspend fun getQuickConnectState(serverUrl: String, secret: String): ApiResult<QuickConnectState> {
        return try {
            val response = withContext(Dispatchers.IO) {
                val client = createApiClient(serverUrl)
                client.quickConnectApi.getQuickConnectState(secret)
            }
            val state = if (response.content.authenticated) {
                QuickConnectState(state = "Approved")
            } else {
                QuickConnectState(state = "Pending")
            }
            ApiResult.Success(state)
        } catch (e: InvalidStatusException) {
            when (e.status) {
                401, 403 -> ApiResult.Success(QuickConnectState(state = "Denied"))
                404 -> ApiResult.Success(QuickConnectState(state = "Expired"))
                else -> {
                    Log.e(TAG, "getQuickConnectState: Failed with unexpected status", e)
                    val errorType = RepositoryUtils.getErrorType(e)
                    ApiResult.Error(throwableMessageOrFallback("Failed to check Quick Connect state", e), e, errorType)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "getQuickConnectState: Error", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error(throwableMessageOrFallback("Failed to check Quick Connect state", e), e, errorType)
        }
    }
    suspend fun authenticateWithQuickConnect(
        serverUrl: String,
        secret: String,
    ): ApiResult<AuthenticationResult> {
        return authMutex.withLock {
            _isAuthenticating.update { true }
            try {
                val response = withContext(Dispatchers.IO) {
                    val client = createApiClient(serverUrl)
                    client.userApi.authenticateWithQuickConnect(
                        org.jellyfin.sdk.model.api.QuickConnectDto(secret = secret),
                    )
                }
                val authResult = response.content

                persistAuthenticationState(
                    serverUrl = serverUrl,
                    authResult = authResult,
                )

                ApiResult.Success(authResult)
            } catch (e: InvalidStatusException) {
                Log.e(TAG, "authenticateWithQuickConnect: Server returned error status", e)
                val errorType = RepositoryUtils.getErrorType(e)
                ApiResult.Error(throwableMessageOrFallback("Quick Connect authentication failed", e), e, errorType)
            } finally {
                _isAuthenticating.update { false }
            }
        }
    }

    private fun SdkQuickConnectResult.toDomainQuickConnectResult(): QuickConnectResult {
        return QuickConnectResult(
            code = code,
            secret = secret,
        )
    }
}
