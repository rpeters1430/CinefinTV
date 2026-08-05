package com.rpeters.cinefintv.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
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

    @Test
    fun validateAndNormalizeUrl_forIpAddressWithPortWithoutScheme_defaultsToHttp() {
        val normalized = ServerUrlValidator.validateAndNormalizeUrl("10.1.1.20:8096")

        assertEquals("http://10.1.1.20:8096", normalized)
    }

    @Test
    fun validateAndNormalizeUrl_forIpAddressWithoutScheme_defaultsToHttp() {
        val normalized = ServerUrlValidator.validateAndNormalizeUrl("192.168.1.50")

        assertEquals("http://192.168.1.50", normalized)
    }

    @Test
    fun validateAndNormalizeUrl_forIpAddressWithExplicitHttps_preservesHttps() {
        val normalized = ServerUrlValidator.validateAndNormalizeUrl("https://10.1.1.20:8920")

        assertEquals("https://10.1.1.20:8920", normalized)
    }

    @Test
    fun validateAndNormalizeUrl_forLocalhostWithoutScheme_defaultsToHttp() {
        val normalized = ServerUrlValidator.validateAndNormalizeUrl("localhost:8096")

        assertEquals("http://localhost:8096", normalized)
    }

    @Test
    fun validateAndNormalizeUrl_forDomainWithoutScheme_stillDefaultsToHttps() {
        val normalized = ServerUrlValidator.validateAndNormalizeUrl("media.example.com")

        assertEquals("https://media.example.com", normalized)
    }

    @Test
    fun normalizeServerUrl_stripsDefaultHttpsPort() {
        val result1 = normalizeServerUrl("https://jellyfin.example.com")
        val result2 = normalizeServerUrl("https://jellyfin.example.com:443")

        assertEquals("https://jellyfin.example.com", result1)
        assertEquals("https://jellyfin.example.com", result2)
    }

    @Test
    fun normalizeServerUrl_stripsDefaultHttpPort() {
        val result1 = normalizeServerUrl("http://jellyfin.example.com")
        val result2 = normalizeServerUrl("http://jellyfin.example.com:80")

        assertEquals("http://jellyfin.example.com", result1)
        assertEquals("http://jellyfin.example.com", result2)
    }

    @Test
    fun normalizeServerUrl_preservesCustomHttpsPort() {
        val result = normalizeServerUrl("https://jellyfin.example.com:8920")
        assertEquals("https://jellyfin.example.com:8920", result)
    }

    @Test
    fun normalizeServerUrl_preservesCustomHttpPort() {
        val result = normalizeServerUrl("http://jellyfin.example.com:8096")
        assertEquals("http://jellyfin.example.com:8096", result)
    }

    @Test
    fun normalizeServerUrlLegacy_preservesDefaultPorts() {
        assertEquals("https://jellyfin.example.com:443", normalizeServerUrlLegacy("https://jellyfin.example.com:443"))
        assertEquals("http://jellyfin.example.com:80", normalizeServerUrlLegacy("http://jellyfin.example.com:80"))
    }
}
