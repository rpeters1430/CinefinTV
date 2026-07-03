package com.rpeters.cinefintv.di

import com.rpeters.cinefintv.core.constants.Constants
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
}
