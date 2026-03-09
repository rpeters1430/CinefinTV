# CinefinTV App Upgrade Roadmap

**Date:** 2026-03-09  
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

## Phase A — Reliability and correctness (P0)

**Why first:** every downstream feature depends on this.

### A1. End-to-end regression sweep on real TV targets
- Validate full user journeys on at least one emulator + one physical device:
  - cold launch with saved session
  - auth failure and reconnect flows
  - browse → detail → playback (movie + episode)
  - music route to background audio player
- Capture a focused bug list with severity labels (critical/high/medium).

### A2. Playback hardening
- Add handling for edge states:
  - stream errors/timeouts
  - invalid saved positions
  - end-of-item transitions (episode autoplay boundaries)
- Ensure track selection states persist predictably while playing and after overlay reopen.

### A3. Session/network resilience
- Verify retry/backoff and circuit-breaker behavior under flaky network simulations.
- Improve user-visible error messages and retry affordances for auth and playback failures.

**Definition of done for Phase A**
- No critical playback/auth regressions in the smoke suite.
- Documented recovery behavior for key failure paths.

---

## Phase B — TV UX polish and navigation quality (P1)

### B1. Focus system QA and fixes
- Audit all major surfaces for D-pad consistency:
  - top tab row ↔ content transitions
  - carousel ↔ rows ↔ cards
  - detail action row, seasons/episodes, cast rail
- Remove hidden/low-visibility focus traps and ensure first focus targets are intentional.

### B2. Visual hierarchy and spacing polish
- Standardize spacing tokens for row headers, chips, rails, and edge insets.
- Verify contrast/readability for text states (focused/selected/disabled).
- Re-check large-title, metadata chip, and long-synopsis truncation behavior.

### B3. Home and discovery refinements
- Improve libraries entry behavior and discoverability from Home.
- Expand recommendation density (balanced rows by content type and recency).

**Definition of done for Phase B**
- Focus traversal feels “single-step predictable” across all top-level destinations.
- UI issues from QA are reduced to minor-only items.

---

## Phase C — Feature depth (P1/P2)

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
