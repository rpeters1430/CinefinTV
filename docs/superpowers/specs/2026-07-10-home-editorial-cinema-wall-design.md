# Home Editorial Cinema Wall Design

## Goal

Take the CinefinTV Home screen to a more premium TV discovery experience without rewriting navigation, playback routing, or the home data pipeline. The first viewport should feel curated and cinematic, while the existing D-pad focus behavior remains predictable on Android TV hardware.

## Current Context

`HomeScreen.kt` already provides:

- Featured carousel with backdrop artwork, metadata, Play and Details actions.
- Horizontal shelves backed by `HomeSectionModel`.
- Focus restoration after app resume and player return.
- Explicit D-pad navigation between the hero, shelves, and app chrome.
- UI test coverage for loading, error, hero actions, D-pad movement, and focus preservation.

The file is also large, so this work should extract focused composables where it directly serves the redesign. It should not introduce broad architecture changes.

## Design Direction

The approved direction is **Editorial Cinema Wall**.

The Home screen should feel like opening a curated cinema wall rather than browsing a flat feed. The hero becomes the visual anchor: richer title block, cleaner metadata, stronger artwork treatment, and progress-aware primary action copy. Below the hero, shelves should read with clearer priority: watch/resume content first, discovery content second, libraries available but quieter.

## User Experience

### First Viewport

When featured content exists, the first viewport contains:

- A full-width cinematic hero using the existing featured carousel data.
- A refined text block with title, compact metadata, subtitle, and description.
- Primary CTA text:
  - `Resume` when the featured item has playback progress.
  - `Play` otherwise.
- Secondary CTA remains `Details`.
- A compact discovery strip that summarizes the most important available sections.

The discovery strip is not a separate navigation system in the first implementation. It is an editorial summary that gives the home screen more depth while keeping D-pad navigation unchanged. It may show up to three priority section labels with counts or short descriptions, for example:

- `Continue Watching`
- `Next Episodes`
- `Recently Added Movies`

### Shelves

Shelves keep the existing horizontal card behavior and the existing `TvMediaCard` component. Visual hierarchy changes by section type:

- `Continue Watching` and `Next Episodes` are priority shelves and should emphasize progress or episode continuity.
- Recently added rows remain standard discovery shelves.
- `My Libraries` stays present but should not dominate the first experience when resume or next-up content exists.

The first implementation should not add "Show all" behavior. That can be a follow-up because it requires routing and section-level affordance decisions beyond the Home visual lift.

### Loading And Error States

Loading and error states keep the current behavior and test tags. Minor copy or spacing polish is acceptable only if it does not change the state machine or focus behavior.

## Component Plan

Keep `HomeScreenContent` as the test-facing entry point. Extract private composables from `HomeScreen.kt` or move them into focused files if the implementation stays clean.

Proposed components:

- `HomeHero`: owns the first-viewport layout for the carousel item.
- `HomeHeroMetadata`: formats and renders year, runtime, community rating, official rating, media quality, and item type.
- `HomeDiscoveryStrip`: renders a compact, non-focusable editorial summary derived from `HomeSectionModel`.
- `HomeShelf`: extracted version of the current section row behavior.

The current `FeaturedCarousel` may remain as the carousel wrapper, but its item content should delegate to the new hero components.

## Data Flow

No repository API changes are required.

Use existing `HomeUiState.Content`:

- `featuredItems` drive the hero.
- `sections` drive shelves and the discovery strip.
- `HomeCardModel.playbackProgress` drives `Resume` versus `Play`.
- Existing metadata fields drive the hero metadata strip.

Any helper that derives section priority should be pure and locally testable where practical.

## Focus And Navigation Contract

The redesign must preserve these behaviors:

- Initial content focus lands on the featured primary action when featured items exist.
- Pressing D-pad down from the featured primary action moves to the first shelf item.
- Pressing D-pad up from the first shelf moves back to the hero when featured content exists.
- Pressing D-pad left from the left edge still escapes to app chrome.
- Returning from player preserves the previously focused shelf item.
- Equivalent content refresh keeps the focused item focused.

The discovery strip should not introduce focus targets in this iteration. It is visual context only.

## Visual System

Use the existing CinefinTV theme primitives:

- `MaterialTheme.colorScheme`
- `LocalCinefinExpressiveColors`
- `LocalCinefinSpacing`
- `CinefinChip`
- `CinefinShelfTitle`
- `TvMediaCard`
- `ImmersiveBackground`

Avoid a one-note palette. The hero should remain dark and cinematic, but it should use existing expressive accent colors for hierarchy and focus states rather than adding a new unrelated color system.

The signature visual element is the editorial discovery strip: a quiet, premium "what is waiting for you" layer that makes the home screen feel curated without adding navigation complexity.

## Testing

Follow test-first implementation for behavior changes.

Add or update UI tests for:

- Hero primary CTA renders `Resume` when `playbackProgress` exists.
- Hero primary CTA renders `Play` when no playback progress exists.
- Discovery strip renders priority section labels when sections exist.
- D-pad down from hero still moves focus to the first shelf item.
- Equivalent content refresh still preserves focused shelf item.

Existing tests in `HomeScreenUiTest` should continue to pass unless their assertions intentionally change to match the new hero copy.

## Non-Goals

- No repository rewrite.
- No new Home route types.
- No "Show all" shelf navigation in this pass.
- No focusable discovery strip in this pass.
- No player behavior changes.
- No broad theme overhaul.
- No performance-heavy artwork analysis or dynamic palette extraction.

## Risks

- The Home screen file is already large. Mitigation: extract only components touched by the redesign.
- Hero layout changes can disturb D-pad focus. Mitigation: keep requesters and focus modifiers at the same semantic points and protect them with UI tests.
- Extra visual layers can hurt low-end TV performance. Mitigation: reuse existing Coil sizing and theme primitives, and avoid per-frame effects or focusable decorative elements.

## Implementation Readiness

This is a single implementation slice. It can be built with focused UI tests, scoped composable extraction, and one debug build verification pass.
