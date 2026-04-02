package com.rpeters.cinefintv.ui.screens.detail

import com.rpeters.cinefintv.ui.components.WatchStatus

data class CastModel(
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String?,
)

data class SimilarMovieModel(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val watchStatus: WatchStatus,
    val playbackProgress: Float?,
)

data class EpisodeModel(
    val id: String,
    val title: String,
    val number: Int?,
    val overview: String?,
    val imageUrl: String?,
    val duration: String?,
    val videoQuality: String?,
    val audioLabel: String?,
    val isWatched: Boolean,
    val playbackProgress: Float?,
    val episodeCode: String?,
)

data class SeasonModel(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val watchStatus: WatchStatus,
    val playbackProgress: Float?,
    val unwatchedCount: Int,
)
