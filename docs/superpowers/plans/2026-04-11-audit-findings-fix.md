# Audit Findings Implementation Plan (2026-04-11)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

## 🎯 Goals
- Fix the subtitle selection regression in the player.
- Standardize TV text sizes for 10-foot legibility compliance.
- Optimize player performance by reducing progress-related recompositions.
- Reduce architectural debt by migrating legacy repository usages.

## 🛠 Tasks

- [ ] **Task 1: Fix Subtitle Selection in PlayerViewModel**
    - Pass `selectedSubtitleTrack?.streamIndex` to `resolvePlaybackUrl` in `loadInternal()`.
    - Ensure `reloadStream` also correctly passes the subtitle index.
    - **Verification:** Log `EnhancedPlaybackManager` decision to confirm `subtitleStreamIndex` is non-null when a track is selected.

- [ ] **Task 2: Standardize Text Sizes in TvMediaCard**
    - Update `title` style to use `18.sp` (standard) and `16.sp` (compact).
    - Update `subtitle` style to use `16.sp` (standard) and `14.sp` (compact).
    - **Verification:** Visual check or unit test asserting `fontSize` values.

- [ ] **Task 3: Optimize Player Recomposition (Position Provider)**
    - Refactor `PlayerControls` to take `positionProvider: () -> Long` instead of `position: Long`.
    - Update `SeekBar` to use this provider and wrap progress calculations in `derivedStateOf`.
    - Update `PlayerScreen` to stop passing the rapidly changing `renderState.position` directly.
    - **Verification:** Observe recomposition counts in Layout Inspector (if available) or verify no regressions in seeker behavior.

- [ ] **Task 4: Migrate Legacy Repository Usages (Core/Data)**
    - Update `ErrorHandler.kt` to use `JellyfinRepositoryCoordinator` if applicable, or remove dependency on `JellyfinRepository`.
    - Audit `ApiModels.kt` for any logic that can be moved to the domain layer.
    - **Verification:** Project builds without errors; legacy grep count decreases.

## 🧪 Validation Strategy
- **Unit Tests:** Run existing `PlayerViewModelTest` and `TvMediaCardTest`.
- **Manual Verification:** 
    - Verify subtitles appear on transcoded streams.
    - Verify D-pad seeking still works accurately.
    - Build check: `./gradlew :app:assembleDebug`.
