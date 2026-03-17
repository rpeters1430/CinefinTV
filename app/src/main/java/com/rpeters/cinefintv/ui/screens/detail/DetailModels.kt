package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.ui.graphics.vector.ImageVector
import com.rpeters.cinefintv.ui.components.WatchStatus

data class DetailInfoRowModel(
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
)

data class DetailPersonModel(
    val id: String,
    val name: String,
    val role: String?,
    val type: String?,
    val imageUrl: String?,
)

data class DetailChapterModel(
    val index: Int,
    val name: String?,
    val positionMs: Long,
    val imageUrl: String?,
    val subtitle: String?,
)

data class DetailHeroModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val overview: String?,
    val imageUrl: String?,
    val backdropUrl: String?,
    val metaBadges: List<String>,
    val infoRows: List<DetailInfoRowModel>,
    val technicalDetails: DetailTechnicalDetails? = null,
    val subtitleOptions: List<String> = emptyList(),
    val cast: List<DetailPersonModel> = emptyList(),
    val chapters: List<DetailChapterModel> = emptyList(),
    val watchStatus: WatchStatus = WatchStatus.NONE,
    val playbackProgress: Float? = null,
    val unwatchedCount: Int? = null,
)

data class DetailTechnicalDetails(
    val videoQuality: String?,
    val audioCodec: String?,
    val audioType: String?,
    val language: String?,
    val bitrate: String? = null,
    val framerate: String? = null,
)

data class DetailSeasonModel(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val overview: String? = null,
    val imageUrl: String? = null,
    val episodeCount: Int = 0,
    val watchStatus: WatchStatus = WatchStatus.NONE,
    val playbackProgress: Float? = null,
    val unwatchedCount: Int? = null,
)

data class DetailEpisodeModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val overview: String?,
    val imageUrl: String?,
    val canResume: Boolean = false,
    val isWatched: Boolean = false,
    val watchStatus: WatchStatus = WatchStatus.NONE,
    val playbackProgress: Float? = null,
    val unwatchedCount: Int? = null,
)

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Error(val message: String) : DetailUiState()
    data class Content(
        val item: DetailHeroModel,
        val seasons: List<DetailSeasonModel>,
        val episodesBySeasonId: Map<String, List<DetailEpisodeModel>>,
        val related: List<DetailHeroModel>,
        val cast: List<DetailPersonModel>,
        val playableItemId: String?,
        val playButtonLabel: String,
        val isDeleteConfirmationVisible: Boolean = false,
        val isDeleting: Boolean = false,
        val isDeleted: Boolean = false,
        val actionErrorMessage: String? = null,
        val refreshErrorMessage: String? = null,
    ) : DetailUiState()
}
