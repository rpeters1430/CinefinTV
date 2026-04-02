package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailTestTags
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
    fun movieDetail_rendersHeroAndOverview() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val listState = rememberLazyListState()

            DetailTestHost {
                MovieDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Example Movie",
                    eyebrow = "2026 · 2h 10m",
                    ratingText = "★ 8.3",
                    genres = listOf("Action", "Thriller"),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    description = "A test movie overview.",
                    heroTagline = null,
                    directorLine = "Director · Studio",
                    heroBadges = emptyList(),
                    heroSecondaryActions = emptyList(),
                    factItems = emptyList(),
                    castItems = emptyList(),
                    similarItems = emptyList(),
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.HeroTitle).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.PrimaryAction).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.Overview).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("A test movie overview.").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun tvShowDetail_rendersSeasonsShelf() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            // Pre-scroll to ensure it's composed
            val listState = remember { LazyListState(firstVisibleItemIndex = 2) }

            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Example Show",
                    eyebrow = "TV SERIES · 2 SEASONS",
                    ratingText = "★ 8.9",
                    genres = listOf("Drama", "Mystery"),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
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
                    castItems = emptyList(),
                    similarItems = emptyList(),
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "A test show overview.",
                    heroTagline = null,
                    creditLine = null,
                    heroBadges = emptyList(),
                    factItems = emptyList(),
                    listState = listState,
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.TvEpisodesPanel).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Season 1").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun movieDetail_rendersCastAndSimilarSections() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            // Start near the first post-hero section so People, Overview, and Similar remain composed.
            val listState = remember { LazyListState(firstVisibleItemIndex = 1) }

            DetailTestHost {
                MovieDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Coverage Movie",
                    eyebrow = "2026 · 1h 55m",
                    ratingText = "★ 7.8",
                    genres = listOf("Sci-Fi", "Drama"),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    description = "Coverage overview text.",
                    heroTagline = null,
                    directorLine = "Writer · Studio",
                    heroBadges = emptyList(),
                    heroSecondaryActions = emptyList(),
                    factItems = emptyList(),
                    castItems = listOf(
                        CastModel(id = "person-1", name = "Jane Doe", role = "Lead", imageUrl = null),
                    ),
                    similarItems = listOf(
                        SimilarMovieModel(
                            id = "movie-2",
                            title = "Another Movie",
                            imageUrl = null,
                            watchStatus = WatchStatus.NONE,
                            playbackProgress = null,
                        ),
                    ),
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.MovieCastSection).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Jane Doe").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.MovieSimilarSection).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Another Movie").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun movieDetail_withoutLogo_keepsHeroTitleVisible() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val listState = rememberLazyListState()

            DetailTestHost {
                MovieDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Fallback Movie",
                    eyebrow = "2026 · 1h 30m",
                    ratingText = "★ 7.1",
                    genres = listOf("Adventure"),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    description = "Fallback movie overview.",
                    heroTagline = null,
                    directorLine = "Studio",
                    heroBadges = emptyList(),
                    heroSecondaryActions = emptyList(),
                    factItems = emptyList(),
                    castItems = emptyList(),
                    similarItems = emptyList(),
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.HeroTitle).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.HeroLogo).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun movieDetail_blankOverview_showsFallbackCopy() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val listState = rememberLazyListState()

            DetailTestHost {
                MovieDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "No Overview Movie",
                    eyebrow = "2026 · 1h 20m",
                    ratingText = "★ 6.9",
                    genres = listOf("Comedy"),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    description = "",
                    heroTagline = null,
                    directorLine = "Studio",
                    heroBadges = emptyList(),
                    heroSecondaryActions = emptyList(),
                    factItems = emptyList(),
                    castItems = emptyList(),
                    similarItems = emptyList(),
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.Overview).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("No overview available.").fetchSemanticsNodes().isNotEmpty())
    }
}
