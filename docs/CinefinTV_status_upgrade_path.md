# CinefinTV — Status, Bugs, and Upgrade Path

Generated from the uploaded `CinefinTV-main.zip` project on 2026-03-17.

## Executive summary

CinefinTV is **past MVP and into a beta-hardening stage**. The project has a strong foundation: modern Android stack, Hilt, Media3, paging, dedicated repositories, TV-specific Compose components, release automation hooks, Firebase integration, and a reasonably healthy test baseline.

The main story is **not missing core features**. The main story is:
1. **TV UX polish and focus behavior still need real-device cleanup**
2. **Some docs are now stale relative to the code**
3. **Architecture cleanup is incomplete because the old `JellyfinRepository` still coexists with the newer repo coordinator**
4. **Playback/subtitle/overlay behavior still needs targeted QA on hardware**

---

## What looks healthy right now

### Platform and tooling
- Modern Android/Kotlin project using Compose, Hilt, KSP, Media3, Retrofit/OkHttp, Firebase, DataStore, and JDK 21.
- `compileSdk = 36`, `minSdk = 26`, `targetSdk = 35`, version `1.2.5` / code `26`.
- Release signing is wired through Gradle properties/environment variables.
- The repo already has release-oriented docs and update infrastructure.

### App scope already implemented
The README and code indicate the app already supports:
- auth/session restore
- home feed
- library browsing
- search
- music browsing
- video playback
- audio playback
- updater/release flow

### Structure quality
- Package layout is fairly organized and large enough to count as a serious app, not a prototype.
- Codebase size is substantial: **154 Kotlin source files**.
- Test baseline exists: **15 JVM test files**.
- There is clear intent toward modular repos and coordinator-based injection.

---

## What is out of date or inconsistent

### 1) Status docs are too optimistic
The status snapshot and roadmap describe Phase A/B as complete and the app as beta-ready, but `bugs.txt` still lists multiple visible UX regressions in the home screen, tabs, detail screens, subtitle selection, and playback overlay.

### 2) Review doc has stale test claims
The review doc says several ViewModels had no tests, but the uploaded project now includes tests for:
- `DetailViewModel`
- `LibraryViewModel`
- `PersonViewModel`
- `SettingsViewModel`

That means the review doc is now partly historical and should be refreshed.

### 3) Firebase comment is misleading
`RemoteConfigModule.kt` says the TV app has no Firebase dependency, but the app clearly applies Google Services, Crashlytics, and Firebase Performance plugins, and binds `FirebaseRemoteConfigRepository`.

### 4) Architecture migration is incomplete
`PlayerViewModel` still injects both:
- `JellyfinRepositoryCoordinator`
- `JellyfinRepository`

That confirms the god-class reduction is **not finished**.

---

## Confirmed high-priority issues from the uploaded project

These are the issues I would treat as the real next wave of work.

### A. TV navigation and first-focus behavior is still the biggest UX risk
Your own bug list strongly centers on:
- tabs auto-moving screens unexpectedly
- returning to the top of screens inconsistently
- detail screens initially focusing the wrong control
- some screens requiring extra D-pad presses before reaching Play
- subtitle popup focus still feeling difficult

This is the most important product issue because Android TV apps live or die on focus predictability.

### B. Playback UX still needs another pass
Open issues still include:
- subtitles selected but not appearing during playback
- popup/overlay selection UI too large
- subtitle selection UX still awkward
- continued playback smoothness noted as only partially mitigated in the review

Even if the player is technically functional, this is still a high-risk area for perceived quality.

### C. Home screen polish is not done
Your bug list still flags:
- oversized carousel height
- carousel timing needs adjustment
- dark/black section headings are unreadable
- left-edge media card clipping
- carousel metadata still showing unwanted collection count text

These are visible first-impression issues and should be batched together.

### D. Architecture debt still exists
The clearest remaining architecture item is the old `JellyfinRepository` staying alive beside the newer specialized repo stack. That keeps the dependency graph harder to test and reason about.

### E. Documentation drift is now a real maintenance problem
The repo has good docs, but several are out of sync with current implementation and current bugs. That makes planning less trustworthy.

---

## Current project status I would assign

### Overall status
**Beta-hardening / polish phase**

### By area
- **Core functionality:** Strong
- **Playback capability:** Good, but not fully hardened
- **TV UX / focus quality:** Medium, still the main blocker to a polished release
- **Architecture:** Good direction, incomplete cleanup
- **Tests:** Better than docs imply, but still missing UI/focus confidence
- **Release discipline:** Good
- **Documentation accuracy:** Needs refresh

### Release readiness
- **Internal / enthusiast beta:** Yes
- **Broader public-facing “polished TV app” release:** Not yet

---

## Recommended upgrade path

## Phase 1 — Fix what users feel immediately (do this first)

### Step 1: Run a “TV focus contract” pass
Create one source of truth for how each screen should behave on entry and re-entry.

For every major screen define:
- what gets first focus on initial load
- what gets first focus when returning from a child screen
- what happens when D-pad up reaches the top anchor
- what happens when focus transitions between tabs and content

Target screens:
1. Home
2. Movies library
3. TV library
4. Collections library
5. TV show detail
6. season detail
7. episode detail
8. movie detail
9. collections detail
10. player subtitle/audio popups

**Deliverable:** a small `docs/focus-contract.md` or similar spec.

### Step 2: Fix the tab row before anything else UI-related
Your tab row sounds like the highest-friction navigation issue.

Actions:
- stop focus movement from auto-triggering route changes unless intentionally selected
- ensure moving up from content into tabs does not silently switch screens
- ensure left/right tab traversal is deterministic
- move Search between Music and Settings if that is your intended IA
- test on emulator and physical TV hardware

**Why this is first:** if tabs are unstable, the whole app feels broken even when screens are otherwise fine.

### Step 3: Fix first-focus entry on all detail and library screens
From your bug list, several screens are not entering at the top as intended.

Actions:
- standardize top anchor behavior
- ensure initial focus goes to the intended hero/top action, not arbitrary cached controls
- for season detail, change default action from Back to Play Season
- for TV show detail, decide whether first focus should be top anchor or Play Show and enforce it consistently

### Step 4: Home screen readability and layout pass
Treat these together as one polish batch:
- reduce carousel height
- slow carousel auto-advance slightly
- remove unwanted “5 Collections” text if not needed
- fix section heading color tokens so headings are never black on dark backgrounds
- fix left-edge card clipping/padding

This is a fast visible win.

---

## Phase 2 — Playback quality and subtitle reliability

### Step 5: Treat subtitles as a reliability bug, not just polish
“Subtitles selected but not appearing” is a real functional defect.

Actions:
- verify whether the selected subtitle track is actually applied in Media3
- verify embedded vs external subtitle behavior separately
- test direct play vs transcoded playback
- log subtitle track IDs, selected track state, and renderer state in debug builds
- test forced/default subtitle flags

**Success condition:** selecting a subtitle always produces visible subtitles when the stream contains a compatible subtitle track.

### Step 6: Rebuild the track-selection popup for TV ergonomics
Actions:
- reduce popup size
- tighten row height and spacing
- make focused option more visually obvious
- ensure focus lands on current selected track
- allow a quick escape/back behavior without losing player state

### Step 7: Finish playback QA matrix
Test combinations such as:
- movie direct play
- movie transcode
- TV episode resume
- next episode autoplay
- home video / collections playback
- subtitle switching during playback
- audio track switching during playback
- 4K/high bitrate content

Document failures in one place instead of mixing them between docs and freeform notes.

---

## Phase 3 — Resolve the remaining architecture debt

### Step 8: Finish migrating off `JellyfinRepository`
This is the most obvious code-health upgrade.

Actions:
- inventory every remaining use of `JellyfinRepository`
- move methods into specialized repos (`media`, `stream`, `search`, `user`, `auth`) or coordinator-backed abstractions
- remove direct `JellyfinRepository` injection from `PlayerViewModel`
- delete the old repo once call sites are gone

**Expected outcome:** easier testing, smaller responsibilities, less duplicated logic.

### Step 9: Audit repository boundaries
While doing the migration, make a simple rule set:
- stream/playback logic only in stream/playback repos
- library/detail fetches only in media repo
- auth/session only in auth repo
- search only in search repo

This prevents the god-class problem from returning under a different name.

### Step 10: Remove doc drift in the same PR wave
Once the architecture pass is done, refresh:
- `README.md`
- `docs/plans/2026-03-02-cinefintv-status.md`
- `docs/plans/2026-03-09-app-upgrade-roadmap.md`
- `docs/2026-03-16-review-bugs-and-improvements.md`
- `bugs.txt`

The docs should reflect current truth, not last week’s truth.

---

## Phase 4 — Testing strategy upgrade

### Step 11: Keep the existing JVM tests, add TV-critical UI tests
You already have a decent ViewModel/unit-test baseline. The next missing confidence layer is UI behavior.

Add Compose UI tests for:
- tab row navigation
- first focus on each major screen
- player popup focus behavior
- returning from detail/player to previous screen
- library initial scroll position/top anchor behavior

### Step 12: Add a manual smoke checklist specifically for TV navigation
A release should not go out until someone manually confirms:
- top tabs work correctly
- no initial focus traps
- back button behavior is consistent
- subtitles work
- play button exists from Home Continue path for Collections/home videos

This can extend `docs/RELEASE_CHECKLIST.md`.

---

## Phase 5 — Product polish after the blockers are gone

Only after the above is stable:

### Step 13: Enrich metadata presentation
For movie/episode/collection detail pages:
- add richer technical metadata
- add symbols/icons for runtime, resolution, audio/subtitle availability, year/status
- improve “ended” vs “present” series labeling

### Step 14: Improve discovery loops
- better resume/re-entry paths
- related content actions
- cleaner collections/home-videos presentation

### Step 15: Consider dynamic theming only after playback and navigation are stable
The app is already using expressive color infrastructure. Keep theming work behind UX stability work.

---

## Recommended issue priority order

### P0 — do now
1. Tab row navigation behavior
2. Initial focus/top-anchor consistency across screens
3. Subtitle selected but not rendering
4. Missing Play button on Collections detail from Continue Watching path
5. Home readability/layout issues (black text, clipping, carousel height)

### P1 — next
6. Subtitle/audio popup redesign for TV
7. Finish playback QA matrix and edge-case fixes
8. Complete `JellyfinRepository` migration
9. Refresh outdated docs to match reality
10. Add Compose UI tests for navigation/focus

### P2 — after stability
11. Richer metadata on detail screens
12. Better series ended/present labeling
13. Discovery and detail polish
14. Additional theming and presentation refinements

---

## Suggested step-by-step execution order for you

If you want the cleanest path forward, do the work in exactly this order:

1. **Create one tracking doc** for current bugs and statuses.
2. **Fix tab-row behavior**.
3. **Fix first-focus/top-anchor behavior** on every main screen.
4. **Fix Collections Continue Watching -> missing Play button**.
5. **Fix subtitle rendering reliability**.
6. **Redesign subtitle/audio popup UX**.
7. **Polish Home screen readability and carousel layout**.
8. **Run a real-device playback/navigation regression sweep**.
9. **Complete migration away from `JellyfinRepository`**.
10. **Refresh all planning/status docs**.
11. **Add Compose UI tests for focus and nav**.
12. **Then do metadata/detail polish**.

---

## My direct recommendation

If you only pick **three** things next, pick these:

1. **Tab/focus/navigation cleanup**
2. **Subtitle/playback reliability**
3. **Repository/doc cleanup after the UX bugs are fixed**

That sequence gives you the biggest user-visible quality improvement while also reducing long-term maintenance pain.

---

## Important note about this review

This review is based on:
- the uploaded source tree
- README / changelog / roadmap docs
- the bug review doc
- the raw `bugs.txt` list
- spot inspection of key Kotlin files

I did **not** run the app or perform device playback testing in this pass, so UI/runtime bug items are prioritized from the project’s own notes plus code structure, not from a live execution session.
