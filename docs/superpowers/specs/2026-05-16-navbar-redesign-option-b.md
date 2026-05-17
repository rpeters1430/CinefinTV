# Design Spec: Side Navbar Redesign (Option B)

**Date**: 2026-05-16
**Status**: Approved
**Topic**: Redesigning the side navigation bar into a smooth, expanding drawer that ensures visibility of all navigation items, including Settings.

## 1. Goal
Replace the current "messy" side navbar with a modern, expanding drawer.
- **Smooth Animation**: Synchronize width expansion and label fading using a single animation state.
- **Guaranteed Visibility**: Ensure all items in `navTabItems` (especially Settings) are rendered and reachable.
- **Clean UI**: Simplify the nested layout structure to improve focus handling and performance.
- **Icon-Only Closed State**: Maintain a minimal footprint when not focused by showing only icons.

## 2. Current State
- **Implementation**: Custom `Box`/`Column` structure in `CinefinAppScaffold` (`CinefinTvApp.kt`).
- **Issues**:
    - **Janky Animations**: Multiple independent animators creating unsynced transitions.
    - **Clipped Content**: Fixed width and height constraints likely causing the "Settings" option to be missing or hidden.
    - **Conditional Labels**: Labels only show when a tab is both selected and focused, making navigation confusing.
    - **Static Width**: The rail slot width is effectively static, wasting screen space when only icons are needed.

## 3. Proposed Design

### 3.1 Animation & Transitions
- **`railProgress`**: A single `Float` state (0f to 1f) driven by `navHasFocus`.
- **Width**: Animate `railWidth` from `80.dp` (collapsed) to `200.dp` (expanded).
- **Alpha**: Labels will have their alpha tied directly to `railProgress` (or a slightly delayed curve) to fade in smoothly as the drawer opens.
- **Icon Position**: Icons will remain centered in the collapsed state and shift to the left in the expanded state.

### 3.2 Layout Structure
- **Container**: A single `Surface` with a dynamic width.
- **Scrollability**: Wrap the nav items in a `Column` with `verticalScroll(rememberScrollState())` to handle overflow on small screens.
- **Settings**: Explicitly verify that the `navTabItems.forEachIndexed` loop includes the `Settings` item and that it's not being clipped by parent height limits (`fillMaxHeight()` on a scrollable container).

### 3.3 Visual Polish
- **Selected State**: Use a distinct background and border for the selected tab.
- **Focus Ring**: Maintain the high-contrast focus ring for D-pad navigation.
- **Background**: Semi-transparent dark surface with a subtle border to separate it from the content area.

## 4. Implementation Plan
1.  **Refactor Constants**: Define `COLLAPSED_RAIL_WIDTH` and `EXPANDED_RAIL_WIDTH`.
2.  **Update `CinefinAppScaffold`**:
    - Replace the multiple `animateDpAsState` calls with a single `animateFloatAsState`.
    - Update the `width` of the rail and its slot to be dynamic.
    - Implement `verticalScroll` for the items column.
3.  **Refactor Tab Items**:
    - Simplify the `Button` content.
    - Ensure labels are visible whenever `railProgress > 0`.
4.  **Verification**:
    - Confirm "Settings" is visible at the bottom of the list.
    - Verify smooth expansion/collapse on D-pad left/right.

## 5. Testing Strategy
- **Manual D-pad Testing**: Verify focus moves smoothly between tabs and into/out of the rail.
- **Visual Regression**: Confirm that labels fade correctly and the width transition is fluid.
- **Scroll Test**: Add dummy items temporarily to ensure vertical scrolling works if Settings were to be pushed off-screen.
