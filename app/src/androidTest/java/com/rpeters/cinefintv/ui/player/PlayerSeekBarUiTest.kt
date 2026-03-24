package com.rpeters.cinefintv.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PlayerSeekBarUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun speedButton_opensSpeedSection() {
        var openedSection: SettingsSection? = null

        composeRule.setContent {
            PlayerTestHost {
                SpeedButtonHarness(
                    onSectionOpened = { section: SettingsSection -> openedSection = section }
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.SpeedButton, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(SettingsSection.SPEED, openedSection)
        }
    }

    @Test
    fun seekBar_leftAndRight_seekPlayer_andShowBubble() {
        val player = mockk<ExoPlayer>(relaxed = true)

        composeRule.setContent {
            PlayerTestHost {
                SeekBarHarness(player = player)
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.SeekBar, useUnmergedTree = true)
            .requestFocus()
        composeRule.onNodeWithTag(PlayerTestTags.SeekBar, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(PlayerTestTags.SeekBubble).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.SeekBar, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionLeft) }

        verify { player.seekTo(40_000L) }
        verify(atLeast = 1) { player.seekTo(30_000L) }
    }

    @Test
    fun seekBar_showsActiveChapterNameInBubble() {
        val player = mockk<ExoPlayer>(relaxed = true)

        composeRule.setContent {
            PlayerTestHost {
                SeekBarHarness(
                    player = player,
                    chapters = listOf(
                        ChapterMarker(positionMs = 0L, name = "Cold Open"),
                        ChapterMarker(positionMs = 40_000L, name = "Main Story"),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.SeekBar, useUnmergedTree = true)
            .requestFocus()
        composeRule.onNodeWithTag(PlayerTestTags.SeekBar, useUnmergedTree = true)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Main Story").assertIsDisplayed()
    }
}

@Composable
private fun SpeedButtonHarness(
    onSectionOpened: (SettingsSection) -> Unit,
) {
    val player = remember { mockk<ExoPlayer>(relaxed = true) }
    val playPauseFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val seekBarFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    PlayerControls(
        isVisible = true,
        isPlaying = true,
        position = 30_000L,
        duration = 120_000L,
        bufferedFraction = 0.5f,
        uiState = PlayerUiState(
            title = "Player Test",
            isLoading = false,
            playbackSpeed = 1.25f,
            audioTracks = listOf(TrackOption("a1", "English", "en", 0)),
            subtitleTracks = listOf(TrackOption("s1", "English CC", "en", 1)),
        ),
        player = player,
        playPauseFocusRequester = playPauseFocusRequester,
        seekBarFocusRequester = seekBarFocusRequester,
        onInteract = {},
        onSettingsClick = { section, _ -> onSectionOpened(section) },
        onBack = {},
    )
}

@Composable
private fun SeekBarHarness(
    player: ExoPlayer,
    chapters: List<ChapterMarker> = emptyList(),
) {
    val playPauseFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val seekBarFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    PlayerControls(
        isVisible = true,
        isPlaying = true,
        position = 30_000L,
        duration = 120_000L,
        bufferedFraction = 0.5f,
        uiState = PlayerUiState(
            title = "Seek Test",
            isLoading = false,
            chapters = chapters,
            audioTracks = listOf(TrackOption("a1", "English", "en", 0)),
            subtitleTracks = listOf(TrackOption("s1", "English CC", "en", 1)),
        ),
        player = player,
        playPauseFocusRequester = playPauseFocusRequester,
        seekBarFocusRequester = seekBarFocusRequester,
        onInteract = {},
        onSettingsClick = { _, _ -> },
        onBack = {},
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
