package com.rpeters.cinefintv.ui.components

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMenuTriggerTest {

    @Test
    fun shouldOpenCardMenu_returnsTrueForMenuKey() {
        assertTrue(
            shouldOpenCardMenu(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_MENU,
                repeatCount = 0,
            ),
        )
    }

    @Test
    fun shouldOpenCardMenu_returnsTrueForLongPressCenterKey() {
        assertTrue(
            shouldOpenCardMenu(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                repeatCount = 2,
            ),
        )
    }

    @Test
    fun shouldOpenCardMenu_returnsFalseForSingleCenterPress() {
        assertFalse(
            shouldOpenCardMenu(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                repeatCount = 0,
            ),
        )
    }
}
