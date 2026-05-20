# Changelog

All notable changes to CinefinTV will be documented in this file.

## [2.0.2] - 2026-05-18

### Added
- **Android 13+ Support**: Added `POST_NOTIFICATIONS` permission to ensure media controls appear on modern Android TV devices.
- **Secure Logging System**: Implemented `SecureLogger` globally to protect user PII and improve performance on low-end hardware by omitting expensive trace logs in production.
- **Collection Support**: Formalized support for Jellyfin "Collections" (BoxSets) and "User Libraries" with dedicated detail and library views.

### Changed
- **Architectural Cleanup**: Renamed all "Stuff" nomenclature to "Collections/Collection" throughout the codebase and navigation routes.
- **Coroutine Standardization**: Standardized on injected `DispatcherProvider` across all repositories and managers for better testability and performance control.
- **Optimized Library Refresh**: Added debouncing to library updates to prevent UI stutter during rapid background data changes.
- **Modernized Installer**: Updated `UpdateManager` to use standard Android installation intents, removing deprecated API usage.
- **Theme Alignment**: Replaced hardcoded "NetflixRed" constants with themed Material 3 primary tokens.

### Fixed
- **Focus Issues**: Resolved duplicate `.focusable()` issue in `SeekBarControl` that caused "sticky" focus during playback.
- **Session Reliability**: Fixed `ensureSessionReady()` to correctly handle 5xx server errors, preventing the app from attempting to use broken server connections.
- **State Efficiency**: Optimized timestamp state in `HomeScreen` to use primitive-backed `mutableLongStateOf` instead of boxed-primitive state.
- **Build Integrity**: Aligned Kotlin and KSP versions to ensure stable symbol processing for Hilt.

## [0.2.0-beta] - 2026-03-14

### Added
- **Modernized Audio Player**: New glassmorphism design with high-res album art focal point and blurred backdrop.
- **Music Queue**: "Next Up" sidebar in the audio player for browsing and direct track selection.
- **Enhanced Video Player**: Dynamic "Skip Intro" and "Skip Credits" button overlays.
- **Standardized UI Tokens**: Introduced `CinefinSpacing` globally for consistent 10-foot padding and margins.
- **Unified Metadata**: New `CinefinChip` component for consistent metadata badges across Carousel and Detail screens.
- **First-Focus Strategy**: Predictable D-pad entry points for all major screens.

### Changed
- **Navigation Overhaul**: Fixed "messy" D-pad traversal with logical focus routing and centered list scrolling.
- **Detail Screen Refactor**: Decomposed 700-line file into modular, focused components (`DetailHeroSection`, `DetailActionRow`, etc.).
- **ViewModel Modularization**: Extracted models and mappers from `DetailViewModel` and `PlayerViewModel` for better maintainability.
- **Improved Buffering**: Configured aggressive 50s buffer for stable 4K playback on TV networks.

### Fixed
- **Stability**: Added defensive padding checks to prevent `IllegalArgumentException` crashes in media cards.
- **Playback Reliability**: Implemented automatic retry logic with exponential backoff for transient network failures.
- **Focus Traps**: Resolved focus issues in subtitle selection and navigation boundaries.

## [0.1.0-mvp] - 2026-03-09
- Initial MVP restoration with Auth, Home, Library, Search, and basic Video/Audio playback.
