# Side Navbar Redesign (Option B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the side navbar into a smooth, expanding drawer that ensures all items (including Settings) are visible and reachable.

**Architecture:** 
- Use a single `animateFloatAsState` (`railProgress`) to drive all navigation drawer transitions.
- Implement a dynamic `railWidth` that expands from 80dp to 200dp.
- Wrap navigation items in a scrollable `Column` to prevent clipping.
- Use `AnimatedVisibility` or alpha-masking for labels driven by `railProgress`.

**Tech Stack:** Jetpack Compose for TV, Material 3, `animateFloatAsState`, `lerp`.

---

## File Map

| File | Change |
|---|---|
| `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt` | Refactor `CinefinAppScaffold` and animation logic. |

---

## Task 1: Refactor Animation Logic and Constants

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt`

- [ ] **Step 1: Define new layout constants**

Add these constants at the top level of `CinefinTvApp.kt` (or inside `CinefinAppScaffold` if preferred):

```kotlin
private val COLLAPSED_RAIL_WIDTH = 80.dp
private val EXPANDED_RAIL_WIDTH = 200.dp
private val RAIL_START_PADDING = 12.dp
```

- [ ] **Step 2: Consolidate animators into `railProgress`**

Find and remove the existing `logoSize`, `iconSize`, `buttonPaddingVertical` animators (lines ~296-315). Replace with:

```kotlin
val railProgress by animateFloatAsState(
    targetValue = if (navHasFocus) 1f else 0f,
    animationSpec = tween(durationMillis = 280),
    label = "railProgress",
)
val railWidth = lerp(COLLAPSED_RAIL_WIDTH, EXPANDED_RAIL_WIDTH, railProgress)
val logoSize = lerp(50.dp, 44.dp, railProgress)
val iconSize = lerp(28.dp, 24.dp, railProgress)
val buttonPaddingVertical = 12.dp // Constant padding for better alignment
```

- [ ] **Step 3: Update `railSlotWidth` to be dynamic**

Replace `val railSlotWidth = railWidth + railSlotStartPadding` with:

```kotlin
val railSlotWidth = railWidth + RAIL_START_PADDING
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt
git commit -m "refactor: consolidate navbar animators and define dynamic width constants"
```

---

## Task 2: Implement Scrollable Drawer Structure

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt`

- [ ] **Step 1: Replace the static `Column` with a scrollable one**

In `CinefinAppScaffold`, find the `Surface` content (around line 377). Update the `Column` to be scrollable:

```kotlin
Column(
    modifier = Modifier
        .fillMaxHeight()
        .verticalScroll(rememberScrollState()) // Add this
        .focusProperties {
            onEnter = {
                selectedTabFocusRequester.requestFocus()
            }
        }
        .focusGroup()
        .testTag(AppTestTags.NavBar),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp), // Slightly tighter spacing
)
```

- [ ] **Step 2: Update the `Surface` width**

Ensure the `Surface` uses the dynamic `railWidth`:

```kotlin
Surface(
    modifier = Modifier
        .fillMaxHeight()
        .width(railWidth), // Uses the animated lerp value
    // ... other properties
)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt
git commit -m "ui: make navbar drawer scrollable and apply dynamic width"
```

---

## Task 3: Redesign Tab Items and Label Visibility

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt`

- [ ] **Step 1: Simplify the `Button` and `Row` layout**

Refactor the `navTabItems` loop to ensure labels are visible whenever the rail is expanding/expanded.

```kotlin
navTabItems.forEachIndexed { index, item ->
    var isFocused by remember { mutableStateOf(false) }
    // Labels are visible whenever the rail is expanding or expanded
    val showLabel = railProgress > 0.05f 
    
    Button(
        onClick = { onNavigateToTab(item.destination) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp) // Ensure button doesn't hit surface edges
            .focusRequester(tabFocusRequesters[index])
            // ... existing focus/testTag logic ...
        // ... existing button properties ...
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = buttonPaddingVertical,
                    horizontal = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (railProgress < 0.1f) Arrangement.Center else Arrangement.Start,
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(iconSize),
                tint = if (index == selectedTabIndex || isFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                },
            )
            
            if (showLabel) {
                Spacer(modifier = Modifier.width(lerp(0.dp, 12.dp, railProgress)))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = if (index == selectedTabIndex || isFocused) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                        alpha = railProgress // Sync opacity with expansion
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // ... color logic ...
                )
            }
        }
    }
}
```

- [ ] **Step 2: Clean up unused imports**

Check for any unused animation imports (like `animateDpAsState` if no longer used anywhere else) and remove them.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt
git commit -m "ui: implement smooth label fading and horizontal arrangement for nav tabs"
```

---

## Task 4: Verification and Final Polish

- [ ] **Step 1: Verify Settings Visibility**

Ensure the `Settings` option appears at the end of the scrollable list.

- [ ] **Step 2: Run existing navigation tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.AppNavigationSmokeUiTest"
```
*(Note: If UI tests require a physical device, verify via manual D-pad navigation on emulator/device)*

- [ ] **Step 3: Commit**

```bash
git commit --allow-empty -m "verify: navbar redesign verified via manual testing and unit tests"
```
