package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getEpisodeCode
import com.rpeters.cinefintv.utils.isWatched
import com.rpeters.cinefintv.utils.getYear
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class EpisodeDetailModel(
    val id: String,
    val title: String,
    val seriesName: String?,
    val seriesId: String?,
    val seasonId: String?,
    val episodeCode: String?,
    val year: Int?,
    val duration: String?,
    val overview: String?,
    val backdropUrl: String?,
    val isWatched: Boolean,
)

data class ChapterModel(
    val id: String,
    val name: String,
    val positionMs: Long,
    val imageUrl: String?,
)

sealed class EpisodeDetailUiState {
    data object Loading : EpisodeDetailUiState()
    data class Error(val message: String) : EpisodeDetailUiState()
    data class Content(
        val episode: EpisodeDetailModel,
        val chapters: List<ChapterModel>,
    ) : EpisodeDetailUiState()
}

@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val episodeId: String = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow<EpisodeDetailUiState>(EpisodeDetailUiState.Loading)
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState.asStateFlow()

    init {
        if (episodeId.isBlank()) {
            _uiState.value = EpisodeDetailUiState.Error("Invalid episode ID")
        } else {
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = EpisodeDetailUiState.Loading

            val episodeResult = repositories.media.getEpisodeDetails(episodeId)

            if (episodeResult is ApiResult.Success) {
                val episodeDto = episodeResult.data
                
                val chapters = episodeDto.chapters?.mapIndexed { index, chapter ->
                    ChapterModel(
                        id = "chapter_$index",
                        name = chapter.name ?: "Chapter ${index + 1}",
                        positionMs = (chapter.startPositionTicks ?: 0L) / 10000L,
                        imageUrl = repositories.stream.getImageUrl(
                            itemId = episodeId,
                            imageType = "Chapter",
                            tag = chapter.imageTag
                        )
                    )
                } ?: emptyList()

                _uiState.value = EpisodeDetailUiState.Content(
                    episode = episodeDto.toEpisodeDetailModel(),
                    chapters = chapters
                )
            } else if (episodeResult is ApiResult.Error) {
                _uiState.value = EpisodeDetailUiState.Error(episodeResult.message)
            }
        }
    }

    private fun BaseItemDto.toEpisodeDetailModel(): EpisodeDetailModel {
        return EpisodeDetailModel(
            id = id.toString(),
            title = getDisplayTitle(),
            seriesName = seriesName,
            seriesId = seriesId?.toString(),
            seasonId = seasonId?.toString(),
            episodeCode = getEpisodeCode(),
            year = getYear(),
            duration = getFormattedDuration(),
            overview = overview,
            backdropUrl = repositories.stream.getBackdropUrl(this),
            isWatched = isWatched(),
        )
    }
}
