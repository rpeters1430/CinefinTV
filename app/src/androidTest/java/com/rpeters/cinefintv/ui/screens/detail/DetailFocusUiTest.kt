package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
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
class DetailFocusUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun movieDetail_rendersHeroCastAndSimilar() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val listState = rememberLazyListState()

            Box(modifier = Modifier.size(1920.dp, 2000.dp)) {
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
        }

        composeRule.onNodeWithTag(DetailTestTags.HeroTitle, useUnmergedTree = true).assertIsDisplayed()
        
        // Scroll to ensure nodes are composed in LazyColumn
        composeRule.onNodeWithTag(DetailTestTags.MovieCastSection, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Jane Doe", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag(DetailTestTags.MovieSimilarSection, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Another Movie", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tvShowDetail_rendersSeasonsNextUpCastAndSimilar() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val listState = rememberLazyListState()

            Box(modifier = Modifier.size(1920.dp, 3000.dp)) {
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
        }

        composeRule.onNodeWithTag(DetailTestTags.TvEpisodesPanel, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DetailTestTags.TvNextUpPanel, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DetailTestTags.TvCastPanel, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DetailTestTags.TvSimilarPanel, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
