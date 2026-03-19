package com.rpeters.cinefintv.ui.components

import androidx.compose.ui.focus.FocusRequester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvScreenFocusRegistryTest {

    @Test
    fun `requesterFor should return null for non-matching route`() {
        val registry = TvScreenFocusRegistry()
        val requester = FocusRequester()
        registry.register("home", requester)

        assertNull(registry.requesterFor("settings"))
    }

    @Test
    fun `requesterFor should match exact route`() {
        val registry = TvScreenFocusRegistry()
        val requester = FocusRequester()
        registry.register("home", requester)

        assertEquals(requester, registry.requesterFor("home"))
    }

    @Test
    fun `requesterFor should match route with prefix`() {
        val registry = TvScreenFocusRegistry()
        val requester = FocusRequester()
        registry.register("home", requester)

        assertEquals(requester, registry.requesterFor("home/deep/link"))
    }

    @Test
    fun `requesterFor should match parameterized route`() {
        val registry = TvScreenFocusRegistry()
        val requester = FocusRequester()
        registry.register("detail/{itemId}", requester)

        // This is expected to fail currently
        assertEquals(requester, registry.requesterFor("detail/12345"))
    }

    @Test
    fun `requesterFor should match complex parameterized route`() {
        val registry = TvScreenFocusRegistry()
        val requester = FocusRequester()
        registry.register("movie/{movieId}/review/{reviewId}", requester)

        // This is expected to fail currently
        assertEquals(requester, registry.requesterFor("movie/123/review/456"))
    }
}
