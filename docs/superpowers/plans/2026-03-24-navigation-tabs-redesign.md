# Navigation Tabs Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the custom navigation bar with the standard TV Material 3 `TabRow` to fix scrolling and focus issues.

**Architecture:** Refactor `CinefinAppScaffold` in `CinefinTvApp.kt` to use `androidx.tv.material3.TabRow` and `androidx.tv.material3.Tab`. This leverages native TV components for better D-pad support and automatic horizontal scrolling.

**Tech Stack:** Kotlin, Jetpack Compose, Compose for TV (Material 3 v1.1.0-beta01).

---

### Task 1: Establish Baseline and Update Imports

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt`
- Test: `app/src/androidTest/java/com/rpeters/cinefintv/ui/AppNavigationSmokeUiTest.kt`

- [ ] **Step 1: Run existing smoke tests as baseline**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.cinefintv.ui.AppNavigationSmokeUiTest`
Expected: PASS (or note existing failures)

- [ ] **Step 2: Update imports in `CinefinTvApp.kt`**

Add `TabRow`, `Tab`, `TabRowDefaults`, and `TabPosition` from `androidx.tv.material3`.

- [ ] **Step 3: Commit**

```bash
git commit -m "chore: update imports for TabRow migration"
```

---

### Task 2: Refactor `CinefinAppScaffold` to use `TabRow`

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt`

- [ ] **Step 1: Replace `Row` with `TabRow` inside the existing `Surface`**

Keep the `Surface` and its `Column` wrapper to preserve styling. Remove `.testTag(AppTestTags.NavBar)` from the outer `Surface` and add it to the `TabRow`.

```kotlin
TabRow(
    selectedTabIndex = selectedTabIndex,
    modifier = Modifier
        .fillMaxWidth()
        .testTag(AppTestTags.NavBar),
    indicator = { tabPositions, isHighlight ->
        TabRowDefaults.UnderlinedIndicator(
            currentTabPosition = tabPositions[selectedTabIndex],
            isHighlight = isHighlight
        )
    }
) {
    navTabItems.forEachIndexed { index, item ->
        Tab(
            selected = index == selectedTabIndex,
            modifier = Modifier.testTag(AppTestTags.tab(item.route)),
            onClick = { onNavigateToTab(item.route) }
        ) {
            // Tab content
        }
    }
}
```

- [ ] **Step 2: Implement `Tab` content with `Icon` and `Text`**

```kotlin
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
) {
    Icon(
        imageVector = item.icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
    )
    Text(
        text = item.label,
        style = MaterialTheme.typography.labelMedium
    )
}
```

- [ ] **Step 3: Remove old navigation state and logic**

Remove `focusedTabIndex` and the `LaunchedEffect(selectedTabIndex)` that was used for manual focus management.

- [ ] **Step 4: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt
git commit -m "feat: migrate navigation bar to TabRow and Tab"
```

---

### Task 3: Verify Navigation and Scrolling

**Files:**
- Test: `app/src/androidTest/java/com/rpeters/cinefintv/ui/AppNavigationSmokeUiTest.kt`

- [ ] **Step 1: Run automated smoke tests**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rpeters.cinefintv.ui.AppNavigationSmokeUiTest`
Expected: PASS

- [ ] **Step 2: Manual verification in emulator**

D-pad right until the end of the navigation bar. Confirm "Settings" scrolls into view and is focusable. Confirm clicking updates the screen correctly.

- [ ] **Step 3: Take verification screenshot**

Run: `adb shell screencap -p /sdcard/nav_fix_v3.png && adb pull /sdcard/nav_fix_v3.png nav_fix_v3.png`

- [ ] **Step 4: Commit verification artifact**

```bash
git add nav_fix_v3.png
git commit -m "test: add verification screenshot for TabRow migration"
```
