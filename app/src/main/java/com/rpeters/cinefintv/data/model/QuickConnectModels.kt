package com.rpeters.cinefintv.data.model

/**
 * ✅ IMPROVEMENT: QuickConnect models extracted for better organization
 * These were previously in the large JellyfinRepository.kt file
 */

data class QuickConnectResult(
    val code: String,
    val secret: String,
)

data class QuickConnectState(
    val state: String, // "Pending", "Approved", "Denied", "Expired"
) {
    val isPending: Boolean get() = state == "Pending"
    val isApproved: Boolean get() = state == "Approved"
    val isDenied: Boolean get() = state == "Denied"
    val isExpired: Boolean get() = state == "Expired"
    val isCompleted: Boolean get() = isApproved || isDenied || isExpired
}
