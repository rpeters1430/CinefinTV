# Design: Systemic D-pad Navigation Rebuild

Rebuilding the focus architecture to ensure consistent, bidirectional navigation across all 10-foot surfaces.

## 1. Systemic Infrastructure Fixes

### 1.1 `TvScreenFocusRegistry` (Route Pattern Matching)
The registry currently matches exact routes or simple prefixes. It needs to support parameterized routes like `detail/{itemId}` by treating `{itemId}` as a wildcard or allowing pattern matching.

**Proposed Change:**
Modify `requesterFor(route)` to normalize both registered and requested routes (e.g., stripping IDs) to ensure `detail/123` always finds the requester registered for `detail/{itemId}`.

### 1.2 Component Focus Delegation
Focusable components (`TvMediaCard`, `CinefinTextInputField`, `TvPersonCard`) currently apply focus modifiers to their containers. This must be fixed so the focus is applied directly to the focusable element.

**Proposed Change:**
- Move `Modifier.focusRequester()` and `.focusProperties` from the `StandardCardContainer` or `Surface` to the inner `Card`, `Button`, or `BasicTextField`.
- Pass focus-specific modifiers into the focusable child while keeping layout modifiers on the container.

## 2. Screen-by-Screen "Focus Map" Rebuild

### 2.1 Standardized Bidirectional Anchors
Every screen will implement the `TvScreenTopFocusAnchor` pattern with a clear "UP" path to the navigation bar.

**Implementation:**
- **UP from Anchor:** Move focus to the currently active Tab in `CinefinTvApp`.
- **DOWN from Anchor:** Move focus to the "Primary Content" (Carousel, Play Button, or List Item 0).

### 2.2 Screen Specific Rebuilds
- **HomeScreen:** Fix Hero Carousel focus delegation. Ensure UP from Carousel/First Row hits the Top Anchor.
- **DetailScreen:** Rebuild the Action Row so that `playButtonRequester` actually focuses the Button, not the Surface. Ensure UP from Action Row hits the Top Anchor.
- **SearchScreen:** Fix `CinefinTextInputField` focus so it's immediately focusable when navigating from the TabBar.
- **LibraryScreen:** Ensure the "Recently Added" shelf is correctly registered and navigable.

## 3. Success Criteria
- Navigation from TabBar to content never "loses" focus.
- Pressing UP from any screen's top-most element consistently returns focus to the TabBar.
- No "dead steps" where a D-pad press has no visible effect.
