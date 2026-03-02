package com.rpeters.cinefintv.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Process
import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Network debugging utility to help diagnose connection issues.
 * Provides detailed network connectivity information and connection testing.
 */
object NetworkDebugger {
    private const val TAG = "NetworkDebugger"
    private const val CONNECTION_TIMEOUT = 5000 // 5 seconds

    /**
     * Check detailed network connectivity status.
     */
    fun checkNetworkStatus(context: Context): NetworkStatus {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork

        if (activeNetwork == null) {
            return NetworkStatus(
                isConnected = false,
                connectionType = "None",
                isMetered = false,
                hasInternet = false,
                details = "No active network",
            )
        }

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (capabilities == null) {
            return NetworkStatus(
                isConnected = false,
                connectionType = context.getString(R.string.unknown),
                isMetered = false,
                hasInternet = false,
                details = "No network capabilities",
            )
        }

        val connectionType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val details = buildString {
            append("Transport: $connectionType")
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                append(", Unmetered")
            } else {
                append(", Metered")
            }
            if (hasInternet) {
                append(", Validated Internet")
            }
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                append(", Captive Portal Detected")
            }
        }

        return NetworkStatus(
            isConnected = true,
            connectionType = connectionType,
            isMetered = isMetered,
            hasInternet = hasInternet,
            details = details,
        )
    }

    /**
     * Test raw socket connection to a server.
     */
    suspend fun testSocketConnection(context: Context, host: String, port: Int): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                val result = withTimeoutOrNull(CONNECTION_TIMEOUT.toLong()) {
                    Socket().use { socket ->
                        // Tag network traffic to avoid StrictMode violations
                        TrafficStats.setThreadStatsTag(Process.myPid())

                        try {
                            socket.connect(InetSocketAddress(host, port), CONNECTION_TIMEOUT)
                            val endTime = System.currentTimeMillis()

                            ConnectionTestResult(
                                success = true,
                                responseTime = endTime - startTime,
                                error = null,
                                details = "Socket connection successful to $host:$port",
                            )
                        } finally {
                            // Clear the traffic stats tag
                            TrafficStats.clearThreadStatsTag()
                        }
                    }
                }

                result ?: ConnectionTestResult(
                    success = false,
                    responseTime = System.currentTimeMillis() - startTime,
                    error = "Connection timeout after ${CONNECTION_TIMEOUT}ms",
                    details = "Failed to connect to $host:$port within timeout",
                )
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Extract host and port from URL.
     */
    fun parseHostPort(url: String): Pair<String, Int>? {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return null
            val port = if (uri.port != -1) {
                uri.port
            } else {
                when (uri.scheme?.lowercase()) {
                    "https" -> 443
                    "http" -> 80
                    else -> return null
                }
            }
            Pair(host, port)
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Log comprehensive network diagnostics.
     */
    fun logNetworkDiagnostics(context: Context, serverUrl: String?) {
        if (!BuildConfig.DEBUG) return

        Log.d(TAG, "=== Network Diagnostics ===")

        val networkStatus = checkNetworkStatus(context)
        Log.d(TAG, "Network Status: $networkStatus")

        if (serverUrl != null) {
            val hostPort = parseHostPort(serverUrl)
            if (hostPort != null) {
                Log.d(TAG, "Server: ${hostPort.first}:${hostPort.second}")
                Log.d(TAG, "Will test socket connection asynchronously")
            } else {
                Log.w(TAG, "Could not parse server URL: $serverUrl")
            }
        }

        Log.d(TAG, "============================")
    }

    /**
     * Test server connectivity and log results.
     */
    suspend fun testAndLogServerConnectivity(context: Context, serverUrl: String) {
        if (!BuildConfig.DEBUG) return

        Log.d(TAG, "Testing server connectivity: $serverUrl")

        val hostPort = parseHostPort(serverUrl)
        if (hostPort == null) {
            Log.w(TAG, "Could not parse server URL for testing: $serverUrl")
            return
        }

        val result = testSocketConnection(context, hostPort.first, hostPort.second)
        Log.d(TAG, "Connection test result: $result")

        if (!result.success) {
            Log.w(TAG, "Server connectivity issue detected - this may cause API failures")

            // Additional diagnostics
            val networkStatus = checkNetworkStatus(context)
            if (!networkStatus.hasInternet) {
                Log.w(TAG, "Device has no validated internet connection")
            } else {
                Log.i(TAG, "Device internet is working, server may be unreachable")
            }
        }
    }
}

/**
 * Network connectivity status information.
 */
data class NetworkStatus(
    val isConnected: Boolean,
    val connectionType: String,
    val isMetered: Boolean,
    val hasInternet: Boolean,
    val details: String,
) {
    override fun toString(): String {
        return "Connected: $isConnected, Type: $connectionType, Internet: $hasInternet, Details: $details"
    }
}

/**
 * Connection test result information.
 */
data class ConnectionTestResult(
    val success: Boolean,
    val responseTime: Long,
    val error: String?,
    val details: String,
) {
    override fun toString(): String {
        return if (success) {
            "Success in ${responseTime}ms - $details"
        } else {
            "Failed in ${responseTime}ms - Error: $error, Details: $details"
        }
    }
}
