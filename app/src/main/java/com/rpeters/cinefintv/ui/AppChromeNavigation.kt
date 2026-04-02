package com.rpeters.cinefintv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

class AppChromeFocusController {
    var topNavFocusRequester: FocusRequester? by mutableStateOf(null)
    var primaryContentFocusRequester: FocusRequester? by mutableStateOf(null)
}

val LocalAppChromeFocusController = compositionLocalOf<AppChromeFocusController?> { null }

@Composable
fun RegisterPrimaryContentFocusRequester(focusRequester: FocusRequester?) {
    val chromeFocusController = LocalAppChromeFocusController.current

    SideEffect {
        chromeFocusController?.primaryContentFocusRequester = focusRequester
    }

    DisposableEffect(chromeFocusController, focusRequester) {
        onDispose {
            val controller = chromeFocusController ?: return@onDispose
            if (controller.primaryContentFocusRequester == focusRequester) {
                controller.primaryContentFocusRequester = null
            }
        }
    }
}

@Stable
class TopLevelDestinationFocus internal constructor(
    private val navFocusRequesterProvider: () -> FocusRequester?,
    private val primaryContentFocusRequesterProvider: () -> FocusRequester?,
) {
    val drawerFocusRequester: FocusRequester?
        get() = navFocusRequesterProvider()

    fun primaryContentModifier(
        down: FocusRequester? = null,
        right: FocusRequester? = null,
    ): Modifier {
        val primaryContentFocusRequester = primaryContentFocusRequesterProvider()
        val drawerFocusRequester = navFocusRequesterProvider()

        return Modifier
            .then(
                if (primaryContentFocusRequester != null) {
                    Modifier.focusRequester(primaryContentFocusRequester)
                } else {
                    Modifier
                }
            )
            .focusProperties {
                drawerFocusRequester?.let {
                    this.up = it
                    this.left = it
                }
                down?.let { this.down = it }
                right?.let { this.right = it }
            }
    }

    fun drawerEscapeModifier(
        isLeftEdge: Boolean = false,
        up: FocusRequester? = null,
        down: FocusRequester? = null,
        left: FocusRequester? = null,
        right: FocusRequester? = null,
    ): Modifier {
        val drawerFocusRequester = navFocusRequesterProvider()

        return Modifier.focusProperties {
            up?.let { this.up = it }
            down?.let { this.down = it }
            right?.let { this.right = it }
            when {
                left != null -> this.left = left
                isLeftEdge && drawerFocusRequester != null -> this.left = drawerFocusRequester
            }
        }
    }
}

@Composable
fun rememberTopLevelDestinationFocus(
    primaryContentFocusRequester: FocusRequester?,
): TopLevelDestinationFocus {
    val chromeFocusController = LocalAppChromeFocusController.current
    val currentNavFocusRequester by rememberUpdatedState(chromeFocusController?.topNavFocusRequester)
    val currentPrimaryContentFocusRequester by rememberUpdatedState(primaryContentFocusRequester)

    RegisterPrimaryContentFocusRequester(primaryContentFocusRequester)

    return remember {
        TopLevelDestinationFocus(
            navFocusRequesterProvider = { currentNavFocusRequester },
            primaryContentFocusRequesterProvider = { currentPrimaryContentFocusRequester },
        )
    }
}
