package com.rpeters.cinefintv.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.navigation.Home
import com.rpeters.cinefintv.ui.navigation.LibraryStuff
import com.rpeters.cinefintv.ui.navigation.LibraryMovies
import com.rpeters.cinefintv.ui.navigation.LibraryMusic
import com.rpeters.cinefintv.ui.navigation.LibraryTvShows
import com.rpeters.cinefintv.ui.navigation.MovieDetail
import com.rpeters.cinefintv.ui.navigation.NavDestination
import com.rpeters.cinefintv.ui.navigation.PersonDetail
import com.rpeters.cinefintv.ui.navigation.Player
import com.rpeters.cinefintv.ui.navigation.Search
import com.rpeters.cinefintv.ui.navigation.SeasonDetail
import com.rpeters.cinefintv.ui.navigation.Settings
import com.rpeters.cinefintv.ui.navigation.StuffDetail
import com.rpeters.cinefintv.ui.navigation.TvShowDetail
import com.rpeters.cinefintv.ui.navigation.appChromeRouteSpec
import com.rpeters.cinefintv.ui.navigation.navigateToTopLevelDestination
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class AppNavigationSmokeUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun topTabs_navigateToMajorDestinations() {
        composeRule.setContent {
            AppSmokeTestHost {
                AppNavigationSmokeHarness()
            }
        }

        composeRule.onNodeWithTag(AppTestTags.NavBar).assertIsDisplayed()
        composeRule.onNodeWithText("Screen: Home").assertIsDisplayed()

        composeRule.onNodeWithTag(AppTestTags.tab("Movies"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Movies").assertIsDisplayed()

        composeRule.onNodeWithTag(AppTestTags.tab("TV Shows"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: TV Shows").assertIsDisplayed()

        composeRule.onNodeWithTag(AppTestTags.tab("Search"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Search").assertIsDisplayed()

        composeRule.onNodeWithTag(AppTestTags.tab("Settings"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Settings").assertIsDisplayed()
    }

    @Test
    fun leftRail_canReturnToHomeAfterNavigatingAwayWithDpad() {
        composeRule.setContent {
            AppSmokeTestHost {
                AppNavigationSmokeHarness()
            }
        }

        composeRule.onNodeWithTag(AppTestTags.tab("Home"))
            .requestFocus()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag(AppTestTags.tab("Movies"))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Screen: Movies").assertIsDisplayed()

        composeRule.onNodeWithTag(AppTestTags.tab("Movies"))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.onNodeWithTag(AppTestTags.tab("Home"))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Screen: Home").assertIsDisplayed()
    }

    @Test
    fun expandingNavRail_doesNotShiftContentHost() {
        composeRule.setContent {
            AppSmokeTestHost {
                AppNavigationSmokeHarness()
            }
        }

        val initialBounds = composeRule.onNodeWithTag(AppTestTags.ContentHost)
            .fetchSemanticsNode()
            .boundsInRoot

        composeRule.onNodeWithTag(AppTestTags.tab("Home"))
            .requestFocus()
            .assertIsFocused()

        composeRule.waitForIdle()

        val expandedBounds = composeRule.onNodeWithTag(AppTestTags.ContentHost)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Expected content host left edge to remain stable during nav expansion, " +
                "but moved from ${initialBounds.left} to ${expandedBounds.left}",
            kotlin.math.abs(initialBounds.left - expandedBounds.left) < 0.5f,
        )
        assertTrue(
            "Expected content host width to remain stable during nav expansion, " +
                "but changed from ${initialBounds.width} to ${expandedBounds.width}",
            kotlin.math.abs(initialBounds.width - expandedBounds.width) < 0.5f,
        )
    }

    @Test
    fun focusedNavRail_showsTabLabel() {
        composeRule.setContent {
            AppSmokeTestHost {
                AppNavigationSmokeHarness()
            }
        }

        composeRule.onNodeWithTag(AppTestTags.tab("Home"))
            .requestFocus()
            .assertIsFocused()

        composeRule.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test
    fun repeatedNavExpansionCycles_keepContentHostStable() {
        composeRule.setContent {
            AppSmokeTestHost {
                AppNavigationSmokeHarness()
            }
        }

        val initialBounds = composeRule.onNodeWithTag(AppTestTags.ContentHost)
            .fetchSemanticsNode()
            .boundsInRoot

        val homeTab = composeRule.onNodeWithTag(AppTestTags.tab("Home"))
        val primaryContent = composeRule.onNodeWithTag("home_content_primary")

        homeTab.requestFocus().assertIsFocused()

        repeat(3) { cycle ->
            homeTab.performKeyInput { pressKey(Key.DirectionRight) }
            primaryContent.assertIsFocused()

            composeRule.waitForIdle()

            val expandedBounds = composeRule.onNodeWithTag(AppTestTags.ContentHost)
                .fetchSemanticsNode()
                .boundsInRoot

            assertTrue(
                "Cycle ${cycle + 1}: content host left edge moved during nav expansion " +
                    "from ${initialBounds.left} to ${expandedBounds.left}",
                kotlin.math.abs(initialBounds.left - expandedBounds.left) < 0.5f,
            )
            assertTrue(
                "Cycle ${cycle + 1}: content host width changed during nav expansion " +
                    "from ${initialBounds.width} to ${expandedBounds.width}",
                kotlin.math.abs(initialBounds.width - expandedBounds.width) < 0.5f,
            )

            primaryContent.performKeyInput { pressKey(Key.DirectionLeft) }
            homeTab.assertIsFocused()
        }
    }

    @Test
    fun detailAndPlayerRoutes_hideNavChrome() {
        composeRule.setContent {
            AppSmokeTestHost {
                AppNavigationSmokeHarness()
            }
        }

        composeRule.onNodeWithTag("go_detail")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Movie Detail").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(AppTestTags.NavBar).fetchSemanticsNodes().isEmpty())

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("Screen: Home").assertIsDisplayed()
        composeRule.onNodeWithTag(AppTestTags.NavBar).assertIsDisplayed()

        composeRule.onNodeWithTag("go_player")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Player").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(AppTestTags.NavBar).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun homeActions_preserveDestinationArguments() {
        composeRule.setContent {
            AppSmokeTestHost {
                AppNavigationSmokeHarness()
            }
        }

        composeRule.onNodeWithTag("go_detail")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Movie Detail").assertIsDisplayed()
        composeRule.onNodeWithText("Arg itemId: smoke-movie").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("Screen: Home").assertIsDisplayed()

        composeRule.onNodeWithTag("go_player_resume")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Player").assertIsDisplayed()
        composeRule.onNodeWithText("Arg itemId: smoke-player").assertIsDisplayed()
        composeRule.onNodeWithText("Arg start: 120000").assertIsDisplayed()
    }

    @Test
    fun libraryActions_openExpectedDetailDestinations() {
        composeRule.setContent {
            AppSmokeTestHost {
                AppNavigationSmokeHarness()
            }
        }

        composeRule.onNodeWithTag(AppTestTags.tab("Movies"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("go_movies_detail")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Movie Detail").assertIsDisplayed()
        composeRule.onNodeWithText("Arg itemId: library-movie").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(AppTestTags.NavBar).fetchSemanticsNodes().isEmpty())

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("Screen: Movies").assertIsDisplayed()
        composeRule.onNodeWithTag(AppTestTags.NavBar).assertIsDisplayed()

        composeRule.onNodeWithTag(AppTestTags.tab("TV Shows"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("go_tv_detail")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: TV Show Detail").assertIsDisplayed()
        composeRule.onNodeWithText("Arg itemId: library-series").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(AppTestTags.NavBar).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun playerBack_returnsToOriginatingDetailScreen() {
        composeRule.setContent {
            AppSmokeTestHost {
                AppNavigationSmokeHarness()
            }
        }

        composeRule.onNodeWithTag("go_detail")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Movie Detail").assertIsDisplayed()
        composeRule.onNodeWithText("Arg itemId: smoke-movie").assertIsDisplayed()

        composeRule.onNodeWithTag("detail_play")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Player").assertIsDisplayed()
        composeRule.onNodeWithText("Arg itemId: smoke-movie").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("Screen: Movie Detail").assertIsDisplayed()
        composeRule.onNodeWithText("Arg itemId: smoke-movie").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(AppTestTags.NavBar).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun playerBack_restoresFocusToHomeContent() {
        composeRule.setContent {
            AppSmokeTestHost {
                AppNavigationSmokeHarness()
            }
        }

        // 1. Move focus to content
        val primaryContent = composeRule.onNodeWithTag("home_content_primary")
        primaryContent.requestFocus().assertIsFocused()

        // 2. Open player
        composeRule.onNodeWithTag("go_player")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Player").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag(AppTestTags.NavBar).fetchSemanticsNodes().isEmpty())

        // 3. Go back
        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }

        // 4. Verify Home is back and focus is restored to content (not nav bar)
        composeRule.onNodeWithText("Screen: Home").assertIsDisplayed()
        composeRule.onNodeWithTag(AppTestTags.NavBar).assertIsDisplayed()
        
        // Wait for focus restoration delay
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        primaryContent.assertIsFocused()
    }
}

@Composable
private fun AppNavigationSmokeHarness() {
    val backStack: NavBackStack<NavKey> = rememberNavBackStack(Home)
    val currentDestination = backStack.lastOrNull() as? NavDestination
    val chromeSpec = appChromeRouteSpec(currentDestination)
    var restoreHomeFocusSignal by remember { mutableIntStateOf(0) }

    BackHandler(enabled = backStack.size > 1) {
        val previousDestination = backStack.getOrNull(backStack.size - 2) as? NavDestination
        if (currentDestination is Player && previousDestination is Home) {
            restoreHomeFocusSignal += 1
        }
        backStack.removeAt(backStack.size - 1)
    }

    fun navigateToTab(destination: NavDestination) {
        backStack.navigateToTopLevelDestination(destination)
    }

    CinefinAppScaffold(
        showNav = chromeSpec.showNav,
        selectedTabIndex = chromeSpec.selectedTabIndex,
        onNavigateToTab = ::navigateToTab,
    ) {
        when (val destination = currentDestination ?: Home) {
            is Home -> {
                val primaryContentRequester = remember { FocusRequester() }
                val destinationFocus = rememberTopLevelDestinationFocus(primaryContentRequester)
                LaunchedEffect(restoreHomeFocusSignal) {
                    if (restoreHomeFocusSignal > 0) {
                        kotlinx.coroutines.delay(200)
                        primaryContentRequester.requestFocus()
                    }
                }
                SmokeScreen("Home") {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .testTag("home_content_primary")
                            .then(destinationFocus.primaryContentModifier()),
                    ) {
                        Text("Primary Content")
                    }
                    Button(
                        onClick = { backStack.add(MovieDetail("smoke-movie")) },
                        modifier = Modifier.testTag("go_detail"),
                    ) {
                        Text("Open Detail")
                    }
                    Button(
                        onClick = { backStack.add(Player("smoke-player")) },
                        modifier = Modifier.testTag("go_player"),
                    ) {
                        Text("Open Player")
                    }
                    Button(
                        onClick = { backStack.add(Player("smoke-player", 120000L)) },
                        modifier = Modifier.testTag("go_player_resume"),
                    ) {
                        Text("Resume Player")
                    }
                }
            }

            is LibraryMovies -> {
                SmokeScreen("Movies") {
                    Button(
                        onClick = { backStack.add(MovieDetail("library-movie")) },
                        modifier = Modifier.testTag("go_movies_detail"),
                    ) {
                        Text("Open Movie Detail")
                    }
                }
            }

            is LibraryTvShows -> {
                SmokeScreen("TV Shows") {
                    Button(
                        onClick = { backStack.add(TvShowDetail("library-series")) },
                        modifier = Modifier.testTag("go_tv_detail"),
                    ) {
                        Text("Open TV Detail")
                    }
                }
            }

            is LibraryStuff -> SmokeScreen("Stuff")
            is LibraryMusic -> SmokeScreen("Music")
            is Search -> SmokeScreen("Search")
            is Settings -> SmokeScreen("Settings")
            is MovieDetail -> {
                SmokeScreen("Movie Detail") {
                    Text("Arg itemId: ${destination.itemId}")
                    Button(
                        onClick = { backStack.add(Player(destination.itemId)) },
                        modifier = Modifier.testTag("detail_play"),
                    ) {
                        Text("Play From Detail")
                    }
                }
            }

            is TvShowDetail -> {
                SmokeScreen("TV Show Detail") {
                    Text("Arg itemId: ${destination.itemId}")
                }
            }

            is Player -> {
                SmokeScreen("Player") {
                    Text("Arg itemId: ${destination.itemId}")
                    Text("Arg start: ${destination.startPositionMs}")
                }
            }

            is SeasonDetail -> SmokeScreen("Season Detail")
            is com.rpeters.cinefintv.ui.navigation.EpisodeDetail -> SmokeScreen("Episode Detail")
            is StuffDetail -> SmokeScreen("Stuff Detail")
            is PersonDetail -> SmokeScreen("Person Detail")
            is com.rpeters.cinefintv.ui.navigation.ServerConnection -> SmokeScreen("Server Connection")
            is com.rpeters.cinefintv.ui.navigation.Login -> SmokeScreen("Login")
            is com.rpeters.cinefintv.ui.navigation.AudioPlayer -> SmokeScreen("Audio Player")
        }
    }
}

@Composable
private fun SmokeScreen(
    label: String,
    actions: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Screen: $label",
            style = MaterialTheme.typography.headlineMedium,
        )
        actions?.invoke()
    }
}

@Composable
private fun AppSmokeTestHost(content: @Composable () -> Unit) {
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

private fun androidx.compose.ui.test.SemanticsNodeInteraction.requestFocus():
    androidx.compose.ui.test.SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }
