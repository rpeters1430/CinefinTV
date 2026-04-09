package com.rpeters.cinefintv.ui.screens.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getItemTypeString
import com.rpeters.cinefintv.utils.toMediaCardPresentation
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
    val itemType: String?,
    val watchStatus: WatchStatus = WatchStatus.NONE,
    val playbackProgress: Float? = null,
    val unwatchedCount: Int? = null,
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
) : ViewModel() {

    private var personId: String = ""

    private val _uiState = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    fun init(id: String) {
        if (personId == id) return
        personId = id
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
        val presentation = toMediaCardPresentation()

        return PersonMediaModel(
            id = id.toString(),
            title = getDisplayTitle(),
            subtitle = presentation.subtitle,
            overview = overview,
            imageUrl = repositories.stream.getPosterCardImageUrl(this),
            itemType = getItemTypeString(),
            watchStatus = presentation.watchStatus,
            playbackProgress = presentation.playbackProgress,
            unwatchedCount = presentation.unwatchedCount,
        )
    }
}
