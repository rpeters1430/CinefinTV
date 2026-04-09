package com.rpeters.cinefintv.ui.navigation

import androidx.navigation3.runtime.NavKey
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavDestination : NavKey, Parcelable

@Serializable
@Parcelize
data object ServerConnection : NavDestination

@Serializable
@Parcelize
data object Login : NavDestination

@Serializable
@Parcelize
data object Home : NavDestination

@Serializable
@Parcelize
data object Search : NavDestination

@Serializable
@Parcelize
data object LibraryMovies : NavDestination

@Serializable
@Parcelize
data object LibraryTvShows : NavDestination

@Serializable
@Parcelize
data object LibraryCollections : NavDestination

@Serializable
@Parcelize
data object LibraryMusic : NavDestination

@Serializable
@Parcelize
data object Settings : NavDestination

@Serializable
@Parcelize
data class MovieDetail(val itemId: String) : NavDestination

@Serializable
@Parcelize
data class TvShowDetail(val itemId: String) : NavDestination

@Serializable
@Parcelize
data class SeasonDetail(val itemId: String) : NavDestination

@Serializable
@Parcelize
data class EpisodeDetail(val itemId: String) : NavDestination

@Serializable
@Parcelize
data class CollectionDetail(val itemId: String) : NavDestination

@Serializable
@Parcelize
data class PersonDetail(val personId: String) : NavDestination

@Serializable
@Parcelize
data class Player(val itemId: String, val startPositionMs: Long = -1L) : NavDestination

@Serializable
@Parcelize
data class AudioPlayer(val itemId: String, val queueIds: List<String>? = null) : NavDestination
