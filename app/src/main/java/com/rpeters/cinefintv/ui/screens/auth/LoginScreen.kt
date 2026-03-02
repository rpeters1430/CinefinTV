package com.rpeters.cinefintv.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
    onLogin: (username: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val canContinue = username.isNotBlank() && password.isNotBlank() && !isAuthenticating
    val helperText = remember(serverUrl) {
        if (serverUrl.isBlank()) {
            "Connect to a server first."
        } else {
            "Signing in to $serverUrl"
        }
    }

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
                enabled = canContinue,
            ) {
                Text(if (isAuthenticating) "Signing In..." else "Sign In")
            }
            OutlinedButton(onClick = onBack) {
                Text("Back")
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
