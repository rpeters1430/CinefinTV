package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailTestTags
import com.rpeters.cinefintv.ui.screens.detail.cinematic.HeroSecondaryAction
import com.rpeters.cinefintv.ui.screens.detail.cinematic.MovieDetailLayout
import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowDetailLayout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DetailFocusUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun movieDetail_rendersHeroCastAndSimilar() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val listState = rememberLazyListState()

            DetailTestHost {
                MovieDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    title = "Example Movie",
                    metadataItems = listOf("2026", "2h 10m", "PG-13"),
                    qualityBadges = listOf("4K", "HDR"),
                    genres = listOf("Action", "Thriller"),
                    primaryActionLabel = "Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    description = "A test movie overview.",
                    heroSecondaryActions = listOf(HeroSecondaryAction(label = "+ Watchlist", onClick = {})),
                    castItems = listOf(CastModel(id = "p1", name = "Jane Doe", role = "Lead", imageUrl = null)),
                    similarItems = listOf(
                        SimilarMovieModel(
                            id = "s1",
                            title = "Another Movie",
                            imageUrl = null,
                            watchStatus = WatchStatus.NONE,
                            playbackProgress = null,
                        )
                    ),
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.HeroTitle).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.PrimaryAction).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.MovieCastSection).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.MovieSimilarSection).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Jane Doe").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Another Movie").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun tvShowDetail_rendersSeasonsNextUpCastAndSimilar() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val listState = rememberLazyListState()

            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    title = "Example Show",
                    metadataItems = listOf("2021-2026", "3 seasons", "Airing"),
                    qualityBadges = listOf("4K"),
                    genres = listOf("Drama"),
                    primaryActionLabel = "Resume",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    nextUpTitle = "S3 · E4",
                    onNextUpClick = {},
                    seasons = listOf(
                        SeasonModel(
                            id = "season-1",
                            title = "Season 1",
                            imageUrl = null,
                            watchStatus = WatchStatus.NONE,
                            playbackProgress = null,
                            unwatchedCount = 1,
                        )
                    ),
                    onSeasonClick = {},
                    castItems = listOf(CastModel(id = "p1", name = "Actor One", role = "Lead", imageUrl = null)),
                    similarItems = listOf(
                        SimilarMovieModel(
                            id = "show-2",
                            title = "Similar Show",
                            imageUrl = null,
                            watchStatus = WatchStatus.NONE,
                            playbackProgress = null,
                        )
                    ),
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "A test show overview.",
                    heroSecondaryActions = listOf(HeroSecondaryAction(label = "···", onClick = {})),
                    listState = listState,
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.TvEpisodesPanel).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.TvNextUpPanel).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.TvCastPanel).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.TvSimilarPanel).fetchSemanticsNodes().isNotEmpty())
    }
}
