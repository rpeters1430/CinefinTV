package com.rpeters.cinefintv.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import com.rpeters.cinefintv.testutil.FakePlayer
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
        val player = FakePlayer()

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

        // Initial 30s + 10s right - 10s left = 30s
        assertEquals(30_000L, player.lastSeekPosition)
    }

    @Test
    fun seekBar_multipleRightPresses_accumulateFromLatestSeekPosition() {
        val player = FakePlayer()

        composeRule.setContent {
            PlayerTestHost {
                SeekBarHarness(player = player)
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.SeekBar, useUnmergedTree = true)
            .requestFocus()
        composeRule.onNodeWithTag(PlayerTestTags.SeekBar, useUnmergedTree = true)
            .performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionLeft)
            }

        // 30s + 10s + 10s - 10s = 40s
        assertEquals(40_000L, player.lastSeekPosition)
    }

    @Test
    fun seekBar_withChapterMarkers_keepsBubbleVisible() {
        val player = FakePlayer()

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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PlayerTestTags.SeekBubble).assertIsDisplayed()
        composeRule.onNodeWithText("Main Story", useUnmergedTree = true).assertIsDisplayed()
    }
}

@Composable
private fun SpeedButtonHarness(
    onSectionOpened: (SettingsSection) -> Unit,
) {
    val player = remember { FakePlayer() }
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
            playbackSpeed = 1.25f,
            audioTracks = listOf(TrackOption("a1", "English", "en", 0)),
            subtitleTracks = listOf(TrackOption("s1", "English CC", "en", 1)),
        ),
        player = player,
        playPauseFocusRequester = playPauseFocusRequester,
        seekBarFocusRequester = seekBarFocusRequester,
        isContentShelfVisible = false,
        onHideShelf = {},
        onInteract = {},
        onSettingsClick = { section, _ -> onSectionOpened(section) },
        onBack = {},
        onOpenItem = {},
    )
}

@Composable
private fun SeekBarHarness(
    player: FakePlayer,
    chapters: List<ChapterMarker> = emptyList(),
) {
    val playPauseFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val seekBarFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var currentPosition by remember { mutableStateOf(30_000L) }

    // Synchronize currentPosition with fake player
    LaunchedEffect(currentPosition) {
        player.lastSeekPosition = currentPosition
    }

    PlayerControls(
        isVisible = true,
        isPlaying = true,
        positionProvider = { currentPosition },
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
