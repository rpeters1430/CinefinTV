package com.rpeters.cinefintv.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavChromeTest {

    @Test
    fun appChromeRouteSpec_hidesNavForFullscreenRoutes() {
        val fullscreenRoutes = listOf(
            AuthRoutes.SERVER_CONNECTION,
            AuthRoutes.LOGIN,
            NavRoutes.player("episode-1"),
            "audio-player/track-1?queue=track-1,track-2",
            NavRoutes.movieDetail("movie-1"),
            NavRoutes.tvShowDetail("series-1"),
            NavRoutes.seasonDetail("season-1"),
            NavRoutes.collectionDetail("collection-1"),
            NavRoutes.personDetail("person-1"),
            NavRoutes.episodeDetail("episode-1"),
        )

        fullscreenRoutes.forEach { route ->
            val spec = appChromeRouteSpec(route)
            assertFalse("Expected nav to be hidden for route $route", spec.showNav)
        }
    }

    @Test
    fun appChromeRouteSpec_selectsExpectedTopLevelTab() {
        assertEquals(0, appChromeRouteSpec(NavRoutes.HOME).selectedTabIndex)
        assertEquals(1, appChromeRouteSpec(NavRoutes.LIBRARY_MOVIES).selectedTabIndex)
        assertEquals(2, appChromeRouteSpec(NavRoutes.LIBRARY_TVSHOWS).selectedTabIndex)
        assertEquals(6, appChromeRouteSpec(NavRoutes.SETTINGS).selectedTabIndex)
    }

    @Test
    fun appChromeRouteSpec_defaultsToHomeWhenRouteUnknownOrNull() {
        assertEquals(0, appChromeRouteSpec("unknown/route").selectedTabIndex)
        assertEquals(0, appChromeRouteSpec(null).selectedTabIndex)
        assertFalse(appChromeRouteSpec(null).showNav)
    }

    @Test
    fun appChromeRouteSpec_showsNavForTopLevelRoutes() {
        val topLevelRoutes = listOf(
            NavRoutes.HOME,
            NavRoutes.SEARCH,
            NavRoutes.LIBRARY_MOVIES,
            NavRoutes.LIBRARY_TVSHOWS,
            NavRoutes.LIBRARY_COLLECTIONS,
            NavRoutes.LIBRARY_MUSIC,
            NavRoutes.SETTINGS,
        )

        topLevelRoutes.forEach { route ->
            assertTrue("Expected nav to be visible for route $route", appChromeRouteSpec(route).showNav)
        }
    }
}
