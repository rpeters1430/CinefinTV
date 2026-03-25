# Design Spec: Navigation Tabs Redesign

**Date**: 2026-03-24
**Status**: Draft
**Topic**: Redesigning the main navigation bar to use standard TV Material 3 components to fix scrolling and focus issues.

## 1. Goal
The primary goal is to replace the current custom `Row`-based navigation bar in `CinefinTvApp.kt` with the standard `androidx.tv.material3.TabRow`. This change will ensure that:
- All navigation tabs are reachable, even on smaller screens or when many tabs are present (by enabling horizontal scrolling).
- Focus management is robust and follows TV Material 3 standards.
- Navigation routes are correctly triggered when tabs are selected.

## 2. Current State
The navigation bar is implemented as a custom `Row` inside a `Surface` in `CinefinAppScaffold`. It uses individual `Button` components for each tab. This implementation does not support scrolling, causing tabs like "Settings" to be cut off if they don't fit horizontally. Focus management is handled manually via `onFocusChanged`.

## 3. Proposed Design

### 3.1 Component Architecture
We will refactor `CinefinAppScaffold` in `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt` to use the following components:

- **`androidx.tv.material3.TabRow`**: The container for the tabs.
    - `selectedTabIndex`: Bound to the `selectedTabIndex` state passed to the scaffold.
    - `indicator`: Uses the standard TV Material 3 underline indicator.
- **`androidx.tv.material3.Tab`**: Individual items within the `TabRow`.
    - `selected`: Boolean indicating if the tab is currently selected.
    - `onFocus`: Triggers navigation or updates focus state when focused (TV pattern often uses "focus to select" or "click to select"). We will stick to "click to select" for consistency with existing navigation logic.
    - `onClick`: Calls `onNavigateToTab(item.route)`.

### 3.2 Focus and Scrolling
`TabRow` provides built-in support for:
- **Focus Traversal**: Standard D-pad left/right navigation between tabs.
- **Auto-Scrolling**: The `TabRow` will automatically scroll to keep the focused tab in view.

### 3.3 Aesthetic Changes
- We will move away from the current "floating pill" background for individual buttons.
- The navigation bar will use the standard `TabRow` styling, which aligns with the overall TV Material 3 theme of the app.
- We will maintain the use of `Icon` and `Text` labels for each tab for clarity.

## 4. Implementation Plan (High Level)
1.  Update imports in `CinefinTvApp.kt` to include `TabRow`, `Tab`, and related TV Material 3 components.
2.  Replace the `Row` and `Button` loop in `CinefinAppScaffold` with a `TabRow` and `Tab` loop.
3.  Ensure `onNavigateToTab` is correctly invoked on tab selection.
4.  Verify that the "Settings" tab is now visible and reachable via D-pad navigation.

## 5. Testing Strategy
- **Manual Verification**: Run the app on an emulator/device and confirm that all tabs can be focused and scrolled into view.
- **Navigation Check**: Ensure that clicking each tab correctly navigates to the corresponding screen and updates the `selectedTabIndex`.
- **Focus Check**: Verify that focus returns to the correct tab when navigating back from a detail screen (if applicable).
