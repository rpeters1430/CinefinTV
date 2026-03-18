# Library Standard Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the compact (text-overlaid-on-image) card style in the Movies, TV Shows, and Stuff library grids with the standard non-compact layout (text below image, left-aligned).

**Architecture:** Three targeted edits across two files. `TvMediaCard` already has a non-compact path that renders text below the image; it just needs its horizontal-card text alignment fixed from centered to start. The two library screens just need `compactMetadata = true` removed from their card call sites.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.tv.material3`

---

## Files

| File | Change |
|---|---|
| `app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt` | Remove conditional centering for horizontal cards (lines 338, 347, 357) |
| `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryScreen.kt` | Remove `compactMetadata = true` (line 204) |
| `app/src/main/java/com/rpeters/cinefintv/ui/screens/collections/CollectionsLibraryScreen.kt` | Remove `compactMetadata = true` (line 161) |

No new files. No ViewModel or data layer changes. No test files to create or modify — these are pure Composable layout changes with no logic.

---

### Task 1: Fix horizontal card text alignment in TvMediaCard

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt:338,347,357`

The non-compact path of `TvMediaCard` uses `isHorizontal = aspectRatio > 1f` to conditionally center text. Replace with unconditional start alignment. Do **not** remove `isHorizontal` — it still drives the `Surface` `containerColor` and `tonalElevation` on lines 322 and 324.

- [ ] **Step 1: Open the file and locate the Column alignment**

Open `app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt`. Find the `Column` inside the non-compact `else` branch (around line 332). It currently has:

```kotlin
horizontalAlignment = if (isHorizontal) Alignment.CenterHorizontally else Alignment.Start,
```

- [ ] **Step 2: Replace the Column alignment**

Change it to:

```kotlin
horizontalAlignment = Alignment.Start,
```

- [ ] **Step 3: Fix title text alignment**

In the same `Column`, find the title `Text` (around line 340):

```kotlin
textAlign = if (isHorizontal) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
```

Change it to:

```kotlin
textAlign = androidx.compose.ui.text.style.TextAlign.Start,
```

- [ ] **Step 4: Fix subtitle text alignment**

Just below, find the subtitle `Text` (inside `if (hasSubtitle)`, around line 355):

```kotlin
textAlign = if (isHorizontal) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
```

Change it to:

```kotlin
textAlign = androidx.compose.ui.text.style.TextAlign.Start,
```

- [ ] **Step 5: Verify the build compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt
git commit -m "fix: left-align text on horizontal TvMediaCard non-compact layout"
```

---

### Task 2: Remove compactMetadata from LibraryScreen

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryScreen.kt:204`

- [ ] **Step 1: Open the file and find the TvMediaCard call**

In the `items { ... }` block (around line 194), locate:

```kotlin
TvMediaCard(
    title = item.title,
    subtitle = item.subtitle,
    imageUrl = item.imageUrl,
    onClick = { onOpenItem(item.id) },
    watchStatus = item.watchStatus,
    playbackProgress = item.playbackProgress,
    unwatchedCount = item.unwatchedCount,
    aspectRatio = 16f / 9f,
    cardWidth = null,
    compactMetadata = true,
    modifier = ...
)
```

- [ ] **Step 2: Remove the compactMetadata line**

Delete the `compactMetadata = true,` line. The parameter defaults to `false`, so no replacement is needed.

- [ ] **Step 3: Verify the build compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryScreen.kt
git commit -m "fix: use standard card layout in LibraryScreen (text below image)"
```

---

### Task 3: Remove compactMetadata from CollectionsLibraryScreen

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/collections/CollectionsLibraryScreen.kt:161`

- [ ] **Step 1: Open the file and find the TvMediaCard call**

In the `items { ... }` block (around line 153), locate:

```kotlin
TvMediaCard(
    title = item.title,
    subtitle = item.subtitle,
    imageUrl = item.imageUrl,
    onClick = { onOpenItem(item.id) },
    watchStatus = item.watchStatus,
    playbackProgress = item.playbackProgress,
    aspectRatio = 16f / 9f,
    compactMetadata = true,
    modifier = ...
)
```

- [ ] **Step 2: Remove the compactMetadata line**

Delete the `compactMetadata = true,` line.

- [ ] **Step 3: Verify the build compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/collections/CollectionsLibraryScreen.kt
git commit -m "fix: use standard card layout in CollectionsLibraryScreen (text below image)"
```

---

### Task 4: Full build and install verification

- [ ] **Step 1: Run a clean debug build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` with APK at `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: Run unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` — no test regressions (the changed code is pure UI layout with no logic)

- [ ] **Step 3: Install and verify on device**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Navigate to Movies, TV Shows, and Stuff library screens. Verify:
- Cards show the image thumbnail with title and subtitle text **below** the image
- Text is **left-aligned**, not centered
- Focus ring, watched badge, progress bar, and unwatched count badge all still display correctly
- D-pad navigation works as expected through the grid
