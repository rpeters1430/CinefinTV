package com.rpeters.cinefintv.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rpeters.cinefintv.testutil.DeterministicDispatcherProvider
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
class SecureCredentialManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val dispatchers = DeterministicDispatcherProvider(mainDispatcherRule.dispatcher)
    private lateinit var context: Context
    private lateinit var credentialManager: SecureCredentialManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        credentialManager = SecureCredentialManager(context, dispatchers)
    }

    @Test
    fun testEncryptDecrypt_softwareFallback() = runTest {
        // Since Keystore might not be fully functional in the Robolectric unit test environment
        // without proper shadows or setups, it will naturally fall back to software encryption.
        // We can test if the software fallback works correctly.
        val originalText = "superSecretPassword123"
        
        credentialManager.savePassword("http://localhost:8096", "testuser", originalText)
        
        val retrievedPassword = credentialManager.getPassword("http://localhost:8096", "testuser")
        assertEquals(originalText, retrievedPassword)
    }

    @Test
    fun testSaveLoadServerState() = runTest {
        val server = JellyfinServer(
            id = "server-id-123",
            name = "Test Server",
            url = "http://localhost:8096",
            isConnected = true,
            userId = "user-id-456",
            username = "testuser",
            accessToken = "access-token-789",
            loginTimestamp = 123456789L,
            normalizedUrl = "http://localhost:8096"
        )

        credentialManager.saveServerState(server)

        val loadedServer = credentialManager.loadServerState()
        assertNotNull(loadedServer)
        assertEquals(server.id, loadedServer?.id)
        assertEquals(server.name, loadedServer?.name)
        assertEquals(server.url, loadedServer?.url)
        assertEquals(server.userId, loadedServer?.userId)
        assertEquals(server.username, loadedServer?.username)
        assertEquals(server.accessToken, loadedServer?.accessToken)
    }
}
