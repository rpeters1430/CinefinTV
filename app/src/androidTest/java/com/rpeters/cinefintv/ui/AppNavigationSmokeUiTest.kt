package com.rpeters.cinefintv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.navigation.navTabItems
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

        composeRule.onNodeWithTag(AppTestTags.tab(NavRoutes.LIBRARY_MOVIES))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Movies").assertIsDisplayed()

        composeRule.onNodeWithTag(AppTestTags.tab(NavRoutes.LIBRARY_TVSHOWS))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: TV Shows").assertIsDisplayed()

        composeRule.onNodeWithTag(AppTestTags.tab(NavRoutes.SEARCH))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Search").assertIsDisplayed()

        composeRule.onNodeWithTag(AppTestTags.tab(NavRoutes.SETTINGS))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Screen: Settings").assertIsDisplayed()
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

        composeRule.onNodeWithTag(AppTestTags.tab(NavRoutes.LIBRARY_MOVIES))
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

        composeRule.onNodeWithTag(AppTestTags.tab(NavRoutes.LIBRARY_TVSHOWS))
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
}

@Composable
private fun AppNavigationSmokeHarness() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showNav = currentRoute != null && !currentRoute.isFullscreenRoute()

    val selectedTabIndex = navTabItems.indexOfFirst { item ->
        currentRoute != null && (currentRoute == item.route || currentRoute.startsWith(item.route))
    }.let { if (it == -1) navTabItems.indexOfFirst { it.route == NavRoutes.HOME } else it }
        .coerceAtLeast(0)

    fun navigateToTab(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    CinefinAppScaffold(
        showNav = showNav,
        selectedTabIndex = selectedTabIndex,
        onNavigateToTab = ::navigateToTab,
    ) {
        NavHost(
            navController = navController,
            startDestination = NavRoutes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(NavRoutes.HOME) {
                SmokeScreen("Home") {
                    Button(
                        onClick = { navController.navigate(NavRoutes.movieDetail("smoke-movie")) },
                        modifier = Modifier.testTag("go_detail"),
                    ) {
                        Text("Open Detail")
                    }
                    Button(
                        onClick = { navController.navigate(NavRoutes.player("smoke-player")) },
                        modifier = Modifier.testTag("go_player"),
                    ) {
                        Text("Open Player")
                    }
                    Button(
                        onClick = { navController.navigate(NavRoutes.player("smoke-player", 120000L)) },
                        modifier = Modifier.testTag("go_player_resume"),
                    ) {
                        Text("Resume Player")
                    }
                }
            }
            composable(NavRoutes.LIBRARY_MOVIES) {
                SmokeScreen("Movies") {
                    Button(
                        onClick = { navController.navigate(NavRoutes.movieDetail("library-movie")) },
                        modifier = Modifier.testTag("go_movies_detail"),
                    ) {
                        Text("Open Movie Detail")
                    }
                }
            }
            composable(NavRoutes.LIBRARY_TVSHOWS) {
                SmokeScreen("TV Shows") {
                    Button(
                        onClick = { navController.navigate(NavRoutes.tvShowDetail("library-series")) },
                        modifier = Modifier.testTag("go_tv_detail"),
                    ) {
                        Text("Open TV Detail")
                    }
                }
            }
            composable(NavRoutes.LIBRARY_COLLECTIONS) { SmokeScreen("Stuff") }
            composable(NavRoutes.LIBRARY_MUSIC) { SmokeScreen("Music") }
            composable(NavRoutes.SEARCH) { SmokeScreen("Search") }
            composable(NavRoutes.SETTINGS) { SmokeScreen("Settings") }
            composable(
                NavRoutes.MOVIE_DETAIL,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId").orEmpty()
                SmokeScreen("Movie Detail") {
                    Text("Arg itemId: $itemId")
                    Button(
                        onClick = { navController.navigate(NavRoutes.player(itemId)) },
                        modifier = Modifier.testTag("detail_play"),
                    ) {
                        Text("Play From Detail")
                    }
                }
            }
            composable(
                NavRoutes.TV_SHOW_DETAIL,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { backStackEntry ->
                SmokeScreen("TV Show Detail") {
                    Text("Arg itemId: ${backStackEntry.arguments?.getString("itemId").orEmpty()}")
                }
            }
            composable(
                NavRoutes.PLAYER,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.StringType },
                    navArgument("start") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                SmokeScreen("Player") {
                    Text("Arg itemId: ${backStackEntry.arguments?.getString("itemId").orEmpty()}")
                    Text("Arg start: ${backStackEntry.arguments?.getString("start") ?: "null"}")
                }
            }
        }
    }
}

private fun String.isFullscreenRoute(): Boolean {
    return startsWith("auth/") ||
        startsWith("player/") ||
        startsWith("audio-player/") ||
        startsWith("movie/detail/") ||
        startsWith("tvshow/detail/") ||
        startsWith("season/detail/") ||
        startsWith("episode/detail/") ||
        startsWith("stuff/detail/") ||
        startsWith("detail/person/")
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
