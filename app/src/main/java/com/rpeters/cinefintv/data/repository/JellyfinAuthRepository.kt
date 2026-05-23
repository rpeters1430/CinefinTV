package com.rpeters.cinefintv.data.repository

import com.rpeters.cinefintv.utils.SecureLogger
import androidx.annotation.VisibleForTesting
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.SecureCredentialManager
import com.rpeters.cinefintv.data.model.QuickConnectResult
import com.rpeters.cinefintv.data.model.QuickConnectState
import com.rpeters.cinefintv.data.network.TokenProvider
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.data.utils.RepositoryUtils
import com.rpeters.cinefintv.utils.normalizeServerUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

import com.rpeters.cinefintv.data.common.DispatcherProvider

@Singleton
open class JellyfinAuthRepository @Inject constructor(
    private val jellyfin: Jellyfin,
    private val secureCredentialManager: SecureCredentialManager,
    private val dispatchers: DispatcherProvider,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) : TokenProvider {
    private val authMutex = Mutex()

    // Background scope for fire-and-forget tasks (e.g. session validation after restore)
    private val repositoryScope = CoroutineScope(SupervisorJob() + dispatchers.io)

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
        private const val RESTORE_STATE_TIMEOUT_MS = 5_000L
        private const val RESTORE_VALIDATION_TIMEOUT_MS = 5_000L

        /** HTTP status codes that indicate the access token has been definitively rejected. */
        private fun isTokenRejectedStatus(status: Int) = status == 401 || status == 403
    }

    private enum class RestoreValidationResult {
        VALID,
        UNREACHABLE,
        INVALID,
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
            SecureLogger.d(TAG, "Saving new token: ${if (token != null) "[PRESENT]" else "[NULL]"}")
        }
        _tokenState.update { token }
        // Server state is also updated in authenticateUser method
    }

    suspend fun testServerConnection(serverUrl: String): ApiResult<PublicSystemInfo> {
        return try {
            SecureLogger.d(TAG, "testServerConnection: Attempting to connect to server")
            val response = withContext(dispatchers.io) {
                val client = createApiClient(serverUrl)
                client.systemApi.getPublicSystemInfo()
            }
            SecureLogger.d(TAG, "testServerConnection: Successfully connected to server")
            ApiResult.Success(response.content)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SecureLogger.e(TAG, "testServerConnection: Error connecting to server", e)
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

            val response = withContext(dispatchers.io) {
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
            SecureLogger.e(TAG, "authenticateUser: Error authenticating", e)
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
            SecureLogger.d(TAG, "forceReAuthenticate: Forcing re-authentication with persisted credentials")
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
                SecureLogger.w(TAG, "reAuthenticate: No saved password found for user $username")
                return false
            }

            SecureLogger.d(TAG, "reAuthenticate: Found saved credentials for $serverUrl, attempting authentication")

            val result = authenticateUserInternal(serverUrl, username, password)
            return if (result is ApiResult.Success) {
                SecureLogger.d(TAG, "reAuthenticate: Successfully re-authenticated user $username")
                true
            } else {
                SecureLogger.w(TAG, "reAuthenticate: Failed to re-authenticate user $username")
                false
            }
        } catch (e: IOException) {
            SecureLogger.e(TAG, "reAuthenticate: I/O error during re-authentication", e)
            return false
        }
    }

    open fun getCurrentServer(): JellyfinServer? = _currentServer.value

    fun isUserAuthenticated(): Boolean = _currentServer.value?.accessToken != null

    fun isTokenMissing(): Boolean {
        return _currentServer.value?.accessToken.isNullOrBlank()
    }

    /**
     * We do not currently have server-issued expiry metadata for Jellyfin access tokens.
     * Until that exists, only treat missing tokens as refreshable via the proactive path.
     */
    fun shouldRefreshToken(): Boolean {
        return isTokenMissing()
    }

    @VisibleForTesting
    fun seedCurrentServer(server: JellyfinServer?) {
        _currentServer.update { server }
        _tokenState.update { server?.accessToken }
        _isConnected.update { server?.isConnected == true }
    }

    suspend fun tryRestoreSession(): Boolean {
        _isSessionRestored.update { null } // Mark as in-progress
        return try {
            val savedServer = try {
                withTimeout(RESTORE_STATE_TIMEOUT_MS) {
                    withContext(dispatchers.io) {
                        secureCredentialManager.loadServerState()
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // DataStore read took too long (e.g. slow Android Keystore on TV hardware).
                // Treat as no saved session so the app routes to login rather than hanging.
                SecureLogger.w(TAG, "tryRestoreSession: timed out loading server state, treating as no saved session")
                _isSessionRestored.update { false }
                return false
            }
            if (savedServer == null || savedServer.accessToken.isNullOrBlank() || savedServer.url.isBlank()) {
                _isSessionRestored.update { false }
                return false
            }

            seedCurrentServer(savedServer)
            val validationResult = try {
                withTimeout(RESTORE_VALIDATION_TIMEOUT_MS) {
                    validateOrRecoverRestoredSession(savedServer)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                SecureLogger.w(TAG, "tryRestoreSession: validation timed out, proceeding optimistically")
                RestoreValidationResult.UNREACHABLE
            }

            when (validationResult) {
                RestoreValidationResult.INVALID -> {
                    logout()
                    _isSessionRestored.update { false }
                    false
                }
                RestoreValidationResult.VALID -> {
                    SecureLogger.d(TAG, "tryRestoreSession: Restored validated session for ${savedServer.url}")
                    _isSessionRestored.update { true }
                    true
                }
                RestoreValidationResult.UNREACHABLE -> {
                    SecureLogger.d(TAG, "tryRestoreSession: Restored session for ${savedServer.url} without live validation")
                    _isSessionRestored.update { true }
                    repositoryScope.launch { validateRestoredSession(savedServer) }
                    true
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.w(TAG, "tryRestoreSession: failed to restore session", e)
            _isSessionRestored.update { false }
            false
        }
    }

    private suspend fun validateOrRecoverRestoredSession(server: JellyfinServer): RestoreValidationResult {
        return try {
            withContext(dispatchers.io) {
                val client = createApiClient(server.url, server.accessToken)
                client.systemApi.getPublicSystemInfo()
            }
            SecureLogger.d(TAG, "validateOrRecoverRestoredSession: token still valid")
            RestoreValidationResult.VALID
        } catch (e: InvalidStatusException) {
            if (!isTokenRejectedStatus(e.status)) {
                SecureLogger.w(TAG, "validateOrRecoverRestoredSession: non-auth status ${e.status}, proceeding optimistically")
                return RestoreValidationResult.UNREACHABLE
            }

            SecureLogger.w(TAG, "validateOrRecoverRestoredSession: token rejected (${e.status}), attempting re-authentication")
            if (forceReAuthenticate()) {
                SecureLogger.d(TAG, "validateOrRecoverRestoredSession: re-authentication succeeded")
                RestoreValidationResult.VALID
            } else {
                SecureLogger.w(TAG, "validateOrRecoverRestoredSession: re-authentication failed")
                RestoreValidationResult.INVALID
            }
        } catch (e: IOException) {
            SecureLogger.d(TAG, "validateOrRecoverRestoredSession: network unavailable, proceeding optimistically")
            RestoreValidationResult.UNREACHABLE
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.w(TAG, "validateOrRecoverRestoredSession: validation failed unexpectedly", e)
            RestoreValidationResult.UNREACHABLE
        }
    }

    /**
     * Validates the restored session against the live server in the background.
     * On a definitive 401/403 (token revoked), logs out and clears state.
     * On network errors, leaves the session intact — the user may be offline.
     *
     * Called from [repositoryScope] (Dispatchers.IO), so no withContext is needed.
     */
    private suspend fun validateRestoredSession(server: JellyfinServer) {
        try {
            withContext(dispatchers.io) {
                val client = createApiClient(server.url, server.accessToken)
                client.systemApi.getPublicSystemInfo()
            }
            SecureLogger.d(TAG, "validateRestoredSession: token still valid")
        } catch (e: InvalidStatusException) {
            if (isTokenRejectedStatus(e.status)) {
                SecureLogger.w(TAG, "validateRestoredSession: token rejected (${e.status}), logging out")
                logout()
                // logout() clears connection/server state but does not update _isSessionRestored;
                // explicitly reset it so downstream observers (e.g. MainActivity) redirect to login.
                _isSessionRestored.update { false }
            }
            // Other HTTP errors (500, 503) — server is up but broken; leave session intact
        } catch (e: IOException) {
            // Network unreachable — leave session intact; Home will show connection errors
            SecureLogger.d(TAG, "validateRestoredSession: network unavailable, proceeding optimistically")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        }
    }

    /**
     * Clears all authentication state and persisted credentials.
     *
     * Note: intentionally does **not** update [_isSessionRestored]. Callers that need to
     * redirect to the login screen (e.g. [validateRestoredSession] on token rejection) must
     * update [_isSessionRestored] themselves after calling this function.
     */
    suspend fun logout() {
        authMutex.withLock {
            _isAuthenticating.update { false }
            val server = _currentServer.value
            if (server != null && server.username != null) {
                try {
                    secureCredentialManager.clearPassword(server.url, server.username)
                    SecureLogger.d(TAG, "logout: Cleared saved credentials for user ${server.username}")
                } catch (e: IOException) {
                    SecureLogger.w(TAG, "logout: I/O error clearing credentials", e)
                }
            }

            // Clear token state
            saveNewToken(null)
            _currentServer.update { null }
            _isConnected.update { false }
            try {
                secureCredentialManager.clearServerState()
            } catch (e: Exception) {
                SecureLogger.w(TAG, "logout: failed to clear server state", e)
            }
            SecureLogger.d(TAG, "logout: User logged out successfully")
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

    /**
     * Ensures the current session is usable before a screen fans out multiple requests.
     * If the token was rejected, attempt a single re-authentication first.
     * Network failures are treated as non-fatal so offline flows can continue.
     */
    suspend fun ensureSessionReady(): Boolean {
        val server = _currentServer.value ?: return false

        return try {
            withContext(dispatchers.io) {
                createApiClient(server.url, server.accessToken).systemApi.getPublicSystemInfo()
            }
            true
        } catch (e: InvalidStatusException) {
            if (isTokenRejectedStatus(e.status)) {
                SecureLogger.w(TAG, "ensureSessionReady: token rejected (${e.status}), attempting re-authentication")
                forceReAuthenticate()
            } else if (e.status >= 500) {
                SecureLogger.e(TAG, "ensureSessionReady: server error ${e.status}, session not ready")
                false
            } else {
                SecureLogger.w(TAG, "ensureSessionReady: non-auth status ${e.status}, proceeding")
                true
            }
        } catch (e: IOException) {
            SecureLogger.d(TAG, "ensureSessionReady: network unavailable, proceeding")
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.w(TAG, "ensureSessionReady: unexpected validation failure, proceeding", e)
            true
        }
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
        saveNewToken(authResult.accessToken)
        _isConnected.update { true }
        try {
            secureCredentialManager.saveServerState(server)
        } catch (e: Exception) {
            SecureLogger.w(TAG, "persistAuthenticationState: failed to persist server state", e)
        }
        try {
            secureCredentialManager.saveProfile(server)
        } catch (e: Exception) {
            SecureLogger.w(TAG, "persistAuthenticationState: failed to save profile entry", e)
        }
    }

    suspend fun getSavedProfiles(): List<JellyfinServer> = withContext(dispatchers.io) {
        secureCredentialManager.loadProfiles()
    }

    suspend fun switchToProfile(server: JellyfinServer): Boolean {
        if (server.accessToken.isNullOrBlank()) return false
        return authMutex.withLock {
            seedCurrentServer(server)
            try {
                secureCredentialManager.saveServerState(server)
            } catch (e: Exception) {
                SecureLogger.w(TAG, "switchToProfile: failed to persist state", e)
            }
            true
        }
    }

    suspend fun removeProfile(server: JellyfinServer) = withContext(dispatchers.io) {
        val userId = server.userId ?: return@withContext
        secureCredentialManager.removeProfile(userId, server.url)
    }

    suspend fun initiateQuickConnect(serverUrl: String): ApiResult<QuickConnectResult> {
        return try {
            val response = withContext(dispatchers.io) {
                val client = createApiClient(serverUrl)
                client.quickConnectApi.initiateQuickConnect()
            }
            ApiResult.Success(response.content.toDomainQuickConnectResult())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SecureLogger.e(TAG, "initiateQuickConnect: Error", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error(throwableMessageOrFallback("Quick Connect error", e), e, errorType)
        }
    }

    suspend fun isQuickConnectEnabled(serverUrl: String): ApiResult<Boolean> {
        return try {
            val response = withContext(dispatchers.io) {
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
                SecureLogger.e(TAG, "isQuickConnectEnabled: Failed with status ${e.status}", e)
                val errorType = RepositoryUtils.getErrorType(e)
                ApiResult.Error(throwableMessageOrFallback("Failed to check Quick Connect availability", e), e, errorType)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SecureLogger.e(TAG, "isQuickConnectEnabled: Error", e)
            val errorType = RepositoryUtils.getErrorType(e)
            ApiResult.Error(throwableMessageOrFallback("Failed to check Quick Connect availability", e), e, errorType)
        }
    }

    suspend fun getQuickConnectState(serverUrl: String, secret: String): ApiResult<QuickConnectState> {
        return try {
            val response = withContext(dispatchers.io) {
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
                    SecureLogger.e(TAG, "getQuickConnectState: Failed with unexpected status", e)
                    val errorType = RepositoryUtils.getErrorType(e)
                    ApiResult.Error(throwableMessageOrFallback("Failed to check Quick Connect state", e), e, errorType)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SecureLogger.e(TAG, "getQuickConnectState: Error", e)
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
                val response = withContext(dispatchers.io) {
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
                SecureLogger.e(TAG, "authenticateWithQuickConnect: Server returned error status", e)
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
