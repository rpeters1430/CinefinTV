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
    const val DETAIL = "detail/{itemId}"
    const val PERSON_DETAIL = "detail/person/{personId}"
    const val COLLECTIONS_DETAIL = "collections/detail/{itemId}"
    const val PLAYER = "player/{itemId}"
    const val AUDIO_PLAYER = "audio-player/{itemId}?queue={queue}"

    fun detail(itemId: String) = "detail/$itemId"
    fun personDetail(personId: String) = "detail/person/$personId"
    fun player(itemId: String) = "player/$itemId"
    fun collectionsDetail(itemId: String) = "collections/detail/$itemId"
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
