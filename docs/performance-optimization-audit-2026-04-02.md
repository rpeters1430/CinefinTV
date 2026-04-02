# CinefinTV Performance Optimization Audit (April 2, 2026)

## Goal
Improve Android TV smoothness, reduce frame drops/jank, and keep focus/navigation animations consistently fluid (target: stable 60fps on mid-tier TV hardware).

## What is already good
- Device-tiered image cache sizing already exists (`DevicePerformanceProfile` + Coil tuning), which is a strong foundation for low-memory devices.
- Most image loads already use bounded `ImageRequest.size(...)` instead of decoding full-resolution images.
- Compose screens already use lazy containers (`LazyColumn`, `LazyRow`, `LazyGrid`) with stable keys in several places.
- The player architecture already tracks dropped frames and playback diagnostics, so there is instrumentation to build on.

## Main bottlenecks identified

### 1) Too many per-frame / high-frequency full-tree recompositions in player UI
`PlayerPlaybackContent` receives a full `renderState` that updates frequently, and many UI branches derive values from it (skip logic, overlays, controls visibility). This likely triggers broad recompositions while video is playing.

**Optimization actions**
1. Split the player UI into finer composables with smaller state inputs:
   - `PlayerTopBadges` (HDR/speed)
   - `PlayerSkipActions`
   - `PlayerBottomControls`
   - `PlayerContentShelf`
2. Feed each child only the minimum primitive state it needs.
3. Use `derivedStateOf` for skip-intro/credits eligibility and avoid recomputing large `when` trees each tick.
4. Gate position updates while controls are hidden:
   - keep 250ms updates only when controls/scrubber are visible;
   - relax to 500-1000ms when hidden.

**Expected impact**: less Compose work per second during playback, smoother overlay animations.

---

### 2) Home screen can trigger many scroll/focus operations in quick succession
Home uses explicit `scrollToItem`, `animateScrollToItem`, repeated `withFrameNanos {}`, and several key handlers that can cascade focus + scroll behavior.

**Optimization actions**
1. Add a small focus-navigation throttle/debounce for repeated DPAD presses.
2. Centralize focus-restoration flow so only one pending focus operation can run at a time (cancel previous coroutine job).
3. Avoid redundant `scrollToItem` calls when target is already visible.
4. For section rows, precompute immutable `visibleItems` and avoid rebuilding transient lists in composition where possible.

**Expected impact**: fewer janky jumps on fast DPAD navigation and reduced main-thread bursts.

---

### 3) Per-card animations and visual effects are heavy in dense rows
`TvMediaCard` applies multiple layered effects (image + gradients + glow + overlays + color animations), multiplied across many cards in a horizontal row.

**Optimization actions**
1. Introduce a low/mid device animation policy:
   - low tier: disable glow layer, reduce scale delta, disable non-critical color animations;
   - mid tier: keep scale but reduce shadow/glow alpha.
2. Replace animated color transitions with instant color switches on low tier.
3. Reduce overlay draw passes where possible (merge gradient + focus overlay logic).
4. Limit offscreen row item count using smaller viewport buffers for image loading.

**Expected impact**: smoother row scrolling and focus transitions, especially on budget TV SoCs.

---

### 4) Motion preference exists but is not fully wired into global motion tokens
The app stores `respectReduceMotion`, but `CinefinMotion` is currently static and not clearly adapted from user/system animation preference.

**Optimization actions**
1. Convert motion tokens into a runtime `CinefinMotionSpec` derived from:
   - user preference (`respectReduceMotion`),
   - system animator duration scale,
   - device performance tier.
2. Route all `tween(...)` durations through this spec.
3. Provide a “reduced motion” profile (shorter or no non-essential transitions).

**Expected impact**: less perceived choppiness for users sensitive to motion and on lower-end devices.

---

### 5) Performance instrumentation exists but is fragmented
There are multiple monitoring helpers (`MainThreadMonitor`, `PerformanceMonitor`, player analytics), but no single release-oriented performance gate.

**Optimization actions**
1. Add a lightweight release checklist metric set:
   - home navigation jank %,
   - average dropped frames during 1080p playback,
   - frame time percentile (P50/P95).
2. Add Macrobenchmark module (startup + scroll + focus navigation + player overlay interactions).
3. Add Baseline Profile generation and install profile at build time.
4. Track regressions in CI thresholds (warn/fail bands).

**Expected impact**: catches jank regressions before release rather than after user reports.

---

### 6) Potential image/palette extraction pressure in hero surfaces
Featured hero performs palette extraction for high-tier devices and applies dynamic seed updates. This can still cause transient UI churn when large hero images load.

**Optimization actions**
1. Defer theme seed updates until image decode is complete and screen is idle.
2. Add deduplication window so repeated hero focus changes do not trigger repeated theme churn.
3. Consider updating seed only when selected featured item changes, not for every transient load event.

**Expected impact**: fewer visual hitches when entering home and rotating featured content.

## Priority roadmap

### Phase 1 (fast wins, 1-2 days)
1. Player recomposition split + lower-frequency hidden-controls updates.
2. Home focus/scroll operation dedupe.
3. Low-tier card animation simplification toggles.

### Phase 2 (1 week)
1. Global motion spec + reduce-motion wiring.
2. Hero palette extraction deferral and dedupe.
3. Add debug overlay for frame time + recomposition counters in critical screens.

### Phase 3 (1-2 weeks)
1. Macrobenchmark module.
2. Baseline Profile generation + packaging.
3. CI performance thresholds and release checklist automation.

## Suggested success criteria
- Home fast-navigation: no visible stutter for 30 seconds of rapid DPAD traversal.
- Player controls: no noticeable lag opening/closing controls during playback.
- Dropped frames: reduce dropped-frame bursts during overlay interactions by at least 30% on mid-tier device.
- 95th percentile frame time under 24ms in core interactions.

## Concrete next implementation tasks
1. Refactor `PlayerPlaybackContent` state slicing and throttled progress updates.
2. Add `FocusNavigationCoordinator` utility to serialize/cancel focus scroll jobs.
3. Add `CardVisualComplexityPolicy` based on performance tier.
4. Introduce `LocalCinefinMotionSpec` and use it in all animation specs.
5. Create `benchmark/` module with startup/home-scroll/player-controls benchmarks.
6. Add baseline profile generation script and Gradle wiring.
