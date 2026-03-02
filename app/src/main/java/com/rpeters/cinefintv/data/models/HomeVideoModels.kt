package com.rpeters.cinefintv.data.models

import com.rpeters.cinefintv.R

/**
 * Filter options for home videos
 */
enum class HomeVideoFilter(val displayNameResId: Int) {
    ALL(R.string.filter_all_home_videos),
    FAVORITES(R.string.filter_favorites),
    UNWATCHED(R.string.filter_unwatched),
    WATCHED(R.string.filter_watched),
    RECENT(R.string.filter_recent),
    ;

    companion object {
        fun getAllFilters() = entries

        fun getBasicFilters() = listOf(ALL, FAVORITES, UNWATCHED, WATCHED)

        fun getSmartFilters() = listOf(RECENT)
    }
}

/**
 * Sort order options for home videos
 */
enum class HomeVideoSortOrder(val displayNameResId: Int) {
    NAME_ASC(R.string.sort_title_asc),
    NAME_DESC(R.string.sort_title_desc),
    DATE_ADDED_DESC(R.string.sort_date_added_desc),
    DATE_ADDED_ASC(R.string.sort_date_added_asc),
    DATE_CREATED_DESC(R.string.sort_date_created_desc),
    DATE_CREATED_ASC(R.string.sort_date_created_asc),
    ;

    companion object {
        fun getDefault() = NAME_ASC
        fun getAllSortOrders() = entries
    }
}

/**
 * View mode options for home videos
 */
enum class HomeVideoViewMode {
    GRID,
    LIST,
    CAROUSEL,
}
