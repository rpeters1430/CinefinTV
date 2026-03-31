package com.rpeters.cinefintv.ui.components

internal fun shouldOpenCardMenu(nativeEvent: android.view.KeyEvent): Boolean {
    return shouldOpenCardMenu(
        action = nativeEvent.action,
        keyCode = nativeEvent.keyCode,
        repeatCount = nativeEvent.repeatCount,
    )
}

internal fun shouldOpenCardMenu(action: Int, keyCode: Int, repeatCount: Int): Boolean {
    if (action != android.view.KeyEvent.ACTION_DOWN) return false

    val isMenuKey = keyCode == android.view.KeyEvent.KEYCODE_MENU
    val isLongPressSelect = repeatCount > 0 && when (keyCode) {
        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
        android.view.KeyEvent.KEYCODE_ENTER,
        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
        android.view.KeyEvent.KEYCODE_BUTTON_A,
        -> true
        else -> false
    }

    return isMenuKey || isLongPressSelect
}
