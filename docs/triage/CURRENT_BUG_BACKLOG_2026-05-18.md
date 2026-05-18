# CinefinTV Current Bug & Improvement Backlog

**Generated:** 2026-05-18  
**Source:** Uploaded `CinefinTV-main(5).zip`, current docs, project structure, and `new_logcat.txt.txt`.

This is not a complete code audit. It is the first structured backlog for the new issue system. Convert each item into a GitHub issue using the labeling and acceptance-criteria guidance in `docs/BUG_AND_IMPROVEMENT_SYSTEM.md`.

---

## P0 / Critical

### CTV-P0-001 — Investigate possible cold-start crash / activity channel disposal

**Labels:** `type: bug`, `severity: critical`, `area: startup`, `source: logcat`, `needs: repro`, `needs: logs`  
**Evidence:** `new_logcat.txt.txt` contains `InputDispatcher ... Channel is unrecoverably broken and will be disposed!` after app startup activity logs.  
**Why it matters:** This may be a normal process/activity termination, but if it corresponds to the app disappearing, freezing, or restarting during cold startup, it is release-blocking.

**Reproduction target:**

1. Cold launch CinefinTV on a real Android TV device.
2. Observe whether the app becomes unresponsive, exits, or restarts around initial home load.
3. Capture full logcat from app start through failure.

**Acceptance criteria:**

- [ ] Determine whether the channel disposal is benign or tied to a user-visible failure.
- [ ] If user-visible, identify the preceding exception, ANR, lifecycle loop, or process kill.
- [ ] Add either a fix or a documented explanation with filtered logcat evidence.

---

## P1 / High

### CTV-P1-001 — Repeated `validateServer` calls during startup/home load

**Labels:** `type: bug`, `severity: high`, `area: performance`, `area: startup`, `source: logcat`, `ready: implementation`  
**Evidence:** `new_logcat.txt.txt` shows dozens/hundreds of `RepositoryUtils validateServer: Server validation passed...` messages within seconds.  
**Likely impact:** Unnecessary CPU/log spam, possible repeated repository calls, slower cold startup, harder debugging.

**Investigation notes:**

- `RepositoryUtils.validateServer()` logs a verbose success every time a repository operation validates server state.
- Many repository methods call `validateServer()` directly.
- The repeated pattern may be normal parallel home-section loading, but the current logging makes it impossible to tell the difference between expected validation and accidental reload loops.

**Suggested implementation options:**

- Remove success-level verbose logging from `validateServer()` or throttle it.
- Add request-level tracing around home startup loads instead of logging every server validation.
- Check Home startup flows for repeated `LaunchedEffect` triggers or duplicate refresh calls.

**Acceptance criteria:**

- [ ] Cold startup logcat no longer spams identical successful validation messages.
- [ ] If repeated network loads remain, create separate issue with exact source.
- [ ] Startup still restores session and loads Home correctly.

---

### CTV-P1-002 — TV focus contract must be enforced across major screens

**Labels:** `type: bug`, `severity: high`, `area: tv-focus`, `source: code-review`, `ready: implementation`  
**Evidence:** Current docs repeatedly identify first-focus and D-pad behavior as the highest TV UX risk. Existing UI tests cover some screens, but the app still has many focus-sensitive screens.

**Screens:**

- Home
- Movies library
- TV library
- Collections
- Search
- Movie detail
- TV show detail
- Season/episode detail
- Person detail
- Player controls
- Settings

**Acceptance criteria:**

- [ ] Each major screen has documented initial focus and return-focus behavior.
- [ ] UI tests cover at least Home, Library, Detail, Search, and Player focus basics.
- [ ] Back from player returns to expected screen and focus target.
- [ ] No screen requires hidden/unintuitive extra D-pad presses to reach the primary action.

---

### CTV-P1-003 — Playback subtitle reliability pass

**Labels:** `type: bug`, `severity: high`, `area: playback`, `needs: repro`, `needs: logs`  
**Evidence:** Historical status docs mention subtitle rendering edge cases and subtitle selection UX as unresolved playback risk.

**Reproduction matrix:**

- Direct play + SRT
- Direct play + ASS/SSA
- Transcode + SRT
- Transcode + embedded subtitles
- Subtitle switch while paused
- Subtitle switch while playing

**Acceptance criteria:**

- [ ] Subtitle track selected in UI matches active Media3/Jellyfin track.
- [ ] Selected subtitle renders or shows a clear unsupported-format message.
- [ ] Track popup returns focus to player controls predictably.
- [ ] Regression test or manual QA proof added to issue/PR.

---

### CTV-P1-004 — Playback direct play/transcode decision visibility

**Labels:** `type: improvement`, `severity: high`, `area: playback`, `area: performance`, `ready: implementation`  
**Problem:** When playback quality is wrong, it is hard to know whether CinefinTV, Media3, or Jellyfin selected direct play/direct stream/transcode.

**Acceptance criteria:**

- [ ] Add a debug-only playback diagnostics panel or structured log line with playback mode, container, codec, bitrate, resolution, selected URL type, and server decision.
- [ ] Diagnostics redact tokens and private URLs.
- [ ] Playback bug template points users to the diagnostic data.

---

## P2 / Medium

### CTV-P2-001 — Split very large UI files into smaller components

**Labels:** `type: refactor`, `severity: medium`, `area: architecture`, `area: home`, `area: detail`, `size: large`  
**Evidence:** Current file sizes include `HomeScreen.kt` at about 1,013 lines and `DetailScreenComponents.kt` at about 1,005 lines.

**Why it matters:** Large Compose files make focus bugs, recomposition issues, and visual regressions harder to isolate.

**Acceptance criteria:**

- [ ] Extract stable sections/components without changing behavior.
- [ ] Preserve existing test tags.
- [ ] Add or update screenshots/manual QA notes for Home and Detail screens.

---

### CTV-P2-002 — Dependency stability audit

**Labels:** `type: improvement`, `severity: medium`, `area: release`, `area: architecture`, `source: code-review`  
**Evidence:** `gradle/libs.versions.toml` includes multiple alpha/beta dependencies such as Navigation3 alpha, DataStore alpha, lifecycle beta, Coil beta, core alpha, benchmark alpha, UIAutomator beta.

**Acceptance criteria:**

- [ ] Create a dependency table: current version, stable option, reason for alpha/beta, migration risk.
- [ ] Downgrade/upgrade to stable where practical.
- [ ] Keep alpha dependencies only where there is a clear feature reason.
- [ ] Document remaining pre-release dependencies in release notes.

---

### CTV-P2-003 — Documentation drift cleanup

**Labels:** `type: docs`, `severity: medium`, `area: release`, `source: code-review`, `ready: implementation`  
**Evidence:** Current docs include status snapshots from March and May with different app versions, different project states, and some stale claims.

**Acceptance criteria:**

- [ ] Keep one current status doc and mark older docs as historical.
- [ ] Update release readiness language to match current issues.
- [ ] Link this bug/improvement system from README and release checklist.

---

### CTV-P2-004 — GitHub issue automation follow-through

**Labels:** `type: improvement`, `severity: medium`, `area: release`, `area: architecture`  
**Problem:** The repo has powerful Gemini workflows, but issue quality determines whether AI-assisted implementation is safe and useful.

**Acceptance criteria:**

- [ ] Confirm `@gemini-cli /triage`, plan, `/approve`, and `/review` still work with the new issue templates.
- [ ] Add an example issue comment snippet to docs.
- [ ] Make sure planned implementation always references acceptance criteria.

---

## P3 / Low / Polish

### CTV-P3-001 — Make playback overlay and track-selection UX easier on TV

**Labels:** `type: improvement`, `severity: low`, `area: playback`, `area: tv-focus`  
**Acceptance criteria:**

- [ ] Track popup size and focus behavior are comfortable from a couch distance.
- [ ] D-pad entry/exit is predictable.
- [ ] Back closes popup before exiting player.

---

### CTV-P3-002 — Home screen visual polish audit

**Labels:** `type: improvement`, `severity: low`, `area: home`, `area: tv-focus`  
**Acceptance criteria:**

- [ ] Featured carousel height and timing feel natural.
- [ ] Section headings are readable on dark/bright artwork.
- [ ] First and last row items are not clipped.
- [ ] Missing artwork fallbacks look intentional.

---

## Suggested first GitHub issues to open

1. **[Performance]: Repeated validateServer logs during cold startup** from `CTV-P1-001`.
2. **[TV Focus]: Define and enforce focus contract across major screens** from `CTV-P1-002`.
3. **[Playback]: Subtitle selected but not rendered / unsupported subtitle behavior** from `CTV-P1-003`.
4. **[Bug]: Investigate InputDispatcher unrecoverable channel disposal after startup** from `CTV-P0-001`.
5. **[Docs]: Mark historical docs and adopt the bug/improvement system** from `CTV-P2-003`.

---

## Notes from local validation

I attempted to run Gradle, but this environment cannot download the Gradle wrapper distribution because internet access is unavailable. The command failed while trying to fetch `https://services.gradle.org/distributions/gradle-9.4.1-bin.zip`. Run the following locally after applying these files:

```bash
./gradlew test
./gradlew assembleDebug
```
