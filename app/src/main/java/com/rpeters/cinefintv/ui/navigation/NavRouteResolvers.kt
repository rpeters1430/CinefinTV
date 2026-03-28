package com.rpeters.cinefintv.ui.navigation

internal fun routeForBrowsableItem(
    itemId: String,
    itemType: String?,
    collectionType: String?,
): String {
    return when {
        itemType.equals("CollectionFolder", ignoreCase = true) -> {
            when (collectionType?.lowercase()) {
                "movies" -> NavRoutes.LIBRARY_MOVIES
                "tvshows" -> NavRoutes.LIBRARY_TVSHOWS
                "music" -> NavRoutes.LIBRARY_MUSIC
                "homevideos" -> NavRoutes.LIBRARY_COLLECTIONS
                else -> NavRoutes.stuffDetail(itemId)
            }
        }
        itemType.equals("Movie", ignoreCase = true) -> NavRoutes.movieDetail(itemId)
        itemType.equals("Series", ignoreCase = true) ||
            itemType.equals("TV Show", ignoreCase = true) -> NavRoutes.tvShowDetail(itemId)
        itemType.equals("Season", ignoreCase = true) -> NavRoutes.seasonDetail(itemId)
        itemType.equals("Episode", ignoreCase = true) -> NavRoutes.player(itemId)
        else -> NavRoutes.stuffDetail(itemId)
    }
}

internal fun routeForLinkedDetailItem(
    itemId: String,
    itemType: String?,
): String {
    return when {
        itemType.equals("Movie", ignoreCase = true) -> NavRoutes.movieDetail(itemId)
        itemType.equals("Series", ignoreCase = true) ||
            itemType.equals("TV Show", ignoreCase = true) -> NavRoutes.tvShowDetail(itemId)
        itemType.equals("Season", ignoreCase = true) -> NavRoutes.seasonDetail(itemId)
        itemType.equals("Episode", ignoreCase = true) -> NavRoutes.player(itemId)
        else -> NavRoutes.stuffDetail(itemId)
    }
}
