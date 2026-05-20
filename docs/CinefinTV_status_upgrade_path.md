# CinefinTV — Status, Bugs, and Upgrade Path

Updated: 2026-05-18 (v2.0.2 Pass)

## Executive summary

CinefinTV has moved from "beta-hardening" into a **production-ready architecture phase**. The foundation is now robust: modern Android stack, Hilt, Media3, and a fully decoupled repository architecture. Recent sprints have eliminated major UX focus traps, modernized the logging system for performance, and standardized coroutine management.

The main story now is **maintaining stability and metadata enrichment**.

---

## What looks healthy right now

### Platform and tooling
- Modern Android/Kotlin project using Compose, Hilt, KSP, Media3, Retrofit/OkHttp, Firebase, DataStore, and JDK 21.
- `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`, version `2.0.2` / code `103`.
- Production-grade coroutine management via `DispatcherProvider`.

### App scope already implemented
- **Authentication:** Proactive re-auth, session restoration, and multi-profile support.
- **Home:** Dynamic carousels, "Continue Watching", and "Next Up" with background refresh.
- **Library:** Paged browsing for Movies, TV, and Collections with debounced updates.
- **Playback:** Media3-based video/audio with "Skip Intro" support and hardware-accelerated FFmpeg.
- **Infrastructure:** Secure logging, intelligent caching, and self-update system.

### Structure quality
- **Architecture:** Fully migrated to `JellyfinRepositoryCoordinator`. The legacy `JellyfinRepository` god-class has been successfully removed.
- **Testing:** 27 test files covering core ViewModels and navigation logic.
- **Naming:** Unified Jellyfin-aligned nomenclature (e.g., "Collections" instead of "Stuff").

---

## Current project status

### Overall status
**Production Stability / Feature Expansion**

### By area
- **Core functionality:** Strong & Hardened
- **Playback capability:** Strong (Subtitles and 4K stability resolved)
- **TV UX / focus quality:** High (Focus contract implemented and enforced)
- **Architecture:** Excellent (Fully decoupled, testable repos)
- **Tests:** Solid baseline, expanding UI coverage
- **Documentation:** Recently synchronized with implementation

### Release readiness
- **Internal / enthusiast beta:** ✅ Ready
- **Public "polished TV app" release:** ✅ Ready for initial rollout

---

## Confirmed Next Steps

### 1. Metadata Enrichment (Phase 5)
- Add symbols/icons for resolution (4K, HDR), audio (Dolby Atmos, 5.1), and subtitle availability.
- Improve "Ended" vs "Running" series status on detail screens.

### 2. Testing Expansion
- Increase Compose UI test coverage for complex focus scenarios.
- Implement automated playback regression suite using `media3-test-utils`.

### 3. Polish & Aesthetics
- Refine detail screen animations.
- Implement more expressive dynamic theming based on item backdrops.

---

## Important note about this update

This status report reflects the state of the codebase after the May 2026 audit and subsequent four implementation sprints. All previously flagged critical architecture debt (`JellyfinRepository` god-class, "Stuff" naming) has been resolved.
