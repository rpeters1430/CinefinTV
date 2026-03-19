package com.rpeters.cinefintv.ui.navigation

import android.net.Uri

object NavRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY_MOVIES = "library/movies"
    const val LIBRARY_TVSHOWS = "library/tvshows"
    const val LIBRARY_COLLECTIONS = "library/collections"
    const val LIBRARY_MUSIC = "library/music"
    const val SETTINGS = "settings"
    const val MOVIE_DETAIL = "movie/detail/{itemId}"
    const val TV_SHOW_DETAIL = "tvshow/detail/{itemId}"
    const val SEASON_DETAIL = "season/detail/{itemId}"
    const val EPISODE_DETAIL = "episode/detail/{itemId}"
    const val STUFF_DETAIL = "stuff/detail/{itemId}"
    const val PERSON_DETAIL = "detail/person/{personId}"
    const val PLAYER = "player/{itemId}?start={start}"
    const val AUDIO_PLAYER = "audio-player/{itemId}?queue={queue}"

    fun movieDetail(itemId: String) = "movie/detail/$itemId"
    fun tvShowDetail(itemId: String) = "tvshow/detail/$itemId"
    fun seasonDetail(itemId: String) = "season/detail/$itemId"
    fun episodeDetail(itemId: String) = "episode/detail/$itemId"
    fun stuffDetail(itemId: String) = "stuff/detail/$itemId"
    fun personDetail(personId: String) = "detail/person/$personId"
    fun player(itemId: String, startPositionMs: Long? = null) = 
        if (startPositionMs != null) "player/$itemId?start=$startPositionMs" else "player/$itemId"
    fun audioPlayer(itemId: String, queueIds: List<String>): String {
        val encodedQueue = queueIds
            .filter(String::isNotBlank)
            .joinToString(",") { Uri.encode(it) }
        return if (encodedQueue.isBlank()) {
            "audio-player/$itemId"
        } else {
            "audio-player/$itemId?queue=$encodedQueue"
        }
    }
}

object AuthRoutes {
    const val SERVER_CONNECTION = "auth/server"
    const val LOGIN = "auth/login"
}
