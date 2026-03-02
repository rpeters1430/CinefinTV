package com.rpeters.cinefintv.data.model

import kotlinx.coroutines.CancellationException

/**
 * ✅ IMPROVEMENT: Enhanced error handling with specific error types
 * Extracted from JellyfinRepository for better organization
 */

sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(
        val message: String,
        val cause: Throwable? = null,
        val errorType: ErrorType = ErrorType.UNKNOWN,
    ) : ApiResult<T>()
    data class Loading<T>(val message: String = "Loading...") : ApiResult<T>()
}

enum class ErrorType {
    NETWORK,
    AUTHENTICATION,
    SERVER_ERROR,
    PARSING_ERROR,
    TIMEOUT,
    UNKNOWN,
}

/**
 * ✅ IMPROVEMENT: Specific error types for better error handling
 */
sealed class JellyfinError {
    object NetworkError : JellyfinError()
    object AuthenticationError : JellyfinError()
    object ServerError : JellyfinError()
    object TimeoutError : JellyfinError()
    data class UnknownError(val message: String, val cause: Throwable? = null) : JellyfinError()

    fun <T> toApiResult(): ApiResult.Error<T> = when (this) {
        is NetworkError -> ApiResult.Error("Network connection failed", null, ErrorType.NETWORK)
        is AuthenticationError -> ApiResult.Error("Authentication failed", null, ErrorType.AUTHENTICATION)
        is ServerError -> ApiResult.Error("Server error occurred", null, ErrorType.SERVER_ERROR)
        is TimeoutError -> ApiResult.Error("Request timed out", null, ErrorType.TIMEOUT)
        is UnknownError -> ApiResult.Error(message, cause, ErrorType.UNKNOWN)
    }
}

/**
 * ✅ IMPROVEMENT: Retry mechanism for failed requests
 */
suspend fun <T> withRetry(
    times: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    operation: suspend () -> T,
): T {
    var currentDelay = initialDelay
    repeat(times - 1) { attempt ->
        try {
            return operation()
        } catch (e: CancellationException) {
            throw e
        }
    }
    return operation() // Last attempt without catch
}
