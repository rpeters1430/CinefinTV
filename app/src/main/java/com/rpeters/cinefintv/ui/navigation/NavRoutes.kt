package com.rpeters.cinefintv.ui.navigation

object NavRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY_MOVIES = "library/movies"
    const val LIBRARY_TVSHOWS = "library/tvshows"
    const val LIBRARY_STUFF = "library/stuff"
    const val LIBRARY_MUSIC = "library/music"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{itemId}"
    const val STUFF_DETAIL = "stuff/detail/{itemId}"
    const val PLAYER = "player/{itemId}"

    fun detail(itemId: String) = "detail/$itemId"
    fun player(itemId: String) = "player/$itemId"
    fun stuffDetail(itemId: String) = "stuff/detail/$itemId"
}

object AuthRoutes {
    const val SERVER_CONNECTION = "auth/server"
    const val LOGIN = "auth/login"
}
