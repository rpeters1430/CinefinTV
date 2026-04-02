package com.rpeters.cinefintv.ui.navigation

data class AppChromeRouteSpec(
    val showNav: Boolean,
    val selectedTabIndex: Int,
)

private val fullscreenRoutePrefixes = listOf(
    "auth/",
    "player/",
    "audio-player/",
    "movie/detail/",
    "tvshow/detail/",
    "season/detail/",
    "episode/detail/",
    "collections/detail/",
    "detail/person/",
)

fun appChromeRouteSpec(currentRoute: String?): AppChromeRouteSpec {
    val showNav = currentRoute != null && fullscreenRoutePrefixes.none(currentRoute::startsWith)
    val selectedTabIndex = navTabItems.indexOfFirst { item ->
        currentRoute != null && (
            currentRoute == item.route ||
                (item.route.isNotEmpty() && currentRoute.startsWith(item.route))
            )
    }.let { index ->
        if (index == -1) {
            navTabItems.indexOfFirst { it.route == NavRoutes.HOME }
        } else {
            index
        }
    }.coerceAtLeast(0)

    return AppChromeRouteSpec(
        showNav = showNav,
        selectedTabIndex = selectedTabIndex,
    )
}
