package com.rpeters.cinefintv.ui.screens.search

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.AppTestTags
import com.rpeters.cinefintv.ui.CinefinAppScaffold
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.screens.home.HomeCardModel
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SearchScreenUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun blankQuery_rendersHeroAndHint() {
        composeRule.setContent {
            SearchTestHost {
                SearchScreenContent(
                    uiState = SearchUiState(),
                    onQueryChange = {},
                    onOpenItem = {},
                )
            }
        }

        composeRule.onNodeWithTag(SearchTestTags.Hero).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.Field).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.Hint).assertIsDisplayed()
    }

    @Test
    fun loadingState_rendersSearchingMessage() {
        composeRule.setContent {
            SearchTestHost {
                SearchScreenContent(
                    uiState = SearchUiState(query = "matrix", isLoading = true),
                    onQueryChange = {},
                    onOpenItem = {},
                )
            }
        }

        composeRule.onNodeWithTag(SearchTestTags.Loading).assertIsDisplayed()
        composeRule.onNodeWithText("Searching for \"matrix\"...").assertIsDisplayed()
    }

    @Test
    fun errorState_rendersMessage() {
        composeRule.setContent {
            SearchTestHost {
                SearchScreenContent(
                    uiState = SearchUiState(query = "matrix", errorMessage = "Search failed"),
                    onQueryChange = {},
                    onOpenItem = {},
                )
            }
        }

        composeRule.onNodeWithTag(SearchTestTags.Error).assertIsDisplayed()
        composeRule.onNodeWithText("Search failed").assertIsDisplayed()
    }

    @Test
    fun emptyResults_rendersEmptyMessage() {
        composeRule.setContent {
            SearchTestHost {
                SearchScreenContent(
                    uiState = SearchUiState(query = "matrix", results = emptyList()),
                    onQueryChange = {},
                    onOpenItem = {},
                )
            }
        }

        composeRule.onNodeWithTag(SearchTestTags.Empty).assertIsDisplayed()
        composeRule.onNodeWithText("No results found for \"matrix\"").assertIsDisplayed()
    }

    @Test
    fun resultsState_clickOpensItem_andFirstResultUpMovesFocusToField() {
        var openedId: String? = null

        composeRule.setContent {
            SearchTestHost {
                SearchScreenContent(
                    uiState = SearchUiState(
                        query = "matrix",
                        results = listOf(
                            sampleSearchResult("movie-1", "The Matrix"),
                            sampleSearchResult("movie-2", "The Matrix Reloaded"),
                        ),
                    ),
                    onQueryChange = {},
                    onOpenItem = { openedId = it.id },
                )
            }
        }

        composeRule.onNodeWithTag(SearchTestTags.ResultsCount).assertIsDisplayed()
        composeRule.onNodeWithText("2 results").assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.resultItem(0)).requestFocus()
        composeRule.onNodeWithTag(SearchTestTags.resultItem(0)).assertIsFocused()
        composeRule.onNodeWithTag(SearchTestTags.resultItem(0))
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag(SearchTestTags.Field).assertIsFocused()
        composeRule.onNodeWithTag(SearchTestTags.resultItem(0)).requestFocus()
        composeRule.onNodeWithTag(SearchTestTags.resultItem(0))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals("movie-1", openedId)
        }
    }

    @Test
    fun selectedTabRight_movesFocusIntoSearchField() {
        composeRule.setContent {
            SearchTestHost {
                CinefinAppScaffold(
                    showNav = true,
                    selectedTabIndex = 5,
                    onNavigateToTab = {},
                ) {
                    SearchScreenContent(
                        uiState = SearchUiState(),
                        onQueryChange = {},
                        onOpenItem = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AppTestTags.tab("Search")).requestFocus()
        composeRule.onNodeWithTag(AppTestTags.tab("Search"))
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(SearchTestTags.Field).assertIsFocused()
    }

    @Test
    fun searchFieldLeft_returnsFocusToSelectedDrawerTab() {
        composeRule.setContent {
            SearchTestHost {
                CinefinAppScaffold(
                    showNav = true,
                    selectedTabIndex = 5,
                    onNavigateToTab = {},
                ) {
                    SearchScreenContent(
                        uiState = SearchUiState(),
                        onQueryChange = {},
                        onOpenItem = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SearchTestTags.Field).requestFocus()
        composeRule.onNodeWithTag(SearchTestTags.Field)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag(AppTestTags.tab("Search")).assertIsFocused()
    }

    @Test
    fun firstSearchResultLeft_returnsFocusToSelectedDrawerTab() {
        composeRule.setContent {
            SearchTestHost {
                CinefinAppScaffold(
                    showNav = true,
                    selectedTabIndex = 5,
                    onNavigateToTab = {},
                ) {
                    SearchScreenContent(
                        uiState = SearchUiState(
                            query = "matrix",
                            results = listOf(
                                sampleSearchResult("movie-1", "The Matrix"),
                                sampleSearchResult("movie-2", "The Matrix Reloaded"),
                            ),
                        ),
                        onQueryChange = {},
                        onOpenItem = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SearchTestTags.resultItem(0)).requestFocus()
        composeRule.onNodeWithTag(SearchTestTags.resultItem(0))
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag(AppTestTags.tab("Search")).assertIsFocused()
    }
}

private fun sampleSearchResult(id: String, title: String): HomeCardModel = HomeCardModel(
    id = id,
    title = title,
    subtitle = "2026",
    imageUrl = null,
    backdropUrl = null,
    description = null,
    year = 2026,
    runtime = "2h 16m",
    rating = "8.7",
    officialRating = "R",
    itemType = "Movie",
    collectionType = "movies",
    watchStatus = WatchStatus.NONE,
    playbackProgress = null,
    unwatchedCount = null,
    mediaQuality = null,
)

private fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }

@Composable
private fun SearchTestHost(content: @Composable () -> Unit) {
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
