package com.rpeters.cinefintv.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.rpeters.cinefintv.ui.LocalCinefinThemeController
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme
import com.rpeters.cinefintv.ui.theme.ThemeColorController

internal fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction =
    apply { performSemanticsAction(SemanticsActions.RequestFocus) }

@Composable
internal fun DetailTestHost(content: @Composable () -> Unit) {
    CinefinTvTheme(useDynamicColors = false) {
        CompositionLocalProvider(
            LocalCinefinThemeController provides object : ThemeColorController {
                override fun updateSeedColor(color: Color?) = Unit
            }
        ) {
            content()
        }
    }
}
