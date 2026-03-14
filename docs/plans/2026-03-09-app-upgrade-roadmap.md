# CinefinTV App Upgrade Roadmap

**Date:** 2026-03-14 (updated)
**Purpose:** Consolidate what to implement/fix next now that MVP is stable.

---

## 1) Current baseline (what we are upgrading from)

The app has reached a solid MVP baseline: auth/session restore, home, library, search, detail, person detail, video playback, and dedicated audio playback are all implemented and passing core JVM checks. This roadmap focuses on lifting overall reliability, TV UX polish, and long-term maintainability.

---

## 2) Guiding goals for the next cycle

1. **Stability first:** eliminate regressions and tighten playback/session resilience.
2. **TV UX quality:** ensure predictable focus behavior and readability on 10-foot displays.
3. **Feature depth in high-impact areas:** detail, playback controls, and discovery.
4. **Operational maintainability:** smaller modules, better tests, clearer docs/release process.

---

## 3) Priority roadmap (ordered)

## Phase A — Reliability and correctness (P0) - [COMPLETED 2026-03-14]

- **Playback hardening**: Implemented automatic retry logic with exponential backoff for transient network failures. Optimized `LoadControl` with 50s buffers for 4K stability.
- **Session resilience**: Enhanced progress reporting and robust cleanup. Added `AnalyticsListener` for detailed error telemetry.
- **Regression sweep**: Added defensive `coerceAtLeast(0.dp)` to layout paddings to prevent reported `IllegalArgumentException` crashes.

---

## Phase B — TV UX polish and navigation quality (P1) - [COMPLETED 2026-03-14]

- **Design Tokens**: Standardized `CinefinSpacing` (gutter, gaps, corners) globally via `LocalCinefinSpacing`.
- **Unified Metadata**: Created `CinefinChip` component; unified metadata rows in Carousel and Detail screens.
- **Focus System**: Refined `TabRow` navigation (focus-to-navigate) and ensured consistent 1.1x focus scaling and glow effects on all media cards.
- **Detail Screen**: Consolidated metadata panels, fixed focus traps in subtitle selection, and refined layout for better readability.

---

## Phase C — Feature depth (P1/P2) - [NEXT]

### C1. Detail experience expansion
- Add richer metadata panels where available:
  - ratings/providers/runtime/year consistency
  - season and episode context improvements
- Improve related-content actions and quicker re-entry to playback.

### C2. Playback feature completion
- Subtitle and audio track UX refinements (discoverability + state feedback).
- Continue episode autoplay polish (skip intros/credits candidate hooks where server metadata supports it).

### C3. Music quality-of-life
- Queue affordances (up next visibility, quick queue actions).
- Better artist/album navigation loops and resume behavior.

---

## Phase D — Architecture, tests, and release discipline (P1)

### D1. Test strategy upgrade
- Expand unit coverage around:
  - Home mapping and fallback rendering
  - playback/autoplay transitions
  - track selection reducers/state handlers
- Add selective Compose UI tests for focus-critical components.

### D2. Refactor and module hygiene
- Continue breaking up oversized screens/viewmodels into focused components.
- Complete cleanup of legacy/encoding-corrupted files and reduce mixed-style patterns.

### D3. Release readiness workflow
- Introduce a lightweight release checklist:
  - smoke test matrix
  - changelog updates
  - known issues + mitigations
- Tag milestone cuts (e.g., `v0.2.0-beta-tv-polish`).

---

## 4) Recommended immediate sprint backlog (next 2 weeks)

1. **Run full TV QA sweep** and file prioritized defects.
2. **Fix top 5 focus/navigation defects** discovered in QA.
3. **Playback hardening pass** (error/retry/saved position edge cases).
4. **Ship first focused UI polish batch** (contrast + spacing + home libraries behavior).
5. **Add targeted tests** for autoplay and track selection state behavior.

---

## 5) Risks and dependencies

- **Real-device variance:** some focus/playback behaviors differ between emulator and hardware.
- **Jellyfin server differences:** metadata and transcoding behavior vary by server version/config.
- **Scope creep risk:** avoid mixing low-impact feature ideas into P0/P1 stabilization milestones.

---

## 6) Success metrics

- Crash-free and fatal playback-error rates improved release-over-release.
- Manual smoke pass completion rate reaches 100% for top user journeys.
- QA-reported focus/navigation defects reduced by at least 50% in the next milestone.
- Unit/UI test coverage increases in playback + home + navigation-critical logic.

---

## 7) Documentation updates to keep in sync

When roadmap work starts, update:
- `README.md` (Known gaps / next priorities)
- `docs/plans/2026-03-02-cinefintv-status.md` (Current priorities / next work)
- this roadmap doc with completed checkpoints and remaining blockers
