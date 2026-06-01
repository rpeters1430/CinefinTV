package com.rpeters.cinefintv.data.repository

import android.content.Context
import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.SecureCredentialManager
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.testutil.DeterministicDispatcherProvider
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfinOptions
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.ApiClientFactory
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.RawResponse
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.api.sockets.SocketConnectionFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinAuthRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val json = Json {
        encodeDefaults = true
    }

    private val dispatchers = DeterministicDispatcherProvider(mainDispatcherRule.dispatcher)

    @Test
    fun tryRestoreSession_whenTokenStillValid_restoresExistingSession() = runTest {
        val secureCredentialManager = mockk<SecureCredentialManager>(relaxed = true)
        val savedServer = savedServer(accessToken = "valid-token")
        val validationClient = fakeApiClient { method, path, _, _, _ ->
            assertEquals(HttpMethod.GET, method)
            assertEquals("/System/Info/Public", path)
            rawResponse(PublicSystemInfo(id = "server-id", serverName = "Server"))
        }
        val jellyfin = jellyfinForClients(savedServer.url to mapOf("valid-token" to validationClient))

        coEvery { secureCredentialManager.loadServerState() } returns savedServer

        val repository = JellyfinAuthRepository(jellyfin, secureCredentialManager, dispatchers)

        val restored = repository.tryRestoreSession()

        assertTrue(restored == true)
        assertEquals(true, repository.isSessionRestored.value)
        assertEquals("valid-token", repository.currentServer.value?.accessToken)
        assertTrue(repository.isConnected.value)
    }

    @Test
    fun forceReAuthenticate_whenAuthSucceeds_updatesCurrentServerToken() = runTest {
        val secureCredentialManager = mockk<SecureCredentialManager>(relaxed = true)
        val savedServer = savedServer(accessToken = "stale-token")
        val refreshedAuth = authenticationResult(accessToken = "fresh-token")
        val authClient = fakeApiClient { method, path, _, _, requestBody ->
            assertTrue(method == HttpMethod.POST)
            assertTrue(path.contains("AuthenticateByName"))
            val authRequest = requestBody as AuthenticateUserByName
            assertEquals(savedServer.username, authRequest.username)
            assertEquals("password123", authRequest.pw)
            rawResponse(refreshedAuth)
        }
        val jellyfin = jellyfinForClients(
            savedServer.url to mapOf(null to authClient),
        )

        coEvery { secureCredentialManager.getPassword(savedServer.url, savedServer.username!!) } returns "password123"
        coEvery { secureCredentialManager.saveServerState(any()) } just runs

        val repository = JellyfinAuthRepository(jellyfin, secureCredentialManager, dispatchers)
        repository.seedCurrentServer(savedServer)

        val success = repository.forceReAuthenticate()

        assertTrue(success)
        assertEquals("fresh-token", repository.currentServer.value?.accessToken)
        assertEquals(savedServer.username, repository.currentServer.value?.username)
        assertTrue(repository.isConnected.value)
    }

    @Test
    fun authenticateUser_whenAuthSucceeds_persistsPasswordAndServerState() = runTest {
        val secureCredentialManager = mockk<SecureCredentialManager>(relaxed = true)
        val authResult = authenticationResult(accessToken = "fresh-token")
        val authClient = fakeApiClient { method, path, _, _, requestBody ->
            assertEquals(HttpMethod.POST, method)
            assertTrue(path.contains("AuthenticateByName"))
            val authRequest = requestBody as AuthenticateUserByName
            assertEquals("demo", authRequest.username)
            assertEquals("password123", authRequest.pw)
            rawResponse(authResult)
        }
        val jellyfin = jellyfinForClients(
            "http://localhost:8096" to mapOf(null to authClient),
        )

        val repository = JellyfinAuthRepository(jellyfin, secureCredentialManager, dispatchers)

        val result = repository.authenticateUser("http://localhost:8096", "demo", "password123")

        assertTrue(result is ApiResult.Success)
        assertEquals("fresh-token", repository.currentServer.value?.accessToken)
        assertTrue(repository.isConnected.value)
        coVerify(exactly = 1) {
            secureCredentialManager.savePassword("http://localhost:8096", "demo", "password123")
        }
        coVerify(exactly = 1) { secureCredentialManager.saveServerState(any()) }
        coVerify(exactly = 1) { secureCredentialManager.saveProfile(any()) }
    }

    @Test
    fun ensureSessionReady_whenTokenRejected_reauthenticatesBeforeReturning() = runTest {
        val secureCredentialManager = mockk<SecureCredentialManager>(relaxed = true)
        val savedServer = savedServer(accessToken = "stale-token")
        val refreshedAuth = authenticationResult(accessToken = "fresh-token")
        val requests = mutableListOf<Pair<String?, String?>>()
        val validationClient = fakeApiClient { method, path, _, _, _ ->
            assertEquals(HttpMethod.GET, method)
            assertEquals("/System/Info/Public", path)
            throw InvalidStatusException(401)
        }
        val authClient = fakeApiClient { method, path, _, _, requestBody ->
            assertTrue(method == HttpMethod.POST)
            assertTrue(path.contains("AuthenticateByName"))
            val authRequest = requestBody as AuthenticateUserByName
            assertEquals(savedServer.username, authRequest.username)
            assertEquals("password123", authRequest.pw)
            rawResponse(refreshedAuth)
        }
        val jellyfin = jellyfinForClients(
            savedServer.url to mapOf(
                "stale-token" to validationClient,
                null to authClient,
            ),
            requests = requests,
        )

        coEvery { secureCredentialManager.getPassword(savedServer.url, savedServer.username!!) } returns "password123"
        coEvery { secureCredentialManager.saveServerState(any()) } just runs

        val repository = JellyfinAuthRepository(jellyfin, secureCredentialManager, dispatchers)
        repository.seedCurrentServer(savedServer)

        val ready = repository.ensureSessionReady()

        assertTrue(ready)
        assertEquals(listOf(savedServer.url to "stale-token", savedServer.url to null), requests)
        assertEquals("fresh-token", repository.currentServer.value?.accessToken)
    }

    @Test
    fun tryRestoreSession_whenTokenRejectedAndReauthFails_clearsSession() = runTest {
        val secureCredentialManager = mockk<SecureCredentialManager>(relaxed = true)
        val savedServer = savedServer(accessToken = "stale-token")
        val validationClient = fakeApiClient { method, path, _, _, _ ->
            assertEquals(HttpMethod.GET, method)
            assertEquals("/System/Info/Public", path)
            throw InvalidStatusException(401)
        }
        val jellyfin = jellyfinForClients(savedServer.url to mapOf("stale-token" to validationClient))

        coEvery { secureCredentialManager.loadServerState() } returns savedServer
        coEvery { secureCredentialManager.getPassword(savedServer.url, savedServer.username!!) } returns null
        coEvery { secureCredentialManager.clearPassword(savedServer.url, savedServer.username!!) } just runs
        coEvery { secureCredentialManager.clearServerState() } just runs

        val repository = JellyfinAuthRepository(jellyfin, secureCredentialManager, dispatchers)

        val restored = repository.tryRestoreSession()

        assertFalse(restored)
        assertEquals(false, repository.isSessionRestored.value)
        assertNull(repository.currentServer.value)
        assertFalse(repository.isConnected.value)
        coVerify(exactly = 1) { secureCredentialManager.clearPassword(savedServer.url, savedServer.username!!) }
        coVerify(exactly = 1) { secureCredentialManager.clearServerState() }
    }

    private fun savedServer(accessToken: String) = JellyfinServer(
        id = "server-id",
        name = "Server",
        url = "http://localhost:8096",
        isConnected = true,
        userId = UUID.randomUUID().toString(),
        username = "demo",
        accessToken = accessToken,
    )

    private fun authenticationResult(accessToken: String) = AuthenticationResult(
        user = UserDto(
            name = "demo",
            id = UUID.randomUUID(),
            hasPassword = true,
            hasConfiguredPassword = true,
            hasConfiguredEasyPassword = false,
        ),
        sessionInfo = null,
        accessToken = accessToken,
        serverId = "server-id",
    )

    private fun rawResponse(content: Any): RawResponse {
        val body = when (content) {
            is PublicSystemInfo -> json.encodeToString(content).encodeToByteArray()
            is AuthenticationResult -> json.encodeToString(content).encodeToByteArray()
            else -> error("Unsupported response content type: ${content::class.java.name}")
        }
        return RawResponse(body = body, status = 200, headers = emptyMap())
    }

    private fun jellyfinForClients(
        vararg mappings: Pair<String, Map<String?, ApiClient>>,
        requests: MutableList<Pair<String?, String?>>? = null,
    ): Jellyfin {
        val clientsByServer = mappings.toMap()
        return Jellyfin(
            createJellyfinOptions {
                context = mockk<Context>(relaxed = true)
                clientInfo = mockk<ClientInfo>(relaxed = true)
                deviceInfo = mockk<DeviceInfo>(relaxed = true)
                socketConnectionFactory = mockk<SocketConnectionFactory>(relaxed = true)
                apiClientFactory = ApiClientFactory { baseUrl, accessToken, _, _, _, _ ->
                    requests?.add(baseUrl to accessToken)
                    clientsByServer[baseUrl]?.get(accessToken)
                        ?: error("Unexpected ApiClient request for baseUrl=$baseUrl token=$accessToken")
                }
            }
        )
    }

    private fun fakeApiClient(
        handler: suspend (
            method: HttpMethod,
            pathTemplate: String,
            pathParameters: Map<String, Any?>,
            queryParameters: Map<String, Any?>,
            requestBody: Any?,
        ) -> RawResponse,
    ): ApiClient = object : ApiClient() {
        override val baseUrl: String? = "http://localhost:8096"
        override val accessToken: String? = null
        override val clientInfo: ClientInfo = mockk(relaxed = true)
        override val deviceInfo: DeviceInfo = mockk(relaxed = true)
        override val httpClientOptions: HttpClientOptions = mockk(relaxed = true)
        override val webSocket: SocketApi = mockk(relaxed = true)

        override fun update(
            baseUrl: String?,
            accessToken: String?,
            clientInfo: ClientInfo,
            deviceInfo: DeviceInfo,
        ) = Unit

        override suspend fun request(
            method: HttpMethod,
            pathTemplate: String,
            pathParameters: Map<String, Any?>,
            queryParameters: Map<String, Any?>,
            requestBody: Any?,
        ): RawResponse = handler(method, pathTemplate, pathParameters, queryParameters, requestBody)
    }
}
