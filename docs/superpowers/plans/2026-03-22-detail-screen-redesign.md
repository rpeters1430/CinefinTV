# Detail Screen Redesign — Cinematic Direction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign movie and TV show detail screens with a cinematic full-bleed aesthetic, fix the scroll anchor bug, and clean up hardcoded colors throughout.

**Architecture:** New `cinematic/` composable layer under `ui/screens/detail/` handles all visual presentation; existing ViewModels and screen wrappers unchanged. Design system tokens extended for `watchedGreen` and `gridContentPadding`. `DetailScreenComponents.kt` trimmed by unifying duplicate components into `MetaFactItem`.

**Tech Stack:** Jetpack Compose (TV Material3 + Material3), Coil3, Palette API (`androidx.palette:palette-ktx`), Hilt, Kotlin coroutines/Flow.

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `ui/theme/Color.kt` | Add `WatchedGreen` color constant |
| Modify | `ui/theme/ExpressiveColors.kt` | Add `watchedGreen` field to `CinefinExpressiveColors` + default |
| Modify | `ui/theme/Theme.kt` | Add `gridContentPadding` to `CinefinSpacing` |
| Modify | `ui/components/TvMediaCard.kt` | Replace 2 hardcoded colors with tokens |
| Modify | `ui/screens/detail/DetailScreenComponents.kt` | Add `MetaFactItem`/`MetaFactStyle`; fix `EpisodeListRow`; remove old hero components in Task 11 |
| Modify | `ui/screens/library/MovieLibraryScreen.kt` | Use `gridContentPadding` token |
| Modify | `ui/screens/library/TvShowLibraryScreen.kt` | Use `gridContentPadding` token |
| Modify | `ui/screens/library/StuffLibraryScreen.kt` | Use `gridContentPadding` token |
| **Create** | `ui/screens/detail/cinematic/ExpandableFactsSection.kt` | Progressive disclosure metadata block |
| **Create** | `ui/screens/detail/cinematic/CinematicHero.kt` | Full-bleed backdrop hero shared by both layouts |
| **Create** | `ui/screens/detail/cinematic/MovieDetailLayout.kt` | Movie continuous scroll layout |
| **Create** | `ui/screens/detail/cinematic/TvShowDetailLayout.kt` | TV show split-panel layout + `TvShowTab` enum |
| Modify | `ui/screens/detail/MovieDetailScreen.kt` | Wire `MovieDetailLayout`, fix scroll anchor |
| Modify | `ui/screens/detail/TvShowDetailScreen.kt` | Wire `TvShowDetailLayout`, fix scroll anchor |

---

## Task 1: Design System Tokens

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/theme/ExpressiveColors.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/theme/Theme.kt`

- [ ] **Step 1: Add `WatchedGreen` to Color.kt**

  At the end of the `// Semantic Tokens` section (after line 26):
  ```kotlin
  val WatchedGreen = Color(0xFF2E7D32)
  ```

- [ ] **Step 2: Add `watchedGreen` to `CinefinExpressiveColors`**

  In `ExpressiveColors.kt`, add to the `CinefinExpressiveColors` data class after `titleAccent` (line 23):
  ```kotlin
  val watchedGreen: Color,
  ```

  In the `LocalCinefinExpressiveColors` default instance (after `titleAccent = CinefinGold,` line 70):
  ```kotlin
  watchedGreen = WatchedGreen,
  ```

- [ ] **Step 3: Add `gridContentPadding` to `CinefinSpacing`**

  In `Theme.kt`, add to `CinefinSpacing` data class after `chipGap` (line 31):
  ```kotlin
  val gridContentPadding: Dp = 56.dp, // Grid edge padding — larger than gutter to give focus-scaled cards room at edges
  ```

- [ ] **Step 4: Verify compilation**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/theme/
  git commit -m "feat: add watchedGreen and gridContentPadding design tokens"
  ```

---

## Task 2: Token Migration — TvMediaCard + EpisodeListRow

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt`

`TvMediaCard.kt` currently imports `Color` from `androidx.compose.ui.graphics` but not `LocalCinefinExpressiveColors`. Check line ~1-50 for existing imports before adding.

- [ ] **Step 1: Add `LocalCinefinExpressiveColors` import to `TvMediaCard.kt`**

  Add after the existing Compose imports:
  ```kotlin
  import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
  ```

- [ ] **Step 2: Add `expressiveColors` local at the top of the relevant composable in `TvMediaCard.kt`**

  In the composable that uses the hardcoded overlay colors (the main `TvMediaCard` composable body), add near the top alongside other locals:
  ```kotlin
  val expressiveColors = LocalCinefinExpressiveColors.current
  ```

- [ ] **Step 3: Replace hardcoded colors in `TvMediaCard.kt`**

  At line ~165 (focus overlay background):
  ```kotlin
  // Before:
  .background(Color.White.copy(alpha = 0.12f))
  // After:
  .background(expressiveColors.focusGlow.copy(alpha = 0.12f))
  ```

  At line ~243 (watched checkmark background):
  ```kotlin
  // Before:
  color = Color(0xFF2E7D32).copy(alpha = 0.95f),
  // After:
  color = expressiveColors.watchedGreen.copy(alpha = 0.95f),
  ```

- [ ] **Step 4: Replace hardcoded color in `EpisodeListRow` in `DetailScreenComponents.kt`**

  At line ~838 (watched checkmark background in episode row):
  ```kotlin
  // Before:
  .background(Color(0xFF2E7D32).copy(alpha = 0.95f), RoundedCornerShape(999.dp)),
  // After:
  .background(expressiveColors.watchedGreen.copy(alpha = 0.95f), RoundedCornerShape(999.dp)),
  ```

  `EpisodeListRow` already has `val expressiveColors = LocalCinefinExpressiveColors.current` at line ~753 — use that existing local.

- [ ] **Step 5: Verify compilation**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt
  git commit -m "fix: replace hardcoded colors in TvMediaCard and EpisodeListRow with design tokens"
  ```

---

## Task 3: Library Screen Padding Token

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/MovieLibraryScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/TvShowLibraryScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/StuffLibraryScreen.kt`

Each library screen has `LocalCinefinSpacing` already imported (they use it for other values). If not, add `import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing`.

- [ ] **Step 1: Replace `56.dp` in `MovieLibraryScreen.kt` (line ~81)**

  ```kotlin
  // Before:
  contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
  // After:
  contentPadding = PaddingValues(horizontal = LocalCinefinSpacing.current.gridContentPadding, vertical = 32.dp),
  ```

- [ ] **Step 2: Same replacement in `TvShowLibraryScreen.kt` (line ~81)**

- [ ] **Step 3: Same replacement in `StuffLibraryScreen.kt` (line ~81)**

- [ ] **Step 4: Verify compilation**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/library/MovieLibraryScreen.kt
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/library/TvShowLibraryScreen.kt
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/library/StuffLibraryScreen.kt
  git commit -m "refactor: use gridContentPadding token in library screens"
  ```

---

## Task 4: MetaFactItem Unification

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt`

This task adds `MetaFactItem` and `MetaFactStyle`, updates the two call sites (`DetailMetaLine` + `DetailFactsColumn`), and marks `DetailLabeledMetaItemView` private→removed and `DetailFactCard` removed. Do **not** yet remove `DetailHeroBox`/`DetailGlassPanel`/`DetailTitleLogo` — those come in Task 11.

- [ ] **Step 1: Add `MetaFactStyle` enum and `MetaFactItem` composable to `DetailScreenComponents.kt`**

  Add after the `DetailLabeledMetaItem` data class (around line 80):
  ```kotlin
  enum class MetaFactStyle { Card, Inline }

  @Composable
  fun MetaFactItem(
      icon: ImageVector,
      label: String,
      value: String,
      style: MetaFactStyle = MetaFactStyle.Card,
      modifier: Modifier = Modifier,
  ) {
      when (style) {
          MetaFactStyle.Card -> {
              // Matches current DetailFactCard visual
              Column(
                  modifier = modifier
                      .border(
                          width = 1.dp,
                          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                          shape = RoundedCornerShape(18.dp),
                      )
                      .padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                  Row(
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                      Icon(
                          imageVector = icon,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(14.dp),
                      )
                      Text(
                          text = label.uppercase(),
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                          maxLines = 1,
                      )
                  }
                  Text(
                      text = value,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onBackground,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                  )
              }
          }
          MetaFactStyle.Inline -> {
              // Matches current DetailLabeledMetaItemView visual
              Row(
                  modifier = modifier
                      .background(
                          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
                          shape = RoundedCornerShape(20.dp),
                      )
                      .padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                  Icon(
                      imageVector = icon,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                  )
                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Text(
                          text = label,
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                          text = value,
                          style = MaterialTheme.typography.titleMedium,
                          color = MaterialTheme.colorScheme.onBackground,
                      )
                  }
              }
          }
      }
  }
  ```

  Required imports to add at the top of the file if not already present:
  ```kotlin
  import androidx.compose.foundation.border
  import androidx.compose.ui.text.style.TextOverflow
  ```

- [ ] **Step 2: Update `DetailMetaLine` to call `MetaFactItem(style = MetaFactStyle.Inline)`**

  Find the `FlowRow` block in `DetailMetaLine` (~line 364) that calls `DetailLabeledMetaItemView(item = item)`. Replace with:
  ```kotlin
  MetaFactItem(
      icon = item.icon,
      label = item.label,
      value = item.value,
      style = MetaFactStyle.Inline,
  )
  ```

- [ ] **Step 3: Update `DetailFactsColumn` to call `MetaFactItem(style = MetaFactStyle.Card)`**

  Find the call to the old card composable inside `DetailFactsColumn` (~line 376). Replace with:
  ```kotlin
  MetaFactItem(
      icon = item.icon,
      label = item.label,
      value = item.value,
      style = MetaFactStyle.Card,
  )
  ```

- [ ] **Step 4: Delete `DetailLabeledMetaItemView` and `DetailFactCard` private functions**

  Remove both private composable functions — they are fully replaced by `MetaFactItem`.

- [ ] **Step 5: Verify compilation and tests**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL

  Run: `./gradlew :app:testDebugUnitTest`
  Expected: All tests pass (no ViewModel logic changed)

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt
  git commit -m "refactor: unify DetailFactCard and DetailLabeledMetaItemView into MetaFactItem"
  ```

---

## Task 5: ExpandableFactsSection

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/ExpandableFactsSection.kt`

- [ ] **Step 1: Create the file**

  ```kotlin
  @file:OptIn(ExperimentalTvMaterial3Api::class)

  package com.rpeters.cinefintv.ui.screens.detail.cinematic

  import androidx.compose.animation.AnimatedVisibility
  import androidx.compose.animation.core.tween
  import androidx.compose.animation.expandVertically
  import androidx.compose.animation.fadeIn
  import androidx.compose.animation.fadeOut
  import androidx.compose.animation.shrinkVertically
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.ExperimentalLayoutApi
  import androidx.compose.foundation.layout.FlowRow
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.size
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.onFocusChanged
  import androidx.compose.ui.graphics.vector.ImageVector
  import androidx.compose.ui.unit.dp
  import androidx.tv.material3.ExperimentalTvMaterial3Api
  import androidx.tv.material3.Icon
  import androidx.tv.material3.MaterialTheme
  import androidx.tv.material3.Text
  import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
  import com.rpeters.cinefintv.ui.screens.detail.MetaFactItem
  import com.rpeters.cinefintv.ui.screens.detail.MetaFactStyle
  import com.rpeters.cinefintv.ui.theme.CinefinMotion
  import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
  import androidx.compose.material.icons.Icons
  import androidx.compose.material.icons.filled.KeyboardArrowDown
  import androidx.compose.material.icons.filled.KeyboardArrowUp
  import androidx.compose.ui.focus.focusable

  /**
   * A row showing a one-line summary of key facts collapsed by default.
   * D-pad select or click expands to a full FlowRow of MetaFactItem cards.
   */
  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  fun ExpandableFactsSection(
      items: List<DetailLabeledMetaItem>,
      summaryText: String,  // e.g. "Christopher Nolan · Warner Bros · English"
      modifier: Modifier = Modifier,
  ) {
      val spacing = LocalCinefinSpacing.current
      var expanded by remember { mutableStateOf(false) }
      var isFocused by remember { mutableStateOf(false) }

      Column(modifier = modifier.fillMaxWidth()) {
          // Collapsed summary row — always visible, acts as the toggle
          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .focusable()
                  .onFocusChanged { isFocused = it.isFocused }
                  .padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
              Text(
                  text = summaryText,
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (isFocused)
                      MaterialTheme.colorScheme.onSurface
                  else
                      MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                  modifier = Modifier.weight(1f),
              )
              Icon(
                  imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                  contentDescription = if (expanded) "Collapse" else "Expand",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier
                      .size(20.dp)
                      .padding(start = 8.dp),
              )
          }

          // Expandable content
          AnimatedVisibility(
              visible = expanded,
              enter = expandVertically(
                  animationSpec = tween(durationMillis = 250, easing = CinefinMotion.Emphasized)
              ) + fadeIn(tween(200)),
              exit = shrinkVertically(
                  animationSpec = tween(durationMillis = 200, easing = CinefinMotion.Emphasized)
              ) + fadeOut(tween(150)),
          ) {
              FlowRow(
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(top = spacing.elementGap),
                  horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                  verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
              ) {
                  items.forEach { item ->
                      MetaFactItem(
                          icon = item.icon,
                          label = item.label,
                          value = item.value,
                          style = MetaFactStyle.Card,
                      )
                  }
              }
          }
      }
  }
  ```

  The summary Row must have both `clickable` and key handling to toggle on D-pad center. Add these modifiers to the summary Row (after `focusable()`, before `padding`):
  ```kotlin
  .clickable { expanded = !expanded }
  .onKeyEvent { event ->
      if (event.key == Key.DirectionCenter && event.type == KeyEventType.KeyUp) {
          expanded = !expanded
          true
      } else false
  }
  ```

  Required additional imports:
  ```kotlin
  import androidx.compose.foundation.clickable
  import androidx.compose.ui.input.key.Key
  import androidx.compose.ui.input.key.KeyEventType
  import androidx.compose.ui.input.key.key
  import androidx.compose.ui.input.key.onKeyEvent
  import androidx.compose.ui.input.key.type
  ```

- [ ] **Step 2: Verify compilation**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/
  git commit -m "feat: add ExpandableFactsSection composable for progressive metadata disclosure"
  ```

---

## Task 6: CinematicHero

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/CinematicHero.kt`

This is the most complex new composable. Read `HomeScreen.kt` lines 247–295 for the Palette extraction pattern before implementing — this is the exact approach to replicate.

- [ ] **Step 1: Create `CinematicHero.kt`**

  ```kotlin
  @file:OptIn(ExperimentalTvMaterial3Api::class)

  package com.rpeters.cinefintv.ui.screens.detail.cinematic

  import android.graphics.Bitmap
  import androidx.compose.animation.AnimatedVisibility
  import androidx.compose.animation.core.tween
  import androidx.compose.animation.fadeIn
  import androidx.compose.foundation.background
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.Spacer
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.height
  import androidx.compose.foundation.layout.heightIn
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.size
  import androidx.compose.ui.graphics.RectangleShape
  import androidx.compose.foundation.layout.wrapContentWidth
  import androidx.compose.foundation.lazy.LazyRow
  import androidx.compose.foundation.lazy.items
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.DisposableEffect
  import androidx.compose.runtime.LaunchedEffect
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.FocusRequester
  import androidx.compose.ui.focus.focusRequester
  import androidx.compose.ui.graphics.Brush
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.graphics.toArgb
  import androidx.compose.ui.layout.ContentScale
  import androidx.compose.ui.platform.LocalConfiguration
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.unit.dp
  import androidx.palette.graphics.Palette
  import androidx.tv.material3.Button
  import androidx.tv.material3.ButtonDefaults
  import androidx.tv.material3.ExperimentalTvMaterial3Api
  import androidx.tv.material3.MaterialTheme
  import androidx.tv.material3.OutlinedButton
  import androidx.tv.material3.Text
  import coil3.compose.AsyncImage
  import coil3.compose.AsyncImagePainter
  import coil3.request.ImageRequest
  import coil3.request.allowHardware
  import com.rpeters.cinefintv.ui.LocalCinefinThemeController
  import com.rpeters.cinefintv.ui.components.CinefinChip
  import com.rpeters.cinefintv.ui.theme.BackgroundDark
  import com.rpeters.cinefintv.ui.theme.CinefinRed
  import com.rpeters.cinefintv.ui.theme.LocalCinefinExpressiveColors
  import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

  /**
   * Full-bleed cinematic hero section shared by MovieDetailLayout and TvShowDetailLayout.
   *
   * @param backdropUrl    URL of the backdrop/fanart image
   * @param logoUrl        URL of the title logo image (optional — falls back to [title])
   * @param title          Text title fallback when no logo available
   * @param eyebrow        Small line above logo, e.g. "TV SERIES · 4 SEASONS" or "2024 · 2h 18m"
   * @param ratingText     Rating string shown in gold, e.g. "★ 8.4"
   * @param genres         List of genre strings shown as chips
   * @param primaryActionLabel   Label for the red primary button, e.g. "▶ Resume S3E4" or "▶ Play"
   * @param onPrimaryAction      Callback for primary button
   * @param secondaryActions     List of (label, callback) pairs for secondary buttons (max 2)
   * @param primaryActionFocusRequester   FocusRequester to attach to the primary button
   */
  @Composable
  fun CinematicHero(
      backdropUrl: String?,
      logoUrl: String?,
      title: String,
      eyebrow: String,
      ratingText: String?,
      genres: List<String>,
      primaryActionLabel: String,
      onPrimaryAction: () -> Unit,
      secondaryActions: List<Pair<String, () -> Unit>> = emptyList(),
      primaryActionFocusRequester: FocusRequester = remember { FocusRequester() },
      modifier: Modifier = Modifier,
  ) {
      val screenHeight = LocalConfiguration.current.screenHeightDp.dp
      val expressiveColors = LocalCinefinExpressiveColors.current
      val spacing = LocalCinefinSpacing.current
      val themeController = LocalCinefinThemeController.current

      var logoLoaded by remember { mutableStateOf(logoUrl == null) } // true if no logo (show text immediately)

      // Clear seed color on leaving this detail screen
      DisposableEffect(Unit) {
          onDispose { themeController.updateSeedColor(null) }
      }

      Box(
          modifier = modifier
              .fillMaxWidth()
              .heightIn(min = screenHeight * 0.52f),
          contentAlignment = Alignment.BottomCenter,
      ) {
          // Backdrop image with Palette extraction
          if (backdropUrl != null) {
              var backdropBitmap by remember { mutableStateOf<Bitmap?>(null) }

              AsyncImage(
                  model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                      .data(backdropUrl)
                      .allowHardware(false) // Required for Palette API
                      .build(),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.matchParentSize(),
                  onSuccess = { state ->
                      val drawable = (state.result.image as? coil3.BitmapImage)?.bitmap
                      if (drawable != null) {
                          Palette.from(drawable).generate { palette ->
                              val dominant = palette?.getDominantColor(CinefinRed.toArgb())
                              if (dominant != null) {
                                  themeController.updateSeedColor(Color(dominant))
                              }
                          }
                      }
                  },
              )
          }

          // Gradient overlay: transparent at top → BackgroundDark at bottom
          Box(
              modifier = Modifier
                  .matchParentSize()
                  .background(
                      Brush.verticalGradient(
                          0.0f to Color.Transparent,
                          0.4f to BackgroundDark.copy(alpha = 0.3f),
                          1.0f to BackgroundDark,
                      )
                  )
          )

          // Content column: eyebrow + logo/title + meta + action bar
          Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
              // Eyebrow
              Text(
                  text = eyebrow.uppercase(),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                  modifier = Modifier.padding(bottom = spacing.elementGap),
              )

              // Logo or title text
              AnimatedVisibility(
                  visible = logoLoaded,
                  enter = fadeIn(tween(300)),
              ) {
                  if (logoUrl != null) {
                      AsyncImage(
                          model = logoUrl,
                          contentDescription = title,
                          modifier = Modifier
                              .heightIn(max = 120.dp)
                              .wrapContentWidth(),
                          onSuccess = { logoLoaded = true },
                          onError = { logoLoaded = true }, // show text fallback handled by if below
                      )
                  } else {
                      Text(
                          text = title,
                          style = MaterialTheme.typography.displaySmall,
                          fontWeight = FontWeight.ExtraBold,
                          color = MaterialTheme.colorScheme.onBackground,
                      )
                  }
              }

              Spacer(Modifier.height(spacing.elementGap))

              // Rating + genre chips
              Row(
                  horizontalArrangement = Arrangement.spacedBy(spacing.chipGap),
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(bottom = spacing.elementGap),
              ) {
                  ratingText?.let {
                      Text(
                          text = it,
                          style = MaterialTheme.typography.labelLarge,
                          color = expressiveColors.titleAccent,
                          fontWeight = FontWeight.Bold,
                      )
                  }
                  genres.take(3).forEach { genre ->
                      CinefinChip(label = genre)
                  }
              }

              // Action bar (frosted glass strip)
              Row(
                  modifier = Modifier
                      .fillMaxWidth()
                      .background(expressiveColors.chromeSurface.copy(alpha = 0.85f))
                      .border(
                          width = 1.dp,
                          color = MaterialTheme.colorScheme.border,
                          shape = RectangleShape,
                      )
                      .padding(horizontal = spacing.gutter, vertical = 16.dp),
                  horizontalArrangement = Arrangement.spacedBy(spacing.elementGap),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                  Button(
                      onClick = onPrimaryAction,
                      colors = ButtonDefaults.colors(
                          containerColor = CinefinRed,
                          contentColor = Color.White,
                          focusedContainerColor = CinefinRed,
                          focusedContentColor = Color.White,
                      ),
                      modifier = Modifier.focusRequester(primaryActionFocusRequester),
                  ) {
                      Text(primaryActionLabel, fontWeight = FontWeight.Bold)
                  }

                  secondaryActions.forEach { (label, action) ->
                      OutlinedButton(onClick = action) {
                          Text(label)
                      }
                  }
              }
          }
      }
  }
  ```

  > **Palette dependency:** Ensure `androidx.palette:palette-ktx` is in `app/build.gradle.kts` dependencies. Check first — it may already be present. If not, add: `implementation("androidx.palette:palette-ktx:1.0.0")`

- [ ] **Step 2: Verify compilation**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL. If palette dependency is missing, add it and re-run.

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/CinematicHero.kt
  git commit -m "feat: add CinematicHero composable with full-bleed backdrop and palette extraction"
  ```

---

## Task 7: MovieDetailLayout

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/MovieDetailLayout.kt`

- [ ] **Step 1: Create `MovieDetailLayout.kt`**

  ```kotlin
  @file:OptIn(ExperimentalTvMaterial3Api::class)

  package com.rpeters.cinefintv.ui.screens.detail.cinematic

  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.ExperimentalLayoutApi
  import androidx.compose.foundation.layout.PaddingValues
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.LazyListState
  import androidx.compose.foundation.lazy.LazyRow
  import androidx.compose.foundation.lazy.items
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.focus.FocusRequester
  import androidx.compose.ui.unit.dp
  import androidx.tv.material3.ExperimentalTvMaterial3Api
  import androidx.tv.material3.MaterialTheme
  import androidx.tv.material3.Text
  import com.rpeters.cinefintv.ui.components.CinefinChip
  import com.rpeters.cinefintv.ui.components.TvMediaCard
  import com.rpeters.cinefintv.ui.components.TvPersonCard
  import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
  import com.rpeters.cinefintv.ui.screens.detail.CastModel       // defined in MovieDetailViewModel/shared models
  import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
  import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing
  import com.rpeters.cinefintv.ui.components.CinefinShelfTitle  // in CinefinTvPrimitives.kt

  /**
   * Movie detail screen content: CinematicHero + continuous vertical scroll.
   * [listState] must be owned by MovieDetailScreen for the scroll-anchor fix.
   */
  @OptIn(ExperimentalLayoutApi::class)
  @Composable
  fun MovieDetailLayout(
      // Hero data
      backdropUrl: String?,
      logoUrl: String?,
      title: String,
      eyebrow: String,          // e.g. "2024 · 2h 18m"
      ratingText: String?,
      genres: List<String>,
      primaryActionLabel: String,
      onPrimaryAction: () -> Unit,
      secondaryActions: List<Pair<String, () -> Unit>>,
      primaryActionFocusRequester: FocusRequester,
      // Content
      description: String,
      factItems: List<DetailLabeledMetaItem>,
      factSummary: String,      // e.g. "Christopher Nolan · Warner Bros · English"
      castItems: List<CastModel>,
      similarItems: List<SimilarMovieModel>,
      onCastClick: (String) -> Unit,
      onSimilarClick: (String) -> Unit,
      // Scroll state (owned by caller for anchor fix)
      listState: LazyListState,
      modifier: Modifier = Modifier,
  ) {
      val spacing = LocalCinefinSpacing.current
      var descriptionExpanded by remember { mutableStateOf(false) }

      LazyColumn(
          state = listState,
          modifier = modifier.fillMaxWidth(),
          contentPadding = PaddingValues(bottom = 48.dp),
      ) {
          // Hero
          item {
              CinematicHero(
                  backdropUrl = backdropUrl,
                  logoUrl = logoUrl,
                  title = title,
                  eyebrow = eyebrow,
                  ratingText = ratingText,
                  genres = genres,
                  primaryActionLabel = primaryActionLabel,
                  onPrimaryAction = onPrimaryAction,
                  secondaryActions = secondaryActions,
                  primaryActionFocusRequester = primaryActionFocusRequester,
              )
          }

          // Description
          item {
              Column(
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = spacing.gutter, vertical = spacing.rowGap),
              ) {
                  Text(
                      text = description,
                      style = MaterialTheme.typography.bodyLarge,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                  )
                  // "Show more" toggle — only shown if text is long
                  if (description.length > 200) {
                      Text(
                          text = if (descriptionExpanded) "Show less" else "Show more",
                          style = MaterialTheme.typography.labelMedium,
                          color = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.padding(top = 4.dp),
                          // Wire D-pad select via Modifier.clickable { descriptionExpanded = !descriptionExpanded }
                      )
                  }
              }
          }

          // Expandable facts
          item {
              ExpandableFactsSection(
                  items = factItems,
                  summaryText = factSummary,
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = spacing.gutter),
              )
          }

          // Genre chips
          item {
              LazyRow(
                  contentPadding = PaddingValues(horizontal = spacing.gutter),
                  horizontalArrangement = Arrangement.spacedBy(spacing.chipGap),
                  modifier = Modifier.padding(vertical = spacing.elementGap),
              ) {
                  items(genres) { genre ->
                      CinefinChip(label = genre)
                  }
              }
          }

          // Cast shelf — only if cast is non-empty
          if (castItems.isNotEmpty()) {
              item {
                  CinefinShelfTitle(
                      title = "Cast",
                      modifier = Modifier.padding(
                          horizontal = spacing.gutter,
                          vertical = spacing.elementGap,
                      ),
                  )
              }
              item {
                  LazyRow(
                      contentPadding = PaddingValues(horizontal = spacing.gutter),
                      horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                  ) {
                      items(castItems) { person ->
                          TvPersonCard(
                              name = person.name,
                              role = person.role,
                              imageUrl = person.imageUrl,
                              onClick = { onCastClick(person.id) },
                          )
                      }
                  }
              }
          }

          // Similar movies shelf — only if non-empty
          if (similarItems.isNotEmpty()) {
              item {
                  CinefinShelfTitle(
                      title = "More Like This",
                      modifier = Modifier.padding(
                          horizontal = spacing.gutter,
                          vertical = spacing.elementGap,
                      ),
                  )
              }
              item {
                  LazyRow(
                      contentPadding = PaddingValues(horizontal = spacing.gutter),
                      horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                  ) {
                      items(similarItems) { item ->
                          TvMediaCard(
                              title = item.title,
                              imageUrl = item.imageUrl,
                              aspectRatio = 16f / 9f,
                              onClick = { onSimilarClick(item.id) },
                          )
                      }
                  }
              }
          }
      }
  }
  ```

  > **Note on imports:** `CastModel` and `SimilarMovieModel` package paths — look at the top-level imports in the existing `MovieDetailScreen.kt` to find the exact package. They are in the same `detail` package or a shared models package.

- [ ] **Step 2: Verify compilation**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/MovieDetailLayout.kt
  git commit -m "feat: add MovieDetailLayout with cinematic hero and continuous scroll"
  ```

---

## Task 8: TvShowDetailLayout

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/TvShowDetailLayout.kt`

- [ ] **Step 1: Create `TvShowDetailLayout.kt`**

  ```kotlin
  @file:OptIn(ExperimentalTvMaterial3Api::class)

  package com.rpeters.cinefintv.ui.screens.detail.cinematic

  import androidx.compose.animation.AnimatedContent
  import androidx.compose.animation.core.tween
  import androidx.compose.animation.fadeIn
  import androidx.compose.animation.fadeOut
  import androidx.compose.animation.togetherWith
  import androidx.compose.foundation.background
  import androidx.compose.foundation.clickable
  import androidx.compose.foundation.layout.Arrangement
  import androidx.compose.foundation.layout.Box
  import androidx.compose.foundation.layout.Column
  import androidx.compose.foundation.layout.PaddingValues
  import androidx.compose.foundation.layout.Row
  import androidx.compose.foundation.layout.defaultMinSize
  import androidx.compose.foundation.layout.fillMaxHeight
  import androidx.compose.foundation.layout.fillMaxSize
  import androidx.compose.foundation.layout.fillMaxWidth
  import androidx.compose.foundation.layout.padding
  import androidx.compose.foundation.layout.width
  import androidx.compose.foundation.lazy.LazyColumn
  import androidx.compose.foundation.lazy.LazyListState
  import androidx.compose.foundation.lazy.LazyRow
  import androidx.compose.foundation.lazy.grid.GridCells
  import androidx.compose.foundation.lazy.grid.LazyGridState
  import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
  import androidx.compose.foundation.lazy.grid.items
  import androidx.compose.foundation.lazy.items
  import androidx.compose.foundation.lazy.itemsIndexed
  import androidx.compose.material3.VerticalDivider
  import androidx.compose.runtime.Composable
  import androidx.compose.runtime.getValue
  import androidx.compose.runtime.mutableStateOf
  import androidx.compose.runtime.remember
  import androidx.compose.runtime.saveable.rememberSaveable
  import androidx.compose.runtime.setValue
  import androidx.compose.ui.Alignment
  import androidx.compose.ui.Modifier
  import androidx.compose.ui.draw.drawBehind
  import androidx.compose.ui.focus.FocusRequester
  import androidx.compose.ui.focus.focusProperties
  import androidx.compose.ui.focus.focusRequester
  import androidx.compose.ui.focus.onFocusChanged
  import androidx.compose.ui.geometry.Size
  import androidx.compose.ui.graphics.Color
  import androidx.compose.ui.text.font.FontWeight
  import androidx.compose.ui.unit.dp
  import androidx.tv.material3.ExperimentalTvMaterial3Api
  import androidx.tv.material3.MaterialTheme
  import androidx.tv.material3.Text
  import com.rpeters.cinefintv.ui.components.CinefinChip
  import com.rpeters.cinefintv.ui.components.TvMediaCard
  import com.rpeters.cinefintv.ui.components.TvPersonCard
  import com.rpeters.cinefintv.ui.screens.detail.CastModel
  import com.rpeters.cinefintv.ui.screens.detail.DetailLabeledMetaItem
  import com.rpeters.cinefintv.ui.screens.detail.EpisodeListRow  // in DetailScreenComponents.kt
  import com.rpeters.cinefintv.ui.screens.detail.EpisodeModel    // defined in SeasonViewModel.kt (same package)
  import com.rpeters.cinefintv.ui.screens.detail.SeasonModel     // defined in TvShowDetailViewModel or shared models
  import com.rpeters.cinefintv.ui.screens.detail.SimilarMovieModel
  import com.rpeters.cinefintv.ui.theme.CinefinRed
  import com.rpeters.cinefintv.ui.theme.LocalCinefinSpacing

  enum class TvShowTab { Episodes, Cast, Similar, Details }

  @Composable
  fun TvShowDetailLayout(
      // Hero data (same params as CinematicHero)
      backdropUrl: String?,
      logoUrl: String?,
      title: String,
      eyebrow: String,
      ratingText: String?,
      genres: List<String>,
      primaryActionLabel: String,
      onPrimaryAction: () -> Unit,
      secondaryActions: List<Pair<String, () -> Unit>>,
      primaryActionFocusRequester: FocusRequester,
      // Episodes
      seasons: List<SeasonModel>,
      selectedSeasonIndex: Int,
      onSeasonSelected: (Int) -> Unit,
      episodes: List<EpisodeModel>,
      resumeEpisodeIndex: Int,               // index to auto-scroll to; -1 if none
      onEpisodeClick: (EpisodeModel) -> Unit,
      // Cast & Similar
      castItems: List<CastModel>,
      similarItems: List<SimilarMovieModel>,
      onCastClick: (String) -> Unit,
      onSimilarClick: (String) -> Unit,
      // Details tab
      description: String,
      factItems: List<DetailLabeledMetaItem>,
      factSummary: String,
      // Tab state — hoisted to caller so LaunchedEffect(itemId) in screen can reset it
      selectedTab: TvShowTab,
      onTabSelected: (TvShowTab) -> Unit,
      // Scroll states (owned by caller for anchor fix)
      episodeListState: LazyListState,
      castGridState: LazyGridState,
      similarGridState: LazyGridState,
      modifier: Modifier = Modifier,
  ) {
      val spacing = LocalCinefinSpacing.current
      val panelFocusRequester = remember { FocusRequester() }

      Column(modifier = modifier.fillMaxSize()) {
          // Hero
          CinematicHero(
              backdropUrl = backdropUrl,
              logoUrl = logoUrl,
              title = title,
              eyebrow = eyebrow,
              ratingText = ratingText,
              genres = genres,
              primaryActionLabel = primaryActionLabel,
              onPrimaryAction = onPrimaryAction,
              secondaryActions = secondaryActions,
              primaryActionFocusRequester = primaryActionFocusRequester,
          )

          // Split panel below hero
          Row(modifier = Modifier.fillMaxSize()) {

              // Left rail
              LazyColumn(
                  modifier = Modifier
                      .width(200.dp)
                      .fillMaxHeight()
                      .padding(top = spacing.rowGap),
              ) {
                  items(TvShowTab.entries) { tab ->
                      var railItemFocused by remember { mutableStateOf(false) }
                      val isSelected = selectedTab == tab

                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .defaultMinSize(minHeight = 48.dp)
                              .drawBehind {
                                  if (isSelected) {
                                      drawRect(
                                          color = CinefinRed,
                                          size = Size(4.dp.toPx(), size.height),
                                      )
                                  }
                              }
                              .onFocusChanged { railItemFocused = it.isFocused }
                              .focusProperties { right = panelFocusRequester }
                              .clickable { onTabSelected(tab) }
                              .padding(horizontal = 16.dp, vertical = 12.dp),
                          verticalAlignment = Alignment.CenterVertically,
                      ) {
                          Text(
                              text = tab.name,
                              style = MaterialTheme.typography.labelLarge,
                              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                              color = if (isSelected || railItemFocused)
                                  MaterialTheme.colorScheme.onSurface
                              else
                                  MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                          )
                      }
                  }
              }

              // Vertical divider
              VerticalDivider(
                  modifier = Modifier.fillMaxHeight(),
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
              )

              // Right panel
              AnimatedContent(
                  targetState = selectedTab,
                  transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                  modifier = Modifier
                      .weight(1f)
                      .fillMaxHeight()
                      .focusRequester(panelFocusRequester),
              ) { tab ->
                  when (tab) {
                      TvShowTab.Episodes -> EpisodesPanel(
                          seasons = seasons,
                          selectedSeasonIndex = selectedSeasonIndex,
                          onSeasonSelected = onSeasonSelected,
                          episodes = episodes,
                          resumeEpisodeIndex = resumeEpisodeIndex,
                          onEpisodeClick = onEpisodeClick,
                          listState = episodeListState,
                      )
                      TvShowTab.Cast -> LazyVerticalGrid(
                          columns = GridCells.Fixed(4),
                          state = castGridState,
                          contentPadding = PaddingValues(spacing.cardGap),
                          horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                          verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
                      ) {
                          items(castItems) { person ->
                              TvPersonCard(
                                  name = person.name,
                                  role = person.role,
                                  imageUrl = person.imageUrl,
                                  onClick = { onCastClick(person.id) },
                              )
                          }
                      }
                      TvShowTab.Similar -> LazyVerticalGrid(
                          columns = GridCells.Fixed(4),
                          state = similarGridState,
                          contentPadding = PaddingValues(spacing.cardGap),
                          horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                          verticalArrangement = Arrangement.spacedBy(spacing.cardGap),
                      ) {
                          items(similarItems) { item ->
                              TvMediaCard(
                                  title = item.title,
                                  imageUrl = item.imageUrl,
                                  aspectRatio = 2f / 3f,
                                  onClick = { onSimilarClick(item.id) },
                              )
                          }
                      }
                      TvShowTab.Details -> DetailsPanel(
                          description = description,
                          factItems = factItems,
                          factSummary = factSummary,
                          genres = genres,
                      )
                  }
              }
          }
      }
  }

  @Composable
  private fun EpisodesPanel(
      seasons: List<Any>,
      selectedSeasonIndex: Int,
      onSeasonSelected: (Int) -> Unit,
      episodes: List<EpisodeModel>,
      resumeEpisodeIndex: Int,
      onEpisodeClick: (EpisodeModel) -> Unit,
      listState: LazyListState,
  ) {
      val spacing = LocalCinefinSpacing.current
      Column {
          // Season selector
          LazyRow(
              contentPadding = PaddingValues(horizontal = spacing.cardGap, vertical = spacing.elementGap),
              horizontalArrangement = Arrangement.spacedBy(spacing.chipGap),
          ) {
              itemsIndexed(seasons) { index, season ->
                  CinefinChip(
                      label = "Season ${index + 1}",
                      strong = index == selectedSeasonIndex,
                      modifier = Modifier.clickable { onSeasonSelected(index) },
                  )
              }
          }
          // Episode list
          LazyColumn(state = listState) {
              items(episodes) { episode ->
                  EpisodeListRow(
                      episode = episode,
                      onClick = { onEpisodeClick(episode) },
                  )
              }
          }
      }
  }

  @Composable
  private fun DetailsPanel(
      description: String,
      factItems: List<DetailLabeledMetaItem>,
      factSummary: String,
      genres: List<String>,
  ) {
      val spacing = LocalCinefinSpacing.current
      LazyColumn(
          contentPadding = PaddingValues(horizontal = spacing.cardGap, bottom = 48.dp),
          verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
      ) {
          item {
              ExpandableFactsSection(
                  items = factItems,
                  summaryText = factSummary,
              )
          }
          item {
              Text(
                  text = description,
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
          }
          item {
              LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.chipGap)) {
                  items(genres) { CinefinChip(label = it) }
              }
          }
      }
  }
  ```

- [ ] **Step 2: Verify compilation**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/cinematic/TvShowDetailLayout.kt
  git commit -m "feat: add TvShowDetailLayout with split panel and TvShowTab navigation"
  ```

---

## Task 9: Wire MovieDetailScreen

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt`

Before editing, read the full current `MovieDetailScreen.kt` to understand how it calls the existing detail components and what data it passes.

- [ ] **Step 1: Add `rememberLazyListState` and `FocusRequester` for scroll anchor**

  In `MovieDetailScreen`, find where the composable state is set up (after `val uiState` collection). Add:
  ```kotlin
  val listState = rememberLazyListState()
  val primaryActionFocus = remember { FocusRequester() }

  LaunchedEffect(itemId) {
      listState.scrollToItem(0)
      snapshotFlow { listState.isScrollInProgress }
          .first { !it }
      try { primaryActionFocus.requestFocus() } catch (_: Exception) {}
  }
  ```

  Required imports:
  ```kotlin
  import androidx.compose.foundation.lazy.rememberLazyListState
  import kotlinx.coroutines.flow.first
  import androidx.compose.runtime.snapshotFlow
  ```

- [ ] **Step 2: Replace the existing hero + content with `MovieDetailLayout`**

  The current screen likely has a structure like:
  ```kotlin
  LazyColumn {
      item { DetailHeroBox(...) { ... } }
      item { /* metadata */ }
      ...
  }
  ```

  Replace the entire `LazyColumn` (and any `Box` wrapping it) with:
  ```kotlin
  MovieDetailLayout(
      backdropUrl = movie.backdropUrl,
      logoUrl = movie.logoUrl,
      title = movie.title,
      eyebrow = listOfNotNull(movie.year?.toString(), movie.formattedDuration).joinToString(" · "),
      ratingText = movie.rating?.let { "★ $it" },
      genres = movie.genres,
      primaryActionLabel = if (movie.canResume()) "▶ Resume" else "▶ Play",
      onPrimaryAction = onPlayMovie,
      secondaryActions = listOf(
          "Watchlist" to { /* toggle watchlist — use existing handler */ },
      ),
      primaryActionFocusRequester = primaryActionFocus,
      description = movie.overview ?: "",
      factItems = buildList {
          // Copy the existing DetailLabeledMetaItem list-building logic from the screen verbatim
          if (movie.directors.isNotEmpty())
              add(DetailLabeledMetaItem(Icons.Default.Movie, "Directed by", movie.directors.take(2).joinToString(", ")))
          if (movie.studios.isNotEmpty())
              add(DetailLabeledMetaItem(Icons.Default.Apartment, "Studio", movie.studios.take(2).joinToString(", ")))
          // ... add remaining items matching the existing screen logic
      },
      factSummary = listOfNotNull(
          movie.directors.firstOrNull(),
          movie.studios.firstOrNull(),
      ).joinToString(" · "),
      castItems = cast,
      similarItems = similarMovies,
      onCastClick = { personId -> navController.navigate("detail/person/$personId") },
      onSimilarClick = { movieId -> navController.navigate("movie/detail/$movieId") },
      listState = listState,
  )
  ```

  > **Key:** `movie`, `cast`, and `similarMovies` are the parameters already passed into `MovieDetailContent`. Wire them directly — no new data fetching needed. The `factItems` list-building logic already exists in the screen (~line 186–215); copy it verbatim.

- [ ] **Step 3: Run ViewModel tests**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.MovieDetailViewModelTest"`
  Expected: All tests pass (no ViewModel logic changed)

- [ ] **Step 4: Build and verify**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL. Install on device/emulator and verify the movie detail screen loads, the hero shows, scroll starts at top, and the primary button receives focus.

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt
  git commit -m "feat: wire MovieDetailLayout into MovieDetailScreen with scroll anchor fix"
  ```

---

## Task 10: Wire TvShowDetailScreen

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt`

Before editing, read the full current `TvShowDetailScreen.kt` to understand existing state and data flow.

- [ ] **Step 1: Add scroll states and anchor fix**

  ```kotlin
  val episodeListState = rememberLazyListState()
  val castGridState = rememberLazyGridState()
  val similarGridState = rememberLazyGridState()
  val primaryActionFocus = remember { FocusRequester() }
  var selectedTab by rememberSaveable { mutableStateOf(TvShowTab.Episodes) }

  LaunchedEffect(itemId) {
      selectedTab = TvShowTab.Episodes   // reset tab on new item
      episodeListState.scrollToItem(0)
      castGridState.scrollToItem(0)
      similarGridState.scrollToItem(0)
      try { primaryActionFocus.requestFocus() } catch (_: Exception) {}
  }
  ```

  Required additional imports:
  ```kotlin
  import androidx.compose.foundation.lazy.grid.rememberLazyGridState
  import androidx.compose.runtime.saveable.rememberSaveable
  import com.rpeters.cinefintv.ui.screens.detail.cinematic.TvShowTab
  ```

  Required imports:
  ```kotlin
  import androidx.compose.foundation.lazy.rememberLazyListState
  import androidx.compose.foundation.lazy.grid.rememberLazyGridState
  ```

- [ ] **Step 2: Replace existing hero + content with `TvShowDetailLayout`**

  Wire all parameters from the screen's `UiState.Content`. Read the current screen to confirm exact field names. Key mappings:
  - `eyebrow`: `"TV SERIES · ${content.seasonCount} SEASONS"`
  - `primaryActionLabel`: `"▶ Resume S${content.resumeSeason}E${content.resumeEpisode}"` or `"▶ Play"`
  - Pass `selectedTab = selectedTab` and `onTabSelected = { selectedTab = it }`
  - Pass `episodeListState`, `castGridState`, `similarGridState`
  - Pass `primaryActionFocusRequester = primaryActionFocus`

- [ ] **Step 3: Run ViewModel tests**

  Run: `./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.detail.TvShowDetailViewModelTest"`
  Expected: All tests pass

- [ ] **Step 4: Build and verify**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL. Verify: TV show detail opens at top, hero loads, left rail shows Episodes/Cast/Similar/Details, D-pad right moves focus into episode list, NEXT badge appears on resume episode.

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt
  git commit -m "feat: wire TvShowDetailLayout into TvShowDetailScreen with split panel and scroll anchor fix"
  ```

---

## Task 11: Remove Replaced Components

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt`

Only do this after Tasks 9 and 10 are complete and the build passes — these components must no longer be referenced.

- [ ] **Step 1: Verify no remaining usages**

  Run: `grep -r "DetailHeroBox\|DetailGlassPanel\|DetailTitleLogo" app/src/main/java/`
  Expected: No results (or only the definitions themselves)

- [ ] **Step 2: Delete the three composable functions from `DetailScreenComponents.kt`**

  Remove:
  - `fun DetailHeroBox(...)` and its entire body
  - `fun DetailGlassPanel(...)` and its entire body
  - `fun DetailTitleLogo(...)` and its entire body

- [ ] **Step 3: Run full test suite**

  Run: `./gradlew :app:testDebugUnitTest`
  Expected: All tests pass

- [ ] **Step 4: Final build**

  Run: `./gradlew :app:assembleDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Final commit**

  ```bash
  git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreenComponents.kt
  git commit -m "refactor: remove DetailHeroBox, DetailGlassPanel, DetailTitleLogo (replaced by CinematicHero)"
  ```

---

## Verification Checklist (on device)

After all tasks complete, verify the following on a real Android TV device or emulator:

- [ ] Movie detail: hero fills >50% of screen, logo/title centered, backdrop fills behind
- [ ] Movie detail: scroll starts at the very top every time you navigate to a movie
- [ ] Movie detail: Play/Resume button receives focus on entry
- [ ] Movie detail: "Show more" expands description, fact section expands on select
- [ ] Movie detail: cast and similar rows are D-pad navigable
- [ ] TV show detail: hero same as movie
- [ ] TV show detail: left rail shows 4 tabs, D-pad right enters episode list
- [ ] TV show detail: episode list shows NEXT badge on resume episode
- [ ] TV show detail: switching seasons reloads episode list
- [ ] TV show detail: Cast tab shows person cards in a 4-column grid
- [ ] TV show detail: Details tab shows expandable facts + description
- [ ] Library screens: grid cards don't clip on focus at the edges
- [ ] No green checkmark color mismatch between TvMediaCard and EpisodeListRow
