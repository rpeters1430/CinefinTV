# CinefinTV — Improvement Plan & Feature Roadmap

**Date:** 2026-06-11
**Baseline:** v2.1.6 (versionCode 117) · 229 Kotlin files · ~47,300 LOC · 48 test files
**Companion doc:** `2026-06-11-bug-improvement-audit.md`

This is two things: a phased plan to harden the project itself, and a ranked set of new features grounded in what the codebase already supports. Feature ranking reflects how much plumbing already exists versus how much is net-new.

---

## Part A — Project improvement plan

The app is past beta and architecturally solid (standardized repositories, Paging3, a focus coordinator, full ViewModel test coverage). The work now is closing the maturity gap before piling on features. Four phases, roughly in order.

### Phase 1 — Ship-readiness (the audit's open High/Medium)
Don't start new features until the release branch is clean:
- Align `compileSdk` off preview 37 to a stable level (`BUILD-4`). TV apps are exempt from the API-36 target rule, so `targetSdk 35` stays.
- Move `navigation` / `navigation3` / `datastore` off alpha or document the pins (`BUILD-2`).
- Centralize the duplicated D-pad focus-retry hack through `FocusNavigationCoordinator` (`FOCUS-1`).
- Add stable keys to the main library grid (`KEYS-1`) and finish the `SecureLogger` sweep in `data/security/*` (`L-1`).

### Phase 2 — Performance & startup
A `macrobenchmark` module already exists — make it earn its keep:
- Generate and ship **Baseline Profiles** for cold-start and the home→detail→player path. On low-power TV SoCs this is the single biggest perceived-speed win.
- Add a **startup benchmark** to CI so regressions are caught (you already have the profileinstaller dependency).
- Audit image loading: confirm Coil is downsampling backdrops to display resolution rather than decoding full 4K artwork into memory on the home carousel.
- Profile recomposition on `HomeScreen` and the player (the god-files), since those are the hottest surfaces.

### Phase 3 — Architecture debt paydown
- Decompose the player trio (`PlayerControls` 1,343 / `PlayerScreen` 1,096 / `PlayerViewModel` 1,120). Extract playback-orchestration concerns out of the ViewModel and split overlays (skip, next-up, track panel) into self-contained composables, mirroring the detail-screen decomposition you already did.
- Establish a single focus-management contract so future screens don't reintroduce inline timing hacks.

### Phase 4 — Resilience & observability
- Crashlytics + Performance are wired; add **custom traces** around playback-start, server-discovery, and library-load so you can see real-world latency by device.
- Harden the **offline/degraded-network** path: a cached "last known home" so the app opens to content instead of a spinner when the server is briefly unreachable.

---

## Part B — New features (ranked)

| # | Feature | Value | Effort | Plumbing already present |
|---|---|---|---|---|
| 1 | Live TV + Guide (EPG) | High | L | Account-policy flags only |
| 2 | Kids Mode / Parental Lock | High | M | `maxParentalRating` + profile switching |
| 3 | Ambient / screensaver mode | High | M-S | None |
| 4 | Subtitle styling | Med-High | M | None |
| 5 | Synced music lyrics | Med | M | Audio player + queue |
| 6 | Smarter Home discovery rows | Med | S-M | Partial recommendations |

### 1. Live TV, Guide (EPG) & basic DVR
The biggest completeness gap. Your user-policy model already carries `enableLiveTvAccess`, `enabledChannels`, and `blockedChannels` — but there's no channel browsing, guide, or recording UI anywhere. Jellyfin's API fully supports Live TV (channels, programs, timers, recordings), so this is purely a client build-out.
- **MVP:** a channel grid with now/next, tap-to-play the live stream through the existing player.
- **Phase 2:** a scrollable program guide (time × channel grid — a natural fit for D-pad), program detail, and "record" / "record series" timers.
- **Gate** the whole section on `enableLiveTvAccess` so it hides for accounts without the permission.

### 2. Kids Mode / Parental Lock
You already deliver `maxParentalRating` from the server policy and `ProfilePickerViewModel.switchToProfile()` already switches users — so the hard parts are half-done. Add:
- A **PIN-locked profile** that filters every library/search/home row by parental rating (the server can also enforce this, but client filtering gives instant feedback).
- A simplified "kids" home (big artwork, no settings, no search-the-web), and a PIN gate to exit back to the adult profile.
- Optional: hide the in-app updater and server settings while in kids mode.
This is high family value for relatively contained effort since the rating data and switching mechanics exist.

### 3. Ambient / screensaver mode
A defining "premium 10-foot" feature that's completely missing. After an idle timeout on a non-playback screen, fade into a slow Ken-Burns slideshow of library backdrops with a clock and (if something's playing) now-playing info. Any D-pad press dismisses it.
- Reuses your existing backdrop image pipeline and a `delay`-based idle detector (you already have idle handling in the player controls).
- Make the timeout and on/off a setting. Low-to-medium effort, disproportionately high polish.

### 4. Subtitle styling
The player has a track panel but no subtitle **appearance** customization — a common accessibility ask. Add a styling sheet: font size, text color, background/opacity, edge style, and vertical position, with a live preview. Media3's `SubtitleView` / `CaptionStyleCompat` supports all of this directly; persist via DataStore alongside your other player prefs.

### 5. Synced music lyrics
Your audio player already has the glassmorphism layout and a "Next Up" queue. Jellyfin 10.9+ serves lyrics (plain and time-synced LRC) via the lyrics endpoint. Add a lyrics pane that highlights the current line in sync with playback position — a genuine delight for the music side of the app, and it slots into the existing audio UI rather than needing a new screen.

### 6. Smarter Home discovery rows
You have some recommendation logic; lean into it to make Home feel alive:
- "Because you watched ___" rows (Jellyfin's similar-items endpoint).
- Per-library "Recently Added," prominent "Continue Watching," and genre-based rows.
- A lightweight **watchlist** row backed by Jellyfin favorites/playlists, with an "add to watchlist" action on detail screens.
Mostly data wiring over existing repositories — good incremental value per unit effort.

---

## Suggested order

Do **Phase 1** first (it's mostly the audit, and you don't want to build features on a preview SDK with alpha nav). Then ship **Ambient mode** and **Subtitle styling** early — they're self-contained, high-polish, and low-risk, so they're satisfying wins that don't touch the data layer. Tackle **Kids Mode** next (medium effort, plumbing exists), then the larger **Live TV** build, with **Baseline Profiles** (Phase 2) running in parallel since it's independent of feature work. **Lyrics** and **smarter Home rows** are good "filler" tasks between the bigger pushes.
