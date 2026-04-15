package com.rpeters.cinefintv.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PlayerControlsFocusUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rightPressOnSettings_keepsFocusOnSettings() {
        composeRule.setContent {
            PlayerTestHost {
                PlayerControlsVisibilityHarness()
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.SettingsButton, useUnmergedTree = true)
            .requestFocus()
        composeRule.onNodeWithTag(PlayerTestTags.SettingsButton, useUnmergedTree = true)
            .assertIsFocused()
        composeRule.onNodeWithTag(PlayerTestTags.SettingsButton, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PlayerTestTags.SettingsButton, useUnmergedTree = true)
            .assertIsFocused()
    }

    @Test
    fun upFromSubtitleButton_movesFocusToSeekBar() {
        composeRule.setContent {
            PlayerTestHost {
                PlayerControlsVisibilityHarness()
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.SubtitleButton, useUnmergedTree = true)
            .requestFocus()
        composeRule.onNodeWithTag(PlayerTestTags.SubtitleButton, useUnmergedTree = true)
            .assertIsFocused()
        composeRule.onNodeWithTag(PlayerTestTags.SubtitleButton, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PlayerTestTags.SeekBar, useUnmergedTree = true)
            .assertIsFocused()
    }

    @Test
    fun upFromAudioButton_movesFocusToSeekBar() {
        composeRule.setContent {
            PlayerTestHost {
                PlayerControlsVisibilityHarness()
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.AudioButton, useUnmergedTree = true)
            .requestFocus()
        composeRule.onNodeWithTag(PlayerTestTags.AudioButton, useUnmergedTree = true)
            .assertIsFocused()
        composeRule.onNodeWithTag(PlayerTestTags.AudioButton, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PlayerTestTags.SeekBar, useUnmergedTree = true)
            .assertIsFocused()
    }

    @Test
    fun leftRightNavigation_subtitleToQuality_andBack() {
        composeRule.setContent {
            PlayerTestHost {
                PlayerControlsVisibilityHarness()
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.SubtitleButton, useUnmergedTree = true)
            .requestFocus()
        composeRule.onNodeWithTag(PlayerTestTags.SubtitleButton, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PlayerTestTags.QualityButton, useUnmergedTree = true)
            .assertIsFocused()

        composeRule.onNodeWithTag(PlayerTestTags.QualityButton, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PlayerTestTags.SubtitleButton, useUnmergedTree = true)
            .assertIsFocused()
    }
}

@Composable
private fun PlayerControlsVisibilityHarness() {
    val player = remember { mockk<ExoPlayer>(relaxed = true) }
    val playPauseFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val seekBarFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    PlayerControls(
        isVisible = true,
        isPlaying = true,
        positionProvider = { 30_000L },
        duration = 120_000L,
        bufferedFraction = 0.5f,
        uiState = PlayerUiState(
            title = "Player Test",
            isLoading = false,
            audioTracks = listOf(TrackOption("a1", "English", "en", 0)),
            subtitleTracks = listOf(TrackOption("s1", "English CC", "en", 1)),
        ),
        player = player,
        playPauseFocusRequester = playPauseFocusRequester,
        seekBarFocusRequester = seekBarFocusRequester,
        isContentShelfVisible = false,
        onHideShelf = {},
        onInteract = {},
        onSettingsClick = { _, _ -> },
        onBack = {},
        onOpenItem = {},
    )
}

private fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }

@Composable
private fun PlayerTestHost(content: @Composable () -> Unit) {
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
