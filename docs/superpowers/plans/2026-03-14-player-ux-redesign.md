# Player UX Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the video playback controls to match YouTube TV's style — transparent controls over a dark gradient, inline seek bar with timestamp bubble and buffered section, red pill Skip Intro chip, and a thumbnail-based Next Up card.

**Architecture:** Incremental refactor of existing player files. `PlayerControls.kt` owns all bottom-bar UI and the new `NextEpisodeCard` composable. `PlayerScreen.kt` owns overlay layout (skip chip + Next Up card placement). `PlayerModels.kt` and `PlayerViewModel.kt` get minimal additions for thumbnail URL.

**Tech Stack:** Jetpack Compose, `androidx.tv.material3`, Coil `AsyncImage`, ExoPlayer, Hilt, Kotlin coroutines.

---

## Chunk 1: Data Layer

### Task 1: Add `nextEpisodeThumbnailUrl` to `PlayerUiState`

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerModels.kt`

- [ ] **Step 1: Add the field**

In `PlayerUiState`, add one line after `val nextEpisodeTitle`:

```kotlin
val nextEpisodeThumbnailUrl: String? = null,
```

Full updated data class tail (lines 25–39 area):
```kotlin
    val nextEpisodeId: String? = null,
    val nextEpisodeTitle: String? = null,
    val nextEpisodeThumbnailUrl: String? = null,   // NEW
    val audioTracks: List<TrackOption> = emptyList(),
```

- [ ] **Step 2: Build to confirm no compile errors**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL` (the new nullable field with a default requires no other changes).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerModels.kt
git commit -m "feat(player): add nextEpisodeThumbnailUrl to PlayerUiState"
```

---

### Task 2: Populate `nextEpisodeThumbnailUrl` in `PlayerViewModel.load()`

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerViewModel.kt:193-225`

- [ ] **Step 1: Locate the next episode fetch block**

In `load()`, find the block (around line 193):
```kotlin
if (isEpisodicContent) {
    val nextResult = repositories.media.getNextEpisode(itemId)
    if (nextResult is ApiResult.Success) {
        val nextEpisode = nextResult.data
        if (nextEpisode != null) {
            nextEpisodeId = nextEpisode.id.toString()
            nextEpisodeTitle = nextEpisode.getDisplayTitle()
        }
    }
}
```

- [ ] **Step 2: Add thumbnail fetch inside the `nextEpisode != null` block**

```kotlin
if (isEpisodicContent) {
    val nextResult = repositories.media.getNextEpisode(itemId)
    if (nextResult is ApiResult.Success) {
        val nextEpisode = nextResult.data
        if (nextEpisode != null) {
            nextEpisodeId = nextEpisode.id.toString()
            nextEpisodeTitle = nextEpisode.getDisplayTitle()
            nextEpisodeThumbnailUrl = repositories.stream.getImageUrl(nextEpisode.id.toString())  // NEW
        }
    }
}
```

Declare `var nextEpisodeThumbnailUrl: String? = null` alongside the other locals at the top of the `launch` block (where `nextEpisodeId` and `nextEpisodeTitle` are declared).

- [ ] **Step 3: Thread into `_uiState.value.copy()`**

Find the `_uiState.value = _uiState.value.copy(...)` call at the end of `load()` and add:
```kotlin
nextEpisodeThumbnailUrl = nextEpisodeThumbnailUrl,
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerViewModel.kt
git commit -m "feat(player): fetch next episode thumbnail URL in load()"
```

---

## Chunk 2: SeekBarControl Rewrite

### Task 3: Rewrite `SeekBarControl` with YouTube TV focused state

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerControls.kt:418-512`

This is a full replacement of the private `SeekBarControl` composable. The surrounding file is untouched in this task.

- [ ] **Step 1: Add missing imports at the top of `PlayerControls.kt`**

The new SeekBarControl uses `drawBehind`, `shadow`, `border`, and `SurfaceDefaults`. Check if these are already imported; add any that aren't:

```kotlin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.tv.material3.SurfaceDefaults
import androidx.compose.foundation.layout.offset
// offset is already imported — confirm
```

Also remove the now-unused imports (will cause warnings otherwise):
```kotlin
// Remove these two lines:
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
```

- [ ] **Step 2: Replace the `SeekBarControl` composable (lines 418–512)**

Delete the entire existing `SeekBarControl` function and replace with:

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeekBarControl(
    position: Long,
    duration: Long,
    bufferedFraction: Float,
    chapters: List<ChapterMarker>,
    onSeek: (Long) -> Unit,
    onInteract: () -> Unit,
    focusRequester: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf(0) }
    // Tracks the live seek position during a hold-seek; resyncs when polled position updates
    var seekPosition by remember(position) { mutableLongStateOf(position) }

    val barHeight by animateDpAsState(if (isFocused) 8.dp else 3.dp, label = "BarHeight")
    val thumbScale by animateFloatAsState(if (isFocused) 1f else 0f, label = "ThumbScale")

    LaunchedEffect(seekDirection, duration) {
        if (seekDirection == 0 || duration <= 0L) return@LaunchedEffect
        while (seekDirection != 0) {
            seekPosition = (seekPosition + seekDirection * com.rpeters.cinefintv.core.constants.Constants.PLAYER_SEEK_INCREMENT_MS)
                .coerceIn(0L, duration)
            onSeek(seekPosition)
            onInteract()
            kotlinx.coroutines.delay(100L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .focusRequester(focusRequester)
            .focusProperties {
                this.up = up
                this.down = down
            }
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (!isFocused) seekDirection = 0
            }
            .onPreviewKeyEvent { keyEvent ->
                when {
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft -> {
                        seekDirection = -1; true
                    }
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight -> {
                        seekDirection = 1; true
                    }
                    keyEvent.type == KeyEventType.KeyUp &&
                        (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight) -> {
                        seekDirection = 0; true
                    }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            val progressFraction =
                if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                else 0f
            val bufferedClamped = bufferedFraction.coerceIn(0f, 1f)

            // Buffered section — lighter grey between progress end and buffered position
            if (bufferedClamped > progressFraction) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(bufferedClamped)
                        .background(Color.White.copy(alpha = 0.35f))
                )
            }

            // Progress fill — CinefinRed
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressFraction)
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Chapter marker ticks — 2dp wide black dividers
            chapters.forEach { chapter ->
                val chapterFraction =
                    if (duration > 0L) (chapter.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    else 0f
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = maxWidth * chapterFraction - 1.dp)
                        .width(2.dp)
                        .height(barHeight)
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }

            // Thumb — only visible when focused
            if (thumbScale > 0f) {
                val thumbDp = 20.dp * thumbScale
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = maxWidth * progressFraction - thumbDp / 2)
                        .size(thumbDp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = MaterialTheme.colorScheme.primary,
                            spotColor = MaterialTheme.colorScheme.primary,
                        )
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(width = 3.dp, color = Color.White, shape = CircleShape)
                )
            }
        }

        // Timestamp bubble — floats above the thumb when focused
        if (isFocused) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                val progressFraction =
                    if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    else 0f
                val bubbleWidth = 56.dp
                val thumbX = maxWidth * progressFraction
                val clampedX = thumbX.coerceIn(0.dp, maxWidth - bubbleWidth)

                Surface(
                    modifier = Modifier
                        .offset(x = clampedX, y = (-30).dp)
                        .width(bubbleWidth),
                    shape = RoundedCornerShape(4.dp),
                    colors = SurfaceDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.85f)
                    )
                ) {
                    Text(
                        text = formatMs(position),
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Update the `SeekBarControl` call site in `PlayerControls` (around line 211)**

The existing call:
```kotlin
SeekBarControl(
    position = position,
    duration = duration,
    chapters = uiState.chapters,
    player = player,
    focusRequester = seekBarFocusRequester,
    up = backFocusRequester,
    down = playPauseFocusRequester,
    onInteract = onInteract,
)
```

Replace with:
```kotlin
SeekBarControl(
    position = position,
    duration = duration,
    bufferedFraction = bufferedFraction,
    chapters = uiState.chapters,
    onSeek = { player.seekTo(it) },
    onInteract = onInteract,
    focusRequester = seekBarFocusRequester,
    up = backFocusRequester,
    down = playPauseFocusRequester,
)
```

Note: `bufferedFraction` is a new `PlayerControls` parameter added in Task 4. For now, temporarily pass `0f` so the file compiles:
```kotlin
bufferedFraction = 0f, // placeholder until Task 4
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerControls.kt
git commit -m "feat(player): rewrite SeekBarControl — YouTube TV style seek bar with bubble and buffered section"
```

---

## Chunk 3: Controls Layout + NextEpisodeCard

### Task 4: Restructure `PlayerControls` layout

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerControls.kt:87-321`

- [ ] **Step 1: Add `bufferedFraction` to `PlayerControls` signature**

Change:
```kotlin
internal fun PlayerControls(
    isVisible: Boolean,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    uiState: PlayerUiState,
    player: ExoPlayer,
    playPauseFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    onInteract: () -> Unit,
    onSettingsClick: (SettingsSection, Rect) -> Unit,
    onBack: () -> Unit,
)
```

To:
```kotlin
internal fun PlayerControls(
    isVisible: Boolean,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    bufferedFraction: Float,              // NEW
    uiState: PlayerUiState,
    player: ExoPlayer,
    playPauseFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    onInteract: () -> Unit,
    onSettingsClick: (SettingsSection, Rect) -> Unit,
    onBack: () -> Unit,
)
```

Also remove the `0f` placeholder from the `SeekBarControl` call and replace with `bufferedFraction`.

- [ ] **Step 2: Replace the gradient stop values**

Current gradient (lines ~127-133):
```kotlin
Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.7f),
    0.3f to Color.Transparent,
    0.7f to Color.Transparent,
    1.0f to Color.Black.copy(alpha = 0.85f)
)
```

Replace with (taller bottom gradient — covers ~45%):
```kotlin
Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.7f),
    0.25f to Color.Transparent,
    0.55f to Color.Transparent,
    1.0f to Color.Black.copy(alpha = 0.92f)
)
```

- [ ] **Step 3: Replace the bottom panel Column**

Delete the entire bottom `Column` block (from `// Floating Minimalist Bottom Panel` comment through the closing `}` of the glassmorphism `Row`) and replace with:

```kotlin
// Bottom controls — transparent, sits directly on gradient
Column(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = spacing.gutter, vertical = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    // Seek row: [current time] [seekbar] [duration]
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = formatMs(position),
            style = MaterialTheme.typography.labelLarge,
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.9f),
        )
        SeekBarControl(
            position = position,
            duration = duration,
            bufferedFraction = bufferedFraction,
            chapters = uiState.chapters,
            onSeek = { player.seekTo(it) },
            onInteract = onInteract,
            focusRequester = seekBarFocusRequester,
            up = backFocusRequester,
            down = playPauseFocusRequester,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMs(duration),
            style = MaterialTheme.typography.labelLarge,
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.5f),
        )
    }

    // Button row: [-10] [spacer] [▶] [spacer] [+10] [divider] [CC] [♪] [⚙]
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Skip Back 10s
        ActionIconButton(
            icon = Icons.Default.Replay10,
            onClick = { onInteract(); player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) },
            modifier = Modifier
                .focusRequester(skipBackFocusRequester)
                .focusProperties {
                    up = seekBarFocusRequester
                    right = playPauseFocusRequester
                }
        )

        Spacer(Modifier.weight(1f))

        // Play/Pause
        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = {
                onInteract()
                if (isPlaying) player.pause() else player.play()
            },
            modifier = Modifier
                .size(56.dp)
                .focusRequester(playPauseFocusRequester)
                .focusProperties {
                    up = seekBarFocusRequester
                    left = skipBackFocusRequester
                    right = skipForwardFocusRequester
                }
        )

        Spacer(Modifier.weight(1f))

        // Skip Forward 10s
        ActionIconButton(
            icon = Icons.Default.Forward10,
            onClick = { onInteract(); player.seekTo((player.currentPosition + 10_000L).coerceIn(0L, duration)) },
            modifier = Modifier
                .focusRequester(skipForwardFocusRequester)
                .focusProperties {
                    up = seekBarFocusRequester
                    left = playPauseFocusRequester
                    right = subtitleFocusRequester
                }
        )

        // Vertical divider
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(1.dp)
                .height(24.dp)
                .background(Color.White.copy(alpha = 0.4f))
        )

        // CC (Subtitles)
        ActionIconButton(
            icon = Icons.Default.ClosedCaption,
            onClick = { onInteract(); onSettingsClick(SettingsSection.SUBTITLES, subtitleButtonBounds) },
            modifier = Modifier
                .focusRequester(subtitleFocusRequester)
                .focusProperties {
                    up = seekBarFocusRequester
                    left = skipForwardFocusRequester
                    right = audioFocusRequester
                }
                .onGloballyPositioned { setSubtitleButtonBounds(it.boundsInRoot()) }
        )

        // ♪ (Audio)
        ActionIconButton(
            icon = Icons.Default.GraphicEq,
            onClick = { onInteract(); onSettingsClick(SettingsSection.AUDIO, audioButtonBounds) },
            modifier = Modifier
                .focusRequester(audioFocusRequester)
                .focusProperties {
                    up = seekBarFocusRequester
                    left = subtitleFocusRequester
                    right = settingsFocusRequester
                }
                .onGloballyPositioned { setAudioButtonBounds(it.boundsInRoot()) }
        )

        // ⚙ (All settings)
        ActionIconButton(
            icon = Icons.Default.Settings,
            onClick = { onInteract(); onSettingsClick(SettingsSection.ALL, moreButtonBounds) },
            modifier = Modifier
                .focusRequester(settingsFocusRequester)
                .focusProperties {
                    up = seekBarFocusRequester
                    left = audioFocusRequester
                }
                .onGloballyPositioned { setMoreButtonBounds(it.boundsInRoot()) }
        )
    }
}
```

- [ ] **Step 4: Build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerControls.kt
git commit -m "feat(player): restructure controls layout — inline seek bar timestamps, no glassmorphism"
```

---

### Task 5: Replace `NextEpisodeCountdown` with `NextEpisodeCard`

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerControls.kt:323-368`

Add `coil3.compose.rememberAsyncImagePainter` import if not present — `AsyncImage` is already imported at line 83.

- [ ] **Step 1: Delete `NextEpisodeCountdown` (lines 323–368)**

Remove the entire `NextEpisodeCountdown` composable function.

- [ ] **Step 2: Write `NextEpisodeCard` in its place**

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NextEpisodeCard(
    title: String,
    thumbnailUrl: String?,
    remainingMs: Long,
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressFraction = ((15_000L - remainingMs) / 15_000f).coerceIn(0f, 1f)

    Surface(
        modifier = modifier.width(180.dp),
        shape = RoundedCornerShape(8.dp),
        colors = SurfaceDefaults.colors(
            containerColor = com.rpeters.cinefintv.ui.theme.SurfaceDark,
        ),
    ) {
        Column {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(com.rpeters.cinefintv.ui.theme.SurfaceDark),
            )

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Starting in ${remainingMs / 1000}s\u2026",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )

                // Draining red progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                Button(
                    onClick = onPlayNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = "▶  Play Now",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerControls.kt
git commit -m "feat(player): replace NextEpisodeCountdown with NextEpisodeCard (thumbnail + progress bar)"
```

---

## Chunk 4: PlayerScreen Wiring

### Task 6: Wire everything together in `PlayerScreen`

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerScreen.kt`

- [ ] **Step 1: Add `slideInHorizontally` / `slideOutHorizontally` imports**

At the top of `PlayerScreen.kt`, add:
```kotlin
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
```

- [ ] **Step 2: Add `bufferedFraction` to the 500ms polling loop**

Find the `LaunchedEffect(player)` block (line ~136):
```kotlin
LaunchedEffect(player) {
    while (true) {
        isPlaying = player.isPlaying
        isBuffering = player.playbackState == Player.STATE_BUFFERING
        position = player.currentPosition.coerceAtLeast(0L)
        duration = player.duration.coerceAtLeast(0L)
        delay(500L)
    }
}
```

Add a `bufferedFraction` state variable alongside the others (near line 131):
```kotlin
var bufferedFraction by remember { mutableStateOf(0f) }
```

Add one line inside the polling loop:
```kotlin
bufferedFraction = if (duration > 0L) player.bufferedPosition.toFloat() / duration.toFloat() else 0f
```

- [ ] **Step 3: Update `PlayerControls` call site to pass `bufferedFraction`**

Find the `PlayerControls(...)` call (~line 268) and add the new parameter:
```kotlin
PlayerControls(
    isVisible = controlsVisible,
    isPlaying = isPlaying,
    position = position,
    duration = duration,
    bufferedFraction = bufferedFraction,   // NEW
    uiState = uiState,
    player = player,
    ...
)
```

- [ ] **Step 4: Replace the skip chip**

Find and delete the existing `AnimatedVisibility` for the skip chip (lines ~322–339):
```kotlin
AnimatedVisibility(
    visible = activeSkipLabel != null,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(horizontal = 48.dp, vertical = 48.dp),
) { ... }
```

Do NOT replace it here — it will move into the shared Column in Step 5.

- [ ] **Step 5: Replace the `NextEpisodeCountdown` Box and add the shared Column**

Find and delete the existing Next Up Box (lines ~374–384):
```kotlin
Box(modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp)) {
    NextEpisodeCountdown(...)
}
```

Replace both deleted blocks with a single shared `Column` at `BottomEnd`:

```kotlin
// Skip Intro chip + Next Up card — shared right-aligned column
Column(
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 96.dp, end = 48.dp),
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    // Skip Intro / Skip Credits chip
    AnimatedVisibility(
        visible = activeSkipLabel != null,
        enter = fadeIn() + slideInHorizontally { it },
        exit = fadeOut() + slideOutHorizontally { it },
    ) {
        Button(
            onClick = {
                player.seekTo(activeSkipTargetMs)
                onInteract()
            },
            modifier = Modifier.focusRequester(skipFocusRequester),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = MaterialTheme.colorScheme.primary,
            ),
            shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
        ) {
            Text(
                text = activeSkipLabel ?: "",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    // Next Up thumbnail card
    val remaining = if (duration > 0L) (duration - position) else -1L
    val showNextUp = remaining in 1L..NEXT_EPISODE_COUNTDOWN_THRESHOLD_MS
        && uiState.isEpisodicContent
        && uiState.autoPlayNextEpisode
        && uiState.nextEpisodeId != null

    AnimatedVisibility(
        visible = showNextUp,
        enter = fadeIn() + slideInHorizontally { it },
        exit = fadeOut() + slideOutHorizontally { it },
    ) {
        NextEpisodeCard(
            title = uiState.nextEpisodeTitle ?: "Next Episode",
            thumbnailUrl = uiState.nextEpisodeThumbnailUrl,
            remainingMs = remaining.coerceAtLeast(0L),
            onPlayNow = {
                coroutineScope.launch {
                    viewModel.getNextEpisodeId()?.let { onOpenItem(it) }
                }
            },
        )
    }
}
```

- [ ] **Step 6: Remove the old `countdownRemaining` state and its `LaunchedEffect`**

The `countdownRemaining` variable (line ~191) and its `LaunchedEffect(position, duration)` (lines ~193–204) are no longer needed. Delete both:
```kotlin
// DELETE:
var countdownRemaining by remember { mutableLongStateOf(-1L) }

// DELETE:
LaunchedEffect(position, duration) {
    if (duration > 0 && uiState.isEpisodicContent && ...) {
        ...
    }
}
```

- [ ] **Step 7: Add `ButtonDefaults` import if missing**

```kotlin
import androidx.tv.material3.ButtonDefaults
```

- [ ] **Step 8: Build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|BUILD"
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Full debug build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerScreen.kt
git commit -m "feat(player): wire YouTube TV controls — skip chip right-aligned, NextEpisodeCard, bufferedFraction"
```

---

## Verification Checklist

After all tasks complete, install on device/emulator and verify:

- [ ] Seek bar is thin (3dp) at rest, grows (8dp) on D-pad focus
- [ ] Red thumb with white ring appears on seek bar focus
- [ ] Timestamp bubble appears above thumb and updates as you scrub
- [ ] Buffered section visible as lighter grey ahead of progress
- [ ] Chapter markers appear as small tick marks (if content has chapters)
- [ ] Timestamps are inline left/right of the seek bar
- [ ] No glassmorphism pill around button row
- [ ] Play/pause button is centered; −10 left, +10 right
- [ ] CC, ♪, ⚙ appear right of a thin divider
- [ ] Skip Intro chip is red pill, bottom-right, slides in from right
- [ ] Skip Intro chip and Next Up card stack cleanly when both visible
- [ ] Next Up card shows thumbnail, title, countdown text, draining bar, Play Now button
- [ ] All text is legible at TV viewing distance (≥18sp)
