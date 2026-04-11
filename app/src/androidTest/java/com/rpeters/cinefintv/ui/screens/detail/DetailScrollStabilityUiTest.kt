package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailOverviewSection
import com.rpeters.cinefintv.ui.screens.detail.cinematic.MovieDetailLayout
import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowDetailLayout
import com.rpeters.cinefintv.ui.screens.detail.cinematic.CinematicHero
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailTestTags
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DetailScrollStabilityUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun movieDetail_onLoad_staysAtTopWithoutDrift() {
        val listState = LazyListState()
        val focusRequester = FocusRequester()
        val anchorFocusRequester = FocusRequester()
        val scrollPositions = mutableListOf<Pair<Int, Int>>()

        composeRule.setContent {
            DetailTestHost {
                MovieDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Stability Test Movie",
                    eyebrow = "2026",
                    ratingText = null,
                    genres = emptyList(),
                    primaryActionLabel = "Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = focusRequester,
                    topFocusRequester = anchorFocusRequester,
                    description = "Description for stability test.",
                    heroTagline = null,
                    directorLine = null,
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

            LaunchedEffect(Unit) {
                // Record all scroll state changes
                launch {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .collect { 
                            scrollPositions.add(it) 
                        }
                }

                // Execute the focus routine that used to cause drift
                focusDetailScreenAtTop(
                    listState = listState,
                    initialFocusRequester = focusRequester,
                    anchorFocusRequester = anchorFocusRequester,
                )
            }
        }

        composeRule.waitForIdle()

        // 1. Final state MUST be at top (item index 0, scroll offset 0)
        assertEquals("Screen must end up at the very top (item index 0)", 0, listState.firstVisibleItemIndex)
        assertEquals("Screen must end up at the very top (scroll offset 0)", 0, listState.firstVisibleItemScrollOffset)

        // 2. Check the history of scroll positions.
        val maxDriftIndex = scrollPositions.maxOf { it.first }
        val maxDriftOffset = scrollPositions.maxOf { it.second }
        
        assertTrue(
            "Drift should be minimal and stay within item 0. Saw max drift index $maxDriftIndex, offset $maxDriftOffset. Full history: $scrollPositions",
            maxDriftIndex == 0 && maxDriftOffset < 50
        )
    }

    @Test
    fun tvShowDetail_onLoad_staysAtTopWithoutDrift() {
        val listState = LazyListState()
        val focusRequester = FocusRequester()
        val anchorFocusRequester = FocusRequester()
        val scrollPositions = mutableListOf<Pair<Int, Int>>()

        composeRule.setContent {
            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Stability Test Show",
                    eyebrow = "TV SERIES",
                    ratingText = null,
                    genres = emptyList(),
                    primaryActionLabel = "Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = focusRequester,
                    topFocusRequester = anchorFocusRequester,
                    nextUpTitle = null,
                    onNextUpClick = null,
                    seasons = emptyList(),
                    onSeasonClick = {},
                    castItems = emptyList(),
                    similarItems = emptyList(),
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "Description for stability test.",
                    heroTagline = null,
                    creditLine = null,
                    heroBadges = emptyList(),
                    heroSecondaryActions = emptyList(),
                    factItems = emptyList(),
                    listState = listState,
                )
            }

            LaunchedEffect(Unit) {
                launch {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .collect { scrollPositions.add(it) }
                }

                focusDetailScreenAtTop(
                    listState = listState,
                    initialFocusRequester = focusRequester,
                    anchorFocusRequester = anchorFocusRequester,
                )
            }
        }

        composeRule.waitForIdle()

        assertEquals("Screen must end up at the very top (item index 0)", 0, listState.firstVisibleItemIndex)
        assertEquals("Screen must end up at the very top (scroll offset 0)", 0, listState.firstVisibleItemScrollOffset)

        val maxDriftIndex = scrollPositions.maxOf { it.first }
        val maxDriftOffset = scrollPositions.maxOf { it.second }

        assertTrue(
            "Drift should be minimal and stay within item 0. Saw max drift index $maxDriftIndex, offset $maxDriftOffset. Full history: $scrollPositions",
            maxDriftIndex == 0 && maxDriftOffset < 50
        )
    }

    @Test
    fun seasonDetail_onLoad_staysAtTopWithoutDrift() {
        val listState = LazyListState()
        val focusRequester = FocusRequester()
        val anchorFocusRequester = FocusRequester()
        val scrollPositions = mutableListOf<Pair<Int, Int>>()

        composeRule.setContent {
            DetailTestHost {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Column {
                            DetailAnchor(
                                focusRequester = anchorFocusRequester,
                                downFocusRequester = focusRequester,
                                onFocused = {},
                            )
                            CinematicHero(
                                backdropUrl = null,
                                logoUrl = null,
                                title = "Season 1",
                                eyebrow = "Test Series · 1 episodes",
                                ratingText = null,
                                genres = emptyList(),
                                primaryActionLabel = "Play",
                                onPrimaryAction = {},
                                primaryActionFocusRequester = focusRequester,
                            )
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                launch {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                        .collect { scrollPositions.add(it) }
                }

                focusDetailScreenAtTop(
                    listState = listState,
                    initialFocusRequester = focusRequester,
                    anchorFocusRequester = anchorFocusRequester,
                )
            }
        }

        composeRule.waitForIdle()

        assertEquals("Screen must end up at the very top (item index 0)", 0, listState.firstVisibleItemIndex)
        assertEquals("Screen must end up at the very top (scroll offset 0)", 0, listState.firstVisibleItemScrollOffset)

        val maxDriftIndex = scrollPositions.maxOf { it.first }
        val maxDriftOffset = scrollPositions.maxOf { it.second }

        assertTrue(
            "Drift should be minimal and stay within item 0. Saw max drift index $maxDriftIndex, offset $maxDriftOffset. Full history: $scrollPositions",
            maxDriftIndex == 0 && maxDriftOffset < 50
        )
    }

    @Test
    fun movieDetail_scrollDownAndUp_reachesTop() {
        val listState = LazyListState()
        val focusRequester = FocusRequester()
        
        composeRule.setContent {
            DetailTestHost {
                MovieDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Scroll Test Movie",
                    eyebrow = "2026",
                    ratingText = null,
                    genres = emptyList(),
                    primaryActionLabel = "Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = focusRequester,
                    description = "A long description to ensure some height." + " Lorem ipsum ".repeat(50),
                    heroTagline = null,
                    directorLine = null,
                    heroBadges = emptyList(),
                    heroSecondaryActions = emptyList(),
                    factItems = emptyList(),
                    castItems = (1..10).map { CastModel("p$it", "Person $it", "Role", null) },
                    similarItems = (1..10).map {
                        SimilarMovieModel("m$it", "Similar $it", null, WatchStatus.NONE, null)
                    },
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }

        composeRule.waitForIdle()

        // 1. Scroll to the bottom (using item index 4 as a safe "bottom" for this layout)
        composeRule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(4)
            }
        }
        composeRule.waitForIdle()
        assertTrue("Should have scrolled away from top", listState.firstVisibleItemIndex > 0)

        // 2. Scroll back to the very top
        composeRule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(0)
            }
        }
        composeRule.waitForIdle()

        // 3. Verify we are at 0,0
        assertEquals("Should be at item index 0", 0, listState.firstVisibleItemIndex)
        assertEquals("Should be at scroll offset 0", 0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    fun tvShowDetail_scrollDownAndUp_reachesTop() {
        val listState = LazyListState()
        val focusRequester = FocusRequester()
        
        composeRule.setContent {
            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Scroll Test Show",
                    eyebrow = "TV SERIES",
                    ratingText = null,
                    genres = emptyList(),
                    primaryActionLabel = "Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = focusRequester,
                    nextUpTitle = null,
                    onNextUpClick = null,
                    seasons = (1..5).map {
                        SeasonModel(
                            id = "s$it",
                            title = "Season $it",
                            imageUrl = null,
                            watchStatus = WatchStatus.NONE,
                            playbackProgress = null,
                            unwatchedCount = 5,
                        )
                    },
                    onSeasonClick = {},
                    castItems = (1..10).map { CastModel("p$it", "Person $it", "Role", null) },
                    similarItems = (1..10).map {
                        SimilarMovieModel("m$it", "Similar $it", null, WatchStatus.NONE, null)
                    },
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "A long description." + " Lorem ipsum ".repeat(50),
                    heroTagline = null,
                    creditLine = null,
                    heroBadges = emptyList(),
                    heroSecondaryActions = emptyList(),
                    factItems = emptyList(),
                    listState = listState,
                )
            }
        }

        composeRule.waitForIdle()

        // 1. Scroll to the bottom (using item index 5 as a safe "bottom" for this layout)
        composeRule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(5)
            }
        }
        composeRule.waitForIdle()
        assertTrue("Should have scrolled away from top", listState.firstVisibleItemIndex > 0)

        // 2. Scroll back to the very top
        composeRule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(0)
            }
        }
        composeRule.waitForIdle()

        // 3. Verify we are at 0,0
        assertEquals("Should be at item index 0", 0, listState.firstVisibleItemIndex)
        assertEquals("Should be at scroll offset 0", 0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    fun seasonDetail_listsEpisodesCorrectly() {
        val listState = LazyListState()
        val episodes = (1..5).map {
            EpisodeModel(
                id = "e$it",
                title = "Episode $it",
                number = it,
                overview = "Overview $it",
                imageUrl = null,
                duration = "45m",
                videoQuality = "HD",
                audioLabel = "5.1",
                isWatched = false,
                playbackProgress = 0f,
                episodeCode = "S01E0$it"
            )
        }

        composeRule.setContent {
            DetailTestHost {
                LazyColumn(state = listState) {
                    item {
                        androidx.compose.foundation.layout.Column {
                            val focusRequester = remember { FocusRequester() }
                            DetailAnchor(focusRequester, onFocused = {})
                            CinematicHero(
                                backdropUrl = null,
                                logoUrl = null,
                                title = "Season 1",
                                eyebrow = "5 episodes",
                                ratingText = null,
                                genres = emptyList(),
                                primaryActionLabel = "Play",
                                onPrimaryAction = {},
                            )
                        }
                    }
                    
                    items(episodes, key = { it.id }) { episode ->
                        EpisodeListRow(
                            episode = episode,
                            onClick = {}
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()

        // Verify that episodes are listed in hierarchy
        val nodes = composeRule.onAllNodesWithTag(DetailTestTags.EpisodeItem).fetchSemanticsNodes()
        assertTrue("Episodes should be present in the list", nodes.isNotEmpty())
        
        // Check for specific episode text to be sure mapping worked
        composeRule.onNodeWithText("Episode 1").assertExists()
    }

    @Test
    fun seasonDetail_overviewDown_focusesFirstEpisode_withoutOverscrollingPastEpisodesHeader() {
        val listState = LazyListState()
        val overviewFocusRequester = FocusRequester()
        val firstEpisodeFocusRequester = FocusRequester()
        val episodes = (1..5).map {
            EpisodeModel(
                id = "e$it",
                title = "Episode $it",
                number = it,
                overview = "Overview $it",
                imageUrl = null,
                duration = "45m",
                videoQuality = "HD",
                audioLabel = "5.1",
                isWatched = false,
                playbackProgress = 0f,
                episodeCode = "S01E0$it"
            )
        }

        composeRule.setContent {
            DetailTestHost {
                val coroutineScope = rememberCoroutineScope()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Column {
                            val focusRequester = remember { FocusRequester() }
                            DetailAnchor(focusRequester, onFocused = {})
                            CinematicHero(
                                backdropUrl = null,
                                logoUrl = null,
                                title = "Season 1",
                                eyebrow = "5 episodes",
                                ratingText = null,
                                genres = emptyList(),
                                primaryActionLabel = "Play",
                                onPrimaryAction = {},
                            )
                        }
                    }
                    item {
                        DetailOverviewSection(
                            title = "Season 1",
                            posterUrl = null,
                            description = "Overview",
                            factItems = emptyList(),
                            chips = emptyList(),
                            focusRequester = overviewFocusRequester,
                            onNavigateDown = {
                                coroutineScope.launch {
                                    listState.scrollToItem(2)
                                    yield()
                                    firstEpisodeFocusRequester.requestFocus()
                                }
                            },
                        )
                    }
                    item {
                        DetailContentSection(
                            title = "Episodes",
                            eyebrow = "5 unwatched",
                            icon = Icons.Default.VideoLibrary,
                        ) {}
                    }
                    items(episodes, key = { it.id }) { episode ->
                        EpisodeListRow(
                            episode = episode,
                            modifier = if (episode.id == episodes.first().id) {
                                Modifier
                                    .focusRequester(firstEpisodeFocusRequester)
                                    .focusProperties { up = overviewFocusRequester }
                            } else {
                                Modifier
                            },
                            onClick = {},
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle { overviewFocusRequester.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Episode 1").assertIsDisplayed()
        assertTrue(
            "Season handoff should stop before overscrolling into the episode rows. Current index: ${listState.firstVisibleItemIndex}",
            listState.firstVisibleItemIndex < 3
        )
    }
}
