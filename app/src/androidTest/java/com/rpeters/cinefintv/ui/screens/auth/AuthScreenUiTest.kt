package com.rpeters.cinefintv.ui.screens.auth

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthScreenUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun serverConnectionScreen_updatesUrl_showsError_andDispatchesContinue() {
        var serverUrl by mutableStateOf("")
        var continueClicks = 0

        composeRule.setContent {
            AuthTestHost {
                ServerConnectionScreen(
                    serverUrl = serverUrl,
                    isLoading = false,
                    errorMessage = "Connection failed",
                    onServerUrlChange = { serverUrl = it },
                    onContinue = { continueClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag(AuthTestTags.ServerField).requestFocus()
        composeRule.onNodeWithTag(AuthTestTags.ServerField).performTextInput("https://media.example.com")
        composeRule.runOnIdle {
            assertEquals("https://media.example.com", serverUrl)
        }

        composeRule.onNodeWithTag(AuthTestTags.ServerError).assertIsDisplayed()
        composeRule.onNodeWithText("Connection failed").assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.ContinueButton)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(1, continueClicks)
        }
    }

    @Test
    fun serverConnectionScreen_loadingStateShowsConnecting() {
        composeRule.setContent {
            AuthTestHost {
                ServerConnectionScreen(
                    serverUrl = "https://media.example.com",
                    isLoading = true,
                    errorMessage = null,
                    onServerUrlChange = {},
                    onContinue = {},
                )
            }
        }

        composeRule.onNodeWithTag(AuthTestTags.ContinueButton).assertTextContains("Connecting...")
    }

    @Test
    fun loginScreen_signInUsesCurrentCredentials_andBackDispatches() {
        var loginUsername: String? = null
        var loginPassword: String? = null
        var backClicks = 0

        composeRule.setContent {
            AuthTestHost {
                LoginScreen(
                    serverUrl = "https://media.example.com",
                    isAuthenticating = false,
                    errorMessage = null,
                    isQuickConnectEnabled = false,
                    isQuickConnectLoading = false,
                    quickConnectCode = null,
                    quickConnectPollStatus = null,
                    quickConnectError = null,
                    onLogin = { username, password ->
                        loginUsername = username
                        loginPassword = password
                    },
                    onUseQuickConnect = {},
                    onGenerateNewCode = {},
                    onLeaveScreen = {},
                    onBack = { backClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag(AuthTestTags.UsernameField).performTextInput("  demo  ")
        composeRule.onNodeWithTag(AuthTestTags.PasswordField).performTextInput("password123")
        composeRule.onNodeWithTag(AuthTestTags.SignInButton).assertIsEnabled()
        composeRule.onNodeWithTag(AuthTestTags.SignInButton)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(AuthTestTags.BackButton)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals("demo", loginUsername)
            assertEquals("password123", loginPassword)
            assertEquals(1, backClicks)
        }
    }

    @Test
    fun loginScreen_quickConnectFlow_opensPanel_andDispatchesActions() {
        var quickConnectRequested = 0
        var newCodeClicks = 0
        var leaveScreenCalls = 0

        composeRule.setContent {
            AuthTestHost {
                LoginScreen(
                    serverUrl = "https://media.example.com",
                    isAuthenticating = false,
                    errorMessage = null,
                    isQuickConnectEnabled = true,
                    isQuickConnectLoading = false,
                    quickConnectCode = "ABCD",
                    quickConnectPollStatus = "Waiting for approval",
                    quickConnectError = null,
                    onLogin = { _, _ -> },
                    onUseQuickConnect = { quickConnectRequested++ },
                    onGenerateNewCode = { newCodeClicks++ },
                    onLeaveScreen = { leaveScreenCalls++ },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(AuthTestTags.QuickConnectButton)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag(AuthTestTags.QuickConnectPanel).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.QuickConnectCode).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.QuickConnectStatus).assertIsDisplayed()

        composeRule.onNodeWithTag(AuthTestTags.QuickConnectNewCodeButton)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(AuthTestTags.QuickConnectCancelButton)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(1, quickConnectRequested)
            assertEquals(1, newCodeClicks)
            assertEquals(1, leaveScreenCalls)
        }
    }

    @Test
    fun loginScreen_quickConnectErrorOverridesStaleCode() {
        composeRule.setContent {
            AuthTestHost {
                LoginScreen(
                    serverUrl = "https://media.example.com",
                    isAuthenticating = false,
                    errorMessage = null,
                    isQuickConnectEnabled = true,
                    isQuickConnectLoading = false,
                    quickConnectCode = "WXYZ",
                    quickConnectPollStatus = null,
                    quickConnectError = "Code expired",
                    onLogin = { _, _ -> },
                    onUseQuickConnect = {},
                    onGenerateNewCode = {},
                    onLeaveScreen = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(AuthTestTags.QuickConnectButton)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithTag(AuthTestTags.QuickConnectError).assertIsDisplayed()
        composeRule.onAllNodesWithTag(AuthTestTags.QuickConnectCode).assertCountEquals(0)
        composeRule.onNodeWithText("Code expired").assertIsDisplayed()
    }
}

private fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }

@Composable
private fun AuthTestHost(content: @Composable () -> Unit) {
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
