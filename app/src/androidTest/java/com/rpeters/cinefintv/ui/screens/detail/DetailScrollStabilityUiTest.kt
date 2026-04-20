package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailTestTags
import com.rpeters.cinefintv.ui.screens.detail.cinematic.FlatDetailHero
import com.rpeters.cinefintv.ui.screens.detail.cinematic.HeroSecondaryAction
import com.rpeters.cinefintv.ui.screens.detail.cinematic.MovieDetailLayout
import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowDetailLayout
import kotlinx.coroutines.launch
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
                    title = "Stability Test Movie",
                    metadataItems = listOf("2026"),
                    qualityBadges = emptyList(),
                    genres = emptyList(),
                    primaryActionLabel = "Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = focusRequester,
                    topFocusRequester = anchorFocusRequester,
                    description = "Description for stability test.",
                    heroSecondaryActions = listOf(HeroSecondaryAction(label = "···", onClick = {})),
                    castItems = emptyList(),
                    similarItems = emptyList(),
                    onCastClick = {},
                    onSimilarClick = {},
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
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
        assertTrue(scrollPositions.maxOf { it.first } == 0)
    }

    @Test
    fun tvShowDetail_onLoad_staysAtTopWithoutDrift() {
        val listState = LazyListState()
        val focusRequester = FocusRequester()
        val anchorFocusRequester = FocusRequester()

        composeRule.setContent {
            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    title = "Stability Test Show",
                    metadataItems = listOf("TV SERIES"),
                    qualityBadges = emptyList(),
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
                    heroSecondaryActions = listOf(HeroSecondaryAction(label = "···", onClick = {})),
                    listState = listState,
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }

    @Test
    fun seasonEpisodeRows_renderAndExposeEpisodeTag() {
        val episodes = (1..3).map {
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
                playbackProgress = if (it == 1) 0.4f else 0f,
                episodeCode = "S01 · E0$it",
            )
        }

        composeRule.setContent {
            DetailTestHost {
                androidx.compose.foundation.lazy.LazyColumn(state = rememberLazyListState()) {
                    item {
                        FlatDetailHero(
                            backdropUrl = null,
                            posterUrl = null,
                            title = "Season 1",
                            metadataItems = listOf("3 episodes"),
                            qualityBadges = emptyList(),
                            genres = emptyList(),
                            summary = "Season overview",
                            primaryActionLabel = "Resume",
                            onPrimaryAction = {},
                            secondaryActions = listOf(HeroSecondaryAction(label = "···", onClick = {})),
                            primaryActionFocusRequester = remember { FocusRequester() },
                        )
                    }
                    items(episodes, key = { it.id }) { episode ->
                        EpisodeListRow(
                            episode = episode,
                            isNext = episode.id == "e1",
                            onClick = {},
                        )
                    }
                }
            }
        }

        assertTrue(composeRule.onAllNodesWithText("Episode 1").fetchSemanticsNodes().isNotEmpty())
        val nodes = composeRule.onAllNodesWithTag(DetailTestTags.EpisodeItem).fetchSemanticsNodes()
        assertTrue(nodes.size >= 3)
    }
}
