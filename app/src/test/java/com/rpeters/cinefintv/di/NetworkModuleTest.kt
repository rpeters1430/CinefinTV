package com.rpeters.cinefintv.di

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
        assertNull(client.cache)
        assertEquals(0, client.interceptors.size)
        assertEquals(0, client.networkInterceptors.size)
    }
}
