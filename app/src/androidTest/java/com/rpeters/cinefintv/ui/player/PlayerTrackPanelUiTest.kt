package com.rpeters.cinefintv.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PlayerTrackPanelUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allSection_showsPlaybackOptions_andAutoplayToggle() {
        composeRule.setContent {
            PlayerTestHost {
                PlayerTrackPanel(
                    isVisible = true,
                    section = SettingsSection.ALL,
                    anchorBounds = Rect(100f, 300f, 200f, 350f),
                    uiState = PlayerUiState(
                        title = "Player Test",
                        isLoading = false,
                        isEpisodicContent = true,
                        autoPlayNextEpisode = true,
                        transcodingQuality = TranscodingQuality.AUTO,
                        playbackSpeed = 1.0f,
                    ),
                    audioTracks = listOf(TrackOption("a1", "English", "en", 0)),
                    subtitleTracks = listOf(TrackOption("s1", "English CC", "en", 1)),
                    onAudioTrackSelected = {},
                    onSubtitleTrackSelected = {},
                    onSectionSelected = {},
                    onQualitySelected = {},
                    onPlaybackSpeedSelected = {},
                    onAutoPlayChange = {},
                    onClose = {},
                    onInteract = {},
                )
            }
        }

        composeRule.onNodeWithText("Playback Options").assertIsDisplayed()
        composeRule.onNodeWithText("Streaming Quality").assertIsDisplayed()
        composeRule.onNodeWithText("Audio Track").assertIsDisplayed()
        composeRule.onNodeWithText("Subtitles").assertIsDisplayed()
        composeRule.onNodeWithText("Playback Speed").assertIsDisplayed()
        composeRule.onNodeWithText("Auto-play next").assertIsDisplayed()
    }

    @Test
    fun audioSection_selectingTrack_updatesSelection_andCloses() {
        composeRule.setContent {
            PlayerTestHost {
                AudioPanelSelectionHarness()
            }
        }

        composeRule.onNodeWithText("Spanish").assertIsDisplayed().requestFocus()
        composeRule.onNodeWithText("Spanish").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("selected:Spanish", useUnmergedTree = true).fetchSemanticsNode()
        composeRule.onNodeWithText("closed:true", useUnmergedTree = true).fetchSemanticsNode()
    }

    @Test
    fun subtitlesSection_selectingNone_updatesSelection_andCloses() {
        composeRule.setContent {
            PlayerTestHost {
                SubtitlePanelSelectionHarness()
            }
        }

        composeRule.onNodeWithText("None").assertIsDisplayed().requestFocus()
        composeRule.onNodeWithText("None").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("subtitle:none", useUnmergedTree = true).fetchSemanticsNode()
        composeRule.onNodeWithText("closed:true", useUnmergedTree = true).fetchSemanticsNode()
    }

    @Test
    fun qualitySection_selectingQuality_updatesSelection_andCloses() {
        composeRule.setContent {
            PlayerTestHost {
                QualityPanelSelectionHarness()
            }
        }

        composeRule.onNodeWithText(TranscodingQuality.HIGH.label).assertIsDisplayed().requestFocus()
        composeRule.onNodeWithText(TranscodingQuality.HIGH.label).performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("quality:${TranscodingQuality.HIGH.label}", useUnmergedTree = true).fetchSemanticsNode()
        composeRule.onNodeWithText("closed:true", useUnmergedTree = true).fetchSemanticsNode()
    }
}

private fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }

@Composable
private fun AudioPanelSelectionHarness() {
    var selected by remember { mutableStateOf("none") }
    var closed by remember { mutableStateOf(false) }
    val audioTracks = listOf(
        TrackOption("a1", "English", "en", 0),
        TrackOption("a2", "Spanish", "es", 1),
    )

    Column {
        PlayerTrackPanel(
            isVisible = !closed,
            section = SettingsSection.AUDIO,
            anchorBounds = Rect(100f, 300f, 200f, 350f),
            uiState = PlayerUiState(
                title = "Player Test",
                isLoading = false,
                selectedAudioTrack = audioTracks.first(),
            ),
            audioTracks = audioTracks,
            subtitleTracks = emptyList(),
            onAudioTrackSelected = { selected = it.label },
            onSubtitleTrackSelected = {},
            onSectionSelected = {},
            onQualitySelected = {},
            onPlaybackSpeedSelected = {},
            onAutoPlayChange = {},
            onClose = { closed = true },
            onInteract = {},
        )

        androidx.tv.material3.Text("selected:$selected")
        androidx.tv.material3.Text("closed:$closed")
    }
}

@Composable
private fun SubtitlePanelSelectionHarness() {
    var selected by remember { mutableStateOf("unset") }
    var closed by remember { mutableStateOf(false) }

    Column {
        PlayerTrackPanel(
            isVisible = !closed,
            section = SettingsSection.SUBTITLES,
            anchorBounds = Rect(100f, 300f, 200f, 350f),
            uiState = PlayerUiState(
                title = "Player Test",
                isLoading = false,
                selectedSubtitleTrack = TrackOption("s1", "English CC", "en", 1),
            ),
            audioTracks = emptyList(),
            subtitleTracks = listOf(TrackOption("s1", "English CC", "en", 1)),
            onAudioTrackSelected = {},
            onSubtitleTrackSelected = { selected = it?.label ?: "none" },
            onSectionSelected = {},
            onQualitySelected = {},
            onPlaybackSpeedSelected = {},
            onAutoPlayChange = {},
            onClose = { closed = true },
            onInteract = {},
        )

        androidx.tv.material3.Text("subtitle:$selected")
        androidx.tv.material3.Text("closed:$closed")
    }
}

@Composable
private fun QualityPanelSelectionHarness() {
    var selected by remember { mutableStateOf(TranscodingQuality.AUTO.label) }
    var closed by remember { mutableStateOf(false) }

    Column {
        PlayerTrackPanel(
            isVisible = !closed,
            section = SettingsSection.QUALITY,
            anchorBounds = Rect(100f, 300f, 200f, 350f),
            uiState = PlayerUiState(
                title = "Player Test",
                isLoading = false,
                transcodingQuality = TranscodingQuality.AUTO,
            ),
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            onAudioTrackSelected = {},
            onSubtitleTrackSelected = {},
            onSectionSelected = {},
            onQualitySelected = { selected = it.label },
            onPlaybackSpeedSelected = {},
            onAutoPlayChange = {},
            onClose = { closed = true },
            onInteract = {},
        )

        androidx.tv.material3.Text("quality:$selected")
        androidx.tv.material3.Text("closed:$closed")
    }
}

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
