# Navigation and Screens Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the TV interface using native Compose for TV spatial navigation and dedicated screens, eliminating the custom focus registry.

**Architecture:** Native Compose navigation. A clean, independent `TabRow` at the top. Specific, focused detail screens (`MovieDetailScreen`, `TvShowDetailScreen`, `SeasonScreen`, `EpisodeDetailScreen`) instead of one monolithic detail screen. Simple `LazyVerticalGrid` Library screens without mixed complex layout elements.

**Tech Stack:** Kotlin, Jetpack Compose for TV (Material 3), Hilt, Android Navigation.

---

### Task 1: Native TV Navigation Foundation (CinefinTvApp & NavGraph)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavRoutes.kt`

- [ ] **Step 1: Update NavRoutes**
Update `NavRoutes.kt` to define the new explicit routes: `movieDetail`, `tvShowDetail`, `seasonDetail`, `episodeDetail`, `stuffDetail`. Remove the generic `detail` route.

- [ ] **Step 2: Update CinefinTvApp.kt TabRow**
Ensure the `TabRow` in `CinefinTvApp.kt` uses standard Compose `Tab`s without any `focusProperties`. It should just be a simple visual element at the top. When a user presses DOWN, Compose's native spatial engine will find the next focusable item in the NavHost.

- [ ] **Step 3: Update NavGraph**
Map the new routes in `NavGraph.kt` to placeholder composables for now.

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavGraph.kt app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavRoutes.kt
git commit -m "feat(nav): establish native tv navigation foundation and routes"
```

---

### Task 2: Rebuild Movie and TV Show Library Screens

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/MovieLibraryScreen.kt`
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/TvShowLibraryScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Implement MovieLibraryScreen**
Create a clean `LazyVerticalGrid(columns = GridCells.Adaptive(160.dp))` that pages through movies using `TvMediaCard`. No custom focus overrides. No "Recently Added" shelf mixed in. Just a pure grid.

- [ ] **Step 2: Implement TvShowLibraryScreen**
Mirror `MovieLibraryScreen` but target TV Show data.

- [ ] **Step 3: Hook up to NavGraph**
Update `NavGraph.kt` to route `LIBRARY_MOVIES` and `LIBRARY_TVSHOWS` to these new screens.

- [ ] **Step 4: Commit**
```bash
git add .
git commit -m "feat(library): rebuild movie and tv show library screens"
```

---

### Task 3: Rebuild Stuff (Collections) Library Screen

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/StuffLibraryScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Implement StuffLibraryScreen**
Create a clean `LazyVerticalGrid` for Collections/Home Videos. Native focus only.

- [ ] **Step 2: Hook up to NavGraph**
Update `NavGraph.kt` to route `LIBRARY_COLLECTIONS` to this new screen.

- [ ] **Step 3: Commit**
```bash
git add .
git commit -m "feat(library): rebuild stuff library screen"
```

---

### Task 4: Build Movie Detail Screen

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/MovieDetailScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Implement MovieDetailScreen UI**
Create a screen focused specifically on Movies.
Layout:
- Backdrop image
- Title, Metadata, Overview
- Row of Buttons: "Play", "Trailer" (if available)
- `LazyRow` for "Cast & Crew"
- `LazyRow` for "Similar Movies"
Rely strictly on native Compose focus traversal.

- [ ] **Step 2: Hook up to NavGraph**
Update `NavGraph.kt` to route `movieDetail/{itemId}` to this screen. Ensure Home/Library screens navigate here when a Movie is clicked.

- [ ] **Step 3: Commit**
```bash
git add .
git commit -m "feat(detail): create movie detail screen"
```

---

### Task 5: Build TV Show and Season Screens

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/TvShowDetailScreen.kt`
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/SeasonScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Implement TvShowDetailScreen**
Layout:
- Backdrop, Title, Metadata
- Button: "Play Next Episode"
- `LazyRow` of "Seasons" (clicking one goes to `SeasonScreen`)
- `LazyRow` for "Cast & Crew"

- [ ] **Step 2: Implement SeasonScreen**
Layout:
- Season Poster, Season Title
- `LazyVerticalGrid` or `LazyColumn` of "Episodes" (clicking one goes to `EpisodeDetailScreen`)

- [ ] **Step 3: Hook up to NavGraph**
Route `tvShowDetail/{itemId}` and `seasonDetail/{itemId}`. Ensure clicks flow correctly.

- [ ] **Step 4: Commit**
```bash
git add .
git commit -m "feat(detail): create tv show and season screens"
```

---

### Task 6: Build TV Episode and Stuff Detail Screens

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/EpisodeDetailScreen.kt`
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/StuffDetailScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Implement EpisodeDetailScreen**
Layout:
- Episode Still, Title, Episode Number
- Button: "Play"
- (Optional) Chapter selection row

- [ ] **Step 2: Implement StuffDetailScreen**
Similar to the old `CollectionsDetailScreen`, showing the collection details and a grid of items inside it.

- [ ] **Step 3: Hook up to NavGraph**
Route `episodeDetail/{itemId}` and `stuffDetail/{itemId}`. Update search/home click handlers to route to the correct specific detail screen based on `item.itemType`.

- [ ] **Step 4: Commit**
```bash
git add .
git commit -m "feat(detail): create episode and stuff detail screens"
```

---

### Task 7: Verification

- [ ] **Step 1: Verify Build**
Run: `./gradlew :app:assembleDebug`
Expected: SUCCESS

- [ ] **Step 2: Commit**
```bash
git add .
git commit -m "fix: finalize navigation and screen rewrite"
```