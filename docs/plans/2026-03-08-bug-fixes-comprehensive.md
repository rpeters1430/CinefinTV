# Bug Fixes: Comprehensive UI/UX Overhaul Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix all reported UI/UX bugs across auth, home, detail, library, stuff, and player screens.

**Architecture:** All fixes are in existing Compose UI files and ViewModels. No new files required except where noted. Tests are JVM unit tests only using MockK + Turbine (no instrumented tests).

**Tech Stack:** Kotlin, Jetpack Compose, TV Material3 (`@OptIn(ExperimentalTvMaterial3Api::class)` required on all TV composables), Hilt, Jellyfin SDK, Coil3

---

## Task 1: Fix Auth Screen — Heading Text Invisible (Black/Dark)

**Root cause:** `Text` with `headlineLarge`/`displayLarge` style in auth screens has no explicit color set. TV Material3 Surface context might render them with dark content color in certain layout contexts.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/ServerConnectionScreen.kt:42-45`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/LoginScreen.kt:72-75`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/LoginScreen.kt:151-154` (QuickConnectPanel)

**Step 1: Add explicit color to all heading Text composables in auth screens**

In `ServerConnectionScreen.kt`, find the heading Text and add `color = Color.White`:
```kotlin
Text(
    text = "Connect to Jellyfin",
    style = MaterialTheme.typography.headlineLarge,
    color = Color.White,  // ADD THIS
)
```

In `LoginScreen.kt`, do the same for "Sign In" and "Quick Connect" headings:
```kotlin
Text(
    text = "Sign In",
    style = MaterialTheme.typography.headlineLarge,
    color = Color.White,  // ADD THIS
)
```
```kotlin
Text(
    text = "Quick Connect",
    style = MaterialTheme.typography.headlineLarge,
    color = Color.White,  // ADD THIS
)
```

**Step 2: Build and verify**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/ServerConnectionScreen.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/LoginScreen.kt
git commit -m "fix: make auth screen headings visible with explicit white color"
```

---

## Task 2: Fix Auth Screen — Placeholder Cursor Position

**Root cause:** `AuthTextField` `decorationBox` renders placeholder Text AND `innerTextField()` in a `Row`, so the cursor appears after the placeholder text instead of at position 0.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/ServerConnectionScreen.kt:113-128`

**Step 1: Replace Row with Box in decorationBox**

The current code in `AuthTextField`:
```kotlin
decorationBox = { innerTextField ->
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        if (value.isBlank()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        innerTextField()
    }
},
```

Change to:
```kotlin
decorationBox = { innerTextField ->
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp),
    ) {
        if (value.isBlank()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        innerTextField()
    }
},
```

You will also need to add `import androidx.compose.foundation.layout.Box` (check if already imported; it may already be there since the file uses Row which is also in `foundation.layout`).

**Step 2: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/ServerConnectionScreen.kt
git commit -m "fix: auth text field placeholder overlaps cursor - use Box instead of Row"
```

---

## Task 3: Fix Home Screen — Remove Invisible Focus Step Before Carousel

**Root cause:** The first item in the home screen `LazyColumn` is a 1dp invisible `Box` that is `focusable()`. When D-pad focus moves from the TabRow down into the list, it lands on this hidden element before reaching the carousel, creating a perceived "dead step".

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt:157-173`

**Step 1: Remove the invisible focusable item**

Delete the entire `item { Box(height=1.dp ... focusable()) }` block (lines 158–173):
```kotlin
// DELETE this entire item block:
item {
    Box(
        modifier = Modifier
            .height(1.dp)
            .fillMaxWidth()
            .onFocusChanged {
                if (it.isFocused) {
                    backgroundImageUrl = null
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            }
            .focusable()
    )
}
```

**Step 2: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt
git commit -m "fix: remove invisible focus trap that caused dead step before carousel"
```

---

## Task 4: Fix Home Screen — Tab Text/Background Colors

**Root cause:** TV Material3 `Tab` has default focus/selected colors that turn the container white and the content dark. Need to override tab colors so text is always visible.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt:154-187`

**Step 1: Add tab color customization**

In the `Tab(...)` composable inside `navTabItems.forEachIndexed`, add explicit colors. TV Material3 `Tab` has a `colors` parameter that takes `TabDefaults.underlinedIndicatorTabColors(...)` or similar. Use `TabDefaults.pillIndicatorTabColors(...)` which is the default style, but override active/selected content colors.

Replace the existing `Tab(...)` block with:
```kotlin
Tab(
    selected = index == selectedTabIndex,
    onFocus = {},
    onClick = {
        if (currentRoute != item.route) {
            navController.navigate(item.route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    },
    colors = TabDefaults.pillIndicatorTabColors(
        activeContentColor = Color.White,
        selectedContentColor = Color.White,
        focusedSelectedContentColor = Color.White,
        focusedUnselectedContentColor = Color.White,
        inactiveContentColor = Color.White.copy(alpha = 0.6f),
    ),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
```

Note: `TabDefaults.pillIndicatorTabColors` may not have exactly these parameter names. Check the TV Material3 API. The key parameters are:
- `activeContentColor` (currently selected + not focused)
- `selectedContentColor`
- `focusedSelectedContentColor`
- `focusedUnselectedContentColor`

If exact API names differ, look at available `TabDefaults` methods in the IDE and map the colors. All content colors should be `Color.White` or `Color.White.copy(alpha = 0.6f)` for inactive.

**Step 2: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt
git commit -m "fix: tab text always white - override TV Material3 default tab colors"
```

---

## Task 5: Fix Carousel — Add Runtime/Year/Rating Metadata

**Root cause:** `HomeCardModel` and `FeaturedCarousel` don't carry or display year, runtime, or rating.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt:281-316`

**Step 1: Add fields to HomeCardModel**

In `HomeViewModel.kt`, update `HomeCardModel`:
```kotlin
data class HomeCardModel(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val backdropUrl: String? = null,
    val description: String? = null,
    val year: Int? = null,          // ADD
    val runtime: String? = null,    // ADD  e.g. "2h 14m"
    val rating: String? = null,     // ADD  e.g. "8.2/10"
    val officialRating: String? = null, // ADD  e.g. "PG-13"
)
```

**Step 2: Populate new fields in toCardModel()**

In `HomeViewModel.toCardModel()`, add:
```kotlin
return HomeCardModel(
    id = id,
    title = item.getDisplayTitle(),
    subtitle = subtitle,
    imageUrl = repositories.stream.getLandscapeImageUrl(item),
    backdropUrl = repositories.stream.getBackdropUrl(item),
    description = item.overview?.take(140),
    year = item.getYear(),                           // ADD
    runtime = item.getFormattedDuration(),           // ADD
    rating = item.communityRating                    // ADD
        ?.let { String.format(java.util.Locale.US, "%.1f", it as Number) },
    officialRating = item.officialRating?.takeIf { it.isNotBlank() },  // ADD
)
```

**Step 3: Display metadata in FeaturedCarousel**

In `HomeScreen.kt`, inside `FeaturedCarousel`, after the title Text and before the description Text, add a metadata Row:
```kotlin
Text(
    text = item.title,
    style = MaterialTheme.typography.headlineLarge,
    color = MaterialTheme.colorScheme.onBackground,
)

// ADD metadata row:
val carouselMeta = listOfNotNull(
    item.year?.toString(),
    item.runtime,
    item.officialRating,
    item.rating?.let { "★ $it" },
).joinToString("  ·  ")
if (carouselMeta.isNotBlank()) {
    Text(
        text = carouselMeta,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

**Step 4: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 5: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeViewModel.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt
git commit -m "feat: show year/runtime/rating metadata in home carousel"
```

---

## Task 6: Fix "Continue Watching" — Improve Subtitle Text

**Root cause:** `"Resume 17%"` is cryptic. Change to `"Continue watching"` or show remaining time.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeViewModel.kt:119-124`

**Step 1: Change the resume subtitle**

In `toCardModel()`, change the resume branch:
```kotlin
// BEFORE:
item.canResume() -> "Resume ${item.getWatchedPercentage().toInt()}%"

// AFTER:
item.canResume() -> {
    val pct = item.getWatchedPercentage().toInt()
    val remaining = item.runTimeTicks?.let { ticks ->
        val remainingTicks = ticks - (ticks * pct / 100)
        val totalSeconds = remainingTicks / 10_000_000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        if (hours > 0) "${hours}h ${minutes}m left" else "${minutes}m left"
    }
    remaining ?: "$pct% watched"
}
```

**Step 2: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeViewModel.kt
git commit -m "fix: replace cryptic 'Resume 17%' with '45m left' remaining time display"
```

---

## Task 7: Improve Card Motion and Remove Red Border Hover Effect

**Root cause:** Two issues: (1) The focus animation (`animateFloatAsState` on a Column wrapper) is not as smooth as it could be because focus detection is on the parent Column, not the focusable Card child. (2) Red border on focus looks like a standard UI selection, not a "hover lift" effect.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt`

**Step 1: Redesign card focus visual**

Replace the current `TvMediaCard` card section with this improved version:
- Move `isFocused` tracking to the `Card`'s `onFocusChanged` (not the Column)
- Replace the red border with a white glow/elevation effect using `graphicsLayer`
- Keep the scale animation but apply it via `graphicsLayer` for better compositing

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMediaCard(
    title: String,
    subtitle: String? = null,
    imageUrl: String? = null,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    modifier: Modifier = Modifier,
    watchStatus: WatchStatus = WatchStatus.NONE,  // new optional param
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "CardScale"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
        animationSpec = tween(durationMillis = 200),
        label = "TitleColor"
    )
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "CardElevation"
    )

    Column(
        modifier = modifier.width(260.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = elevation.toPx()
                }
        ) {
            Card(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged {
                        isFocused = it.isFocused || it.hasFocus
                        if (it.isFocused || it.hasFocus) onFocus()
                    },
                scale = CardDefaults.scale(focusedScale = 1.0f),
                border = CardDefaults.border(
                    focusedBorder = androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    )
                ),
                shape = CardDefaults.shape(MaterialTheme.shapes.extraSmall)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = title.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }

            // Watch status overlay
            if (watchStatus != WatchStatus.NONE) {
                WatchStatusOverlay(watchStatus, modifier = Modifier.align(Alignment.TopEnd))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

enum class WatchStatus { NONE, WATCHED, IN_PROGRESS }

@Composable
private fun WatchStatusOverlay(status: WatchStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(6.dp)
    ) {
        when (status) {
            WatchStatus.WATCHED -> {
                Surface(
                    shape = RoundedCornerShape(50),
                    colors = SurfaceDefaults.colors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.9f)),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
            WatchStatus.IN_PROGRESS -> {
                Surface(
                    shape = RoundedCornerShape(50),
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("▶", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Color.White)
                    }
                }
            }
            WatchStatus.NONE -> {}
        }
    }
}
```

Required new imports for TvMediaCard.kt:
```kotlin
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.graphicsLayer // note: this is an extension on Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
```

Note: `graphicsLayer` accepts a lambda. Use `Modifier.graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = elevation.toPx() }`.

**Step 2: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt
git commit -m "feat: smooth spring card animations, white border hover, lift elevation effect"
```

---

## Task 8: Fix Year Range Showing Full ISO DateTime

**Root cause:** `getYearRange()` in `Extensions.kt` uses `endDate` directly in string interpolation. `endDate` on `BaseItemDto` is an `OffsetDateTime` object (from the Jellyfin SDK), so its `toString()` gives the full ISO 8601 string like `2025-10-18T17:00:00+00:00` instead of just `2025`.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/utils/Extensions.kt:70-80`

**Step 1: Extract year from endDate**

```kotlin
fun BaseItemDto.getYearRange(): String? {
    val startYear = getYear() ?: return null
    if (!isSeries()) return startYear.toString()

    val endYear = endDate?.year  // OffsetDateTime has .year property
    return if (endYear != null) {
        "$startYear - $endYear"
    } else {
        "$startYear - Present"
    }
}
```

Note: `endDate` in the Jellyfin Kotlin SDK is `OffsetDateTime?`. Access `.year` to get just the integer year.

**Step 2: Change "Years" label to "Year" in DetailViewModel**

In `DetailViewModel.kt` line ~301:
```kotlin
// BEFORE:
add(DetailInfoRowModel("Years", yearRange, Icons.Default.CalendarToday))

// AFTER:
add(DetailInfoRowModel("Year", yearRange, Icons.Default.CalendarToday))
```

**Step 3: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 4: Run tests**
```bash
./gradlew :app:testDebugUnitTest
```

**Step 5: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/utils/Extensions.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModel.kt
git commit -m "fix: year range shows full ISO datetime - extract .year from OffsetDateTime"
```

---

## Task 9: Fix Duplicate Rating in Detail Screen

**Root cause:** In `DetailViewModel.toHeroModel()`, community rating is added to BOTH `metaBadges` and `infoRows`. This shows it twice in the UI.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModel.kt:280-325`

**Step 1: Remove rating from metaBadges**

In `toHeroModel()`, the `metaBadges` build block:
```kotlin
val metaBadges = buildList {
    if (!item.isSeries() && !item.isSeason()) {
        add(item.getMediaTypeLabel())
    }
    item.officialRating?.takeIf { it.isNotBlank() }?.let(::add)
    formatCommunityRating(item)?.let(::add)  // REMOVE THIS LINE
}
```

Remove the `formatCommunityRating(item)?.let(::add)` line. Rating already appears in `infoRows`.

**Step 2: Remove "Movie" type badge for movies**

Also in `metaBadges`, change the condition so "Movie" doesn't appear since it's obvious:
```kotlin
val metaBadges = buildList {
    // Only show type label for non-obvious types (not Movie, not Series, not Season, not Episode)
    if (!item.isSeries() && !item.isSeason() && !item.isMovie() && !item.isEpisode()) {
        add(item.getMediaTypeLabel())
    }
    item.officialRating?.takeIf { it.isNotBlank() }?.let(::add)
    // rating removed from here - it's in infoRows
}
```

**Step 3: Remove episode count from Series view (keep for Season)**

In `infoRows` build block:
```kotlin
// BEFORE:
if (totalEpisodeCount > 0) {
    add(DetailInfoRowModel("Episodes", totalEpisodeCount.toString(), Icons.AutoMirrored.Filled.FormatListBulleted))
}

// AFTER: only show episode count for Season detail, not Series
if (item.isSeason() && totalEpisodeCount > 0) {
    add(DetailInfoRowModel("Episodes", totalEpisodeCount.toString(), Icons.AutoMirrored.Filled.FormatListBulleted))
}
```

**Step 4: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 5: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModel.kt
git commit -m "fix: remove duplicate rating badge, remove Movie type badge, episode count only on Season"
```

---

## Task 10: Fix Detail Screen — Left-Align Content and Limit Overview

**Root cause:** The detail content `Column` has no explicit `horizontalAlignment` and the overview text is limited to 5 lines instead of 4. Also want explicit left-alignment.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreen.kt:200-230`

**Step 1: Add horizontalAlignment and fix maxLines**

Find the main content Column in DetailScreen (around line 200):
```kotlin
item {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
```

Change to:
```kotlin
item {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,   // ADD
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
```

Also find the overview text (around line 222):
```kotlin
Text(
    text = displayOverview,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 5,        // CHANGE to 4
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.fillMaxWidth(0.7f)
)
```

Change `maxLines = 5` to `maxLines = 4`.

**Step 2: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreen.kt
git commit -m "fix: detail screen content left-aligned, overview limited to 4 lines"
```

---

## Task 11: Fix Season Card — Use Parent Series Backdrop for Better Image

**Root cause:** Seasons often have only a Primary (portrait poster) image. `getWideCardImageUrl()` falls back to `Primary` for seasons which is portrait — it looks wrong in the 16:9 landscape card layout. Should use the series backdrop when the season has no landscape image.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/data/repository/JellyfinStreamRepository.kt:492-524`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModel.kt:227-234`

**Step 1: Update getWideCardImageUrl to accept a parent fallback**

Add an overloaded version or a `fallbackItem` parameter:
```kotlin
/**
 * Get a strictly horizontal thumbnail for wide cards.
 * For seasons without a backdrop, falls back to the parent series backdrop via [parentItem].
 */
fun getWideCardImageUrl(item: BaseItemDto, parentItem: BaseItemDto? = null): String? {
    return try {
        val server = authRepository.getCurrentServer() ?: return null
        if (server.accessToken.isNullOrBlank() || server.url.isNullOrBlank()) return null

        val itemId = item.id.toString()

        if (item.type == BaseItemKind.EPISODE) {
            item.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
                return getImageUrl(itemId, "Primary", tag)
            }
        }

        item.backdropImageTags?.firstOrNull()?.let { tag ->
            return getImageUrl(itemId, "Backdrop", tag)
        }

        item.imageTags?.get(ImageType.THUMB)?.let { tag ->
            return getImageUrl(itemId, "Thumb", tag)
        }

        // For seasons: try parent series backdrop before falling back to portrait Primary
        if (item.type == BaseItemKind.SEASON && parentItem != null) {
            parentItem.backdropImageTags?.firstOrNull()?.let { tag ->
                return getImageUrl(parentItem.id.toString(), "Backdrop", tag)
            }
            parentItem.imageTags?.get(ImageType.THUMB)?.let { tag ->
                return getImageUrl(parentItem.id.toString(), "Thumb", tag)
            }
        }

        // Last resort: Primary (may be portrait)
        if (item.type == BaseItemKind.SEASON) {
            item.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
                return getImageUrl(itemId, "Primary", tag)
            }
        }

        null
    } catch (e: CancellationException) {
        throw e
    }
}
```

**Step 2: Pass the parent series item from DetailViewModel**

In `DetailViewModel.loadSeasonsAndEpisodes()`, the series `item` is available. Pass it to `getWideCardImageUrl`:
```kotlin
// In the isSeries() branch, when building seasonModels:
DetailSeasonModel(
    id = season.id.toString(),
    title = season.getDisplayTitle(),
    subtitle = buildSeasonSubtitle(season, episodeModels.size),
    overview = season.overview?.takeIf { it.isNotBlank() },
    imageUrl = repositories.stream.getWideCardImageUrl(season, parentItem = item),  // pass parent
    episodeCount = episodeModels.size.takeIf { it > 0 } ?: (season.childCount ?: 0),
)
```

**Step 3: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 4: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/data/repository/JellyfinStreamRepository.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModel.kt
git commit -m "fix: season cards show series backdrop instead of portrait primary image"
```

---

## Task 12: Fix Season Detail — Background Image Uses Higher Quality Parent Backdrop

**Root cause:** When viewing a Season detail page, the season item's `backdropUrl` may be low-quality or a portrait image (Primary fallback in `getBackdropUrl`). Need to fall back to the parent series backdrop when season has none.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/data/repository/JellyfinStreamRepository.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModel.kt:340-352`

**Step 1: Add getBackdropUrl variant with parent fallback**

Add a new method to `JellyfinStreamRepository`:
```kotlin
/**
 * Get backdrop URL for an item, using a parent item's backdrop as fallback.
 * Useful for Seasons/Episodes that don't have their own backdrop.
 */
fun getBackdropUrlWithFallback(item: BaseItemDto, parentItem: BaseItemDto? = null): String? {
    val backdropTag = item.backdropImageTags?.firstOrNull()
    if (backdropTag != null) {
        val server = authRepository.getCurrentServer() ?: return null
        return "${server.url}/Items/${item.id}/Images/Backdrop?tag=$backdropTag&maxHeight=$BACKDROP_MAX_HEIGHT&maxWidth=$BACKDROP_MAX_WIDTH"
    }

    // Try parent backdrop
    if (parentItem != null) {
        val parentBackdropTag = parentItem.backdropImageTags?.firstOrNull()
        if (parentBackdropTag != null) {
            val server = authRepository.getCurrentServer() ?: return null
            return "${server.url}/Items/${parentItem.id}/Images/Backdrop?tag=$parentBackdropTag&maxHeight=$BACKDROP_MAX_HEIGHT&maxWidth=$BACKDROP_MAX_WIDTH"
        }
    }

    // Final fallback to existing logic
    return getBackdropUrl(item)
}
```

**Step 2: Use this in DetailViewModel for Season items**

In `toHeroModel()`, when building `DetailHeroModel`:
```kotlin
return DetailHeroModel(
    id = item.id.toString(),
    title = item.getDisplayTitle(),
    subtitle = subtitleParts.joinToString(" | ").ifBlank { null },
    overview = item.overview?.takeIf { it.isNotBlank() },
    imageUrl = repositories.stream.getLandscapeImageUrl(item),
    backdropUrl = repositories.stream.getBackdropUrl(item),   // existing
    ...
)
```

To support this, we need the parent series item when processing a Season. This requires a refactor of `toHeroModel()` to optionally accept a parent. The simplest approach: in the Season loading path within `loadSeasonsAndEpisodes`, we already have the series `item`. We can instead fetch the series item from the detail page and set it on the hero.

A simpler quick fix: In `DetailViewModel.load()`, after the season is detected, re-call `getBackdropUrl` with the parent. Since `item` is in scope when calling `toHeroModel()`, add an optional `parentForBackdrop: BaseItemDto? = null` parameter:

```kotlin
private fun toHeroModel(
    item: BaseItemDto,
    seasons: List<DetailSeasonModel> = emptyList(),
    episodesBySeasonId: Map<String, List<DetailEpisodeModel>> = emptyMap(),
    parentForBackdrop: BaseItemDto? = null,  // ADD
): DetailHeroModel {
    ...
    return DetailHeroModel(
        ...
        backdropUrl = repositories.stream.getBackdropUrlWithFallback(item, parentForBackdrop),  // CHANGE
        ...
    )
}
```

Then in `load()` when loading a Season detail, first fetch the parent series and pass it:
```kotlin
// In load() -> detailResult success path:
val item = detailResult.data
val parentForBackdrop: BaseItemDto? = if (item.isSeason() && item.seriesId != null) {
    (repositories.media.getItemDetails(item.seriesId.toString()) as? ApiResult.Success)?.data
} else null

val heroModel = toHeroModel(
    item = item,
    seasons = seasonsAndEpisodes.first,
    episodesBySeasonId = seasonsAndEpisodes.second,
    parentForBackdrop = parentForBackdrop,  // ADD
)
```

**Step 3: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 4: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/data/repository/JellyfinStreamRepository.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModel.kt
git commit -m "fix: season detail uses high-quality series backdrop when season has no backdrop"
```

---

## Task 13: Add Episode Watch Status Overlay to Cards

**Root cause:** Episode cards in Season detail don't show any watched status indicator. User wants a checkmark for watched episodes and a play indicator for partially-watched.

**Files:**
- `TvMediaCard.kt` — already updated in Task 7 to support `WatchStatus` parameter
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreen.kt:426-445`

**Step 1: Pass watch status to episode cards in DetailScreen**

In `DetailScreen.kt`, find the Episodes `LazyRow` (around line 427):
```kotlin
items(
    episodes,
    key = { it.id },
    contentType = { "MediaCard" }
) { episode ->
    TvMediaCard(
        title = episode.title,
        subtitle = episode.subtitle,
        imageUrl = episode.imageUrl,
        onClick = { onOpenItem(episode.id) },
        onFocus = { focusedDescription = episode.overview },
    )
}
```

Change to:
```kotlin
items(
    episodes,
    key = { it.id },
    contentType = { "MediaCard" }
) { episode ->
    TvMediaCard(
        title = episode.title,
        subtitle = episode.subtitle,
        imageUrl = episode.imageUrl,
        onClick = { onOpenItem(episode.id) },
        onFocus = { focusedDescription = episode.overview },
        watchStatus = when {
            episode.isWatched -> WatchStatus.WATCHED
            episode.canResume -> WatchStatus.IN_PROGRESS
            else -> WatchStatus.NONE
        },
    )
}
```

`WatchStatus` is the enum added to `TvMediaCard.kt` in Task 7. Import it:
```kotlin
import com.rpeters.cinefintv.ui.components.WatchStatus
```

**Step 2: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreen.kt
git commit -m "feat: episode cards show watch status overlay (checkmark/play indicator)"
```

---

## Task 14: Add Episode Air Date to Episode Detail

**Root cause:** When viewing an episode detail (DetailScreen for an Episode), there's no air date shown. The `DetailEpisodeModel` doesn't carry premiere date. Also the "Years" label for episodes should say "Date Aired".

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModel.kt`

**Step 1: Add air date to episode infoRows in toHeroModel()**

When `item.isEpisode()`, add the premiere date. The `BaseItemDto` has a `premiereDate: OffsetDateTime?` field. In the `infoRows` build block:
```kotlin
// For episodes: show air date instead of year range
if (item.isEpisode()) {
    val airDate = item.premiereDate
    if (airDate != null) {
        val formatted = "${airDate.year}-${airDate.monthValue.toString().padStart(2, '0')}-${airDate.dayOfMonth.toString().padStart(2, '0')}"
        add(DetailInfoRowModel("Date Aired", formatted, Icons.Default.CalendarToday))
    }
} else {
    // existing year range logic
    val yearRange = item.getYearRange()
    if (yearRange != null) {
        add(DetailInfoRowModel("Year", yearRange, Icons.Default.CalendarToday))
    }
}
```

This replaces the existing `val yearRange = item.getYearRange()` block for episodes.

**Step 2: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModel.kt
git commit -m "feat: episode detail shows air date with 'Date Aired' label"
```

---

## Task 15: Fix Library Screens — Remove Redundant Titles

**Root cause:** Movie library shows "Movies" and "All Movies" headers (redundant since the tab already says Movies). Stuff library shows "Stuff (Home Videos)" title.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryScreen.kt:112-130`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/stuff/StuffLibraryScreen.kt:78-84`

**Step 1: Remove title and "All Movies" items from LibraryScreen**

In `LibraryScreen.kt`, find the `LazyVerticalGrid` content. Remove the two header items:
```kotlin
// DELETE this entire item block:
item(span = { GridItemSpan(maxLineSpan) }) {
    Text(
        text = state.title,
        style = MaterialTheme.typography.displaySmall,
        color = Color.White,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

// DELETE this entire item block:
if (category == LibraryCategory.MOVIES) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = stringResource(R.string.filter_all_movies),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}
```

You can also remove the `import androidx.compose.ui.res.stringResource` import if it becomes unused after this change.

**Step 2: Remove title from StuffLibraryScreen**

In `StuffLibraryScreen.kt`, remove the title header item:
```kotlin
// DELETE:
item(span = { GridItemSpan(maxLineSpan) }) {
    Text(
        text = "Stuff (Home Videos)",
        style = MaterialTheme.typography.displaySmall,
        color = Color.White,
    )
}
```

**Step 3: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 4: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryScreen.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/stuff/StuffLibraryScreen.kt
git commit -m "fix: remove redundant library title headers (already shown in tab nav)"
```

---

## Task 16: Add Rating and Runtime to Movie Library Cards

**Root cause:** Movie cards in the library grid only show year as subtitle. User wants rating and runtime visible under cards.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryViewModel.kt:72-84`

**Step 1: Include rating fields in LibraryItems API call**

In `JellyfinMediaRepository.getLibraryItems()`, the `fields` list already includes `OVERVIEW` and others. We need `MEDIA_STREAMS` for codec info, but for rating/runtime the basic fields from `BaseItemDto` are already included (communityRating, runTimeTicks, officialRating come in the default response).

**Step 2: Update toCardModel in LibraryViewModel to include rating/runtime in subtitle**

```kotlin
private fun toCardModel(item: BaseItemDto): HomeCardModel? {
    val id = item.id.toString()

    // Build a richer subtitle for movies
    val subtitle = if (item.isMovie()) {
        val parts = mutableListOf<String>()
        item.getYear()?.let { parts.add(it.toString()) }
        item.getFormattedDuration()?.let { parts.add(it) }
        (item.communityRating as? Number)?.toDouble()?.takeIf { it > 0 }
            ?.let { parts.add("★ ${"%.1f".format(it)}") }
        parts.joinToString("  ·  ").ifBlank { null }
    } else {
        item.getYear()?.toString()
            ?: item.getFormattedDuration()
            ?: item.type.toString().replace('_', ' ')
    }

    return HomeCardModel(
        id = id,
        title = item.getDisplayTitle(),
        subtitle = subtitle,
        imageUrl = repositories.stream.getLandscapeImageUrl(item),
    )
}
```

You'll need to add `import com.rpeters.cinefintv.utils.isMovie` (check if already imported).

**Step 3: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 4: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryViewModel.kt
git commit -m "feat: movie library cards show year, runtime, and star rating"
```

---

## Task 17: Fix Stuff Library — Increase Limit and Verify Subfolder Loading

**Root cause:** The stuff library uses `limit = 180` and the repository uses `recursive = true` in its API call (confirmed in JellyfinMediaRepository.kt line 151). Subfolder videos should already be included. The limit may be too low or there may be a display issue. Increase limit and verify.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/stuff/StuffLibraryViewModel.kt:42`

**Step 1: Increase limit to ensure all items load**

```kotlin
// BEFORE:
when (val result = repositories.media.getLibraryItems(collectionType = "homevideos", limit = 180))

// AFTER:
when (val result = repositories.media.getLibraryItems(collectionType = "homevideos", limit = 500))
```

**Step 2: Build and verify with actual device/emulator**

The `recursive = true` is already set in `getLibraryItems`. If videos in subfolders are still not showing, the issue may be that the library `parentId` is not being set. Currently `parentId = null` which fetches from all libraries. We may need to first find the homevideos library ID and use that as parentId.

To verify, check if `getLibraryItems` with `collectionType = "homevideos"` and no `parentId` returns items. This should work as the API uses `collectionType` to filter.

**Step 3: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/stuff/StuffLibraryViewModel.kt
git commit -m "fix: increase stuff library item limit to 500 to show all videos"
```

---

## Task 18: Add Technical Details to Stuff Detail Screen

**Root cause:** Stuff (home video) detail screen shows minimal info. User wants quality/codec/duration etc.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/stuff/StuffDetailViewModel.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/stuff/StuffDetailScreen.kt`

**Step 1: Add techInfo fields to StuffDetailModel**

In `StuffDetailViewModel.kt`, update the model:
```kotlin
data class StuffDetailModel(
    val id: String,
    val title: String,
    val overview: String?,
    val metadataLine: String?,
    val imageUrl: String?,
    val backdropUrl: String?,
    val techDetails: List<Pair<String, String>> = emptyList(), // ADD: label to value pairs
)
```

**Step 2: Populate techDetails in load()**

In `StuffDetailViewModel.load()`, after fetching item details:
```kotlin
val item = detailsResult.data

// Extract technical info from mediaStreams
val techDetails = mutableListOf<Pair<String, String>>()
item.mediaSources?.firstOrNull()?.mediaStreams?.let { streams ->
    streams.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO }?.let { video ->
        video.displayTitle?.takeIf { it.isNotBlank() }?.let { techDetails.add("Video" to it) }
        video.codec?.takeIf { it.isNotBlank() }?.let { techDetails.add("Codec" to it.uppercase()) }
        if (video.width != null && video.height != null) {
            techDetails.add("Resolution" to "${video.width}×${video.height}")
        }
    }
    streams.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }?.let { audio ->
        audio.displayTitle?.takeIf { it.isNotBlank() }?.let { techDetails.add("Audio" to it) }
    }
}
item.getFormattedDuration()?.let { techDetails.add("Duration" to it) }
item.container?.takeIf { it.isNotBlank() }?.let { techDetails.add("Format" to it.uppercase()) }
```

Update the `StuffDetailModel` creation to include `techDetails`.

Note: `mediaSources` and `mediaStreams` may not be populated unless `ItemFields.MEDIA_STREAMS` is requested. In `getItemDetails()` in `JellyfinMediaRepository`, check if `MEDIA_STREAMS` is in the fields list. If not, add it.

**Step 3: Display techDetails in StuffDetailScreen**

In `StuffDetailScreen.kt`, after the overview text and before the Play button Row, add:
```kotlin
if (state.item.techDetails.isNotEmpty()) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.item.techDetails.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "$label:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}
```

**Step 4: Ensure getItemDetails includes MEDIA_STREAMS field**

Find `getItemDetails` in `JellyfinMediaRepository.kt` and confirm `ItemFields.MEDIA_STREAMS` is in the fields list. If not, add it.

**Step 5: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 6: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/stuff/StuffDetailViewModel.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/stuff/StuffDetailScreen.kt \
        app/src/main/java/com/rpeters/cinefintv/data/repository/JellyfinMediaRepository.kt
git commit -m "feat: stuff detail screen shows video codec/resolution/audio technical details"
```

---

## Task 19: Fix Player — Redesign Controls Layout

**Root cause:** Three player issues: (1) Center play/pause/seek buttons are redundant given D-pad controls; (2) Play/Pause/Stop should be bottom-left; (3) Back button press doesn't exit because `onInteract()` is always called before the key check.

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerScreen.kt`

**Step 1: Fix Back button exit bug**

The bug: `onInteract()` is called unconditionally at the top of the key handler, making `controlsVisible = true` before the Back key check runs. So Back always sees `controlsVisible = true` and hides controls instead of going back.

Fix: Don't call `onInteract()` for Back/Escape key when controls are hidden:

```kotlin
.onKeyEvent { keyEvent ->
    if (keyEvent.type == KeyEventType.KeyDown) {
        when (keyEvent.key) {
            Key.Back, Key.Escape -> {
                when {
                    isTrackPanelVisible -> {
                        isTrackPanelVisible = false
                        true
                    }
                    controlsVisible -> {
                        onInteract()  // reset timer
                        controlsVisible = false
                        true
                    }
                    else -> {
                        // Controls hidden, Back exits the player
                        false  // let system handle → navigates back
                    }
                }
            }
            else -> {
                onInteract()  // only interact for non-back keys
                when (keyEvent.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                        } else {
                            if (isPlaying) player.pause() else player.play()
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        true
                    }
                    Key.DirectionRight -> {
                        player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration))
                        true
                    }
                    else -> false
                }
            }
        }
    } else {
        false
    }
}
```

**Step 2: Remove center controls, add Play/Pause/Stop to bottom-left**

Find the "Center Controls" Row (around line 392-416) and **delete** it entirely:
```kotlin
// DELETE this entire block:
// Center Controls
Row(
    modifier = Modifier.align(Alignment.Center),
    horizontalArrangement = Arrangement.spacedBy(32.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    OutlinedButton(onClick = { ... }) { Icon(Icons.Default.Replay10 ...) }
    Button(onClick = { ... }, modifier = Modifier.focusRequester(playPauseFocusRequester), ...) {
        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow ...)
    }
    OutlinedButton(onClick = { ... }) { Icon(Icons.Default.Forward10 ...) }
}
```

Then in the "Bottom Controls" Column, add play controls to the left of the progress bar. Change the bottom controls structure:

```kotlin
// Bottom Controls
Column(
    modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .padding(horizontal = 48.dp, vertical = 32.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    // Progress Bar
    if (duration > 0) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT: Play controls + time
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rewind 10s
                OutlinedButton(
                    onClick = { onInteract(); player.seekTo((position - 10_000).coerceAtLeast(0)) }
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", modifier = Modifier.size(20.dp))
                }

                // Play/Pause
                Button(
                    onClick = { onInteract(); if (isPlaying) player.pause() else player.play() },
                    modifier = Modifier.focusRequester(playPauseFocusRequester),
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Forward 10s
                OutlinedButton(
                    onClick = { onInteract(); player.seekTo((position + 10_000).coerceAtMost(duration)) }
                ) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(20.dp))
                }

                // Stop (navigate back)
                OutlinedButton(onClick = { onInteract(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Stop", modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.width(8.dp))

                // Time
                Text(
                    text = "${formatMs(position)} / ${formatMs(duration)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // RIGHT: Toggles
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isEpisodicContent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto-play", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                        Switch(
                            checked = uiState.autoPlayNextEpisode,
                            onCheckedChange = { onInteract(); viewModel.setAutoPlayNextEpisode(it) },
                        )
                    }
                }

                OutlinedButton(onClick = { onInteract(); isTrackPanelVisible = !isTrackPanelVisible }) {
                    Icon(Icons.Default.Settings, contentDescription = "Media Settings", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Audio / Subs")
                }
            }
        }
    }
}
```

Also remove the old top-bar Back button since Stop/Back is now at the bottom:
```kotlin
// UPDATE top bar to only show title (remove the back button from top):
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(32.dp)
        .align(Alignment.TopStart),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(uiState.title, style = MaterialTheme.typography.headlineSmall)
}
```

**Step 3: Remove unused imports**

After removing center controls, remove any icons no longer used.

**Step 4: Build**
```bash
./gradlew :app:assembleDebug
```

**Step 5: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerScreen.kt
git commit -m "fix: player back button exits, center controls removed, controls moved to bottom bar"
```

---

## Task 20: Final Build and Smoke Test

**Step 1: Full clean build**
```bash
./gradlew clean :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 2: Run all unit tests**
```bash
./gradlew :app:testDebugUnitTest
```
Expected: All tests pass

**Step 3: Install and manually verify key flows on device/emulator**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Key things to verify:
- [ ] Auth screens: headings are white/visible
- [ ] Auth text field: cursor starts at beginning (not after placeholder)
- [ ] Home: D-pad down from tabs goes directly to carousel (no hidden step)
- [ ] Home tabs: text always white, no white background on any tab state
- [ ] Home carousel: shows year/runtime/rating beneath title
- [ ] Continue Watching: shows "X min left" instead of "Resume Y%"
- [ ] Cards: smooth scale animation on focus, white border instead of red, slight elevation lift
- [ ] TV Show detail: year shows "2018 - 2023" not "2018 - 2025-10-18T17:00"
- [ ] TV Show detail: no duplicate rating, no "Movie" badge for movies
- [ ] TV Show detail: episode count NOT shown (only on season detail)
- [ ] Season detail: backdrop is the series backdrop (high quality)
- [ ] Season cards: show landscape/backdrop image instead of portrait
- [ ] Episode cards: show watched checkmark or play indicator
- [ ] Library screens: no title headers
- [ ] Movie library: shows "2023 · 2h 14m · ★ 7.8" under cards
- [ ] Stuff library: all videos including subfolders listed
- [ ] Stuff detail: shows codec/resolution/audio info
- [ ] Player: Back button exits player after first press (not after controls fade)
- [ ] Player: No center play controls, bottom-left has prev/play/next/stop

---

## Notes for Implementor

### TV Material3 quirks:
- Every `@Composable` using TV Material3 needs `@OptIn(ExperimentalTvMaterial3Api::class)`
- Use `androidx.tv.material3.Text`, not `androidx.compose.material3.Text`
- `TabDefaults` in TV M3 may have different method signatures — check available overloads in IDE autocomplete

### Spring animation imports:
```kotlin
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
```

### graphicsLayer for card scale:
```kotlin
import androidx.compose.ui.graphics.graphicsLayer  // This is NOT a class, it's an extension
// Usage:
Modifier.graphicsLayer {
    scaleX = scale
    scaleY = scale
    shadowElevation = elevation.toPx()
}
```

### OffsetDateTime from Jellyfin SDK:
The `BaseItemDto.endDate` and `BaseItemDto.premiereDate` fields are `OffsetDateTime?` from `java.time`. Access `.year`, `.monthValue`, `.dayOfMonth` properties directly.

### MEDIA_STREAMS field:
To get codec/resolution data, `ItemFields.MEDIA_STREAMS` must be included in the `getItemDetails()` API call fields list. Check `JellyfinMediaRepository.getItemDetails()`.
