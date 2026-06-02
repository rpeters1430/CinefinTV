# Skip Intro / Credits Design

**Date:** 2026-06-01  
**Status:** Approved

## Overview

Integrate the [intro-skipper Jellyfin plugin](https://github.com/intro-skipper/intro-skipper) to provide precise intro and credits timestamps during video playback. Replaces the existing chapter-name heuristic with plugin API data. Adds an auto-skip preference (per segment type) surfaced in the Settings screen.

## Assumptions

- The intro-skipper plugin is installed on the user's Jellyfin server.
- Plugin timestamps are in seconds (floating point); the app converts to milliseconds.
- If the plugin returns no valid data for an item, the existing chapter-based detection remains as a fallback.

---

## Section 1: Data Layer

### IntroSkipperRepository

**File:** `data/repository/IntroSkipperRepository.kt`  
**Type:** `@Singleton`, extends `BaseJellyfinRepository`

Calls the plugin's REST endpoint using the injected `OkHttpClient` (same pattern as trickplay fetching in `JellyfinStreamRepository`):

```
GET {serverBaseUrl}/Episode/{itemId}/IntroTimestamps
Authorization: MediaBrowser Token="..."
```

**Response shape** (timestamps in seconds as `Double`):
```json
{
  "Valid": true,
  "Introduction": { "Start": 15.5, "End": 75.2 },
  "Credits":      { "Start": 1320.0, "End": 1380.0 }
}
```

**Return type:** `IntroSkipperSegments(intro: SkipRange?, credits: SkipRange?)` — seconds converted to milliseconds. Returns `null` on any error (HTTP failure, plugin absent, item not analyzed). Both `intro` and `credits` within a valid response can independently be null if not present.

**Serialization models** (kotlinx.serialization, `ignoreUnknownKeys = true`):
```kotlin
@Serializable data class IntroSkipperResponse(
    @SerialName("Valid") val valid: Boolean,
    @SerialName("Introduction") val introduction: IntroSkipperSegment? = null,
    @SerialName("Credits") val credits: IntroSkipperSegment? = null,
)
@Serializable data class IntroSkipperSegment(
    @SerialName("Start") val start: Double,
    @SerialName("End") val end: Double,
)
data class IntroSkipperSegments(val intro: SkipRange?, val credits: SkipRange?)
```

**Dependencies:** `OkHttpClient`, `JellyfinAuthRepository`, `JellyfinSessionManager`, `JellyfinCache`, `DispatcherProvider` — all already in the singleton graph. No new Hilt module needed.

---

### IntroSkipPreferencesRepository

**File:** `data/preferences/IntroSkipPreferencesRepository.kt`  
**Type:** `@Singleton`

Stores two booleans in the existing `playback_preferences` DataStore (shares `@PlaybackPreferencesDataStore` qualifier, different keys — no new Hilt provision needed):

| DataStore key | Default | Meaning |
|---|---|---|
| `auto_skip_intro` | `false` | Auto-seek to end of intro when entering intro range |
| `auto_skip_credits` | `false` | Auto-seek to end of credits when entering credits range |

**Public API:**
```kotlin
val preferencesFlow: Flow<IntroSkipPreferences>
suspend fun setAutoSkipIntro(enabled: Boolean)
suspend fun setAutoSkipCredits(enabled: Boolean)
```

```kotlin
data class IntroSkipPreferences(
    val autoSkipIntro: Boolean = false,
    val autoSkipCredits: Boolean = false,
) {
    companion object { val DEFAULT = IntroSkipPreferences() }
}
```

---

## Section 2: ViewModel + UiState

### PlayerUiState additions

```kotlin
val autoSkipIntro: Boolean = false,
val autoSkipCredits: Boolean = false,
```

### PlayerViewModel changes

**New injected dependencies:** `IntroSkipperRepository`, `IntroSkipPreferencesRepository`

**In `init {}`:** New `collectLatest` block collects `IntroSkipPreferencesRepository.preferencesFlow` and updates `autoSkipIntro`/`autoSkipCredits` in `_uiState`.

**In `loadInternal()`:** After chapter-based skip range detection, call the plugin. Plugin timestamps replace chapter-derived ones when `Valid = true`; chapters remain the fallback if the call returns `null` or a segment is absent:

```kotlin
introSkipperRepository.getSegments(resolvedItemId)?.let { segments ->
    introSkipRange = segments.intro ?: introSkipRange
    creditsSkipRange = segments.credits ?: creditsSkipRange
}
```

No new public ViewModel functions — auto-skip is entirely preference-driven.

---

## Section 3: Auto-skip Logic + UI Positioning

### Layout change

`PlayerSkipActions` (single composable at `BottomEnd`) is replaced by two independent composables in `PlayerPlaybackContent`:

| Composable | Alignment | Padding |
|---|---|---|
| `PlayerSkipIntroAction` | `BottomStart` | `bottom = 96.dp, start = 48.dp` |
| `PlayerSkipCreditsAction` | `BottomEnd` | `bottom = 96.dp, end = 48.dp` |

Each polls `player.currentPosition` every 500 ms against its respective `SkipRange`.

### Auto-skip behavior

Each composable receives an `autoSkip: Boolean` prop (`uiState.autoSkipIntro` / `uiState.autoSkipCredits`).

- **`autoSkip = false`:** Shows the button via `AnimatedVisibility`; auto-focuses so D-pad center confirms. Existing behavior.
- **`autoSkip = true`:** Calls `onSkip(targetMs)` immediately on entering the range. The button is still briefly visible (provides feedback) then fades as the seek completes. Focus is not requested — no UI interruption.

### Focus

- Intro button: reuses existing `skipFocusRequester`.
- Credits button: new `creditsFocusRequester` (same `FocusRequester` pattern).
- `overlayActionFocused` in `PlayerPlaybackContent` covers both via `onFocusChanged`.

---

## Section 4: Settings Screen

### New category

```kotlin
SKIP_INTRO(
    label = "Skip Intro",
    description = "Auto-skip behavior for intros and credits",
    icon = Icons.Default.FastForward,
)
```

### SettingsUiState addition

```kotlin
val introSkip: IntroSkipPreferences = IntroSkipPreferences.DEFAULT,
```

### SettingsViewModel changes

- Inject `IntroSkipPreferencesRepository`
- Add `introSkipPreferencesRepository.preferencesFlow` to the `combine(...)` in `init {}`
- Expose `setAutoSkipIntro(Boolean)` and `setAutoSkipCredits(Boolean)` functions

### Settings panel UI

Two `CinefinSwitchListItem` rows under the **Skip Intro** category:

| Label | Subtitle |
|---|---|
| "Auto-skip intros" | "Automatically skip intro segments when detected" |
| "Auto-skip credits" | "Automatically skip end credits when detected" |

No new dialogs — simple on/off toggles using the existing switch pattern.

---

## Files Changed / Created

| File | Change |
|---|---|
| `data/repository/IntroSkipperRepository.kt` | **New** |
| `data/preferences/IntroSkipPreferencesRepository.kt` | **New** |
| `ui/player/PlayerModels.kt` | Add `autoSkipIntro`, `autoSkipCredits` to `PlayerUiState` |
| `ui/player/PlayerViewModel.kt` | Inject repos, update `init {}` and `loadInternal()` |
| `ui/player/PlayerScreen.kt` | Split `PlayerSkipActions` → `PlayerSkipIntroAction` + `PlayerSkipCreditsAction` |
| `ui/screens/settings/SettingsScreen.kt` | Add `SKIP_INTRO` category + two toggle rows |
| `ui/screens/settings/SettingsViewModel.kt` | Inject repo, wire prefs, expose setters |

---

## Error Handling

- `IntroSkipperRepository.getSegments()` catches all exceptions and returns `null` — never throws. Errors are logged via `SecureLogger`.
- A `null` result leaves the chapter-based skip ranges untouched.
- A `Valid = false` response is treated the same as `null`.
