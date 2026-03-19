package com.rpeters.cinefintv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

class TvScreenFocusRegistry {
    private val primaryRequesters = mutableStateMapOf<String, FocusRequester>()

    fun register(route: String, requester: FocusRequester) {
        primaryRequesters[route] = requester
    }

    fun unregister(route: String, requester: FocusRequester) {
        if (primaryRequesters[route] == requester) {
            primaryRequesters.remove(route)
        }
    }

    fun requesterFor(route: String?): FocusRequester? {
        if (route.isNullOrBlank()) return null
        return primaryRequesters[route]
            ?: primaryRequesters.entries.firstOrNull { (registeredRoute, _) ->
                val regParts = registeredRoute.split('/')
                val routeParts = route.split('/')
                if (routeParts.size < regParts.size) return@firstOrNull false

                regParts.indices.all { i ->
                    val regPart = regParts[i]
                    val routePart = routeParts[i]
                    (regPart.startsWith('{') && regPart.endsWith('}')) || regPart == routePart
                }
            }?.value
    }
}

val LocalTvScreenFocusRegistry = compositionLocalOf<TvScreenFocusRegistry?> { null }

data class TvScreenFocusState(
    val topAnchorRequester: FocusRequester,
    val primaryContentRequester: FocusRequester,
)

@Composable
fun ProvideTvScreenFocusRegistry(
    content: @Composable () -> Unit,
) {
    val registry = remember { TvScreenFocusRegistry() }
    CompositionLocalProvider(LocalTvScreenFocusRegistry provides registry) {
        content()
    }
}

@Composable
fun rememberTvScreenFocusState(): TvScreenFocusState {
    val topAnchorRequester = remember { FocusRequester() }
    val primaryContentRequester = remember { FocusRequester() }
    return remember(topAnchorRequester, primaryContentRequester) {
        TvScreenFocusState(
            topAnchorRequester = topAnchorRequester,
            primaryContentRequester = primaryContentRequester,
        )
    }
}

@Composable
fun RegisterPrimaryScreenFocus(
    route: String,
    requester: FocusRequester,
) {
    val registry = LocalTvScreenFocusRegistry.current ?: return
    DisposableEffect(route, requester, registry) {
        registry.register(route, requester)
        onDispose {
            registry.unregister(route, requester)
        }
    }
}

@Composable
fun RequestScreenFocus(
    key: Any?,
    requester: FocusRequester,
    enabled: Boolean = true,
) {
    LaunchedEffect(key, enabled) {
        if (enabled) requester.requestFocus()
    }
}

@Composable
fun DismissImeOnDispose() {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    DisposableEffect(keyboardController, focusManager, view) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            view.clearFocus()
        }
    }
}

@Composable
fun DismissImeOnEnter(key: Any? = Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    LaunchedEffect(key) {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        view.clearFocus()
    }
}

@Composable
fun TvScreenTopFocusAnchor(
    state: TvScreenFocusState,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollFocusAnchor(
        modifier = modifier
            .focusRequester(state.topAnchorRequester)
            .focusProperties {
                down = state.primaryContentRequester
            },
        onFocused = onFocused,
    )
}
