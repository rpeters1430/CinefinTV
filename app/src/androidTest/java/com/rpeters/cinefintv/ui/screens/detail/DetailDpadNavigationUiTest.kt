package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailTestTags
import com.rpeters.cinefintv.ui.screens.detail.cinematic.HeroSecondaryAction
import com.rpeters.cinefintv.ui.screens.detail.cinematic.MovieDetailLayout
import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowDetailLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DetailDpadNavigationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun movieDetail_primaryDownMovesToFirstCast() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel("c1", "Actor", "Lead", null)),
            similarItems = emptyList(),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
    }

    @Test
    fun movieDetail_castDownMovesToFirstSimilar() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel("c1", "Actor", "Lead", null)),
            similarItems = listOf(SimilarMovieModel("s1", "Similar", null, WatchStatus.NONE, null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // cast
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // similar
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstSimilarItem).assertIsFocused()
    }

    @Test
    fun tvShowDetail_primaryDownMovesToSeasons() {
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(),
            primaryActionFocusRequester = focus,
            seasons = listOf(
                SeasonModel(
                    id = "season-1",
                    title = "Season 1",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                    unwatchedCount = 2,
                )
            ),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstSeasonItem).assertIsFocused()
    }

    @Test
    fun tvShowDetail_seasonsDownMovesToNextUpWhenPresent() {
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(),
            primaryActionFocusRequester = focus,
            nextUpTitle = "S1E2",
            onNextUpClick = {},
            seasons = listOf(
                SeasonModel(
                    id = "season-1",
                    title = "Season 1",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                    unwatchedCount = 2,
                )
            ),
            castItems = listOf(CastModel("c1", "Actor", "Lead", null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // seasons
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // next up
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.TvNextUpAction).assertIsFocused()
    }

    private fun setMovieContent(
        listState: LazyListState,
        primaryActionFocusRequester: FocusRequester,
        castItems: List<CastModel> = emptyList(),
        similarItems: List<SimilarMovieModel> = emptyList(),
    ) {
        composeRule.setContent {
            DetailTestHost {
                MovieDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    title = "Movie",
                    metadataItems = listOf("2026", "2h"),
                    qualityBadges = listOf("4K"),
                    genres = emptyList(),
                    primaryActionLabel = "Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    description = "Movie description",
                    heroSecondaryActions = listOf(HeroSecondaryAction(label = "···", onClick = {})),
                    castItems = castItems,
                    similarItems = similarItems,
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }
    }

    private fun setTvShowContent(
        listState: LazyListState,
        primaryActionFocusRequester: FocusRequester,
        nextUpTitle: String? = null,
        onNextUpClick: (() -> Unit)? = null,
        seasons: List<SeasonModel> = emptyList(),
        castItems: List<CastModel> = emptyList(),
    ) {
        composeRule.setContent {
            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    title = "Show",
                    metadataItems = listOf("2020-2026", "2 seasons"),
                    qualityBadges = listOf("4K"),
                    genres = emptyList(),
                    primaryActionLabel = "Resume",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    nextUpTitle = nextUpTitle,
                    onNextUpClick = onNextUpClick,
                    seasons = seasons,
                    onSeasonClick = {},
                    castItems = castItems,
                    similarItems = listOf(SimilarMovieModel("s1", "Similar", null, WatchStatus.NONE, null)),
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "Show description",
                    heroSecondaryActions = listOf(HeroSecondaryAction(label = "···", onClick = {})),
                    listState = listState,
                )
            }
        }
    }
}
