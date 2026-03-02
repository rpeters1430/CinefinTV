package com.rpeters.cinefintv.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketException

/**
 * Interceptor that monitors network state during requests and provides
 * better error context for network failures.
 *
 * This interceptor:
 * 1. Checks network connectivity before making requests
 * 2. Enhances error messages for common network failures
 * 3. Logs network state transitions for debugging
 */
class NetworkStateInterceptor(
    private val connectivityChecker: ConnectivityChecker,
) : Interceptor {
    companion object {
        private const val TAG = "NetworkStateInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Check network connectivity before proceeding
        if (!connectivityChecker.isOnline()) {
            Log.w(TAG, "No network connectivity available for ${request.url}")
            throw IOException("No internet connection available. Please check your network settings.")
        }

        val networkType = connectivityChecker.getNetworkType()
        Log.d(TAG, "Request to ${request.url.host} via $networkType")

        return try {
            chain.proceed(request)
        } catch (e: SocketException) {
            // Enhance SocketException with network context
            val message = when {
                e.message?.contains("Software caused connection abort", ignoreCase = true) == true -> {
                    "Connection aborted - possible network switch or unstable connection ($networkType)"
                }
                e.message?.contains("Connection reset", ignoreCase = true) == true -> {
                    "Connection reset by peer - server may have closed connection prematurely ($networkType)"
                }
                else -> {
                    "Network error: ${e.message} (Network: $networkType)"
                }
            }
            Log.e(TAG, message, e)
            throw IOException(message, e)
        }
    }
}
