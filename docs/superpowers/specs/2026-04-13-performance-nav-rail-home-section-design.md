# Performance: Nav Rail Animation Consolidation + HomeSection Layout

**Date:** 2026-04-13  
**Scope:** Two targeted performance fixes in the high-risk UI areas — nav rail recomposition and HomeSection layout overhead. No new features; no changes outside these two files.

---

## 1. Nav Rail Animation Consolidation

### Problem

`CinefinAppScaffold` (`CinefinTvApp.kt` lines 296–315) declares four independent `animateDpAsState` calls all keyed to `navHasFocus`:

- `logoSize` — 50dp ↔ 46dp
- `iconSize` — 28dp ↔ 22dp
- `buttonPaddingVertical` — 14dp ↔ 10dp
- `buttonPaddingHorizontal` — 12dp ↔ 12dp (same value both ways — no-op animator)

Each creates its own animator object and independently schedules a recomposition every frame for the full animation duration (~280ms). This means 4× recomposition cost on every frame during every D-pad focus event on the nav rail.

### Fix

Replace the four `animateDpAsState` calls with a single `animateFloatAsState` progress value:

```kotlin
val railProgress by animateFloatAsState(
    targetValue = if (navHasFocus) 1f else 0f,
    animationSpec = tween(durationMillis = 280),
    label = "railProgress",
)
```

Derive all four values by lerping from their endpoints:

```kotlin
val logoSize               = lerp(50.dp, 46.dp, railProgress)
val iconSize               = lerp(28.dp, 22.dp, railProgress)
val buttonPaddingVertical  = lerp(14.dp, 10.dp, railProgress)
val buttonPaddingHorizontal = 12.dp  // plain val — was animating constant → constant
```

`lerp` for `Dp` is available from `androidx.compose.ui.unit.lerp`.

**Result:** 4 animators → 1 animator. One recomposition per frame during the rail transition instead of four.

### What does not change

- `AnimatedVisibility` for tab labels — already a single animation per tab, correct behavior, not touched.
- Animation duration (280ms) — unchanged.
- Visual result — identical to current behavior.

---

## 2. HomeSection `BoxWithConstraints` Removal

### Problem

`HomeSection` wraps the `LazyRow` in `BoxWithConstraints` (line 754 of `HomeScreen.kt`) solely to read `maxWidth` for computing `cardWidth` and `rowSpacing`. `BoxWithConstraints` forces a subcomposition pass — it must fully measure a subcompose node before its content lambda can run, adding a layout phase on every recomposition of each section row.

### Fix

Lift the width calculation one level up to `HomeLoadedContent`, reading from `LocalConfiguration` which is already available in that scope:

```kotlin
private val NAV_RAIL_SLOT_WIDTH = 208.dp  // railSlotWidth (196dp rail + 12dp start padding)

// Inside HomeLoadedContent:
val screenWidth = LocalConfiguration.current.screenWidthDp.dp
val availableWidth = screenWidth - NAV_RAIL_SLOT_WIDTH
val cardWidth = remember(availableWidth, spacing.gutter, spacing.cardGap) {
    ((availableWidth - (spacing.gutter * 2) - (spacing.cardGap * 2)) / 3f)
        .coerceIn(220.dp, 400.dp)
}
val rowSpacing = remember(spacing.cardGap) {
    (spacing.cardGap - 4.dp).coerceAtLeast(12.dp)
}
```

Pass `cardWidth` and `rowSpacing` into `HomeSection` as parameters. Remove `BoxWithConstraints` from `HomeSection`, replacing it with a plain `Box` (or removing the wrapper entirely if not needed for other reasons).

`NAV_RAIL_SLOT_WIDTH` is defined as a file-level constant in `HomeScreen.kt`. It duplicates the value from `CinefinAppScaffold` — this is intentional; layout constants are safe to duplicate rather than over-engineer a shared dependency.

### What does not change

- Card sizing logic — identical calculation, just moved up.
- `remember` keys — same dependencies, same invalidation behavior.
- Visual result — identical.

---

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt` | Replace 4 `animateDpAsState` with 1 `animateFloatAsState` + lerp |
| `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt` | Remove `BoxWithConstraints`; lift card width calc to `HomeLoadedContent` |

---

## Testing

- Run `./gradlew :app:testDebugUnitTest` — no ViewModel/logic changes, but confirm nothing breaks.
- Manual: D-pad left into nav rail, verify expand/collapse animation looks identical.
- Manual: Home screen loads sections with correct card widths at standard TV resolution.
- Existing UI tests: `AppNavigationSmokeUiTest`, `HomeScreenUiTest` should pass unchanged.
