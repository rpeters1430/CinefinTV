package com.rpeters.cinefintv.core

import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.repository.common.ApiResult
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Comprehensive error handling system for the Jellyfin Android app.
 * Provides consistent error classification, logging, and user-friendly messages.
 */

/**
 * Enumeration of error types for consistent error handling.
 */
enum class JellyfinErrorType(val code: String) {
    // Network errors
    NETWORK_UNREACHABLE("NETWORK_001"),
    CONNECTION_TIMEOUT("NETWORK_002"),
    SSL_ERROR("NETWORK_003"),
    UNKNOWN_HOST("NETWORK_004"),
    DNS_RESOLUTION("NETWORK_005"), // DNS hostname resolution failures

    // Server errors
    SERVER_UNAVAILABLE("SERVER_001"),
    SERVER_ERROR("SERVER_002"),
    SERVER_TIMEOUT("SERVER_003"),

    // Authentication errors
    UNAUTHORIZED("AUTH_001"),
    FORBIDDEN("AUTH_002"),
    SESSION_EXPIRED("AUTH_003"),
    INVALID_CREDENTIALS("AUTH_004"),

    // Client errors
    BAD_REQUEST("CLIENT_001"),
    NOT_FOUND("CLIENT_002"),
    CONFLICT("CLIENT_003"),

    // Application errors
    PARSING_ERROR("APP_001"),
    CACHE_ERROR("APP_002"),
    CONFIGURATION_ERROR("APP_003"),
    OPERATION_CANCELLED("APP_004"),

    // Unknown errors
    UNKNOWN("UNKNOWN_001"),
    ;

    /**
     * Convert JellyfinErrorType to ErrorType for ApiResult compatibility
     */
    fun toErrorType(): com.rpeters.cinefintv.data.repository.common.ErrorType {
        return when (this) {
            NETWORK_UNREACHABLE, CONNECTION_TIMEOUT, SSL_ERROR ->
                com.rpeters.cinefintv.data.repository.common.ErrorType.NETWORK

            UNKNOWN_HOST, DNS_RESOLUTION ->
                com.rpeters.cinefintv.data.repository.common.ErrorType.DNS_RESOLUTION

            UNAUTHORIZED, SESSION_EXPIRED, INVALID_CREDENTIALS ->
                com.rpeters.cinefintv.data.repository.common.ErrorType.UNAUTHORIZED

            FORBIDDEN ->
                com.rpeters.cinefintv.data.repository.common.ErrorType.FORBIDDEN

            NOT_FOUND ->
                com.rpeters.cinefintv.data.repository.common.ErrorType.NOT_FOUND

            SERVER_UNAVAILABLE, SERVER_ERROR, SERVER_TIMEOUT ->
                com.rpeters.cinefintv.data.repository.common.ErrorType.SERVER_ERROR

            CONNECTION_TIMEOUT, SERVER_TIMEOUT ->
                com.rpeters.cinefintv.data.repository.common.ErrorType.TIMEOUT

            OPERATION_CANCELLED ->
                com.rpeters.cinefintv.data.repository.common.ErrorType.OPERATION_CANCELLED

            BAD_REQUEST, CONFLICT, PARSING_ERROR, CACHE_ERROR, CONFIGURATION_ERROR, UNKNOWN ->
                com.rpeters.cinefintv.data.repository.common.ErrorType.UNKNOWN
        }
    }
}

/**
 * Detailed error information for better error handling and reporting.
 */
data class JellyfinError(
    val type: JellyfinErrorType,
    val message: String,
    val userMessage: String,
    val cause: Throwable? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val context: Map<String, Any> = emptyMap(),
)

/**
 * Central error handler for the application.
 */
object ErrorHandler {
    private const val TAG = "ErrorHandler"

    /**
     * Handle and classify exceptions into JellyfinError.
     */
    fun handleException(exception: Throwable, context: Map<String, Any> = emptyMap()): JellyfinError {
        val error = when (exception) {
            is CancellationException -> {
                JellyfinError(
                    type = JellyfinErrorType.OPERATION_CANCELLED,
                    message = "Operation was cancelled",
                    userMessage = "Operation cancelled",
                    cause = exception,
                    context = context,
                )
            }

            is UnknownHostException -> {
                JellyfinError(
                    type = JellyfinErrorType.DNS_RESOLUTION,
                    message = "Unable to resolve host: ${exception.message}",
                    userMessage = "Could not find an IP address for the server. Please check the server address for typos, or try using an IP address directly (e.g., 192.168.1.100).",
                    cause = exception,
                    context = context,
                )
            }

            is ConnectException -> {
                JellyfinError(
                    type = JellyfinErrorType.NETWORK_UNREACHABLE,
                    message = "Connection failed: ${exception.message}",
                    userMessage = "Unable to connect to server. Please check your network connection.",
                    cause = exception,
                    context = context,
                )
            }

            is SocketTimeoutException -> {
                JellyfinError(
                    type = JellyfinErrorType.CONNECTION_TIMEOUT,
                    message = "Connection timed out: ${exception.message}",
                    userMessage = "Connection timed out. Please try again.",
                    cause = exception,
                    context = context,
                )
            }

            is SSLException -> {
                JellyfinError(
                    type = JellyfinErrorType.SSL_ERROR,
                    message = "SSL error: ${exception.message}",
                    userMessage = "Secure connection failed. Please check your server configuration.",
                    cause = exception,
                    context = context,
                )
            }

            is IOException -> {
                JellyfinError(
                    type = JellyfinErrorType.NETWORK_UNREACHABLE,
                    message = "Network I/O error: ${exception.message}",
                    userMessage = "Network error occurred. Please check your connection.",
                    cause = exception,
                    context = context,
                )
            }

            else -> {
                JellyfinError(
                    type = JellyfinErrorType.UNKNOWN,
                    message = "Unexpected error: ${exception.message}",
                    userMessage = "An unexpected error occurred. Please try again.",
                    cause = exception,
                    context = context,
                )
            }
        }

        logError(error)
        return error
    }

    /**
     * Handle HTTP errors from API responses.
     */
    fun handleHttpError(code: Int, message: String?, context: Map<String, Any> = emptyMap()): JellyfinError {
        val error = when (code) {
            401 -> JellyfinError(
                type = JellyfinErrorType.UNAUTHORIZED,
                message = "Unauthorized access: $message",
                userMessage = "Authentication failed. Please log in again.",
                context = context,
            )

            403 -> JellyfinError(
                type = JellyfinErrorType.FORBIDDEN,
                message = "Access forbidden: $message",
                userMessage = "You don't have permission to access this content.",
                context = context,
            )

            404 -> JellyfinError(
                type = JellyfinErrorType.NOT_FOUND,
                message = "Resource not found: $message",
                userMessage = "The requested content was not found.",
                context = context,
            )

            400 -> JellyfinError(
                type = JellyfinErrorType.BAD_REQUEST,
                message = "Bad request: $message",
                userMessage = "Invalid request. Please try again.",
                context = context,
            )

            409 -> JellyfinError(
                type = JellyfinErrorType.CONFLICT,
                message = "Conflict: $message",
                userMessage = "A conflict occurred. Please try again.",
                context = context,
            )

            in 500..599 -> JellyfinError(
                type = JellyfinErrorType.SERVER_ERROR,
                message = "Server error ($code): $message",
                userMessage = "The server encountered an error. Please try again later.",
                context = context,
            )

            else -> JellyfinError(
                type = JellyfinErrorType.UNKNOWN,
                message = "HTTP error ($code): $message",
                userMessage = "An error occurred. Please try again.",
                context = context,
            )
        }

        logError(error)
        return error
    }

    /**
     * Convert JellyfinError to ApiResult.Error.
     */
    fun <T> toApiResult(error: JellyfinError): ApiResult.Error<T> {
        return ApiResult.Error(
            message = error.userMessage,
            errorType = error.type.toErrorType(),
        )
    }

    /**
     * Log error information with appropriate log level.
     */
    private fun logError(error: JellyfinError) {
        val contextInfo = if (error.context.isNotEmpty()) {
            "Context: ${error.context}"
        } else {
            ""
        }

        when (error.type) {
            JellyfinErrorType.OPERATION_CANCELLED -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "[${error.type.code}] ${error.message} $contextInfo")
                }
            }

            JellyfinErrorType.NETWORK_UNREACHABLE,
            JellyfinErrorType.CONNECTION_TIMEOUT,
            JellyfinErrorType.UNKNOWN_HOST,
            JellyfinErrorType.DNS_RESOLUTION,
            -> {
                Log.w(TAG, "[${error.type.code}] ${error.message} $contextInfo", error.cause)
            }

            JellyfinErrorType.SSL_ERROR,
            JellyfinErrorType.UNAUTHORIZED,
            JellyfinErrorType.FORBIDDEN,
            -> {
                Log.w(TAG, "[${error.type.code}] ${error.message} $contextInfo", error.cause)
            }

            JellyfinErrorType.SERVER_ERROR,
            JellyfinErrorType.UNKNOWN,
            -> {
                Log.e(TAG, "[${error.type.code}] ${error.message} $contextInfo", error.cause)
            }

            else -> {
                Log.i(TAG, "[${error.type.code}] ${error.message} $contextInfo", error.cause)
            }
        }
    }

    /**
     * Check if an error is recoverable (user can retry).
     */
    fun isRecoverable(error: JellyfinError): Boolean {
        return when (error.type) {
            JellyfinErrorType.NETWORK_UNREACHABLE,
            JellyfinErrorType.CONNECTION_TIMEOUT,
            JellyfinErrorType.SERVER_UNAVAILABLE,
            JellyfinErrorType.SERVER_TIMEOUT,
            JellyfinErrorType.UNKNOWN_HOST,
            JellyfinErrorType.DNS_RESOLUTION,
            -> true

            JellyfinErrorType.OPERATION_CANCELLED,
            JellyfinErrorType.UNAUTHORIZED,
            JellyfinErrorType.FORBIDDEN,
            JellyfinErrorType.NOT_FOUND,
            JellyfinErrorType.BAD_REQUEST,
            JellyfinErrorType.SSL_ERROR,
            JellyfinErrorType.INVALID_CREDENTIALS,
            -> false

            else -> false
        }
    }

    /**
     * Get user-friendly title for error type.
     */
    fun getErrorTitle(error: JellyfinError): String {
        return when (error.type) {
            JellyfinErrorType.NETWORK_UNREACHABLE,
            JellyfinErrorType.CONNECTION_TIMEOUT,
            -> "Connection Error"

            JellyfinErrorType.UNKNOWN_HOST,
            JellyfinErrorType.DNS_RESOLUTION,
            -> "DNS Resolution Error"

            JellyfinErrorType.SSL_ERROR -> "Security Error"

            JellyfinErrorType.SERVER_UNAVAILABLE,
            JellyfinErrorType.SERVER_ERROR,
            JellyfinErrorType.SERVER_TIMEOUT,
            -> "Server Error"

            JellyfinErrorType.UNAUTHORIZED,
            JellyfinErrorType.FORBIDDEN,
            JellyfinErrorType.SESSION_EXPIRED,
            JellyfinErrorType.INVALID_CREDENTIALS,
            -> "Authentication Error"

            JellyfinErrorType.NOT_FOUND -> "Not Found"

            JellyfinErrorType.BAD_REQUEST,
            JellyfinErrorType.CONFLICT,
            -> "Request Error"

            JellyfinErrorType.PARSING_ERROR,
            JellyfinErrorType.CACHE_ERROR,
            JellyfinErrorType.CONFIGURATION_ERROR,
            -> "Application Error"

            JellyfinErrorType.OPERATION_CANCELLED -> "Cancelled"

            JellyfinErrorType.UNKNOWN -> "Unknown Error"
        }
    }
}
