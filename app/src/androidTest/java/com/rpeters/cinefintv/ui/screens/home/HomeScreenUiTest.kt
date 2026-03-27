package com.rpeters.cinefintv.ui.screens.home

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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
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
class HomeScreenUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingState_rendersLoadingSurface() {
        composeRule.setContent {
            HomeTestHost {
                HomeScreenContent(
                    uiState = HomeUiState.Loading,
                    onOpenItem = {},
                    onPlayItem = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.Loading).assertIsDisplayed()
        composeRule.onNodeWithText("Loading home...").assertIsDisplayed()
    }

    @Test
    fun errorState_retryInvokesCallback() {
        var retryCount = 0

        composeRule.setContent {
            HomeTestHost {
                HomeScreenContent(
                    uiState = HomeUiState.Error("Network timeout"),
                    onOpenItem = {},
                    onPlayItem = {},
                    onRetry = { retryCount++ },
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.Error).assertIsDisplayed()
        composeRule.onNodeWithText("Network timeout").assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.RetryButton)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(1, retryCount)
        }
    }

    @Test
    fun contentState_featuredButtonsInvokeCallbacks_andSectionsRender() {
        var openedItemId: String? = null
        var playedItemId: String? = null
        val featured = sampleCard(id = "featured-1", title = "Featured One")

        composeRule.setContent {
            HomeTestHost {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = listOf(featured)),
                    onOpenItem = { openedItemId = it.id },
                    onPlayItem = { playedItemId = it },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.FeaturedCarousel).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.FeaturedTitle).assertIsDisplayed()
        composeRule.onNodeWithText("My Libraries").assertIsDisplayed()

        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(HomeTestTags.FeaturedDetailsButton)
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("featured-1", playedItemId)
        assertEquals("featured-1", openedItemId)
    }

    @Test
    fun featuredPlayDown_movesFocusToFirstSectionItem() {
        composeRule.setContent {
            HomeTestHost {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))),
                    onOpenItem = {},
                    onPlayItem = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton)
            .requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton)
            .assertIsFocused()
        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .assertIsFocused()
    }

    @Test
    fun firstSectionDown_movesFocusToSecondSectionFirstItem() {
        composeRule.setContent {
            HomeTestHost {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))),
                    onOpenItem = {},
                    onPlayItem = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .assertIsFocused()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(1, 0))
            .assertIsFocused()
    }

    @Test
    fun nonFirstItem_supportsVerticalNavigationBetweenSections() {
        composeRule.setContent {
            HomeTestHost {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))),
                    onOpenItem = {},
                    onPlayItem = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 1))
            .requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 1))
            .assertIsFocused()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 1))
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(1, 0))
            .assertIsFocused()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(1, 0))
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .assertIsFocused()
    }

    @Test
    fun featuredFallbackCopy_rendersWhenDescriptionAndQualityAreMissing() {
        composeRule.setContent {
            HomeTestHost {
                HomeScreenContent(
                    uiState = sampleContentState(
                        featuredItems = listOf(
                            sampleCard(
                                id = "featured-1",
                                title = "Fallback Featured",
                                description = null,
                                mediaQuality = null,
                            )
                        )
                    ),
                    onOpenItem = {},
                    onPlayItem = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Featured title from your library").assertIsDisplayed()
    }
}

private fun sampleContentState(
    featuredItems: List<HomeCardModel>,
): HomeUiState.Content = HomeUiState.Content(
    featuredItems = featuredItems,
    sections = listOf(
        HomeSectionModel(
            title = "My Libraries",
            items = listOf(
                sampleCard(id = "library-1", title = "Movies Library", subtitle = "Library"),
                sampleCard(id = "library-2", title = "TV Library", subtitle = "Library"),
            ),
        ),
        HomeSectionModel(
            title = "Recently Added Movies",
            items = listOf(
                sampleCard(id = "movie-1", title = "Movie One"),
                sampleCard(id = "movie-2", title = "Movie Two"),
            ),
        ),
    ),
)

private fun sampleCard(
    id: String,
    title: String,
    subtitle: String? = "2026",
    description: String? = "Sample description",
    mediaQuality: String? = "4K HDR",
): HomeCardModel = HomeCardModel(
    id = id,
    title = title,
    subtitle = subtitle,
    imageUrl = null,
    backdropUrl = null,
    description = description,
    year = 2026,
    runtime = "1h 40m",
    rating = "8.2",
    officialRating = "PG-13",
    itemType = "Movie",
    collectionType = "movies",
    watchStatus = WatchStatus.NONE,
    playbackProgress = null,
    unwatchedCount = null,
    mediaQuality = mediaQuality,
)

private fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }

@Composable
private fun HomeTestHost(content: @Composable () -> Unit) {
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
