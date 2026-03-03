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
            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
