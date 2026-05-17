package com.rpeters.cinefintv.data.repository

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

data class DiscoveredServer(
    val serviceName: String,
    val host: String,
    val port: Int,
    val url: String,
)

class ServerDiscoveryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun discoverServers(): Flow<List<DiscoveredServer>> = callbackFlow {
        val found = mutableMapOf<String, DiscoveredServer>()
        // NsdManager only supports one active resolve at a time on API < 34 — serialize with a channel
        val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        launch {
            for (serviceInfo in resolveQueue) {
                val resolved = suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            cont.resume(null)
                        }
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            cont.resume(info)
                        }
                    })
                } ?: continue

                val host = resolved.host?.hostAddress ?: continue
                val port = resolved.port
                val scheme = if (port == 443 || port == 8920) "https" else "http"
                found[resolved.serviceName] = DiscoveredServer(
                    serviceName = resolved.serviceName,
                    host = host,
                    port = port,
                    url = "$scheme://$host:$port",
                )
                trySend(found.values.toList())
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName.contains("jellyfin", ignoreCase = true)) {
                    resolveQueue.trySend(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                found.remove(serviceInfo.serviceName)
                trySend(found.values.toList())
            }
        }

        trySend(emptyList())
        nsdManager.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            resolveQueue.close()
            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
        }
    }
}
