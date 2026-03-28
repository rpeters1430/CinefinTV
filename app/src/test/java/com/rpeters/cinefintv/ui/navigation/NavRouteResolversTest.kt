package com.rpeters.cinefintv.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavRouteResolversTest {

    @Test
    fun routeForBrowsableItem_mapsCollectionFoldersToExpectedLibraries() {
        assertEquals(
            NavRoutes.LIBRARY_MOVIES,
            routeForBrowsableItem("movies-folder", "CollectionFolder", "movies"),
        )
        assertEquals(
            NavRoutes.LIBRARY_TVSHOWS,
            routeForBrowsableItem("tv-folder", "CollectionFolder", "tvshows"),
        )
        assertEquals(
            NavRoutes.LIBRARY_MUSIC,
            routeForBrowsableItem("music-folder", "CollectionFolder", "music"),
        )
        assertEquals(
            NavRoutes.LIBRARY_COLLECTIONS,
            routeForBrowsableItem("homevideos-folder", "CollectionFolder", "homevideos"),
        )
        assertEquals(
            NavRoutes.stuffDetail("other-folder"),
            routeForBrowsableItem("other-folder", "CollectionFolder", "books"),
        )
    }

    @Test
    fun routeForBrowsableItem_mapsPlayableAndDetailMediaTypes() {
        assertEquals(
            NavRoutes.movieDetail("movie-1"),
            routeForBrowsableItem("movie-1", "Movie", null),
        )
        assertEquals(
            NavRoutes.tvShowDetail("series-1"),
            routeForBrowsableItem("series-1", "Series", null),
        )
        assertEquals(
            NavRoutes.tvShowDetail("tv-show-1"),
            routeForBrowsableItem("tv-show-1", "TV Show", null),
        )
        assertEquals(
            NavRoutes.seasonDetail("season-1"),
            routeForBrowsableItem("season-1", "Season", null),
        )
        assertEquals(
            NavRoutes.player("episode-1"),
            routeForBrowsableItem("episode-1", "Episode", null),
        )
        assertEquals(
            NavRoutes.stuffDetail("unknown-1"),
            routeForBrowsableItem("unknown-1", "BoxSet", null),
        )
    }

    @Test
    fun routeForLinkedDetailItem_mapsKnownTypes_andFallsBackToStuffDetail() {
        assertEquals(
            NavRoutes.movieDetail("movie-1"),
            routeForLinkedDetailItem("movie-1", "Movie"),
        )
        assertEquals(
            NavRoutes.tvShowDetail("series-1"),
            routeForLinkedDetailItem("series-1", "Series"),
        )
        assertEquals(
            NavRoutes.tvShowDetail("tv-show-1"),
            routeForLinkedDetailItem("tv-show-1", "TV Show"),
        )
        assertEquals(
            NavRoutes.seasonDetail("season-1"),
            routeForLinkedDetailItem("season-1", "Season"),
        )
        assertEquals(
            NavRoutes.player("episode-1"),
            routeForLinkedDetailItem("episode-1", "Episode"),
        )
        assertEquals(
            NavRoutes.stuffDetail("unknown-1"),
            routeForLinkedDetailItem("unknown-1", "Person"),
        )
    }
}
