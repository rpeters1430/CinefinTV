package com.rpeters.cinefintv.ui.navigation

data class AppChromeRouteSpec(
    val showNav: Boolean,
    val selectedTabIndex: Int,
)

fun appChromeRouteSpec(currentDestination: NavDestination?): AppChromeRouteSpec {
    val showNav = currentDestination != null && when (currentDestination) {
        is ServerConnection,
        is Login,
        is MovieDetail,
        is TvShowDetail,
        is SeasonDetail,
        is EpisodeDetail,
        is CollectionDetail,
        is PersonDetail,
        is Player,
        is AudioPlayer -> false
        else -> true
    }

    return AppChromeRouteSpec(
        showNav = showNav,
        selectedTabIndex = selectedTopLevelTabIndex(currentDestination),
    )
}
