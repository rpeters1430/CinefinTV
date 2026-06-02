package com.rpeters.cinefintv.data.repository

import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.SecureCredentialManager
import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import com.rpeters.cinefintv.testutil.DeterministicDispatcherProvider
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jellyfin.sdk.Jellyfin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntroSkipperRepositoryTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val dispatchers by lazy { DeterministicDispatcherProvider(mainDispatcherRule.dispatcher) }

    private val fakeServer = JellyfinServer(
        id = "server-1",
        name = "Test Server",
        url = "http://localhost:8096",
        accessToken = "test-token",
        userId = "user-1",
    )

    private lateinit var authRepository: JellyfinAuthRepository
    private lateinit var sessionManager: JellyfinSessionManager
    private lateinit var cache: JellyfinCache

    @Before
    fun setUp() {
        authRepository = JellyfinAuthRepository(
            jellyfin = mockk<Jellyfin>(relaxed = true),
            secureCredentialManager = mockk<SecureCredentialManager>(relaxed = true),
            dispatchers = dispatchers,
        )
        authRepository.seedCurrentServer(fakeServer)
        sessionManager = mockk(relaxed = true)
        cache = mockk(relaxed = true)
    }

    private fun fakeResponse(body: String, code: Int = 200): Response =
        Response.Builder()
            .request(Request.Builder().url("http://localhost:8096/Episode/item-1/IntroTimestamps").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody())
            .build()

    private fun buildRepo(responseProvider: (Request) -> Response): IntroSkipperRepository =
        object : IntroSkipperRepository(
            authRepository, sessionManager, cache, dispatchers,
            mockk<OkHttpClient>(relaxed = true),
        ) {
            override fun executeCall(request: Request): Response = responseProvider(request)
        }

    private fun buildRepoThrowing(exception: Exception): IntroSkipperRepository =
        object : IntroSkipperRepository(
            authRepository, sessionManager, cache, dispatchers,
            mockk<OkHttpClient>(relaxed = true),
        ) {
            override fun executeCall(request: Request): Response = throw exception
        }

    @Test
    fun getSegments_parsesIntroAndCredits() = runTest {
        val json = """{"Valid":true,"Introduction":{"Start":15.5,"End":75.2},"Credits":{"Start":1320.0,"End":1380.0}}"""
        val result = buildRepo { fakeResponse(json) }.getSegments("item-1")

        assertEquals(15_500L, result?.intro?.startMs)
        assertEquals(75_200L, result?.intro?.endMs)
        assertEquals(1_320_000L, result?.credits?.startMs)
        assertEquals(1_380_000L, result?.credits?.endMs)
    }

    @Test
    fun getSegments_returnsNull_whenValidFalse() = runTest {
        val json = """{"Valid":false}"""
        assertNull(buildRepo { fakeResponse(json) }.getSegments("item-1"))
    }

    @Test
    fun getSegments_returnsNull_onHttpError() = runTest {
        assertNull(buildRepo { fakeResponse("", code = 404) }.getSegments("item-1"))
    }

    @Test
    fun getSegments_returnsNull_onException() = runTest {
        assertNull(buildRepoThrowing(RuntimeException("network error")).getSegments("item-1"))
    }

    @Test
    fun getSegments_handlesNullCredits() = runTest {
        val json = """{"Valid":true,"Introduction":{"Start":10.0,"End":60.0}}"""
        val result = buildRepo { fakeResponse(json) }.getSegments("item-1")
        assertEquals(10_000L, result?.intro?.startMs)
        assertNull(result?.credits)
    }
}
