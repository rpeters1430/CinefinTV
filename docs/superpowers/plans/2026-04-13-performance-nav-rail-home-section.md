# Performance: Nav Rail Animation + HomeSection Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce Compose recomposition cost during nav rail focus transitions from 4 animators to 1, and remove the `BoxWithConstraints` subcomposition overhead from each home section row.

**Architecture:** Two independent surgical edits — `CinefinTvApp.kt` (nav rail) and `HomeScreen.kt` (home section). No new files, no logic changes, no API surface changes. Both changes preserve identical visual output.

**Tech Stack:** Jetpack Compose, `animateFloatAsState`, `androidx.compose.ui.unit.lerp`, `LocalConfiguration`

---

## File Map

| File | Change |
|---|---|
| `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt` | Replace 4 `animateDpAsState` with 1 `animateFloatAsState` + lerp |
| `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt` | Remove `BoxWithConstraints`; lift card width computation to `HomeLoadedContent`; add params to `HomeSection` |

---

## Task 1: Nav Rail — Consolidate 4 animators into 1

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt:4-5,296-315`

These are pure animation/visual changes with no logic. The existing unit test suite has no tests for `CinefinAppScaffold` animation, so we verify existing tests pass before and after.

- [ ] **Step 1: Run existing unit tests to establish baseline**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: All tests pass. If any fail before our change, note them — they are pre-existing failures unrelated to this task.

- [ ] **Step 2: Update imports in `CinefinTvApp.kt`**

In `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt`, replace line 4:

```kotlin
import androidx.compose.animation.core.animateDpAsState
```

with:

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.lerp
```

`tween` at line 5 stays — it's still used.

- [ ] **Step 3: Replace the 4 `animateDpAsState` blocks with a single `animateFloatAsState`**

In `CinefinTvApp.kt`, find this block (lines 296–315):

```kotlin
val logoSize by animateDpAsState(
    targetValue = if (navHasFocus) 46.dp else 50.dp,
    animationSpec = tween(durationMillis = 280),
    label = "logoSize",
)
val iconSize by animateDpAsState(
    targetValue = if (navHasFocus) 22.dp else 28.dp,
    animationSpec = tween(durationMillis = 280),
    label = "iconSize",
)
val buttonPaddingVertical by animateDpAsState(
    targetValue = if (navHasFocus) 10.dp else 14.dp,
    animationSpec = tween(durationMillis = 280),
    label = "buttonPaddingVertical",
)
val buttonPaddingHorizontal by animateDpAsState(
    targetValue = 12.dp,
    animationSpec = tween(durationMillis = 280),
    label = "buttonPaddingHorizontal",
)
```

Replace it with:

```kotlin
val railProgress by animateFloatAsState(
    targetValue = if (navHasFocus) 1f else 0f,
    animationSpec = tween(durationMillis = 280),
    label = "railProgress",
)
val logoSize = lerp(50.dp, 46.dp, railProgress)
val iconSize = lerp(28.dp, 22.dp, railProgress)
val buttonPaddingVertical = lerp(14.dp, 10.dp, railProgress)
val buttonPaddingHorizontal = 12.dp
```

Note: `buttonPaddingHorizontal` was animating `12.dp → 12.dp` (no-op). It is now a plain `val`. Nothing downstream changes — it's still named `buttonPaddingHorizontal` and used identically in the `Row` padding below.

- [ ] **Step 4: Run unit tests again to confirm nothing broke**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: Same result as Step 1.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt
git commit -m "perf: consolidate nav rail animators from 4 to 1 (animateFloatAsState + lerp)"
```

---

## Task 2: HomeSection — Remove `BoxWithConstraints`

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: Add the `NAV_RAIL_SLOT_WIDTH` constant and `LocalConfiguration` import**

At the top of `HomeScreen.kt`, add one import after the existing `import androidx.compose.ui.platform.LocalDensity` line:

```kotlin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
```

Just below the `private const val HOME_RESUME_REFRESH_THRESHOLD_MS` line (line 90), add:

```kotlin
private val NAV_RAIL_SLOT_WIDTH = 208.dp  // 196dp rail + 12dp start padding (matches CinefinAppScaffold)
```

- [ ] **Step 2: Add `cardWidth` and `rowSpacing` computation to `HomeLoadedContent`**

In `HomeLoadedContent` (starts at line 245), find the `val spacing = LocalCinefinSpacing.current` line. After it and before `when (val state = uiState)`, add:

```kotlin
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

Wait — `HomeLoadedContent` does not itself call `HomeSection`. That happens inside `HomeLoadedContent` → `HomeLoadedContent`'s `LazyColumn` → `itemsIndexed` → `HomeSection`. The `spacing` val is already available at `HomeLoadedContent` level (line 164 in `HomeScreenContent`, and separately inside `HomeLoadedContent`).

Actually, `HomeLoadedContent` signature is at line 245. Check: `spacing` is read inside `HomeSection` via `LocalCinefinSpacing.current`. We want to lift the width calc to `HomeLoadedContent` so we add it there.

In `HomeLoadedContent` (line ~259), after:
```kotlin
val chromeFocusController = LocalAppChromeFocusController.current
```

add:
```kotlin
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

`spacing` is already a parameter of `HomeLoadedContent` — do not re-declare it. `HomeSection` also reads `val spacing = LocalCinefinSpacing.current` internally for its title padding — that stays as-is. We are only lifting the `cardWidth`/`rowSpacing` computation.

- [ ] **Step 3: Update `HomeSection` signature to accept `cardWidth` and `rowSpacing`**

Find the `HomeSection` function signature (line 720):

```kotlin
private fun HomeSection(
    sectionIndex: Int,
    title: String,
    items: List<HomeCardModel>,
    onOpenItem: (HomeCardModel) -> Unit,
    restoredFocusedItemId: String?,
    onItemFocused: (String) -> Unit,
    onEpisodeMenuRequested: (HomeCardModel) -> Unit,
    firstItemFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    onNavigateUp: (() -> Unit)?,
    onNavigateDown: (() -> Unit)?,
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
    modifier: Modifier = Modifier,
)
```

Replace with:

```kotlin
private fun HomeSection(
    sectionIndex: Int,
    title: String,
    items: List<HomeCardModel>,
    cardWidth: Dp,
    rowSpacing: Dp,
    onOpenItem: (HomeCardModel) -> Unit,
    restoredFocusedItemId: String?,
    onItemFocused: (String) -> Unit,
    onEpisodeMenuRequested: (HomeCardModel) -> Unit,
    firstItemFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    onNavigateUp: (() -> Unit)?,
    onNavigateDown: (() -> Unit)?,
    destinationFocus: com.rpeters.cinefintv.ui.TopLevelDestinationFocus,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 4: Remove `BoxWithConstraints` from `HomeSection` body and use the new params**

In the `HomeSection` body, find (lines 754–828):

```kotlin
BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val cardWidth = remember(maxWidth, spacing.gutter, spacing.cardGap) {
        ((maxWidth - (spacing.gutter * 2) - (spacing.cardGap * 2)) / 3f)
            .coerceIn(220.dp, 400.dp)
    }
    val rowSpacing = remember(spacing.cardGap) {
        (spacing.cardGap - 4.dp).coerceAtLeast(12.dp)
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        contentPadding = PaddingValues(horizontal = spacing.gutter),
    ) {
        itemsIndexed(
            // ... all the card items ...
        )
    }
}
```

Replace the entire `BoxWithConstraints` wrapper with just the `LazyRow` directly (the `cardWidth` and `rowSpacing` are now parameters, no local `remember` needed):

```kotlin
LazyRow(
    horizontalArrangement = Arrangement.spacedBy(rowSpacing),
    contentPadding = PaddingValues(horizontal = spacing.gutter),
) {
    itemsIndexed(
        items = visibleItems,
        key = { _, item -> item.id },
        contentType = { _, item -> item.itemType ?: "media" },
    ) { index, item ->
        val focusModifier = if (upFocusRequester != null) {
            destinationFocus.drawerEscapeModifier(
                isLeftEdge = index == 0,
                up = upFocusRequester,
            )
        } else {
            destinationFocus.drawerEscapeModifier(isLeftEdge = index == 0)
        }

        TvMediaCard(
            title = item.title,
            subtitle = item.subtitle ?: item.year?.toString(),
            imageUrl = item.imageUrl,
            onClick = { onOpenItem(item) },
            onMenuAction = if (item.itemType == "Episode") {
                { onEpisodeMenuRequested(item) }
            } else {
                null
            },
            watchStatus = item.watchStatus,
            unwatchedCount = item.unwatchedCount,
            playbackProgress = item.playbackProgress,
            onFocus = { onItemFocused(item.id) },
            aspectRatio = 16f / 9f,
            cardWidth = cardWidth,
            modifier = Modifier
                .testTag(HomeTestTags.sectionItem(sectionIndex, index))
                .then(
                    if (index == 0 || index == restoredFocusIndex) {
                        Modifier.blockBringIntoView()
                    } else {
                        Modifier
                    }
                )
                .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                .then(focusModifier)
                .onPreviewKeyEvent { keyEvent ->
                    val nativeEvent = keyEvent.nativeKeyEvent
                    when {
                        onNavigateDown != null &&
                            nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onNavigateDown()
                            true
                        }
                        onNavigateUp != null &&
                            nativeEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                            nativeEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            onNavigateUp()
                            true
                        }
                        else -> false
                    }
                }
        )
    }
}
```

Also remove the `BoxWithConstraints` import from the top of `HomeScreen.kt`:

```kotlin
import androidx.compose.foundation.layout.BoxWithConstraints
```

- [ ] **Step 5: Update the `HomeSection` call sites in `HomeLoadedContent`**

In `HomeLoadedContent`, find the single `HomeSection(` call inside `itemsIndexed` (around line 409). Add `cardWidth = cardWidth` and `rowSpacing = rowSpacing` after `items = section.items`:

```kotlin
HomeSection(
    sectionIndex = index,
    title = section.title,
    items = section.items,
    cardWidth = cardWidth,
    rowSpacing = rowSpacing,
    onOpenItem = onOpenItem,
    restoredFocusedItemId = if (lastFocusedSectionId == section.id) {
        lastFocusedItemId
    } else {
        null
    },
    onItemFocused = { itemId ->
        lastFocusedSectionId = section.id
        lastFocusedItemId = itemId
        ensureSectionVisible(index)
    },
    onEpisodeMenuRequested = { selectedEpisodeMenuItem = it },
    firstItemFocusRequester = sectionFocusRequesters[index],
    upFocusRequester = upFocusRequester,
    onNavigateUp = when {
        index == 0 && state.featuredItems.isNotEmpty() -> {
            requestFocusAtListIndex(
                requester = featuredPrimaryActionRequester,
                listIndex = 0,
            )
        }
        index > 0 -> {
            requestFocusAtListIndex(
                requester = sectionFocusRequesters[index - 1],
                listIndex = if (state.featuredItems.isNotEmpty()) index else index - 1,
            )
        }
        else -> null
    },
    onNavigateDown = sectionFocusRequesters.getOrNull(index + 1)?.let { nextRequester ->
        requestFocusAtListIndex(
            requester = nextRequester,
            listIndex = if (state.featuredItems.isNotEmpty()) index + 2 else index + 1,
        )
    },
    destinationFocus = destinationFocus,
)
```

- [ ] **Step 6: Run unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: All tests pass (same result as Task 1 Step 4).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt
git commit -m "perf: remove BoxWithConstraints from HomeSection, lift cardWidth calc to HomeLoadedContent"
```

---

## Verification

After both tasks are committed, do a quick manual check on a device or emulator:

1. Launch the app and navigate to Home — confirm sections render with correct card widths.
2. D-pad left to the nav rail — confirm it expands and collapses with the same visual result as before.
3. D-pad right back to content — confirm focus returns correctly.
