package com.rpeters.cinefintv.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.compose.ui.unit.dp
import com.rpeters.cinefintv.ui.AppChromeFocusController
import com.rpeters.cinefintv.ui.LocalAppChromeFocusController
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
                    onOpenSeries = {},
                    onOpenSeason = {},
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
                    onOpenSeries = {},
                    onOpenSeason = {},
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
                    onOpenSeries = {},
                    onOpenSeason = {},
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
                    onOpenSeries = {},
                    onOpenSeason = {},
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
    fun featuredPlayDown_scrollsShelfIntoViewInConstrainedViewport() {
        composeRule.setContent {
            HomeTestHost {
                Box(modifier = Modifier.height(520.dp)) {
                    HomeScreenContent(
                        uiState = sampleContentState(featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))),
                        onOpenItem = {},
                        onPlayItem = {},
                        onOpenSeries = {},
                        onOpenSeason = {},
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton)
            .requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton)
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .assertIsFocused()
            .assertIsDisplayed()
    }

    @Test
    fun featuredPlayUp_movesFocusToTopNav() {
        composeRule.setContent {
            HomeTestHost(showTopNav = true) {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))),
                    onOpenItem = {},
                    onPlayItem = {},
                    onOpenSeries = {},
                    onOpenSeason = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton).requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("top_nav").assertIsFocused()
    }

    @Test
    fun firstSectionDown_movesFocusToSecondSectionFirstItem() {
        composeRule.setContent {
            HomeTestHost {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))),
                    onOpenItem = {},
                    onPlayItem = {},
                    onOpenSeries = {},
                    onOpenSeason = {},
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
    fun firstSectionUp_withFeatured_movesFocusToFeaturedPlay() {
        composeRule.setContent {
            HomeTestHost(showTopNav = true) {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))),
                    onOpenItem = {},
                    onPlayItem = {},
                    onOpenSeries = {},
                    onOpenSeason = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0)).requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton).assertIsFocused()
    }

    @Test
    fun firstSectionUp_withoutFeatured_movesFocusToTopNav() {
        composeRule.setContent {
            HomeTestHost(showTopNav = true) {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = emptyList()),
                    onOpenItem = {},
                    onPlayItem = {},
                    onOpenSeries = {},
                    onOpenSeason = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0)).requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("top_nav").assertIsFocused()
    }

    @Test
    fun nonFirstItem_supportsVerticalNavigationBetweenSections() {
        composeRule.setContent {
            HomeTestHost {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))),
                    onOpenItem = {},
                    onPlayItem = {},
                    onOpenSeries = {},
                    onOpenSeason = {},
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
                    onOpenSeries = {},
                    onOpenSeason = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Featured title from your library").assertIsDisplayed()
    }

    @Test
    fun topNavDown_movesFocusToFeaturedPlay() {
        composeRule.setContent {
            HomeTestHost(showTopNav = true) {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))),
                    onOpenItem = {},
                    onPlayItem = {},
                    onOpenSeries = {},
                    onOpenSeason = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("top_nav").requestFocus()
        composeRule.onNodeWithTag("top_nav")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton).assertIsFocused()
    }

    @Test
    fun returningFromPlayer_restoresFocusedHomeItem_andDpadNavigationStillWorks() {
        composeRule.setContent {
            HomeTestHost {
                HomeNavigationHarness(
                    uiState = sampleContentState(
                        featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))
                    )
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .requestFocus()
            .assertIsFocused()
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("player_back").assertIsDisplayed()
        composeRule.onNodeWithTag("player_back")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(1, 0))
            .assertIsFocused()
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
private fun HomeNavigationHarness(
    uiState: HomeUiState,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            HomeScreenContent(
                uiState = uiState,
                onOpenItem = { navController.navigate("player") },
                onPlayItem = { navController.navigate("player") },
                onOpenSeries = {},
                onOpenSeason = {},
                onRetry = {},
            )
        }
        composable("player") {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("player_back"),
                ) {
                    Text("Back To Home")
                }
            }
        }
    }
}

@Composable
private fun HomeTestHost(
    showTopNav: Boolean = false,
    content: @Composable () -> Unit,
) {
    CinefinTvTheme(useDynamicColors = false) {
        val chromeFocusController = remember { AppChromeFocusController() }
        val topNavFocusRequester = remember { FocusRequester() }
        chromeFocusController.topNavFocusRequester = if (showTopNav) topNavFocusRequester else null

        CompositionLocalProvider(
            LocalAppChromeFocusController provides chromeFocusController,
            LocalCinefinThemeController provides object : ThemeColorController {
                override fun updateSeedColor(color: Color?) = Unit
            }
        ) {
            androidx.compose.foundation.layout.Column {
                if (showTopNav) {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .focusRequester(topNavFocusRequester)
                            .focusProperties {
                                chromeFocusController.primaryContentFocusRequester?.let { down = it }
                            }
                            .testTag("top_nav"),
                    ) {
                        Text("Top Nav")
                    }
                }
                content()
            }
        }
    }
}
