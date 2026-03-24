package com.rpeters.cinefintv.ui.screens.detail.cinematic

object DetailTestTags {
    const val PrimaryAction = "detail_primary_action"
    const val Overview = "detail_overview"
    const val HeroTitle = "detail_hero_title"
    const val HeroLogo = "detail_hero_logo"
    const val MovieCastSection = "movie_detail_cast_section"
    const val MovieSimilarSection = "movie_detail_similar_section"
    const val TvEpisodesPanel = "tv_detail_episodes_panel"
    const val TvCastPanel = "tv_detail_cast_panel"
    const val TvSimilarPanel = "tv_detail_similar_panel"
    const val TvDetailsPanel = "tv_detail_details_panel"

    fun tvTab(tab: TvShowTab): String = "tv_detail_tab_${tab.name}"
}
