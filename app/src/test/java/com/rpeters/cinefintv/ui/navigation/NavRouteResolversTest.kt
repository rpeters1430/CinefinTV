package com.rpeters.cinefintv.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavRouteResolversTest {

    @Test
    fun routeForBrowsableItem_mapsCollectionFoldersToExpectedLibraries() {
        assertEquals(
            LibraryMovies,
            routeForBrowsableItem("movies-folder", "CollectionFolder", "movies"),
        )
        assertEquals(
            LibraryTvShows,
            routeForBrowsableItem("tv-folder", "CollectionFolder", "tvshows"),
        )
        assertEquals(
            LibraryMusic,
            routeForBrowsableItem("music-folder", "CollectionFolder", "music"),
        )
        assertEquals(
            LibraryCollections,
            routeForBrowsableItem("homevideos-folder", "CollectionFolder", "homevideos"),
        )
        assertEquals(
            CollectionDetail("other-folder"),
            routeForBrowsableItem("other-folder", "CollectionFolder", "books"),
        )
    }

    @Test
    fun routeForBrowsableItem_mapsPlayableAndDetailMediaTypes() {
        assertEquals(
            MovieDetail("movie-1"),
            routeForBrowsableItem("movie-1", "Movie", null),
        )
        assertEquals(
            TvShowDetail("series-1"),
            routeForBrowsableItem("series-1", "Series", null),
        )
        assertEquals(
            TvShowDetail("tv-show-1"),
            routeForBrowsableItem("tv-show-1", "TV Show", null),
        )
        assertEquals(
            SeasonDetail("season-1"),
            routeForBrowsableItem("season-1", "Season", null),
        )
        assertEquals(
            Player("episode-1"),
            routeForBrowsableItem("episode-1", "Episode", null),
        )
        assertEquals(
            CollectionDetail("unknown-1"),
            routeForBrowsableItem("unknown-1", "BoxSet", null),
        )
    }

    @Test
    fun routeForLinkedDetailItem_mapsKnownTypes_andFallsBackToCollectionDetail() {
        assertEquals(
            MovieDetail("movie-1"),
            routeForLinkedDetailItem("movie-1", "Movie"),
        )
        assertEquals(
            TvShowDetail("series-1"),
            routeForLinkedDetailItem("series-1", "Series"),
        )
        assertEquals(
            TvShowDetail("tv-show-1"),
            routeForLinkedDetailItem("tv-show-1", "TV Show"),
        )
        assertEquals(
            SeasonDetail("season-1"),
            routeForLinkedDetailItem("season-1", "Season"),
        )
        assertEquals(
            Player("episode-1"),
            routeForLinkedDetailItem("episode-1", "Episode"),
        )
        assertEquals(
            PersonDetail("person-1"),
            routeForLinkedDetailItem("person-1", "Person"),
        )
    }
}
