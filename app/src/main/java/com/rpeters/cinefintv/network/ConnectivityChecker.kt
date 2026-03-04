package com.rpeters.cinefintv.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced connectivity checker with network state monitoring.
 *
 * Provides:
 * - Current network connectivity status
 * - Real-time network state changes via Flow
 * - Network type detection (WiFi vs Cellular)
 */
@Singleton
class ConnectivityChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ConnectivityChecker"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Check if device currently has internet connectivity.
     */
    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Get current network type (WiFi, Cellular, or Other/None).
     */
    fun getNetworkType(): NetworkType {
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
    }

    /**
     * Check if the current connection is metered.
     */
    fun isMetered(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * Get estimated downstream bandwidth in Kbps.
     */
    fun getEstimatedBandwidth(): Int {
        val network = connectivityManager.activeNetwork ?: return -1
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return -1
        return caps.linkDownstreamBandwidthKbps
    }

    /**
     * Get a network quality assessment based on type and meteredness.
     */
    fun getNetworkQuality(): ConnectivityQuality {
        val type = getNetworkType()
        val metered = isMetered()
        val bandwidth = getEstimatedBandwidth()

        return when {
            type == NetworkType.ETHERNET -> ConnectivityQuality.EXCELLENT
            type == NetworkType.WIFI && !metered -> {
                if (bandwidth > 50000) ConnectivityQuality.EXCELLENT else ConnectivityQuality.GOOD
            }
            type == NetworkType.WIFI && metered -> ConnectivityQuality.FAIR
            type == NetworkType.CELLULAR -> {
                if (bandwidth > 20000) ConnectivityQuality.GOOD else ConnectivityQuality.FAIR
            }
            else -> ConnectivityQuality.POOR
        }
    }

    /**
     * Observe network connectivity changes in real-time.
     * Emits true when connected, false when disconnected.
     */
    fun observeNetworkConnectivity(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            private val availableNetworks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                availableNetworks.add(network)
                trySend(true)
                Log.d(TAG, "Network available: $network")
            }

            override fun onLost(network: Network) {
                availableNetworks.remove(network)
                trySend(availableNetworks.isNotEmpty())
                Log.d(TAG, "Network lost: $network (${availableNetworks.size} remaining)")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                val isConnected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(isConnected)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state
        trySend(isOnline())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Observe network type changes (WiFi <-> Cellular switches).
     */
    fun observeNetworkType(): Flow<NetworkType> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getNetworkType())
            }

            override fun onLost(network: Network) {
                trySend(getNetworkType())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(getNetworkType())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state
        trySend(getNetworkType())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}

/**
 * Enum representing network quality assessment.
 */
enum class ConnectivityQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
}

/**
 * Enum representing different network types.
 */
enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
    NONE,
}
