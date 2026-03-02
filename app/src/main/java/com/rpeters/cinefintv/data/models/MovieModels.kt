package com.rpeters.cinefintv.data.models

import com.rpeters.cinefintv.R

/**
 * Filter options for movies
 */
enum class MovieFilter(val displayNameResId: Int) {
    ALL(R.string.filter_all_movies),
    FAVORITES(R.string.filter_favorites),
    UNWATCHED(R.string.filter_unwatched),
    WATCHED(R.string.filter_recent),
    RECENT_RELEASES(R.string.filter_recent_releases),
    HIGH_RATED(R.string.filter_high_rated),
    ACTION(R.string.filter_action),
    COMEDY(R.string.filter_comedy),
    DRAMA(R.string.filter_drama),
    SCI_FI(R.string.filter_sci_fi),
    ;

    companion object {
        fun getAllFilters() = entries

        fun getBasicFilters() = listOf(ALL, FAVORITES, UNWATCHED, WATCHED)

        fun getSmartFilters() = listOf(RECENT_RELEASES, HIGH_RATED)

        fun getGenreFilters() = listOf(ACTION, COMEDY, DRAMA, SCI_FI)
    }
}

/**
 * Sort order options for movies
 */
enum class MovieSortOrder(val displayNameResId: Int) {
    NAME(R.string.sort_title_asc),
    YEAR(R.string.sort_year_desc),
    RATING(R.string.sort_rating_desc),
    RECENTLY_ADDED(R.string.sort_date_added_desc),
    RUNTIME(R.string.sort_runtime_desc),
}

/**
 * View mode options for movies
 */
enum class MovieViewMode {
    GRID,
    LIST,
}

/**
 * Library type definitions
 */
enum class LibraryType {
    MOVIES,
    SHOWS,
    MUSIC,
    BOOKS,
    AUDIOBOOKS,
    PHOTOS,
    MIXED,
}
