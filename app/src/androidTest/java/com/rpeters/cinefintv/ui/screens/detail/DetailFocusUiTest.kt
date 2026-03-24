package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailTestTags
import com.rpeters.cinefintv.ui.screens.detail.cinematic.MovieDetailLayout
import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowDetailLayout
import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowTab
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController
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
    fun movieDetail_primaryActionDown_movesFocusToOverview() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val overviewFocusRequester = remember { FocusRequester() }
            val listState = remember { LazyListState(firstVisibleItemIndex = 2) }

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
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    overviewFocusRequester = overviewFocusRequester,
                    description = "A test movie overview.",
                    factItems = emptyList(),
                    factSummary = "Director · Studio",
                    castItems = emptyList(),
                    similarItems = emptyList(),
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }

        }

        composeRule.onAllNodesWithText("Example Movie").fetchSemanticsNodes()
        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction, useUnmergedTree = true)
            .requestFocus()
        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction, useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(DetailTestTags.Overview, useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun tvDetail_primaryActionDown_movesFocusToEpisodesRail_andEpisodesPanelRenders() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val episodeListState = rememberLazyListState()
            val castGridState = rememberLazyGridState()
            val similarGridState = rememberLazyGridState()

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
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    seasons = listOf(
                        SeasonModel(
                            id = "season-1",
                            title = "Season 1",
                            imageUrl = null,
                            episodeCount = 2,
                            unwatchedCount = 1,
                        )
                    ),
                    selectedSeasonIndex = 0,
                    onSeasonSelected = {},
                    episodes = listOf(
                        EpisodeModel(
                            id = "episode-1",
                            title = "Pilot",
                            number = 1,
                            overview = "Pilot overview",
                            imageUrl = null,
                            duration = "44m",
                            videoQuality = null,
                            audioLabel = null,
                            isWatched = false,
                            playbackProgress = null,
                            episodeCode = "S1:E1",
                        )
                    ),
                    resumeEpisodeIndex = 0,
                    onEpisodeClick = {},
                    castItems = listOf(
                        CastModel(id = "person-1", name = "Jane Doe", role = "Lead", imageUrl = null)
                    ),
                    similarItems = listOf(
                        SimilarMovieModel(id = "show-2", title = "Another Show", imageUrl = null)
                    ),
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "A test show overview.",
                    factItems = emptyList(),
                    factSummary = "Creator · Network",
                    selectedTab = TvShowTab.Episodes,
                    onTabSelected = {},
                    episodeListState = episodeListState,
                    castGridState = castGridState,
                    similarGridState = similarGridState,
                )
            }

        }

        composeRule.onAllNodesWithText("Example Show").fetchSemanticsNodes()
        composeRule.onNodeWithText("Pilot", useUnmergedTree = true)
        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction, useUnmergedTree = true)
            .requestFocus()
        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction, useUnmergedTree = true).assertIsFocused()
        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(
            DetailTestTags.tvTab(TvShowTab.Episodes),
            useUnmergedTree = true,
        ).assertIsFocused()
    }

    @Test
    fun movieDetail_rendersOverviewCastAndSimilarSections() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val overviewFocusRequester = remember { FocusRequester() }
            val listState = remember { LazyListState(firstVisibleItemIndex = 2) }

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
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    overviewFocusRequester = overviewFocusRequester,
                    description = "Coverage overview text.",
                    factItems = emptyList(),
                    factSummary = "Writer · Studio",
                    castItems = listOf(
                        CastModel(id = "person-1", name = "Jane Doe", role = "Lead", imageUrl = null),
                    ),
                    similarItems = listOf(
                        SimilarMovieModel(id = "movie-2", title = "Another Movie", imageUrl = null),
                    ),
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }

        composeRule.onAllNodesWithText("Coverage Movie").fetchSemanticsNodes()
        composeRule.onNodeWithTag(DetailTestTags.MovieCastSection).assertIsDisplayed()
        composeRule.onNodeWithText("Jane Doe").fetchSemanticsNode()
        composeRule.onNodeWithTag(DetailTestTags.MovieSimilarSection).assertIsDisplayed()
        composeRule.onNodeWithText("Another Movie").fetchSemanticsNode()
    }

    @Test
    fun tvDetail_tabSwitching_rendersCastSimilarAndDetailsPanels() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val episodeListState = rememberLazyListState()
            val castGridState = rememberLazyGridState()
            val similarGridState = rememberLazyGridState()
            var selectedTab by remember { mutableStateOf(TvShowTab.Episodes) }

            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Tabbed Show",
                    eyebrow = "TV SERIES · 1 SEASON",
                    ratingText = "★ 9.1",
                    genres = listOf("Drama"),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    seasons = listOf(
                        SeasonModel(
                            id = "season-1",
                            title = "Season 1",
                            imageUrl = null,
                            episodeCount = 1,
                            unwatchedCount = 1,
                        )
                    ),
                    selectedSeasonIndex = 0,
                    onSeasonSelected = {},
                    episodes = listOf(
                        EpisodeModel(
                            id = "episode-1",
                            title = "Pilot",
                            number = 1,
                            overview = "Pilot overview",
                            imageUrl = null,
                            duration = "44m",
                            videoQuality = null,
                            audioLabel = null,
                            isWatched = false,
                            playbackProgress = null,
                            episodeCode = "S1:E1",
                        )
                    ),
                    resumeEpisodeIndex = 0,
                    onEpisodeClick = {},
                    castItems = listOf(
                        CastModel(id = "person-1", name = "John Actor", role = "Lead", imageUrl = null)
                    ),
                    similarItems = listOf(
                        SimilarMovieModel(id = "show-2", title = "Sibling Show", imageUrl = null)
                    ),
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "Tabbed show overview.",
                    factItems = emptyList(),
                    factSummary = "Creator · Network",
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    episodeListState = episodeListState,
                    castGridState = castGridState,
                    similarGridState = similarGridState,
                )
            }
        }

        composeRule.onNodeWithText("Pilot").fetchSemanticsNode()
        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction, useUnmergedTree = true)
            .requestFocus()
        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(
            DetailTestTags.tvTab(TvShowTab.Episodes),
            useUnmergedTree = true,
        ).assertIsFocused()

        composeRule.onNodeWithTag(DetailTestTags.tvTab(TvShowTab.Episodes), useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(
            DetailTestTags.tvTab(TvShowTab.Cast),
            useUnmergedTree = true,
        ).assertIsFocused()
        composeRule.onNodeWithTag(DetailTestTags.tvTab(TvShowTab.Cast), useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DetailTestTags.TvCastPanel).fetchSemanticsNode()
        composeRule.onNodeWithText("John Actor").fetchSemanticsNode()

        composeRule.onNodeWithTag(DetailTestTags.tvTab(TvShowTab.Cast), useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(
            DetailTestTags.tvTab(TvShowTab.Similar),
            useUnmergedTree = true,
        ).assertIsFocused()
        composeRule.onNodeWithTag(DetailTestTags.tvTab(TvShowTab.Similar), useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DetailTestTags.TvSimilarPanel).fetchSemanticsNode()
        composeRule.onNodeWithText("Sibling Show").fetchSemanticsNode()

        composeRule.onNodeWithTag(DetailTestTags.tvTab(TvShowTab.Similar), useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(
            DetailTestTags.tvTab(TvShowTab.Details),
            useUnmergedTree = true,
        ).assertIsFocused()
        composeRule.onNodeWithTag(DetailTestTags.tvTab(TvShowTab.Details), useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DetailTestTags.TvDetailsPanel).fetchSemanticsNode()
        composeRule.onNodeWithText("Tabbed show overview.").fetchSemanticsNode()
    }

    @Test
    fun movieDetail_withoutLogo_keepsHeroTitleVisible() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val overviewFocusRequester = remember { FocusRequester() }
            val listState = remember { LazyListState(firstVisibleItemIndex = 0) }

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
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    overviewFocusRequester = overviewFocusRequester,
                    description = "Fallback movie overview.",
                    factItems = emptyList(),
                    factSummary = "Studio",
                    castItems = emptyList(),
                    similarItems = emptyList(),
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }

        composeRule.onNodeWithTag(DetailTestTags.HeroTitle).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.HeroLogo).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun tvDetail_selectedPanels_andHeroTitleRenderWithoutLogo() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }

            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Fallback Show",
                    eyebrow = "TV SERIES · 1 SEASON",
                    ratingText = "★ 8.0",
                    genres = listOf("Drama"),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    seasons = listOf(
                        SeasonModel(
                            id = "season-1",
                            title = "Season 1",
                            imageUrl = null,
                            episodeCount = 1,
                            unwatchedCount = 0,
                        )
                    ),
                    selectedSeasonIndex = 0,
                    onSeasonSelected = {},
                    episodes = listOf(
                        EpisodeModel(
                            id = "episode-1",
                            title = "Pilot",
                            number = 1,
                            overview = "Pilot overview",
                            imageUrl = null,
                            duration = "44m",
                            videoQuality = null,
                            audioLabel = null,
                            isWatched = false,
                            playbackProgress = null,
                            episodeCode = "S1:E1",
                        )
                    ),
                    resumeEpisodeIndex = 0,
                    onEpisodeClick = {},
                    castItems = listOf(
                        CastModel(id = "person-1", name = "Focus Actor", role = "Lead", imageUrl = null)
                    ),
                    similarItems = listOf(
                        SimilarMovieModel(id = "show-2", title = "Companion Show", imageUrl = null)
                    ),
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "Fallback show overview.",
                    factItems = emptyList(),
                    factSummary = "Creator · Network",
                    selectedTab = TvShowTab.Details,
                    onTabSelected = {},
                    episodeListState = rememberLazyListState(),
                    castGridState = rememberLazyGridState(),
                    similarGridState = rememberLazyGridState(),
                )
            }
        }

        composeRule.onNodeWithTag(DetailTestTags.HeroTitle).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(DetailTestTags.HeroLogo).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag(DetailTestTags.TvDetailsPanel).fetchSemanticsNode()
        composeRule.onNodeWithText("Fallback show overview.").fetchSemanticsNode()
    }

    @Test
    fun movieDetail_blankOverview_showsFallbackCopy() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }
            val overviewFocusRequester = remember { FocusRequester() }
            val listState = remember { LazyListState(firstVisibleItemIndex = 1) }

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
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    overviewFocusRequester = overviewFocusRequester,
                    description = "",
                    factItems = emptyList(),
                    factSummary = "Studio",
                    castItems = emptyList(),
                    similarItems = emptyList(),
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }

        composeRule.onNodeWithTag(DetailTestTags.Overview).fetchSemanticsNode()
        composeRule.onNodeWithText("No overview available.").fetchSemanticsNode()
    }

    @Test
    fun tvDetail_blankDetailsOverview_showsFallbackCopy() {
        composeRule.setContent {
            val primaryActionFocusRequester = remember { FocusRequester() }

            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "No Overview Show",
                    eyebrow = "TV SERIES · 1 SEASON",
                    ratingText = "★ 7.4",
                    genres = listOf("Drama"),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    seasons = listOf(
                        SeasonModel(
                            id = "season-1",
                            title = "Season 1",
                            imageUrl = null,
                            episodeCount = 1,
                            unwatchedCount = 0,
                        )
                    ),
                    selectedSeasonIndex = 0,
                    onSeasonSelected = {},
                    episodes = listOf(
                        EpisodeModel(
                            id = "episode-1",
                            title = "Pilot",
                            number = 1,
                            overview = "Pilot overview",
                            imageUrl = null,
                            duration = "44m",
                            videoQuality = null,
                            audioLabel = null,
                            isWatched = false,
                            playbackProgress = null,
                            episodeCode = "S1:E1",
                        )
                    ),
                    resumeEpisodeIndex = 0,
                    onEpisodeClick = {},
                    castItems = emptyList(),
                    similarItems = emptyList(),
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "",
                    factItems = emptyList(),
                    factSummary = "Creator · Network",
                    selectedTab = TvShowTab.Details,
                    onTabSelected = {},
                    episodeListState = rememberLazyListState(),
                    castGridState = rememberLazyGridState(),
                    similarGridState = rememberLazyGridState(),
                )
            }
        }

        composeRule.onNodeWithTag(DetailTestTags.TvDetailsPanel).fetchSemanticsNode()
        composeRule.onNodeWithText("No overview available.").fetchSemanticsNode()
    }
}

private fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }

@Composable
private fun DetailTestHost(content: @Composable () -> Unit) {
    CinefinTvTheme(useDynamicColors = false) {
        CompositionLocalProvider(
            LocalCinefinThemeController provides object : ThemeColorController {
                override fun updateSeedColor(color: Color?) = Unit
            }
        ) {
            content()
        }
    }
}
