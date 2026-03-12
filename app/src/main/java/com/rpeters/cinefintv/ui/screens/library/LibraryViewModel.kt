package com.rpeters.cinefintv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.ui.screens.home.HomeCardModel
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getFormattedDuration
import com.rpeters.cinefintv.utils.getUnwatchedEpisodeCardLabel
import com.rpeters.cinefintv.utils.getWatchedPercentage
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isMovie
import com.rpeters.cinefintv.utils.isSeries
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import javax.inject.Inject

sealed class LibraryUiState {
    data object Loading : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
    data class Content(
        val title: String,
        val items: List<HomeCardModel>,
    ) : LibraryUiState()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repositories: JellyfinRepositoryCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var lastCategory: LibraryCategory? = null

    fun load(category: LibraryCategory, forceRefresh: Boolean = false) {
        if (!forceRefresh && lastCategory == category && _uiState.value is LibraryUiState.Content) {
            return
        }
        lastCategory = category

        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading

            when (
                val result = repositories.media.getLibraryItems(
                    itemTypes = category.itemTypes,
                    limit = 10_000,
                    collectionType = category.collectionType,
                )
            ) {
                is ApiResult.Success -> {
                    val items = result.data.mapNotNull(::toCardModel)
                    _uiState.value = if (items.isEmpty()) {
                        LibraryUiState.Error("No ${category.title.lowercase()} were found.")
                    } else {
                        LibraryUiState.Content(
                            title = category.title,
                            items = items,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.value = LibraryUiState.Error(result.message)
                }
                is ApiResult.Loading -> Unit
            }
        }
    }

    private fun toCardModel(item: BaseItemDto): HomeCardModel? {
        val id = item.id.toString()
        val isResumable = item.canResume()
        val isWatched = item.isWatched()
        val watchStatus = when {
            isWatched -> WatchStatus.WATCHED
            isResumable -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        }
        val playbackProgress = if (isResumable) {
            item.getWatchedPercentage().toFloat() / 100f
        } else null

        val subtitle = when {
            item.isMovie() -> listOfNotNull(
                item.getYear()?.toString(),
                item.getFormattedDuration(),
                item.communityRating?.let { "★ ${"%.1f".format(it)}" },
            ).joinToString(" · ").ifBlank { item.type.toString().replace('_', ' ') }
            item.isSeries() -> item.getUnwatchedEpisodeCardLabel()
                ?: item.getYear()?.toString()
                ?: item.type.toString().replace('_', ' ')
            else -> item.getYear()?.toString()
                ?: item.getFormattedDuration()
                ?: item.type.toString().replace('_', ' ')
        }

        return HomeCardModel(
            id = id,
            title = item.getDisplayTitle(),
            subtitle = subtitle,
            imageUrl = repositories.stream.getLandscapeImageUrl(item),
            watchStatus = watchStatus,
            playbackProgress = playbackProgress,
        )
    }
}
