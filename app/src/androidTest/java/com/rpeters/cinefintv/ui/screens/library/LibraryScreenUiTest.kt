package com.rpeters.cinefintv.ui.screens.library

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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class LibraryScreenUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingState_rendersLoadingSurface() {
        composeRule.setContent {
            LibraryTestHost {
                LibraryGridContent(
                    uiState = LibraryGridUiState.Loading,
                    errorTitle = "Failed to load movies",
                    emptyTitle = "No movies found",
                    columnCount = 5,
                    aspectRatio = 2f / 3f,
                    onOpenItem = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(LibraryTestTags.Loading).assertIsDisplayed()
    }

    @Test
    fun errorState_retryInvokesCallback() {
        var retryCount = 0

        composeRule.setContent {
            LibraryTestHost {
                LibraryGridContent(
                    uiState = LibraryGridUiState.Error("Request failed"),
                    errorTitle = "Failed to load movies",
                    emptyTitle = "No movies found",
                    columnCount = 5,
                    aspectRatio = 2f / 3f,
                    onOpenItem = {},
                    onRetry = { retryCount++ },
                )
            }
        }

        composeRule.onNodeWithTag(LibraryTestTags.Error).assertIsDisplayed()
        composeRule.onNodeWithText("Request failed").assertIsDisplayed()
        composeRule.onNodeWithTag(LibraryTestTags.RetryButton)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(1, retryCount)
        }
    }

    @Test
    fun emptyState_rendersConfiguredMessage() {
        composeRule.setContent {
            LibraryTestHost {
                LibraryGridContent(
                    uiState = LibraryGridUiState.Empty,
                    errorTitle = "Failed to load collections",
                    emptyTitle = "No collections found",
                    columnCount = 4,
                    aspectRatio = 16f / 9f,
                    onOpenItem = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(LibraryTestTags.Empty).assertIsDisplayed()
        composeRule.onNodeWithText("No collections found").assertIsDisplayed()
    }

    @Test
    fun contentState_clickOpensItem_andGridRenders() {
        var openedId: String? = null

        composeRule.setContent {
            LibraryTestHost {
                LibraryGridContent(
                    uiState = LibraryGridUiState.Content(items = sampleLibraryItems()),
                    errorTitle = "Failed to load TV shows",
                    emptyTitle = "No TV shows found",
                    columnCount = 5,
                    aspectRatio = 2f / 3f,
                    onOpenItem = { openedId = it.id },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(LibraryTestTags.Grid).assertIsDisplayed()
        composeRule.onNodeWithText("Movie One").assertIsDisplayed()
        composeRule.onNodeWithTag(LibraryTestTags.item(0))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals("movie-1", openedId)
        }
    }

    @Test
    fun gridFocusMovesDownToNextRow() {
        composeRule.setContent {
            LibraryTestHost {
                LibraryGridContent(
                    uiState = LibraryGridUiState.Content(items = sampleLibraryItems(count = 8)),
                    errorTitle = "Failed to load movies",
                    emptyTitle = "No movies found",
                    columnCount = 4,
                    aspectRatio = 16f / 9f,
                    onOpenItem = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(LibraryTestTags.item(0)).requestFocus()
        composeRule.onNodeWithTag(LibraryTestTags.item(0)).assertIsFocused()
        composeRule.onNodeWithTag(LibraryTestTags.item(0))
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(LibraryTestTags.item(4)).assertIsFocused()
    }
}

private fun sampleLibraryItems(count: Int = 2): List<LibraryCardModel> =
    List(count) { index ->
        LibraryCardModel(
            id = "movie-${index + 1}",
            title = if (index == 0) "Movie One" else "Movie ${index + 1}",
            subtitle = "2026",
            imageUrl = null,
            itemType = "Movie",
            watchStatus = WatchStatus.NONE,
            playbackProgress = null,
            unwatchedCount = null,
        )
    }

private fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }

@Composable
private fun LibraryTestHost(content: @Composable () -> Unit) {
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
