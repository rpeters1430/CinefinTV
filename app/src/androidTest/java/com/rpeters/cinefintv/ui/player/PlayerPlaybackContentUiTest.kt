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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PlayerPlaybackContentUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun directionPress_whenControlsHidden_showsOverlayAndFocusesPlayPauseButton() {
        composeRule.setContent {
            PlayerPlaybackTestHost {
                PlaybackShellHarness(initialControlsVisible = false)
            }
        }

        assertTrue(composeRule.onAllNodesWithTag(PlayerTestTags.ControlsOverlay).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag(PlayerTestTags.PlaybackRoot)
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag(PlayerTestTags.ControlsOverlay).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.PlayPauseButton, useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun centerPress_whenControlsHidden_revealsOverlay_withoutTogglingPlayback() {
        val player = FakePlayer()

        composeRule.setContent {
            PlayerPlaybackTestHost {
                PlaybackShellHarness(
                    player = player,
                    initialControlsVisible = false,
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.PlaybackRoot)
            .requestFocus()
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.onNodeWithTag(PlayerTestTags.ControlsOverlay).assertIsDisplayed()
        assertEquals(false, player.pauseCalled)
        assertEquals(false, player.playCalled)
    }

    @Test
    fun skipIntroAction_clickSeeksToSkipTarget() {
        val player = FakePlayer(currentPositionMs = 10_000L, durationMs = 90_000L)

        composeRule.setContent {
            PlayerPlaybackTestHost {
                PlaybackShellHarness(
                    player = player,
                    initialControlsVisible = false,
                    uiState = PlayerUiState(
                        title = "Episode Test",
                        isLoading = false,
                        introSkipRange = SkipRange(startMs = 5_000L, endMs = 18_000L),
                    ),
                    renderState = PlayerRenderState(
                        isPlaying = true,
                        duration = 90_000L,
                    ),
                    positionProvider = { 10_000L },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.SkipIntroAction).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.SkipActionButton)
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(18_000L, player.lastSeekPosition)
    }

    @Test
    fun nextEpisodeCard_clickInvokesOpenItem() {
        var openedItemId: String? = null

        composeRule.setContent {
            PlayerPlaybackTestHost {
                PlaybackShellHarness(
                    initialControlsVisible = false,
                    onOpenItem = { openedItemId = it },
                    uiState = PlayerUiState(
                        title = "Series Title",
                        isLoading = false,
                        isEpisodicContent = true,
                        autoPlayNextEpisode = true,
                        nextEpisodeId = "episode-2",
                        nextEpisodeTitle = "Episode 2",
                    ),
                    renderState = PlayerRenderState(
                        isPlaying = true,
                        duration = 120_000L,
                    ),
                    positionProvider = { 110_000L },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.NextEpisodeCard).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.NextEpisodeButton)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals("episode-2", openedItemId)
        }
    }

    @Test
    fun directionPress_whenNextEpisodeVisible_focusesPlayPauseButtonNotNextEpisodeButton() {
        composeRule.setContent {
            PlayerPlaybackTestHost {
                PlaybackShellHarness(
                    initialControlsVisible = false,
                    uiState = PlayerUiState(
                        title = "Series Title",
                        isLoading = false,
                        isEpisodicContent = true,
                        autoPlayNextEpisode = true,
                        nextEpisodeId = "episode-2",
                        nextEpisodeTitle = "Episode 2",
                    ),
                    renderState = PlayerRenderState(
                        isPlaying = true,
                        duration = 120_000L,
                    ),
                    positionProvider = { 110_000L },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.PlaybackRoot)
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag(PlayerTestTags.ControlsOverlay).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.PlayPauseButton, useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun resumeDialog_rendersAndDispatchesResumeChoice() {
        var resumeChoice: Boolean? = null

        composeRule.setContent {
            PlayerPlaybackTestHost {
                PlaybackShellHarness(
                    uiState = PlayerUiState(
                        title = "Movie Test",
                        isLoading = false,
                        shouldShowResumeDialog = true,
                        savedPlaybackPositionMs = 95_000L,
                    ),
                    onResumePlayback = { resumeChoice = it },
                )
            }
        }

        composeRule.onNodeWithTag(PlayerTestTags.ResumeDialog).assertIsDisplayed()
        composeRule.onNodeWithText("Would you like to continue from 01:35 or start from the beginning?")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Resume")
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(true, resumeChoice)
        }
    }
}

@Composable
private fun PlaybackShellHarness(
    player: Player = remember { FakePlayer() },
    uiState: PlayerUiState = PlayerUiState(
        title = "Playback Harness",
        isLoading = false,
        audioTracks = listOf(TrackOption("a1", "English", "en", 0)),
        subtitleTracks = listOf(TrackOption("s1", "English CC", "en", 1)),
    ),
    renderState: PlayerRenderState = PlayerRenderState(
        isPlaying = true,
        duration = 120_000L,
        bufferedFraction = 0.5f,
    ),
    positionProvider: () -> Long = { 30_000L },
    initialControlsVisible: Boolean = true,
    onOpenItem: (String) -> Unit = {},
    onResumePlayback: (Boolean) -> Unit = {},
) {
    var controlsVisible by remember { mutableStateOf(initialControlsVisible) }
    PlayerPlaybackContent(
        exoPlayer = player,
        uiState = uiState,
        renderStateProvider = { renderState },
        positionProvider = positionProvider,
        controlsVisible = controlsVisible,
        onControlsVisibleChange = { controlsVisible = it },
        onBack = {},
        onOpenItem = onOpenItem,
        onResumePlayback = onResumePlayback,
        onAudioTrackSelected = {},
        onSubtitleTrackSelected = {},
        onQualitySelected = {},
        onPlaybackSpeedSelected = {},
        onAutoPlayChange = {},
        showVideoSurface = false,
        controlsHideDelayMs = 5_000L,
    )
}

private fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }

@Composable
private fun PlayerPlaybackTestHost(content: @Composable () -> Unit) {
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
