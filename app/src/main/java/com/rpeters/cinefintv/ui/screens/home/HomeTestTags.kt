package com.rpeters.cinefintv.ui.screens.home

object HomeTestTags {
    const val Loading = "home_loading"
    const val Error = "home_error"
    const val RetryButton = "home_retry_button"
    const val FeaturedCarousel = "home_featured_carousel"
    const val FeaturedTitle = "home_featured_title"
    const val FeaturedPlayButton = "home_featured_play_button"
    const val FeaturedDetailsButton = "home_featured_details_button"

    fun section(index: Int): String = "home_section_$index"
    fun sectionItem(sectionIndex: Int, itemIndex: Int): String = "home_section_${sectionIndex}_item_$itemIndex"
}
