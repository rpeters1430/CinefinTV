package com.rpeters.cinefintv.di

import android.content.Context
import com.rpeters.cinefintv.core.constants.Constants
import com.rpeters.cinefintv.data.security.PinningHostnameVerifier
import com.rpeters.cinefintv.data.security.PinningTrustManager
import com.rpeters.cinefintv.network.ConnectivityChecker
import com.rpeters.cinefintv.network.JellyfinAuthInterceptor
import io.mockk.every
import io.mockk.mockk
import java.io.File
import javax.net.ssl.SSLContext
import okhttp3.ConnectionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun provideUpdateHttpClient_usesRedirectsAndNoSharedCacheOrInterceptors() {
        val client = NetworkModule.provideUpdateHttpClient()

        assertTrue(client.followRedirects)
        assertTrue(client.followSslRedirects)
        assertEquals((Constants.NETWORK_TIMEOUT_SECONDS * 1000L).toInt(), client.connectTimeoutMillis)
        assertEquals((Constants.NETWORK_READ_TIMEOUT_SECONDS * 1000L).toInt(), client.readTimeoutMillis)
        assertEquals((Constants.NETWORK_WRITE_TIMEOUT_SECONDS * 1000L).toInt(), client.writeTimeoutMillis)
        assertNull(client.cache)
        assertEquals(0, client.interceptors.size)
        assertEquals(0, client.networkInterceptors.size)
    }

    @Test
    fun provideOkHttpClient_allowsCleartextConnectionsForLocalHttpServers() {
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir"), "network-module-test-cache")
        val connectivityChecker = mockk<ConnectivityChecker>(relaxed = true)
        val authInterceptor = mockk<JellyfinAuthInterceptor>(relaxed = true)
        val sslSocketFactory = SSLContext.getDefault().socketFactory
        val pinningTrustManager = mockk<PinningTrustManager>(relaxed = true)
        val hostnameVerifier = PinningHostnameVerifier()

        val client = NetworkModule.provideOkHttpClient(
            context = context,
            connectivityChecker = connectivityChecker,
            authInterceptor = authInterceptor,
            sslSocketFactory = sslSocketFactory,
            pinningTrustManager = pinningTrustManager,
            hostnameVerifier = hostnameVerifier,
        )

        // Regression guard: without ConnectionSpec.CLEARTEXT, OkHttp rejects all plain-HTTP
        // connections outright, which blocked ExoPlayer playback from local Jellyfin servers
        // running without SSL/TLS.
        assertTrue(client.connectionSpecs.contains(ConnectionSpec.CLEARTEXT))
    }
}
