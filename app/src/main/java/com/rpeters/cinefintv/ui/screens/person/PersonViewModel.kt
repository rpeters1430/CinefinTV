package com.rpeters.cinefintv.ui.screens.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getUnwatchedEpisodeCardLabel
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isSeries
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

data class PersonHeroModel(
    val id: String,
    val name: String,
    val overview: String?,
    val imageUrl: String?,
)

data class PersonMediaModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val overview: String?,
    val imageUrl: String?,
    val watchStatus: WatchStatus = WatchStatus.NONE,
    val playbackProgress: Float? = null,
)

sealed class PersonUiState {
    data object Loading : PersonUiState()
    data class Error(val message: String) : PersonUiState()
    data class Content(
        val person: PersonHeroModel,
        val media: List<PersonMediaModel>,
    ) : PersonUiState()
}

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val personId: String = savedStateHandle.get<String>("personId").orEmpty()

    private val _uiState = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    init {
        if (personId.isBlank()) {
            _uiState.value = PersonUiState.Error("Invalid person ID")
        } else {
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PersonUiState.Loading
            
            val personResult = repositories.media.getPersonDetails(personId)
            val itemsResult = repositories.media.getItemsByPerson(personId)

            if (personResult is ApiResult.Success) {
                val personDto = personResult.data
                val mediaItems = if (itemsResult is ApiResult.Success) {
                    itemsResult.data.map { it.toMediaModel() }
                } else {
                    emptyList()
                }

                _uiState.value = PersonUiState.Content(
                    person = personDto.toHeroModel(),
                    media = mediaItems
                )
            } else if (personResult is ApiResult.Error) {
                _uiState.value = PersonUiState.Error(personResult.message)
            }
        }
    }

    private fun BaseItemDto.toHeroModel(): PersonHeroModel {
        return PersonHeroModel(
            id = id.toString(),
            name = getDisplayTitle(),
            overview = overview,
            imageUrl = repositories.stream.getImageUrl(
                itemId = id.toString(),
                tag = imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)
            )
        )
    }

    private fun BaseItemDto.toMediaModel(): PersonMediaModel {
        val year = getYear()
        val typeLabel = when (type) {
            org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> "Movie"
            org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> "TV Show"
            org.jellyfin.sdk.model.api.BaseItemKind.EPISODE -> "Episode"
            else -> null
        }
        
        val subtitle = if (isSeries()) {
            getUnwatchedEpisodeCardLabel()
                ?: listOfNotNull(year?.toString(), typeLabel).joinToString(" | ")
        } else {
            listOfNotNull(year?.toString(), typeLabel).joinToString(" | ")
        }

        val isResumable = canResume()
        val isWatched = isWatched()
        val watchStatus = when {
            isWatched -> WatchStatus.WATCHED
            isResumable -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        }
        val playbackProgress = if (isResumable) {
            getWatchedPercentage().toFloat() / 100f
        } else null

        return PersonMediaModel(
            id = id.toString(),
            title = getDisplayTitle(),
            subtitle = subtitle.ifBlank { null },
            overview = overview,
            imageUrl = repositories.stream.getLandscapeImageUrl(this),
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
        )
    }
}
