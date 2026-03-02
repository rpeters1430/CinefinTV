package com.rpeters.cinefintv.data.model

data class CurrentUserDetails(
    val name: String,
    val primaryImageTag: String?,
    val lastLoginDate: String?,
    val isAdministrator: Boolean,
)
