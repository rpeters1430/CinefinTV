package com.rpeters.cinefintv.utils

import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import java.util.regex.Pattern

/**
 * Secure logging utility that prevents sensitive information from being logged.
 *
 * Features:
 * - Automatic sanitization of tokens, passwords, and API keys
 * - Debug-only logging to prevent production data leaks
 * - Structured logging levels with consistent formatting
 * - Support for exception logging with stack traces
 */
object SecureLogger {

    private const val MAX_LOG_LENGTH = 4000 // Android Log limit

    // Control log verbosity - set to false to reduce excessive logging
    var enableVerboseLogging: Boolean = BuildConfig.DEBUG

    // Patterns to identify and sanitize sensitive data
    private val SENSITIVE_PATTERNS = listOf(
        // Authentication tokens
        Pattern.compile("(token|access_token|refresh_token|api_key|apikey)([\"\\s=:]+)([^\\s&\"\\}]+)", Pattern.CASE_INSENSITIVE),
        // Passwords
        Pattern.compile("(password|pwd|passwd)([\"\\s=:]+)([^\\s&\"\\}]+)", Pattern.CASE_INSENSITIVE),
        // Authorization headers
        Pattern.compile("(authorization|auth)([\"\\s=:]+)([^\\s&\"\\}]+)", Pattern.CASE_INSENSITIVE),
        // Session IDs
        Pattern.compile("(sessionid|session_id|jsessionid)([\"\\s=:]+)([^\\s&\"\\}]+)", Pattern.CASE_INSENSITIVE),
        // User credentials in URLs
        Pattern.compile("(https?://[^:/]+):([^@]+)@", Pattern.CASE_INSENSITIVE),
        // Android app data paths - redact full package-specific paths
        Pattern.compile("/data/(data|app|user)/[^\\s]+", Pattern.CASE_INSENSITIVE),
        // Android UIDs (typically 5-6 digits starting with 10000+)
        Pattern.compile("\\bUID\\s*[=:]?\\s*\\d{5,6}\\b", Pattern.CASE_INSENSITIVE),
        // Process IDs
        Pattern.compile("\\b(pid|PID)[=:\\s]+(\\d+)\\b"),
        // Android package paths with random identifiers
        Pattern.compile("~~[A-Za-z0-9+/=_-]+~~/"),
    )

    private const val REPLACEMENT = "$1$2***"

    /**
     * Log debug message with automatic sanitization.
     * Only logs in debug builds when verbose logging is enabled.
     */
    fun d(tag: String, message: String, data: Any? = null) {
        if (BuildConfig.DEBUG && enableVerboseLogging) {
            val sanitizedMessage = sanitizeForLogging(message)
            val sanitizedData = data?.let { sanitizeForLogging(it.toString()) } ?: ""
            val fullMessage = if (sanitizedData.isNotEmpty()) "$sanitizedMessage | Data: $sanitizedData" else sanitizedMessage
            logChunked(Log.DEBUG, tag, fullMessage)
        }
    }

    /**
     * Log verbose debug message - can be completely disabled in production.
     * Use this for very frequent or detailed logging that may cause performance issues.
     */
    fun v(tag: String, message: String, data: Any? = null) {
        // Verbose logging - only in debug with verbose enabled
        if (BuildConfig.DEBUG && enableVerboseLogging) {
            d(tag, "[VERBOSE] $message", data)
        }
    }

    /**
     * Log info message with automatic sanitization.
     */
    fun i(tag: String, message: String, data: Any? = null) {
        val sanitizedMessage = sanitizeForLogging(message)
        val sanitizedData = data?.let { sanitizeForLogging(it.toString()) } ?: ""
        val fullMessage = if (sanitizedData.isNotEmpty()) "$sanitizedMessage | Data: $sanitizedData" else sanitizedMessage
        logChunked(Log.INFO, tag, fullMessage)
    }

    /**
     * Log warning message with automatic sanitization.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val sanitizedMessage = sanitizeForLogging(message)
        if (throwable != null) {
            Log.w(tag, sanitizedMessage, throwable)
        } else {
            logChunked(Log.WARN, tag, sanitizedMessage)
        }
    }

    /**
     * Log error message with automatic sanitization.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val sanitizedMessage = sanitizeForLogging(message)
        if (throwable != null) {
            Log.e(tag, sanitizedMessage, throwable)
        } else {
            logChunked(Log.ERROR, tag, sanitizedMessage)
        }
    }

    /**
     * Log authentication-related messages with extra security.
     */
    fun auth(tag: String, message: String, success: Boolean = true) {
        if (BuildConfig.DEBUG) {
            val status = if (success) "SUCCESS" else "FAILURE"
            val sanitizedMessage = sanitizeForLogging(message)
            d(tag, "AUTH [$status]: $sanitizedMessage")
        }
    }

    /**
     * Log API requests with automatic URL sanitization.
     */
    fun api(tag: String, method: String, url: String, responseCode: Int? = null) {
        if (BuildConfig.DEBUG) {
            val sanitizedUrl = sanitizeUrl(url)
            val response = responseCode?.let { " | Response: $it" } ?: ""
            d(tag, "API [$method]: $sanitizedUrl$response")
        }
    }

    /**
     * Log network errors with sanitized details.
     */
    fun networkError(tag: String, error: String, url: String? = null, throwable: Throwable? = null) {
        val sanitizedError = sanitizeForLogging(error)
        val sanitizedUrl = url?.let { " | URL: ${sanitizeUrl(it)}" } ?: ""
        e(tag, "NETWORK ERROR: $sanitizedError$sanitizedUrl", throwable)
    }

    /**
     * Sanitize sensitive information from log messages.
     */
    private fun sanitizeForLogging(text: String): String {
        var sanitized = text

        // Apply credential/token patterns with $1$2*** replacement
        for (i in 0..4) {
            sanitized = SENSITIVE_PATTERNS[i].matcher(sanitized).replaceAll(REPLACEMENT)
        }

        // Apply path/UID patterns with simple *** replacement
        for (i in 5 until SENSITIVE_PATTERNS.size) {
            sanitized = SENSITIVE_PATTERNS[i].matcher(sanitized).replaceAll("***")
        }

        // Additional sanitization for common JWT tokens
        sanitized = sanitized.replace(Regex("eyJ[A-Za-z0-9+/=._-]{20,}"), "eyJ***")

        // Sanitize UUID-like strings that might be session IDs
        sanitized = sanitized.replace(
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE),
            "UUID-***",
        )

        // Sanitize file paths more aggressively
        sanitized = sanitized.replace(Regex("/data/[^\\s]+"), "/data/***")
        sanitized = sanitized.replace(Regex("/storage/emulated/\\d+/[^\\s]*"), "/storage/emulated/***/")

        // Limit length to prevent oversized logs
        return if (sanitized.length > MAX_LOG_LENGTH) {
            sanitized.take(MAX_LOG_LENGTH - 3) + "..."
        } else {
            sanitized
        }
    }

    /**
     * Sanitize URLs by removing credentials and sensitive query parameters.
     */
    private fun sanitizeUrl(url: String): String {
        // Remove user credentials from URLs
        val withoutCredentials = url.replace(Regex("(https?://)[^:/]+:[^@]+@"), "$1***:***@")

        // Sanitize common sensitive query parameters
        return withoutCredentials.replace(
            Regex("([?&])(token|api_key|apikey|password|auth)=([^&]+)", RegexOption.IGNORE_CASE),
            "$1$2=***",
        )
    }

    /**
     * Log long messages in chunks to avoid Android's log limit.
     */
    private fun logChunked(priority: Int, tag: String, message: String) {
        if (message.length <= MAX_LOG_LENGTH) {
            Log.println(priority, tag, message)
            return
        }

        var index = 0
        while (index < message.length) {
            val end = minOf(index + MAX_LOG_LENGTH, message.length)
            val chunk = message.substring(index, end)
            val chunkTag = if (index > 0) "$tag[${index / MAX_LOG_LENGTH + 1}]" else tag
            Log.println(priority, chunkTag, chunk)
            index = end
        }
    }
}

/**
 * Extension functions for easier logging in classes.
 */
inline fun <reified T> T.logDebug(message: String, data: Any? = null) {
    SecureLogger.d(T::class.java.simpleName, message, data)
}

inline fun <reified T> T.logInfo(message: String, data: Any? = null) {
    SecureLogger.i(T::class.java.simpleName, message, data)
}

inline fun <reified T> T.logWarning(message: String, throwable: Throwable? = null) {
    SecureLogger.w(T::class.java.simpleName, message, throwable)
}

inline fun <reified T> T.logError(message: String, throwable: Throwable? = null) {
    SecureLogger.e(T::class.java.simpleName, message, throwable)
}

inline fun <reified T> T.logAuth(message: String, success: Boolean = true) {
    SecureLogger.auth(T::class.java.simpleName, message, success)
}

inline fun <reified T> T.logApi(method: String, url: String, responseCode: Int? = null) {
    SecureLogger.api(T::class.java.simpleName, method, url, responseCode)
}

inline fun <reified T> T.logNetworkError(error: String, url: String? = null, throwable: Throwable? = null) {
    SecureLogger.networkError(T::class.java.simpleName, error, url, throwable)
}
