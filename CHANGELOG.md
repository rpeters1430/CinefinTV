# Changelog

All notable changes to CinefinTV will be documented in this file.

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
