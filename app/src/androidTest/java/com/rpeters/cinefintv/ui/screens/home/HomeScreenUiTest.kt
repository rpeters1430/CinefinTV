package com.rpeters.cinefintv.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.compose.ui.unit.dp
import com.rpeters.cinefintv.ui.AppChromeFocusController
import com.rpeters.cinefintv.ui.LocalAppChromeFocusController
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.components.WatchStatus
import com.rpeters.cinefintv.ui.navigation.Home
import com.rpeters.cinefintv.ui.navigation.Player
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.settleInitialHomeFocus()
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
                        shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                    )
                }
            }
        }

        composeRule.settleInitialHomeFocus()
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton).requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton)
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("top_nav").assertIsFocused()
    }

    @Test
    fun featuredPlayLeft_movesFocusToTopNav() {
        composeRule.setContent {
            HomeTestHost(showTopNav = true) {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))),
                    onOpenItem = {},
                    onPlayItem = {},
                    onOpenSeries = {},
                    onOpenSeason = {},
                    onRetry = {},
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton).requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton)
            .performKeyInput { pressKey(Key.DirectionLeft) }
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.settleInitialHomeFocus()
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.settleInitialHomeFocus()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0)).requestFocus()
        composeRule.waitUntilNodeIsFocused(HomeTestTags.sectionItem(0, 0), timeoutMillis = 3_000)
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitUntilNodeIsFocused(HomeTestTags.FeaturedPlayButton, timeoutMillis = 5_000)
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0)).requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("top_nav").assertIsFocused()
    }

    @Test
    fun firstSectionLeft_movesFocusToTopNav() {
        composeRule.setContent {
            HomeTestHost(showTopNav = true) {
                HomeScreenContent(
                    uiState = sampleContentState(featuredItems = emptyList()),
                    onOpenItem = {},
                    onPlayItem = {},
                    onOpenSeries = {},
                    onOpenSeason = {},
                    onRetry = {},
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0)).requestFocus()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .performKeyInput { pressKey(Key.DirectionLeft) }
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.settleInitialHomeFocus()
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
    fun leftEdgeItem_canNavigateUpAcrossMultipleSections() {
        composeRule.setContent {
            HomeTestHost(showTopNav = true) {
                HomeScreenContent(
                    uiState = HomeUiState.Content(
                        featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One")),
                        sections = listOf(
                            HomeSectionModel(
                                id = HomeSectionId.NEXT_EPISODES,
                                title = "Next Episodes",
                                items = listOf(sampleCard(id = "episode-1", title = "Episode One")),
                            ),
                            HomeSectionModel(
                                id = HomeSectionId.RECENT_MOVIES,
                                title = "Recently Added",
                                items = listOf(sampleCard(id = "recent-1", title = "Recent One")),
                            ),
                            HomeSectionModel(
                                id = HomeSectionId.RECENT_COLLECTIONS,
                                title = "Collections",
                                items = listOf(sampleCard(id = "collection-1", title = "Collection One")),
                            ),
                        ),
                    ),
                    onOpenItem = {},
                    onPlayItem = {},
                    onOpenSeries = {},
                    onOpenSeason = {},
                    onRetry = {},
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.settleInitialHomeFocus()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .requestFocus()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(1, 0))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(2, 0))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(1, 0))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton).assertIsFocused()
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
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
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.onNodeWithTag("top_nav").requestFocus()
        composeRule.onNodeWithTag("top_nav")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(HomeTestTags.FeaturedPlayButton).assertIsFocused()
    }

    @Test
    fun returningFromPlayer_preservesHomeContent() {
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
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag("player_back").assertIsDisplayed()
        composeRule.onNodeWithTag("player_back")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.mainClock.advanceTimeBy(1_100)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 0))
            .assertIsDisplayed()
    }

    @Test
    fun equivalentContentRefresh_keepsFocusedItemFocused() {
        val uiState = mutableStateOf<HomeUiState>(
            sampleContentState(
                featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))
            )
        )

        composeRule.setContent {
            HomeTestHost {
                HomeScreenContent(
                    uiState = uiState.value,
                    onOpenItem = {},
                    onPlayItem = {},
                    onOpenSeries = {},
                    onOpenSeason = {},
                    onRetry = {},
                    shouldRestoreFocusOnResume = false,
                    onConsumedRestore = {},
                )
            }
        }

        composeRule.settleInitialHomeFocus()
        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 1))
            .requestFocus()
            .assertIsFocused()

        composeRule.runOnIdle {
            uiState.value = sampleContentState(
                featuredItems = listOf(sampleCard(id = "featured-1", title = "Featured One"))
            )
        }

        composeRule.onNodeWithTag(HomeTestTags.sectionItem(0, 1))
            .assertIsFocused()
    }
}

private fun sampleContentState(
    featuredItems: List<HomeCardModel>,
): HomeUiState.Content = HomeUiState.Content(
    featuredItems = featuredItems,
    sections = listOf(
        HomeSectionModel(
            id = HomeSectionId.LIBRARIES,
            title = "My Libraries",
            items = listOf(
                sampleCard(id = "library-1", title = "Movies Library", subtitle = "Library"),
                sampleCard(id = "library-2", title = "TV Library", subtitle = "Library"),
            ),
        ),
        HomeSectionModel(
            id = HomeSectionId.RECENT_MOVIES,
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

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.settleInitialHomeFocus() {
    mainClock.advanceTimeBy(1_100)
    waitForIdle()
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitUntilNodeIsFocused(
    tag: String,
    timeoutMillis: Long = 3_000L,
) {
    waitUntil(timeoutMillis) {
        runCatching {
            onNodeWithTag(tag).assertIsFocused()
            true
        }.getOrDefault(false)
    }
}

@Composable
private fun HomeNavigationHarness(
    uiState: HomeUiState,
) {
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(Home)
    var shouldRestoreFocusOnResume by remember { mutableStateOf(false) }
    val isPlayerVisible = backStack.lastOrNull() is Player

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreenContent(
            uiState = uiState,
            onOpenItem = { backStack.add(Player(it.id)) },
            onPlayItem = { backStack.add(Player(it)) },
            onOpenSeries = {},
            onOpenSeason = {},
            onRetry = {},
            shouldRestoreFocusOnResume = shouldRestoreFocusOnResume,
            onConsumedRestore = { shouldRestoreFocusOnResume = false },
        )

        if (isPlayerVisible) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = {
                        shouldRestoreFocusOnResume = true
                        backStack.removeAt(backStack.size - 1)
                    },
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
