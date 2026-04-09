package com.rpeters.cinefintv.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavChromeTest {

    @Test
    fun appChromeRouteSpec_hidesNavForFullscreenRoutes() {
        val fullscreenDestinations = listOf(
            ServerConnection,
            Login,
            Player("episode-1"),
            AudioPlayer("track-1", listOf("track-1", "track-2")),
            MovieDetail("movie-1"),
            TvShowDetail("series-1"),
            SeasonDetail("season-1"),
            CollectionDetail("collection-1"),
            PersonDetail("person-1"),
            EpisodeDetail("episode-1"),
        )

        fullscreenDestinations.forEach { dest ->
            val spec = appChromeRouteSpec(dest)
            assertFalse("Expected nav to be hidden for destination $dest", spec.showNav)
        }
    }

    @Test
    fun appChromeRouteSpec_selectsExpectedTopLevelTab() {
        assertEquals(0, appChromeRouteSpec(Home).selectedTabIndex)
        assertEquals(1, appChromeRouteSpec(LibraryMovies).selectedTabIndex)
        assertEquals(2, appChromeRouteSpec(LibraryTvShows).selectedTabIndex)
        assertEquals(6, appChromeRouteSpec(Settings).selectedTabIndex)
    }

    @Test
    fun appChromeRouteSpec_defaultsToHomeWhenRouteUnknownOrNull() {
        // Unknown destination (if any new ones added but not in chrome)
        // For now, since it's a sealed interface, we can't easily pass an "unknown" one 
        // without defining it.
        assertEquals(0, appChromeRouteSpec(null).selectedTabIndex)
        assertFalse(appChromeRouteSpec(null).showNav)
    }

    @Test
    fun appChromeRouteSpec_showsNavForTopLevelRoutes() {
        val topLevelDestinations = listOf(
            Home,
            Search,
            LibraryMovies,
            LibraryTvShows,
            LibraryCollections,
            LibraryMusic,
            Settings,
        )

        topLevelDestinations.forEach { dest ->
            assertTrue("Expected nav to be visible for destination $dest", appChromeRouteSpec(dest).showNav)
        }
    }
}
