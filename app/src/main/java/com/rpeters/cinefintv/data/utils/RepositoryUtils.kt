package com.rpeters.cinefintv.data.utils

import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.repository.common.ErrorType
import com.rpeters.cinefintv.data.security.PinningValidationException
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import retrofit2.HttpException
import java.util.UUID

/**
 * ✅ PHASE 4: Utility functions extracted from JellyfinRepository
 * Centralized helper functions for error handling, validation, and parsing
 */
object RepositoryUtils {
    private const val TAG = "RepositoryUtils"

    /**
     * Checks if an error message is authentication-related.
     * Used for determining if an IllegalStateException should be mapped to AUTHENTICATION error type.
     */
    internal fun isAuthenticationRelatedError(message: String): Boolean {
        return message.contains("authenticated", ignoreCase = true) ||
            message.contains("authentication", ignoreCase = true) ||
            message.contains("token", ignoreCase = true)
    }

    /**
     * Extracts HTTP status code from InvalidStatusException using multiple patterns
     */
    fun extractStatusCode(e: InvalidStatusException): Int? {
        return try {
            val message = e.message ?: return null

            // Pattern 1: "Invalid HTTP status in response: 401"
            val pattern1 = """Invalid HTTP status in response:\s*(\d{3})""".toRegex()
            pattern1.find(message)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

            // Pattern 2: Any 3-digit number that looks like an HTTP status
            val pattern2 = """\b([4-5]\d{2})\b""".toRegex()
            pattern2.find(message)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

            // Pattern 3: Generic 3-digit number extraction
            val pattern3 = """\b(\d{3})\b""".toRegex()
            val matches = pattern3.findAll(message).map { it.groupValues[1].toIntOrNull() }.filterNotNull()

            // Return the first match that looks like an HTTP status code
            matches.firstOrNull { it in 400..599 }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Determines error type from various exception types
     */
    fun getErrorType(e: Throwable): ErrorType {
        // Check for DNS resolution errors first (including nested GaiException)
        if (isDnsResolutionError(e)) {
            return ErrorType.DNS_RESOLUTION
        }

        return when (e) {
            is java.util.concurrent.CancellationException,
            is kotlinx.coroutines.CancellationException,
            -> ErrorType.OPERATION_CANCELLED

            is PinningValidationException,
            -> ErrorType.PINNING

            is java.net.UnknownHostException,
            -> ErrorType.DNS_RESOLUTION

            is java.net.ConnectException,
            is java.net.SocketTimeoutException,
            -> ErrorType.NETWORK

            is IllegalStateException -> {
                val message = e.message ?: ""
                if (isAuthenticationRelatedError(message)) {
                    ErrorType.AUTHENTICATION
                } else {
                    ErrorType.UNKNOWN
                }
            }

            is HttpException -> when (e.code()) {
                400 -> ErrorType.BAD_REQUEST
                401 -> ErrorType.UNAUTHORIZED
                403 -> ErrorType.FORBIDDEN
                404 -> ErrorType.NOT_FOUND
                in 500..599 -> ErrorType.SERVER_ERROR
                else -> ErrorType.UNKNOWN
            }

            is InvalidStatusException -> {
                val statusCode = extractStatusCode(e)
                when (statusCode) {
                    400 -> ErrorType.BAD_REQUEST
                    401 -> ErrorType.UNAUTHORIZED
                    403 -> ErrorType.FORBIDDEN
                    404 -> ErrorType.NOT_FOUND
                    in 500..599 -> ErrorType.SERVER_ERROR
                    else -> {
                        // Fallback: check message content for specific errors
                        val message = e.message ?: ""
                        when {
                            message.contains("401") -> ErrorType.UNAUTHORIZED
                            message.contains("400") -> ErrorType.BAD_REQUEST
                            else -> ErrorType.UNKNOWN
                        }
                    }
                }
            }

            else -> if (e.hasCauseOfType<PinningValidationException>()) {
                ErrorType.PINNING
            } else {
                ErrorType.UNKNOWN
            }
        }
    }

    /**
     * Checks if an exception is related to DNS resolution failure.
     * Handles android.system.GaiException which can be thrown by OkHttp's DNS resolver.
     */
    private fun isDnsResolutionError(e: Throwable): Boolean {
        // Check the exception class name to avoid direct import of android.system.GaiException
        // This is necessary because GaiException is not available in all Android versions
        var current: Throwable? = e
        while (current != null) {
            val className = current.javaClass.name
            val message = current.message ?: ""

            // Check for GaiException by class name
            if (className.contains("GaiException")) {
                SecureLogger.w(TAG, "DNS resolution error detected: GaiException - $message")
                return true
            }

            // Check for specific DNS error messages
            if (message.contains("EAI_NODATA", ignoreCase = true) ||
                message.contains("EAI_NONAME", ignoreCase = true) ||
                message.contains("No address associated with hostname", ignoreCase = true) ||
                message.contains("Unable to resolve host", ignoreCase = true)
            ) {
                SecureLogger.w(TAG, "DNS resolution error detected in message: $message")
                return true
            }

            current = current.cause
        }
        return false
    }

    /**
     * Validates server state and throws descriptive exceptions
     */
    fun validateServer(server: JellyfinServer?): JellyfinServer {
        if (server == null) {
            SecureLogger.w(TAG, "validateServer: Server is null - user may not be authenticated or connection was lost")
            throw IllegalStateException("Server is not available. Please check your connection and try logging in again.")
        }

        if (server.accessToken == null) {
            SecureLogger.w(TAG, "validateServer: Server has no access token - authentication may have expired")
            throw IllegalStateException("Authentication token is missing. Please log in again.")
        }

        if (server.userId == null) {
            SecureLogger.w(TAG, "validateServer: Server has no user ID - authentication may be incomplete")
            throw IllegalStateException("User authentication is incomplete. Please log in again.")
        }

        if (BuildConfig.DEBUG) {
            SecureLogger.v(TAG, "validateServer: Server validation passed for user ${server.username} on ${server.url}")
        }

        return server
    }

    /**
     * Safely parses UUID with descriptive error messages
     */
    fun parseUuid(id: String, idType: String): UUID {
        return runCatching { UUID.fromString(id) }.getOrElse {
            throw IllegalArgumentException("Invalid $idType ID: $id")
        }
    }

    /**
     * Checks if exception should be retried based on type
     */
    fun isRetryableException(e: Exception): Boolean {
        return when (getErrorType(e)) {
            ErrorType.NETWORK -> true
            ErrorType.SERVER_ERROR -> true
            ErrorType.UNAUTHORIZED -> true // Allow retry for token refresh
            else -> false
        }
    }

    /**
     * Determines if exception is a 401 authentication error
     */
    fun is401Error(e: Exception): Boolean {
        return (e is HttpException && e.code() == 401) ||
            (e is InvalidStatusException && e.message?.contains("401") == true)
    }
}

private inline fun <reified T : Throwable> Throwable.hasCauseOfType(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return true
        current = current.cause
    }
    return false
}
