package com.rpeters.cinefintv.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerUrlValidatorTest {

    @Test
    fun validateAndNormalizeUrl_forHostWithoutScheme_doesNotAppendDefaultHttpsPort() {
        val normalized = ServerUrlValidator.validateAndNormalizeUrl("jellyfin.example.com")

        assertEquals("https://jellyfin.example.com", normalized)
    }

    @Test
    fun validateAndNormalizeUrl_forHttpHostWithoutPort_doesNotAppendDefaultHttpPort() {
        val normalized = ServerUrlValidator.validateAndNormalizeUrl("http://jellyfin.example.com")

        assertEquals("http://jellyfin.example.com", normalized)
    }

    @Test
    fun validateAndNormalizeUrl_forUrlWithExplicitPort_preservesPort() {
        val normalized = ServerUrlValidator.validateAndNormalizeUrl("https://jellyfin.example.com:8920")

        assertEquals("https://jellyfin.example.com:8920", normalized)
    }
}
