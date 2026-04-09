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

    val selectedTabIndex = navTabItems.indexOfFirst { item ->
        currentDestination != null && currentDestination::class == item.destination::class
    }.let { index ->
        if (index == -1) {
            navTabItems.indexOfFirst { it.destination is Home }
        } else {
            index
        }
    }.coerceAtLeast(0)

    return AppChromeRouteSpec(
        showNav = showNav,
        selectedTabIndex = selectedTabIndex,
    )
}
