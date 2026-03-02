package com.rpeters.cinefintv.utils

import com.rpeters.cinefintv.core.constants.Constants

/**
 * Backwards-compatible constants wrapper used by unit tests.
 * Delegates to the centralized Constants object.
 */
object AppConstants {
    object Rating {
        const val HIGH_RATING_THRESHOLD = Constants.Rating.HIGH_RATING_THRESHOLD
        const val EXCELLENT_RATING_THRESHOLD = Constants.Rating.EXCELLENT_RATING_THRESHOLD
        const val GOOD_RATING_THRESHOLD = Constants.Rating.GOOD_RATING_THRESHOLD
        const val AVERAGE_RATING_THRESHOLD = Constants.Rating.AVERAGE_RATING_THRESHOLD
    }

    object Security {
        const val KEY_ALIAS = Constants.Security.KEY_ALIAS
        const val ENCRYPTION_TRANSFORMATION = Constants.Security.ENCRYPTION_TRANSFORMATION
        const val IV_LENGTH = Constants.Security.IV_LENGTH
    }

    object Search {
        const val SEARCH_DEBOUNCE_MS = Constants.SEARCH_DEBOUNCE_MS
    }

    object Playback {
        const val RESUME_THRESHOLD_PERCENT = Constants.Playback.RESUME_THRESHOLD_PERCENT
        const val WATCHED_THRESHOLD_PERCENT = Constants.Playback.WATCHED_THRESHOLD_PERCENT
    }
}
