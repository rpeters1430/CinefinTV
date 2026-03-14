# Player UX Redesign — YouTube TV Style
**Date:** 2026-03-14
**Status:** Approved

## Goal

Redesign the video playback screen to match the look and feel of YouTube TV on Android TV: minimal transparent controls over a dark gradient, inline seek bar with an authentic focused state, red accent throughout, and richer overlay cards for Skip Intro and Next Up.

## Scope

**Files changed:**
- `ui/player/PlayerModels.kt` — add `nextEpisodeThumbnailUrl: String?` to `PlayerUiState`
- `ui/player/PlayerViewModel.kt` — populate `nextEpisodeThumbnailUrl` in `load()`
- `ui/player/PlayerControls.kt` — layout restructured, glassmorphism removed, seek row inlined, `SeekBarControl` updated, `NextEpisodeCountdown` composable replaced
- `ui/player/PlayerScreen.kt` — skip chip repositioned, `NextEpisodeCountdown` call site updated to pass new parameters, co-visibility layout wrapper added

**Files unchanged:**
- `PlayerTrackPanel.kt`, `PlayerLifecycle.kt`, `PlayerMappers.kt`, `PlayerUtils.kt`
- All playback logic, key handling, resume dialog, speed badge, buffering spinner, playback reporting

---

## Section 1 — Controls Bar Layout

### Structure

```
[top strip]   ← Back   Title / S2 E4 · Episode Title
[video area]
[bottom]      1:02:14 ━━━━━━●──────── 2:15:00
              −10           ▶          +10  │  CC  ♪  ⚙
```

### Changes to `PlayerControls.kt`

- **Remove glassmorphism pill** wrapping the button row. Buttons sit directly on the gradient overlay — no `Surface`, no border, no background.
- **Seek row**: single `Row` containing `[current time Text] [SeekBarControl, Modifier.weight(1f)] [duration Text]`. Timestamps are inline left and right of the track.
- **Button row**: `Row` with `Spacer(Modifier.weight(1f))` on both sides of the play/pause button to center it. Left group: `−10s skip back`. Right group: `+10s skip forward`. Settings group separated by a `1.dp` vertical `Divider` at 40% alpha: `CC · ♪ · ⚙`. No previous/next episode buttons in the button row.
- **Gradient overlay**: increase from ~35% to ~45% of screen height.
- Auto-hide timer, focus management, and key handling are unchanged.

---

## Section 2 — Seek Bar Focused State

### Updated `SeekBarControl` signature

```kotlin
@Composable
private fun SeekBarControl(
    position: Long,
    duration: Long,
    bufferedFraction: Float,       // NEW — fraction 0..1 of buffered content
    onSeek: (Long) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
)
```

`bufferedFraction` is sourced in `PlayerScreen.kt`'s 500ms polling loop:
```kotlin
val bufferedFraction = if (duration > 0L) {
    player.bufferedPosition.toFloat() / duration.toFloat()
} else 0f
```

### Unfocused state

- Track height: `3.dp` (reduced from current 6dp — more subtle when idle)
- Fill: `CinefinRed` (#E50914)
- Buffered section: `rgba(255,255,255,0.35)` layer rendered from the fill end to `bufferedFraction` position, same height as the track
- Chapter markers: `2.dp × 8.dp` white vertical tick marks at their fractional positions along the track
- No thumb visible

### Focused state

Triggered when the composable receives focus via D-pad.

- Track height animates `3.dp → 8.dp` via `animateDpAsState` (8dp is clearly visible at 10 feet, not extreme)
- **Thumb**: `20.dp` circle, `CinefinRed` fill, `3.dp` white stroke border, red drop shadow via `drawBehind`. Animates scale `0f → 1f` via `animateFloatAsState`.
- **Timestamp bubble**: `Surface(RoundedCornerShape(4.dp))` floating above the thumb, dark background, white text `18.sp`, displaying `formatMs(position)`. Horizontal position computed as:
  ```kotlin
  val rawOffsetDp = (progressFraction * trackWidthPx).toDp()
  val clampedOffset = rawOffsetDp.coerceIn(0.dp, trackWidth - bubbleWidth)
  ```
  where `trackWidthPx` is captured via `onGloballyPositioned` on the track `Box`, and `bubbleWidth` is `48.dp` (fixed). A `4.dp × 4.dp` downward triangle is drawn below the bubble via `Canvas` using `drawPath`.
- Left/right D-pad seek uses the existing `PLAYER_SEEK_INCREMENT_MS` constant.

---

## Section 3 — Skip Intro Chip

### Visual

- **Style**: solid `CinefinRed` pill — `RoundedCornerShape(50%)`, `8.dp` vertical / `20.dp` horizontal padding, white text `18.sp`, `FontWeight.SemiBold`
- **Labels**: "Skip Intro →" or "Skip Credits →" depending on active range
- **Position**: `Alignment.BottomEnd` in the root `PlayerScreen` `Box`, with `padding(bottom = 96.dp, end = 48.dp)` — 96dp places it above the controls bar which occupies roughly the bottom 80dp of the screen
- **Focused**: existing `skipFocusRequester` behavior preserved; focused state uses TV Material3 `Button` default focused colors (white container, dark text)

### Animation

- Enter: `fadeIn() + slideInHorizontally { it }` (slides in from right)
- Exit: `fadeOut() + slideOutHorizontally { it }`

### Co-visibility with Next Up card

Both elements live inside a single `Column(horizontalAlignment = Alignment.End)` anchored at `Alignment.BottomEnd` in the root `Box` of `PlayerScreen.kt`, with `padding(bottom = 96.dp, end = 48.dp)` and `8.dp` vertical spacing between them. Skip chip is first (top), Next Up card is second (bottom). This replaces the two currently separate `Box` placements for each element.

---

## Section 4 — Next Up Thumbnail Card

### Trigger

Unchanged: `(duration - position) in 1L..15_000L` and `nextEpisodeId != null`.

### New state field

```kotlin
// PlayerUiState
val nextEpisodeThumbnailUrl: String? = null
```

Populated in `PlayerViewModel.load()` after the existing `nextEpisodeId` fetch:
```kotlin
nextEpisodeThumbnailUrl = nextEpisode?.let {
    repositories.stream.getImageUrl(it.id.toString())
}
```

(`getImageUrl` is the correct method on `JellyfinStreamRepository`; it accepts `itemId: String` and defaults `imageType = "Primary"`.)

### Composable signature

The old `NextEpisodeCountdown` composable in `PlayerControls.kt` is **deleted**. A new `NextEpisodeCard` composable is written in `PlayerControls.kt` and called from `PlayerScreen.kt`:

```kotlin
@Composable
fun NextEpisodeCard(
    title: String,
    thumbnailUrl: String?,
    remainingMs: Long,          // raw remaining ms — used for both text and progress bar
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Inside the composable:
- Countdown text: `"Starting in ${remainingMs / 1000}s..."` — derived from `remainingMs`
- Progress bar fill fraction: `((15_000L - remainingMs) / 15_000f).coerceIn(0f, 1f)` — fills left-to-right as time runs out

### Card layout (160.dp wide, `RoundedCornerShape(8.dp)`, `SurfaceDark` background)

```
┌─────────────────────────┐
│  AsyncImage             │  height=90.dp, ContentScale.Crop, crossfade(true)
│  (episode thumbnail)    │  placeholder = SurfaceDark color block
├─────────────────────────┤
│  UP NEXT                │  labelMedium, 18.sp override, muted color
│  S2 E5 · Episode Title  │  bodyMedium, 18.sp, white, FontWeight.SemiBold
│  Starting in 8s...      │  labelMedium, 18.sp override, muted
│  ══════════════════════ │  2.dp red progress bar (fills as countdown runs)
│  [▶ Play Now]           │  TV Material3 Button, CinefinRed, 18.sp
└─────────────────────────┘
```

All text uses explicit `fontSize = 18.sp` overrides where the default style would be smaller, to comply with the 10-foot minimum text size rule.

### Animation

- Enter: `fadeIn() + slideInHorizontally { it }`
- Exit: `fadeOut() + slideOutHorizontally { it }`

---

## Implementation Order

1. **`PlayerModels.kt`** — add `nextEpisodeThumbnailUrl: String? = null` to `PlayerUiState`
2. **`PlayerViewModel.kt`** — populate via `repositories.stream.getImageUrl(nextEpisode.id.toString())`
3. **`PlayerControls.kt`** — restructure controls layout (seek row inline, remove glassmorphism, new button row)
4. **`PlayerControls.kt`** — update `SeekBarControl` (add `bufferedFraction`, new focused state, timestamp bubble)
5. **`PlayerControls.kt`** — delete `NextEpisodeCountdown`, write `NextEpisodeCard`
6. **`PlayerScreen.kt`** — add `bufferedFraction` to polling loop, reposition skip chip, wrap skip chip + Next Up in shared `Column`, update `NextEpisodeCard` call site

## TV-Specific Notes

- All interactive elements (`Button`, `OutlinedButton`) use `androidx.tv.material3` variants
- Timestamp bubble and Next Up card are display-only (not focusable) — focus stays on seek bar / play button
- Skip chip auto-focus when entering a skip range uses the existing `skipFocusRequester` in `PlayerScreen.kt` — behaviour preserved, element just repositioned
- All text in the new card uses `fontSize = 18.sp` minimum per the 10-foot viewing rule
- `bufferedFraction` guards against divide-by-zero when `duration == 0L`
