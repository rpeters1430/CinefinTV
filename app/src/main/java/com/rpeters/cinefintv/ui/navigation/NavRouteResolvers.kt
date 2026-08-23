package com.rpeters.cinefintv.ui.navigation

fun routeForBrowsableItem(
    itemId: String,
    itemType: String?,
    collectionType: String?,
): NavDestination {
    return when {
        itemType.equals("CollectionFolder", ignoreCase = true) -> {
            when (collectionType?.lowercase()) {
                "movies" -> LibraryMovies
                "tvshows" -> LibraryTvShows
                "music" -> LibraryMusic
                "playlists" -> LibraryPlaylists
                else -> CollectionDetail(itemId)
            }
        }
        itemType.equals("Movie", ignoreCase = true) -> MovieDetail(itemId)
        itemType.equals("Series", ignoreCase = true) ||
            itemType.equals("TV Show", ignoreCase = true) -> TvShowDetail(itemId)
        itemType.equals("Season", ignoreCase = true) -> SeasonDetail(itemId)
        itemType.equals("Episode", ignoreCase = true) -> Player(itemId)
        itemType.equals("Playlist", ignoreCase = true) -> PlaylistDetail(itemId)
        else -> CollectionDetail(itemId)
    }
}

fun routeForLinkedDetailItem(
    itemId: String,
    itemType: String?,
): NavDestination {
    return when {
        itemType.equals("Movie", ignoreCase = true) -> MovieDetail(itemId)
        itemType.equals("Series", ignoreCase = true) ||
            itemType.equals("TV Show", ignoreCase = true) -> TvShowDetail(itemId)
        itemType.equals("Season", ignoreCase = true) -> SeasonDetail(itemId)
        itemType.equals("Episode", ignoreCase = true) -> Player(itemId)
        itemType.equals("Person", ignoreCase = true) -> PersonDetail(itemId)
        itemType.equals("Playlist", ignoreCase = true) -> PlaylistDetail(itemId)
        else -> CollectionDetail(itemId)
    }
}
