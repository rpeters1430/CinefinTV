package com.rpeters.cinefintv.ui.screens.detail

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
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
import com.rpeters.cinefintv.ui.screens.detail.cinematic.DetailTestTags
import com.rpeters.cinefintv.ui.screens.detail.cinematic.MovieDetailLayout
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
    fun movieDetail_overview_downNavigatesToFirstCastItem() {
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

        composeRule.onNodeWithTag(DetailTestTags.FirstCastItem).assertIsFocused()
    }

    @Test
    fun movieDetail_firstCastItem_upNavigatesToOverview() {
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
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }   // → overview
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DetailTestTags.Overview).assertIsFocused()
    }

    @Test
    // Also covers spec row 9: overview DOWN → similar when no cast but similar present
    fun movieDetail_overview_downNavigatesToFirstSimilar_whenNoCast() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            similarItems = listOf(SimilarMovieModel(id = "s1", title = "Similar", imageUrl = null)),
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
    fun movieDetail_firstSimilarItem_upNavigatesToFirstCast_whenBothPresent() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 3),
            primaryActionFocusRequester = focus,
            castItems = listOf(CastModel(id = "c1", name = "Actor", role = "Lead", imageUrl = null)),
            similarItems = listOf(SimilarMovieModel(id = "s1", title = "Similar", imageUrl = null)),
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
    fun movieDetail_firstSimilarItem_upNavigatesToOverview_whenNoCast() {
        val focus = FocusRequester()
        setMovieContent(
            listState = LazyListState(firstVisibleItemIndex = 2),
            primaryActionFocusRequester = focus,
            similarItems = listOf(SimilarMovieModel(id = "s1", title = "Similar", imageUrl = null)),
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

    // -------------------------------------------------------------------------
    // Layout setup helpers
    // -------------------------------------------------------------------------

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
                    logoUrl = null,
                    title = "Test Movie",
                    eyebrow = "2026 · 2h",
                    ratingText = null,
                    genres = emptyList(),
                    primaryActionLabel = "▶ Play",
                    onPrimaryAction = {},
                    secondaryActions = emptyList(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    description = "Test overview.",
                    factItems = emptyList(),
                    factSummary = "",
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
