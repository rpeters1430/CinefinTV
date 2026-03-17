package com.rpeters.cinefintv.data.security

import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Custom X509ExtendedTrustManager that implements dynamic certificate pinning with TOFU.
 *
 * This TrustManager:
 * 1. Delegates to the system's default TrustManager for standard certificate validation
 * 2. Performs additional certificate pinning validation using CertificatePinningManager
 * 3. Implements Trust-on-First-Use (TOFU) for new servers
 *
 * SECURITY FLOW:
 * - On first connection: System validates cert → Store pin (TOFU)
 * - Subsequent connections: System validates cert → Verify against stored pin
 * - Pin mismatch: Reject connection (potential MITM attack)
 *
 * Note: This TrustManager blocks to perform async pin validation. OkHttp calls
 * checkServerTrusted on a background thread, making this safe.
 */
class PinningTrustManager(
    private val systemTrustManager: X509TrustManager,
    private val certPinningManager: CertificatePinningManager,
    private val onFirstConnection: ((String, X509Certificate) -> Boolean)? = null,
) : X509ExtendedTrustManager() {

    companion object {
        private const val TAG = "PinningTrustManager"
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Client certificate validation - delegate to system
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) {
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) {
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Fallback method - no hostname available
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "checkServerTrusted called without hostname - using basic validation")
        }
        systemTrustManager.checkServerTrusted(chain, authType)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) {
        val hostname = socket?.inetAddress?.hostName
        performServerTrustedCheck(chain, authType, hostname)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) {
        val hostname = engine?.peerHost
        performServerTrustedCheck(chain, authType, hostname)
    }

    private fun performServerTrustedCheck(
        chain: Array<out X509Certificate>?,
        authType: String?,
        hostname: String?,
    ) {
        // Input validation
        if (chain == null || chain.isEmpty()) {
            throw CertificateException("Certificate chain is null or empty")
        }

        if (hostname == null) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Hostname not available - using basic validation")
            }
            systemTrustManager.checkServerTrusted(chain, authType)
            return
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Validating certificate for hostname: $hostname")
        }

        // Step 1: Perform standard certificate validation via system TrustManager
        // Android's RootTrustManager requires hostname-aware validation
        try {
            // Use reflection to call the 3-parameter version required by Android
            val method = systemTrustManager.javaClass.getMethod(
                "checkServerTrusted",
                Array<X509Certificate>::class.java,
                String::class.java,
                String::class.java,
            )
            method.invoke(systemTrustManager, chain, authType, hostname)
        } catch (e: NoSuchMethodException) {
            // Fallback to 2-parameter version if not available
            systemTrustManager.checkServerTrusted(chain, authType)
        }

        // Step 2: Perform certificate pinning validation
        val serverCert = chain[0]

        // Check certificate pinning (blocking call - OkHttp calls this on background thread)
        // CRITICAL: OkHttp's TrustManager API is synchronous, so we must use runBlocking here.
        // We verify that this is NOT called on the main thread to avoid deadlocks.
        check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            "checkServerTrusted must not be called on the main thread as it contains blocking operations"
        }

        runBlocking {
            try {
                val storedPin = certPinningManager.getStoredPinRecord(hostname)

                if (storedPin == null) {
                    // First connection to this server (TOFU)
                    handleFirstConnection(hostname, serverCert, chain.toList())
                } else {
                    // Subsequent connection - validate against stored pin
                    validateAgainstStoredPin(hostname, chain.toList(), storedPin)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    /**
     * Handles first connection to a server (TOFU model).
     */
    private suspend fun handleFirstConnection(
        hostname: String,
        serverCert: X509Certificate,
        chain: List<X509Certificate>,
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "First connection to $hostname - storing certificate pin")
        }

        // Compute the pin for this certificate
        val pin = certPinningManager.computeCertificatePin(serverCert)
        val backupPins = chain.drop(1).mapNotNull { cert ->
            runCatching { certPinningManager.computeCertificatePin(cert) }.getOrNull()
        }

        // Check if there's a callback for user confirmation
        val shouldTrust = onFirstConnection?.invoke(hostname, serverCert) ?: true

        if (shouldTrust) {
            // Store the pin for future connections
            certPinningManager.storePin(hostname, pin, backupPins)

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Trusted and stored pin for $hostname")
            }
        } else {
            // User rejected the certificate
            throw CertificateException("User rejected certificate for $hostname")
        }
    }

    /**
     * Validates certificate against stored pin.
     */
    private suspend fun validateAgainstStoredPin(
        hostname: String,
        chain: List<X509Certificate>,
        storedPin: PinnedCertificateRecord,
    ) {
        // Compute pins for all certificates in chain
        val chainPins = chain.mapNotNull { cert ->
            try {
                certPinningManager.computeCertificatePin(cert)
            } catch (e: CancellationException) {
                throw e
            }
        }

        // Check if any certificate in the chain matches the stored pin
        val matchedPin = chainPins.firstOrNull { pin ->
            pin == storedPin.primaryPin || storedPin.backupPins.contains(pin)
        }
        val certificateDetails = certPinningManager.toCertificateDetails(chain)

        if (matchedPin == null) {
            if (certPinningManager.isTemporarilyTrusted(hostname, chainPins)) {
                certPinningManager.clearTemporaryTrust(hostname)
                return
            }
            val message = "Certificate pin mismatch for $hostname (Possible MITM attack detected!)"
            Log.e(TAG, "$message - Expected: ${storedPin.primaryPin}, Got: ${chainPins.joinToString()}")
            throw PinningValidationException.PinMismatch(
                hostname = hostname,
                pinRecord = storedPin,
                attemptedPins = chainPins,
                certificateDetails = certificateDetails,
            )
        }

        if (storedPin.isExpired(System.currentTimeMillis())) {
            if (certPinningManager.isTemporarilyTrusted(hostname, chainPins)) {
                certPinningManager.clearTemporaryTrust(hostname)
                return
            }
            val message = "Certificate pin expired for $hostname"
            Log.e(TAG, "$message - First seen: ${storedPin.firstSeenEpochMillis}")
            throw PinningValidationException.PinExpired(
                hostname = hostname,
                pinRecord = storedPin,
                attemptedPins = chainPins,
                certificateDetails = certificateDetails,
            )
        }

        if (matchedPin != storedPin.primaryPin) {
            certPinningManager.promotePin(hostname, matchedPin, chainPins, storedPin)
        } else {
            certPinningManager.refreshValidation(hostname, storedPin, chainPins)
        }
        certPinningManager.clearTemporaryTrust(hostname)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Certificate pin validated successfully for $hostname")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return systemTrustManager.acceptedIssuers
    }
}
