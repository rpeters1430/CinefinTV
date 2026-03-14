package com.rpeters.cinefintv.ui.screens.stuff

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaStreamType
import javax.inject.Inject

sealed class StuffDetailUiState {
    data object Loading : StuffDetailUiState()
    data class Error(val message: String) : StuffDetailUiState()
    data class Content(
        val item: StuffDetailModel,
        val moreFromStuff: List<StuffItemCardModel>,
        val isDeleteConfirmationVisible: Boolean = false,
        val isDeleting: Boolean = false,
        val isDeleted: Boolean = false,
        val actionErrorMessage: String? = null,
    ) : StuffDetailUiState()
}

data class StuffTechnicalDetails(
    val videoCodec: String?,
    val resolution: String?,
    val audioCodec: String?,
    val audioChannels: String?,
    val duration: String?,
) {
    val summary: String?
        get() {
            val parts = buildList {
                if (videoCodec != null) add(videoCodec)
                if (resolution != null) add(resolution)
                val audioLabel = when {
                    audioCodec != null && audioChannels != null -> "$audioCodec $audioChannels"
                    audioCodec != null -> audioCodec
                    audioChannels != null -> audioChannels
                    else -> null
                }
                if (audioLabel != null) add(audioLabel)
                if (duration != null) add(duration)
            }
            return parts.joinToString(" · ").ifBlank { null }
        }
}

data class StuffDetailModel(
    val id: String,
    val title: String,
    val overview: String?,
    val metadataLine: String?,
    val imageUrl: String?,
    val backdropUrl: String?,
    val technicalDetails: StuffTechnicalDetails? = null,
)

@HiltViewModel
class StuffDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val itemId = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow<StuffDetailUiState>(StuffDetailUiState.Loading)
    val uiState: StateFlow<StuffDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun heightToResolutionLabel(height: Int?): String? = when {
        height == null -> null
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        else -> "${height}p"
    }

    fun load() {
        if (itemId.isBlank()) {
            _uiState.value = StuffDetailUiState.Error("No Stuff item ID was provided.")
            return
        }

        viewModelScope.launch {
            _uiState.value = StuffDetailUiState.Loading

            val detailsResult = repositories.media.getItemDetails(itemId)
            val libraryResult = repositories.media.getLibraryItems(collectionType = "homevideos", limit = 40)

            if (detailsResult !is ApiResult.Success) {
                val message = (detailsResult as? ApiResult.Error)?.message ?: "Unable to load Stuff details."
                _uiState.value = StuffDetailUiState.Error(message)
                return@launch
            }

            val item = detailsResult.data
            val metadata = listOfNotNull(item.getYear()?.toString(), item.getFormattedDuration())
                .joinToString(" • ")
                .ifBlank { null }

            // Parse technical details from media streams
            val streams = item.mediaSources?.firstOrNull()?.mediaStreams
                ?: item.mediaStreams.orEmpty()
            val videoStream = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
            val audioStream = streams.firstOrNull { it.type == MediaStreamType.AUDIO }
            val technicalDetails = StuffTechnicalDetails(
                videoCodec = videoStream?.codec?.uppercase(),
                resolution = heightToResolutionLabel(videoStream?.height),
                audioCodec = audioStream?.codec?.uppercase(),
                audioChannels = audioStream?.channelLayout,
                duration = item.getFormattedDuration(),
            )

            val moreItems = if (libraryResult is ApiResult.Success) {
                libraryResult.data
                    .asSequence()
                    .filter { it.id.toString() != itemId }
                    .take(16)
                    .map {
                        val isResumable = it.canResume()
                        val isWatched = it.isWatched()
                        val watchStatus = when {
                            isWatched -> WatchStatus.WATCHED
                            isResumable -> WatchStatus.IN_PROGRESS
                            else -> WatchStatus.NONE
                        }
                        val playbackProgress = if (isResumable) {
                            it.getWatchedPercentage().toFloat() / 100f
                        } else null

                        StuffItemCardModel(
                            id = it.id.toString(),
                            title = it.getDisplayTitle(),
                            subtitle = it.getYear()?.toString() ?: it.getFormattedDuration(),
                            imageUrl = repositories.stream.getLandscapeImageUrl(it),
                            watchStatus = watchStatus,
                            playbackProgress = playbackProgress,
                        )
                    }
                    .toList()
            } else {
                emptyList()
            }

            _uiState.value = StuffDetailUiState.Content(
                item = StuffDetailModel(
                    id = itemId,
                    title = item.getDisplayTitle(),
                    overview = item.overview,
                    metadataLine = metadata,
                    imageUrl = repositories.stream.getLandscapeImageUrl(item),
                    backdropUrl = repositories.stream.getBackdropUrl(item),
                    technicalDetails = technicalDetails,
                ),
                moreFromStuff = moreItems,
            )
        }
    }

    fun requestDelete() {
        val state = _uiState.value as? StuffDetailUiState.Content ?: return
        _uiState.value = state.copy(
            isDeleteConfirmationVisible = true,
            actionErrorMessage = null,
        )
    }

    fun cancelDelete() {
        val state = _uiState.value as? StuffDetailUiState.Content ?: return
        _uiState.value = state.copy(
            isDeleteConfirmationVisible = false,
            actionErrorMessage = null,
        )
    }

    fun confirmDelete() {
        val state = _uiState.value as? StuffDetailUiState.Content ?: return

        viewModelScope.launch {
            _uiState.value = state.copy(
                isDeleting = true,
                actionErrorMessage = null,
            )

            when (val result = repositories.user.deleteItem(state.item.id)) {
                is ApiResult.Success -> {
                    _uiState.value = state.copy(
                        isDeleting = false,
                        isDeleteConfirmationVisible = false,
                        isDeleted = true,
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = state.copy(
                        isDeleting = false,
                        actionErrorMessage = result.message,
                    )
                }
                is ApiResult.Loading -> Unit
            }
        }
    }
}
