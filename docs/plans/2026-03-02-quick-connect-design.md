# Quick Connect UI Design

**Date:** 2026-03-02
**Scope:** `LoginScreen.kt` only — ViewModel, NavGraph, and data layer are already complete.

---

## Overview

Quick Connect is a Jellyfin feature that lets users sign in to an Android TV client by approving a short code in the Jellyfin web app or mobile app instead of typing a username and password on a TV keyboard. The backend flow (API calls, polling, authentication) is fully implemented in `AuthViewModel` and `JellyfinAuthRepository`. This design covers only the UI presentation.

---

## Mode Switching

The `LoginScreen` renders in one of two mutually exclusive modes controlled by a single local state variable:

```kotlin
var showQuickConnectPanel by rememberSaveable { mutableStateOf(false) }
```

| Mode | Trigger | What renders |
|---|---|---|
| Sign In | Default / cancel pressed | Username + password form, Sign In button, Back button, "Use Quick Connect" button (if enabled) |
| Quick Connect | "Use Quick Connect" pressed | Quick Connect panel — code display, status, New Code + Cancel buttons |

The two modes never appear simultaneously.

---

## Quick Connect Panel

### Layout

```
Quick Connect

Enter this code in the Jellyfin app on your phone or computer:

    8    3    7    4

Waiting for approval...

[New Code]  [Cancel]
```

### States

| ViewModel state | Code area shows | Buttons |
|---|---|---|
| `isQuickConnectLoading = true`, code = null | "Generating code..." at display size | New Code disabled, Cancel enabled |
| `quickConnectCode != null` | Code in spaced monospace at `displayLarge` | New Code enabled, Cancel enabled |
| `quickConnectPollStatus != null` | Same code display + status text below | No change |
| `quickConnectError != null` | Error text (accent colour); code hidden | New Code enabled, Cancel enabled |

### Code display

The code is displayed as a single `Text` composable with wide letter-spacing, using `MaterialTheme.typography.displayLarge`. No per-digit boxes.

```kotlin
Text(
    text = quickConnectCode.toCharArray().joinToString("    "),
    style = MaterialTheme.typography.displayLarge,
    letterSpacing = 8.sp,
)
```

---

## Cancel Behaviour

The Cancel button:
1. Calls `onLeaveScreen()` → maps to `viewModel.stopQuickConnect()` (cancels the poll job, clears QC state)
2. Sets `showQuickConnectPanel = false` → returns to sign-in form

The existing `DisposableEffect(Unit) { onDispose { onLeaveScreen() } }` already handles cleanup if the user navigates away while polling is active — no change needed.

---

## LoginScreen Signature

No changes to the composable signature. All Quick Connect callbacks (`onUseQuickConnect`, `onGenerateNewCode`, `onLeaveScreen`) are already present.

---

## Files Changed

| File | Change |
|---|---|
| `ui/screens/auth/LoginScreen.kt` | Add mode-switch local state; extract `QuickConnectPanel` private composable |

No other files require changes.
