package com.rpeters.cinefintv.data.preferences

/**
 * Preferences for controlling sensitive library actions such as deleting items.
 */
data class LibraryActionsPreferences(
    val enableManagementActions: Boolean = false,
) {
    companion object {
        val DEFAULT = LibraryActionsPreferences()
    }
}
