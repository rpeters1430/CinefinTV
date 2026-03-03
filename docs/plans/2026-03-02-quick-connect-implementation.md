# Quick Connect UI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the ad-hoc inline Quick Connect text in LoginScreen with a proper mode-switching panel that is readable from a TV.

**Architecture:** Local `showQuickConnectPanel` state in `LoginScreen` toggles between the sign-in form and a new private `QuickConnectPanel` composable. No ViewModel or data layer changes needed — all Quick Connect logic already exists.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.tv:tv-material3`, MockK + coroutines-test for ViewModel tests.

---

### Task 1: Add AuthViewModel Quick Connect unit tests

These tests document and lock in the existing ViewModel behavior before the UI is touched.

**Files:**
- Modify: `app/src/test/java/com/rpeters/cinefintv/ui/screens/auth/AuthViewModelTest.kt`

**Step 1: Add the four new test methods**

Append to the existing `AuthViewModelTest` class (inside the class body, before the closing `}`):

```kotlin
@Test
fun startQuickConnect_whenServerNotConnected_setsError() = runTest {
    coEvery { authRepository.tryRestoreSession() } returns false

    val viewModel = AuthViewModel(authRepository, secureCredentialManager)
    advanceUntilIdle()

    // No server URL set — both connectedServerUrl and serverUrlInput are blank
    viewModel.startQuickConnect()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("Connect to a valid server first.", state.quickConnectError)
    assertFalse(state.isQuickConnectLoading)
}

@Test
fun startQuickConnect_whenInitiateSucceeds_setsCodeAndSecret() = runTest {
    coEvery { authRepository.tryRestoreSession() } returns false
    coEvery { authRepository.initiateQuickConnect(any()) } returns
        ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectResult(code = "8374", secret = "secret123"))
    // Return Pending so the poll loop doesn't complete during this test
    coEvery { authRepository.getQuickConnectState(any(), any()) } returns
        ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectState("Pending"))

    val viewModel = AuthViewModel(authRepository, secureCredentialManager)
    advanceUntilIdle()

    viewModel.updateServerUrlInput("https://demo.jellyfin.org")
    viewModel.startQuickConnect()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("8374", state.quickConnectCode)
    assertEquals("secret123", state.quickConnectSecret)
    assertFalse(state.isQuickConnectLoading)
}

@Test
fun quickConnectPolling_whenApproved_setsLoginSucceeded() = runTest {
    val authResult: org.jellyfin.sdk.model.api.AuthenticationResult = mockk()
    coEvery { authRepository.tryRestoreSession() } returns false
    coEvery { authRepository.initiateQuickConnect(any()) } returns
        ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectResult(code = "8374", secret = "secret123"))
    coEvery { authRepository.getQuickConnectState(any(), any()) } returns
        ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectState("Approved"))
    coEvery { authRepository.authenticateWithQuickConnect(any(), any()) } returns
        ApiResult.Success(authResult)

    val viewModel = AuthViewModel(authRepository, secureCredentialManager)
    advanceUntilIdle()

    viewModel.updateServerUrlInput("https://demo.jellyfin.org")
    viewModel.startQuickConnect()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.loginSucceeded)
    assertFalse(state.isAuthenticating)
}

@Test
fun quickConnectPolling_whenDenied_setsErrorAndStopsPolling() = runTest {
    coEvery { authRepository.tryRestoreSession() } returns false
    coEvery { authRepository.initiateQuickConnect(any()) } returns
        ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectResult(code = "8374", secret = "secret123"))
    coEvery { authRepository.getQuickConnectState(any(), any()) } returns
        ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectState("Denied"))

    val viewModel = AuthViewModel(authRepository, secureCredentialManager)
    advanceUntilIdle()

    viewModel.updateServerUrlInput("https://demo.jellyfin.org")
    viewModel.startQuickConnect()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.loginSucceeded)
    assertTrue(state.quickConnectError?.contains("denied", ignoreCase = true) == true)
    assertEquals(null, state.quickConnectPollStatus)
}

@Test
fun stopQuickConnect_clearsAllQuickConnectState() = runTest {
    coEvery { authRepository.tryRestoreSession() } returns false
    coEvery { authRepository.initiateQuickConnect(any()) } returns
        ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectResult(code = "8374", secret = "secret123"))
    coEvery { authRepository.getQuickConnectState(any(), any()) } returns
        ApiResult.Success(com.rpeters.cinefintv.data.model.QuickConnectState("Pending"))

    val viewModel = AuthViewModel(authRepository, secureCredentialManager)
    advanceUntilIdle()

    viewModel.updateServerUrlInput("https://demo.jellyfin.org")
    viewModel.startQuickConnect()
    advanceUntilIdle()

    // Code should be set at this point
    assertNotNull(viewModel.uiState.value.quickConnectCode)

    viewModel.stopQuickConnect()

    val state = viewModel.uiState.value
    assertNull(state.quickConnectCode)
    assertNull(state.quickConnectSecret)
    assertNull(state.quickConnectPollStatus)
    assertNull(state.quickConnectError)
    assertFalse(state.isQuickConnectLoading)
}
```

Add these imports to the import block at the top of the file if not already present:
```kotlin
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
```

**Step 2: Run the new tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.auth.AuthViewModelTest" 2>&1 | tail -30
```

Expected: All 5 new tests PASS (ViewModel logic is already implemented).

**Step 3: Commit**

```bash
git add app/src/test/java/com/rpeters/cinefintv/ui/screens/auth/AuthViewModelTest.kt
git commit -m "test: add Quick Connect ViewModel unit tests"
```

---

### Task 2: Refactor LoginScreen — extract QuickConnectPanel + mode switch

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/LoginScreen.kt`

**Step 1: Replace the entire file contents**

The goal is:
1. Add `var showQuickConnectPanel by rememberSaveable { mutableStateOf(false) }` to `LoginScreen`
2. Use an `if/else` to render either the sign-in column or `QuickConnectPanel`
3. Remove the old inline QC text/buttons from the sign-in column
4. Add a new private `QuickConnectPanel` composable

Full replacement for `LoginScreen.kt`:

```kotlin
package com.rpeters.cinefintv.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    serverUrl: String,
    isAuthenticating: Boolean,
    errorMessage: String?,
    isQuickConnectEnabled: Boolean,
    isQuickConnectLoading: Boolean,
    quickConnectCode: String?,
    quickConnectPollStatus: String?,
    quickConnectError: String?,
    onLogin: (username: String, password: String) -> Unit,
    onUseQuickConnect: () -> Unit,
    onGenerateNewCode: () -> Unit,
    onLeaveScreen: () -> Unit,
    onBack: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showQuickConnectPanel by rememberSaveable { mutableStateOf(false) }

    val canSignIn = username.isNotBlank() && password.isNotBlank() && !isAuthenticating

    val helperText = remember(serverUrl) {
        if (serverUrl.isBlank()) "Connect to a server first." else "Signing in to $serverUrl"
    }

    if (showQuickConnectPanel) {
        QuickConnectPanel(
            isLoading = isQuickConnectLoading,
            code = quickConnectCode,
            pollStatus = quickConnectPollStatus,
            error = quickConnectError,
            onNewCode = onGenerateNewCode,
            onCancel = {
                onLeaveScreen()
                showQuickConnectPanel = false
            },
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Sign In",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AuthTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = "Username",
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Text,
            )

            AuthPasswordField(
                value = password,
                onValueChange = { password = it },
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { onLogin(username.trim(), password) },
                    enabled = canSignIn,
                ) {
                    Text(if (isAuthenticating) "Signing In..." else "Sign In")
                }
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
            }

            if (isQuickConnectEnabled) {
                OutlinedButton(
                    onClick = {
                        showQuickConnectPanel = true
                        onUseQuickConnect()
                    },
                    enabled = !isAuthenticating,
                ) {
                    Text("Use Quick Connect")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onLeaveScreen() }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickConnectPanel(
    isLoading: Boolean,
    code: String?,
    pollStatus: String?,
    error: String?,
    onNewCode: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 64.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Quick Connect",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Enter this code in the Jellyfin app on your phone or computer:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            isLoading && code == null -> {
                Text(
                    text = "Generating code...",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            code != null -> {
                Text(
                    text = code.toCharArray().joinToString("    "),
                    style = MaterialTheme.typography.displayLarge,
                )
            }
            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (pollStatus != null) {
            Text(
                text = pollStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onNewCode,
                enabled = !isLoading,
            ) {
                Text("New Code")
            }
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    AuthTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "Password",
        imeAction = ImeAction.Done,
        keyboardType = KeyboardType.Password,
        visualTransformation = PasswordVisualTransformation(),
    )
}
```

**Step 2: Build to verify it compiles**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

**Step 3: Run full test suite**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all tests pass.

**Step 4: Manual smoke test (on device or emulator)**

1. Launch the app, connect to a Jellyfin server
2. On the LoginScreen, confirm "Use Quick Connect" button appears only if the server has Quick Connect enabled
3. Tap "Use Quick Connect" → sign-in form disappears, Quick Connect panel appears with "Generating code..."
4. Code appears in large spaced format: e.g. `8    3    7    4`
5. Status shows "Waiting for approval..."
6. Tap "Cancel" → returns to sign-in form, code is gone
7. Tap "Use Quick Connect" again → new code generated
8. Approve from another device → app logs in and navigates to Home

**Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/LoginScreen.kt
git commit -m "feat: Quick Connect mode-switch panel with large spaced code display"
```

---

### Task 3: Update status doc

**Files:**
- Modify: `docs/plans/2026-03-02-cinefintv-status.md`

**Step 1: Mark Quick Connect as complete**

In the "What is still missing / future work" section, change:
```
- Quick Connect auth option
```
to:
```
- Quick Connect auth option ✅ complete
```

**Step 2: Commit**

```bash
git add docs/plans/2026-03-02-cinefintv-status.md
git commit -m "docs: mark Quick Connect UI as complete"
```
