package com.rpeters.cinefintv.data.security

import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession

/**
 * Simple HostnameVerifier that delegates to the default verifier.
 *
 * Note: With X509ExtendedTrustManager, hostname is automatically passed
 * to checkServerTrusted, so we just use standard hostname verification here.
 */
class PinningHostnameVerifier : HostnameVerifier {

    companion object {
        private const val TAG = "PinningHostnameVerifier"

        // Use the default HTTPS hostname verifier
        private val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
    }

    override fun verify(hostname: String?, session: SSLSession?): Boolean {
        if (hostname == null || session == null) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Hostname or session is null")
            }
            return false
        }

        // Delegate to default hostname verifier
        val verified = defaultVerifier.verify(hostname, session)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Hostname verification for $hostname: $verified")
        }

        return verified
    }
}
