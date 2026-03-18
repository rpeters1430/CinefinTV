# CinefinTV Focus Contract

**Date:** 2026-03-17
**Purpose:** Define the expected TV focus behavior before additional UX polish work.

## Global rules

- Focus movement alone must not trigger route changes.
- `Center`/`Enter` activates the focused control and is the only way tabs change routes.
- Moving `Down` from the top nav always returns to the currently displayed screen, not the tab that happens to be focused.
- Moving `Up` from the first interactive element on a screen returns to that screen's top anchor before leaving content.
- A screen's top anchor must scroll the viewport back to the top and provide a deterministic `Down` target.

## Top navigation

- Entering the tab row from content should land on the currently selected tab.
- Moving `Left` and `Right` across tabs previews focus only; it does not swap screens.
- Pressing `Down` from any tab returns focus to the active screen's primary focus target.
- Search remains after Music and before Settings in the current IA.

## Screen entry targets

- Home:
  Initial focus goes to the featured carousel when present, otherwise the first content row.
- Movies / TV Shows / Collections libraries:
  Initial focus goes to the top anchor, then `Down` enters the first grid card.
- Search:
  Initial focus goes to the search field.
- Settings:
  Initial focus goes to the first actionable setting row.
- Person detail:
  Initial focus goes to the top anchor, then `Down` enters the back button/content panel.

## Detail and playback targets

- Movie detail:
  Initial focus should land on the primary play action.
- TV show detail:
  Initial focus should land on the agreed primary action, not a cached shelf item.
- Season detail:
  Initial focus should prefer `Play Season` over `Back`.
- Episode detail:
  Initial focus should land on `Play`.
- Player track panels:
  Opening the panel should focus the currently selected track or the `Off` row when no subtitle is active.

## Follow-up work from this contract

- Verify the tab row behavior on emulator and physical Android TV hardware.
- Register focus targets for any detail routes that still fall back to generic nav behavior.
- Add focused Compose UI coverage for tab-to-content transitions once the contract is stable.
