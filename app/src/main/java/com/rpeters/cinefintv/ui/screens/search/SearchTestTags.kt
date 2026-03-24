package com.rpeters.cinefintv.ui.screens.search

object SearchTestTags {
    const val Hero = "search_hero"
    const val Field = "search_field"
    const val Hint = "search_hint"
    const val Loading = "search_loading"
    const val Error = "search_error"
    const val Empty = "search_empty"
    const val ResultsCount = "search_results_count"

    fun resultItem(index: Int): String = "search_result_$index"
}
