package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailTestTags
import com.rpeters.cinefintv.ui.screens.detail.cinematic.MovieDetailLayout
import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowDetailLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DetailDpadNavigationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // -------------------------------------------------------------------------
    // MovieDetailLayout — focus routing tests
    // -------------------------------------------------------------------------

    @Test
    fun movieDetail_onLoad_playButtonHasFocus() {
        val focus = FocusRequester()
        setMovieContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction).assertIsFocused()
    }

    @Test
    fun movieDetail_playButton_downNavigatesToOverview() {
        val focus = FocusRequester()
        setMovieContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun movieDetail_cast_downDoesNotMoveFocus_whenNoSimilarPresent() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → cast
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // no lower section
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
    }

    @Test
    fun movieDetail_firstCastItem_upNavigatesToOverview() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 1),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → cast
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    // Also covers spec row 9: overview DOWN → similar when no cast but similar present
    fun movieDetail_overview_downNavigatesToFirstSimilar_whenNoCastButSimilarPresent() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            similarItems = listOf(
                SimilarMovieModel(
                    id = "s1",
                    title = "Similar",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                )
            ),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → similar
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstSimilarItem).assertIsFocused()
    }

    @Test
    fun movieDetail_firstSimilarItem_upNavigatesToCast_whenCastPresent() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 3),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
            similarItems = listOf(
                SimilarMovieModel(
                    id = "s1",
                    title = "Similar",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                )
            ),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        // Navigate to similar: play → overview → cast → similar
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // similar → cast
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
    }

    @Test
    fun movieDetail_overview_upReturnsFromCastPath() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 3),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
            similarItems = listOf(
                SimilarMovieModel(
                    id = "s1",
                    title = "Similar",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                )
            ),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // play -> cast
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // cast -> overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // overview -> similar
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) } // similar -> overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) } // cast -> overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun movieDetail_firstSimilarItem_upNavigatesToOverview_whenNoCast() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            similarItems = listOf(
                SimilarMovieModel(
                    id = "s1",
                    title = "Similar",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                )
            ),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → similar
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun movieDetail_overview_downDoesNotMoveFocus_whenNoOptionalRows() {
        val focus = FocusRequester()
        setMovieContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // no target
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun movieDetail_scrollDown_upFromPlayButton_returnsToTop() {
        val focus = FocusRequester()
        val topFocus = FocusRequester()
        val restoreSignal = mutableIntStateOf(0)
        val listState = LazyListState()
        setMovieContent(
            listState = listState,
            primaryActionFocusRequester = focus,
            topFocusRequester = topFocus,
            restoreSignal = restoreSignal,
            castItems = (1..10).map { CastModel("c$it", "Actor $it", "Role", null) },
            similarItems = (1..10).map {
                SimilarMovieModel("s$it", "Similar $it", null, WatchStatus.NONE, null)
            }
        )

        // 1. Initial focus on play button
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()

        // 2. Scroll down manually to some offset (item 3 is usually cast row)
        composeRule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(3) 
            }
        }
        composeRule.waitForIdle()
        assertTrue("Should be scrolled down. Current index: ${listState.firstVisibleItemIndex}", listState.firstVisibleItemIndex > 0)

        // 3. Trigger the same top-restore routine the real detail screen uses.
        composeRule.runOnIdle {
            restoreSignal.intValue += 1
        }
        composeRule.waitForIdle()

        // 4. Press UP
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        // 5. Verify we are back at 0,0
        assertEquals("Should be at item index 0 after scrolling back up", 0, listState.firstVisibleItemIndex)
        assertEquals("Should be at scroll offset 0 after scrolling back up", 0, listState.firstVisibleItemScrollOffset)
    }

    // -------------------------------------------------------------------------
    // TvShowDetailLayout — focus routing tests
    // -------------------------------------------------------------------------

    @Test
    fun tvShowDetail_onLoad_playButtonHasFocus() {
        val focus = FocusRequester()
        setTvShowContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction).assertIsFocused()
    }

    @Test
    fun tvShowDetail_playButton_downNavigatesToOverview() {
        val focus = FocusRequester()
        setTvShowContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun tvShowDetail_overview_downDoesNotMoveFocus_whenOnlySeasonsAreAboveOverview() {
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            seasons = listOf(
                SeasonModel(
                    id = "s1",
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
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → season
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // no lower section
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun tvShowDetail_firstSeason_upNavigatesToPrimaryAction() {
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 1),
            primaryActionFocusRequester = focus,
            seasons = listOf(
                SeasonModel(
                    id = "s1",
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
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → season
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → play
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.PrimaryAction).assertIsFocused()
    }

    @Test
    fun tvShowDetail_cast_downDoesNotMoveFocus_whenNoSimilarPresent() {
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → season
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → cast
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // no lower section
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
    }

    @Test
    fun tvShowDetail_firstCast_upNavigatesToOverview_whenBothPresent() {
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 1),
            primaryActionFocusRequester = focus,
            seasons = listOf(
                SeasonModel(
                    id = "s1",
                    title = "Season 1",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                    unwatchedCount = 2,
                )
            ),
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
        )

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).requestFocus()
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun tvShowDetail_firstSimilar_upNavigatesToCast_whenCastPresent() {
        // Seasons + cast + similar all present; similar is at LazyColumn index 5
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 4),
            primaryActionFocusRequester = focus,
            seasons = listOf(
                SeasonModel(
                    id = "s1",
                    title = "Season 1",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                    unwatchedCount = 2,
                )
            ),
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
            similarItems = listOf(
                SimilarMovieModel(
                    id = "sim1",
                    title = "Similar Show",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                )
            ),
        )

        // Navigate: play → overview → season → cast → similar
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // similar → cast
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
    }

    @Test
    fun tvShowDetail_overview_upReturnsFromCastPath() {
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 4),
            primaryActionFocusRequester = focus,
            seasons = listOf(
                SeasonModel(
                    id = "s1",
                    title = "Season 1",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                    unwatchedCount = 2,
                )
            ),
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
            similarItems = listOf(
                SimilarMovieModel(
                    id = "sim1",
                    title = "Similar Show",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                )
            ),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // play -> season
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // season -> cast
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // cast -> overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // overview -> similar
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) } // similar -> overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) } // cast -> overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun tvShowDetail_firstSimilar_upNavigatesToOverview_whenNoCast() {
        // Seasons + similar (no cast); similar is at LazyColumn index 4
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 3),
            primaryActionFocusRequester = focus,
            seasons = listOf(
                SeasonModel(
                    id = "s1",
                    title = "Season 1",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                    unwatchedCount = 2,
                )
            ),
            similarItems = listOf(
                SimilarMovieModel(
                    id = "sim1",
                    title = "Similar Show",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                )
            ),
        )

        // Navigate: play → overview → season → similar
        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // similar → overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun tvShowDetail_firstSimilar_upNavigatesToOverview_whenNoSeasonsNoCast() {
        // Similar only; similar is at LazyColumn index 3
        val focus = FocusRequester()
        setTvShowContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            similarItems = listOf(
                SimilarMovieModel(
                    id = "sim1",
                    title = "Similar Show",
                    imageUrl = null,
                    watchStatus = WatchStatus.NONE,
                    playbackProgress = null,
                )
            ),
        )

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → similar
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    fun tvShowDetail_overview_downDoesNotMoveFocus_whenNoOptionalRows() {
        val focus = FocusRequester()
        setTvShowContent(LazyListState(), focus)

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // → overview
        composeRule.waitForIdle()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } // no target
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    // TV Show layout helper — note: no factSummary parameter
    private fun setTvShowContent(
        listState: LazyListState,
        primaryActionFocusRequester: FocusRequester,
        seasons: List<SeasonModel> = emptyList(),
        castItems: List<CastModel> = emptyList(),
        similarItems: List<SimilarMovieModel> = emptyList(),
    ) {
        composeRule.setContent {
            DetailTestHost {
                TvShowDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Test Show",
                    eyebrow = "TV SERIES",
                    ratingText = null,
                    genres = emptyList(),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    nextUpTitle = null,
                    onNextUpClick = null,
                    seasons = seasons,
                    onSeasonClick = {},
                    castItems = castItems,
                    similarItems = similarItems,
                    onCastClick = {},
                    onSimilarClick = {},
                    description = "Test overview.",
                    heroTagline = null,
                    creditLine = null,
                    heroBadges = emptyList(),
                    heroSecondaryActions = emptyList(),
                    factItems = emptyList(),
                    listState = listState,
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Layout setup helpers
    // -------------------------------------------------------------------------

    private fun setMovieContent(
        listState: LazyListState,
        primaryActionFocusRequester: FocusRequester,
        topFocusRequester: FocusRequester = FocusRequester(),
        restoreSignal: MutableIntState? = null,
        castItems: List<CastModel> = emptyList(),
        similarItems: List<SimilarMovieModel> = emptyList(),
    ) {
        composeRule.setContent {
            val restoreToken = restoreSignal?.intValue ?: 0

            DetailTestHost {
                LaunchedEffect(restoreToken) {
                    if (restoreSignal != null && restoreToken > 0) {
                        focusDetailScreenAtTop(
                            listState = listState,
                            initialFocusRequester = primaryActionFocusRequester,
                            anchorFocusRequester = topFocusRequester,
                        )
                    }
                }

                MovieDetailLayout(
                    backdropUrl = null,
                    posterUrl = null,
                    logoUrl = null,
                    title = "Test Movie",
                    eyebrow = "2026 · 2h",
                    ratingText = null,
                    genres = emptyList(),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    topFocusRequester = topFocusRequester,
                    description = "Test overview.",
                    heroTagline = null,
                    directorLine = null,
                    heroBadges = emptyList(),
                    heroSecondaryActions = emptyList(),
                    factItems = emptyList(),
                    castItems = castItems,
                    similarItems = similarItems,
                    onCastClick = {},
                    onSimilarClick = {},
                    listState = listState,
                )
            }
        }
    }
}
