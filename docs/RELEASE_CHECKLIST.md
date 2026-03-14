# CinefinTV Release Checklist

This checklist must be completed before tagging any formal release (e.g., `v0.2.0-beta`).

## 1. Automated Checks
- [ ] Build clean: `./gradlew clean assembleDebug`
- [ ] Unit tests pass: `./gradlew test` (Wait until tests are fully restored)
- [ ] Lint check: `./gradlew lintDebug` (No fatal errors)

## 2. Manual Smoke Tests (10-foot Experience)

### Authentication
- [ ] Fresh login via Server URL + Credentials.
- [ ] Quick Connect flow (if enabled on server).
- [ ] Session persistence: Kill app and restart; should land on Home.

### Browsing & Navigation
- [ ] Home: Hero carousel auto-scrolls and is focusable.
- [ ] Home: Browse rows (Continue Watching, etc.) scroll smoothly.
- [ ] Library: Movies/TV/Music grids load and allow focus navigation.
- [ ] Search: Keyboard works, results appear, and can be navigated.
- [ ] Detail: Play button is auto-focused on load.
- [ ] Detail: Seasons/Episodes/Cast shelves are accessible via D-pad.

### Playback
- [ ] Video: Standard playback starts within 5 seconds.
- [ ] Video: Play/Pause, Seek (10s), and Progress bar work correctly.
- [ ] Video: Audio/Subtitle track selection works and updates immediately.
- [ ] Video: "Skip Intro" button appears and functions when markers exist.
- [ ] Audio: Background playback works when navigating away from player.
- [ ] Audio: Music queue (Next Up) allows track switching.

## 3. Deployment & Release
- [ ] Update `app/build.gradle.kts` version code and name.
- [ ] Update `updates/version.json` for self-update mechanism.
- [ ] Update `CHANGELOG.md` with new features and fixes.
- [ ] Run `git tag -a vX.Y.Z -m "Release X.Y.Z"`
- [ ] Push tags: `git push origin --tags`
