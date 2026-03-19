# D-pad Navigation Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the focus architecture of CinefinTV to ensure consistent, bidirectional navigation across all 10-foot surfaces, fixing "dead steps" and lost focus issues.

**Architecture:** A systematic approach starting with core infrastructure fixes (route matching, component focus delegation) followed by a screen-by-screen standard focus map implementation using bidirectional anchors.

**Tech Stack:** Kotlin, Jetpack Compose for TV (Material 3), Hilt, Android Navigation.

---

### Task 1: Fix Route Pattern Matching in `TvScreenFocusRegistry`

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/components/TvScreenFocus.kt`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/components/TvScreenFocusRegistryTest.kt`

- [ ] **Step 1: Write a failing test for parameterized route matching**

Create `app/src/test/java/com/rpeters/cinefintv/ui/components/TvScreenFocusRegistryTest.kt`:
```kotlin
package com.rpeters.cinefintv.ui.components

import androidx.compose.ui.focus.FocusRequester
import org.junit.Assert.assertEquals
import org.junit.Test

class TvScreenFocusRegistryTest {
    @Test
    fun `requesterFor should match parameterized routes`() {
        val registry = TvScreenFocusRegistry()
        val requester = FocusRequester()
        registry.register("detail/{itemId}", requester)
        
        val result = registry.requesterFor("detail/12345")
        assertEquals(requester, result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.components.TvScreenFocusRegistryTest"`
Expected: FAIL (returns null)

- [ ] **Step 3: Update `TvScreenFocusRegistry` implementation**

Modify `app/src/main/java/com/rpeters/cinefintv/ui/components/TvScreenFocus.kt`:
```kotlin
fun requesterFor(route: String?): FocusRequester? {
    if (route.isNullOrBlank()) return null
    
    // 1. Try exact match
    primaryRequesters[route]?.let { return it }
    
    // 2. Try pattern matching (e.g. "detail/123" matches "detail/{itemId}")
    val requestedParts = route.split("/")
    return primaryRequesters.entries.firstOrNull { (registeredRoute, _) ->
        val registeredParts = registeredRoute.split("/")
        if (requestedParts.size != registeredParts.size) return@firstOrNull false
        
        registeredParts.zip(requestedParts).all { (reg, req) ->
            reg == req || (reg.startsWith("{") && reg.endsWith("}"))
        }
    }?.value ?: primaryRequesters.entries.firstOrNull { (registeredRoute, _) ->
        route.startsWith("$registeredRoute/")
    }?.value
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.components.TvScreenFocusRegistryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/components/TvScreenFocus.kt
git add app/src/test/java/com/rpeters/cinefintv/ui/components/TvScreenFocusRegistryTest.kt
git commit -m "fix(focus): improve route pattern matching in TvScreenFocusRegistry"
```

---

### Task 2: Fix Focus Delegation in `CinefinTextInputField`

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/components/CinefinTvPrimitives.kt`

- [ ] **Step 1: Move focus modifiers from Surface to BasicTextField**

Update `CinefinTextInputField` in `app/src/main/java/com/rpeters/cinefintv/ui/components/CinefinTvPrimitives.kt`:
```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTextInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val expressiveColors = LocalCinefinExpressiveColors.current
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = modifier.fillMaxWidth(), // Container only gets layout modifiers
        shape = shape,
        // ... colors and border stay same ...
    ) {
        Column(...) {
            // ... label stays same ...
            BasicTextField(
                // ... props same ...
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                    // Apply the focus-related modifiers passed from parent here
                    .then(modifier), 
                // ... decorationBox same ...
            )
        }
    }
}
```
*Wait, `modifier` passed to `CinefinTextInputField` usually contains `focusRequester`. If we apply it to both `Surface` and `BasicTextField`, it might be ambiguous. We should strip focus modifiers from the Surface.*

- [ ] **Step 2: Clean up modifier application in `CinefinTextInputField`**

```kotlin
// In CinefinTextInputField
Surface(
    modifier = Modifier.fillMaxWidth(), // Only layout, no focus
    // ...
) {
    // ...
    BasicTextField(
        // ...
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .then(modifier), // Requesters land here
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/components/CinefinTvPrimitives.kt
git commit -m "fix(focus): delegate focus modifiers to inner field in CinefinTextInputField"
```

---

### Task 3: Fix Focus Delegation in `TvMediaCard` and `TvPersonCard`

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/components/TvPersonCard.kt`

- [ ] **Step 1: Move focus modifiers to inner `Card` in `TvMediaCard`**

Modify `app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt`:
```kotlin
StandardCardContainer(
    modifier = if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth(), // No focus
    imageCard = {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .onFocusChanged { ... }
                .then(modifier), // Focus modifiers land on the focusable Card
            // ...
        )
    },
    // ...
)
```

- [ ] **Step 2: Move focus modifiers to inner `Card` in `TvPersonCard`**

Modify `app/src/main/java/com/rpeters/cinefintv/ui/components/TvPersonCard.kt`:
```kotlin
StandardCardContainer(
    modifier = Modifier.width(180.dp), // No focus
    imageCard = {
        Card(
            onClick = onClick,
            modifier = Modifier
                .size(172.dp)
                .onFocusChanged { ... }
                .then(modifier), // Focus modifiers land on the focusable Card
            // ...
        )
    },
    // ...
)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt
git add app/src/main/java/com/rpeters/cinefintv/ui/components/TvPersonCard.kt
git commit -m "fix(focus): delegate focus modifiers to inner Card in Media and Person cards"
```

---

### Task 4: Standardize Bidirectional Anchors in `HomeScreen`

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: Update `HomeScreen` to use standard bidirectional anchor**

```kotlin
// In HomeScreen content
item {
    TvScreenTopFocusAnchor(
        state = TvScreenFocusState(
            topAnchorRequester = topAnchorRequester,
            primaryContentRequester = primaryContentRequester
        ),
        onFocused = {
            coroutineScope.launch { listState.animateScrollToItem(0) }
        },
    )
}

// In FeaturedCarousel or HomeSection (UP from first item)
.focusProperties {
    up = topAnchorRequester
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt
git commit -m "feat(focus): implement bidirectional navigation in HomeScreen"
```

---

### Task 5: Rebuild `DetailScreen` Focus Map

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailActionRow.kt`

- [ ] **Step 1: Update `DetailActionRow` to accept and apply focus modifiers correctly**

Ensure buttons in the action row correctly use the requesters.

- [ ] **Step 2: Link `DetailScreen` top anchor to action row**

```kotlin
// In DetailScreen
val screenFocus = rememberTvScreenFocusState()
RegisterPrimaryScreenFocus(NavRoutes.DETAIL, screenFocus.primaryContentRequester)

TvScreenTopFocusAnchor(
    state = screenFocus,
    onFocused = { ... }
)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreen.kt
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailActionRow.kt
git commit -m "feat(focus): rebuild DetailScreen focus map"
```

---

### Task 6: Rebuild Remaining Screens (Library, Search, Settings)

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/settings/SettingsScreen.kt`

- [ ] **Step 1: Apply standard anchor and delegation pattern to LibraryScreen**
- [ ] **Step 2: Apply standard anchor and delegation pattern to SearchScreen**
- [ ] **Step 3: Apply standard anchor and delegation pattern to SettingsScreen**
- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryScreen.kt
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/search/SearchScreen.kt
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/settings/SettingsScreen.kt
git commit -m "feat(focus): standardize focus map across Library, Search, and Settings"
```

---

### Task 7: Verification and Cleanup

- [ ] **Step 1: Verify all unit tests pass**
Run: `./gradlew :app:testDebugUnitTest`

- [ ] **Step 2: Manual walkthrough (D-pad flows)**
- [ ] **Step 3: Final commit and cleanup**
