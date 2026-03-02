package com.rpeters.cinefintv.data.security

import android.util.Log
import com.rpeters.cinefintv.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CertificatePinner
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SSL certificate pinning for Jellyfin servers.
 *
 * Adds rotation metadata, backup pins, expiry enforcement, and temporary overrides.
 */
@Singleton
class CertificatePinningManager @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {

    companion object {
        private const val TAG = "CertificatePinningManager"
        private const val PIN_PREFIX = "cert_pin_"
        private const val HASH_ALGORITHM = "SHA-256"
        private const val PIN_EXPIRY_DAYS_DEFAULT = 90L
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
        private const val TEMP_TRUST_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    }

    private val defaultExpiryMillis = PIN_EXPIRY_DAYS_DEFAULT * MILLIS_PER_DAY
    private val overrideMutex = Mutex()
    private val temporaryOverrides = mutableMapOf<String, TemporaryPinOverride>()

    /**
     * Gets the stored certificate pin record for a hostname, migrating legacy pins when found.
     */
    suspend fun getStoredPinRecord(hostname: String): PinnedCertificateRecord? {
        val key = getPinKey(hostname)
        val storedValue = encryptedPreferences.getEncryptedString(key).firstOrNull()
        if (storedValue.isNullOrEmpty()) return null

        val record = parsePinRecord(hostname, storedValue) ?: return null

        // Persist migrated records so future reads include metadata
        if (!storedValue.trimStart().startsWith("{")) {
            storeRecord(record)
        }

        return record
    }

    /**
     * Stores a certificate pin for a hostname with optional backup pins.
     */
    suspend fun storePin(
        hostname: String,
        pin: String,
        backupPins: List<String> = emptyList(),
        firstSeenEpochMillis: Long? = null,
    ) {
        val now = timeProvider()
        val firstSeen = firstSeenEpochMillis ?: now
        val record = PinnedCertificateRecord(
            hostname = hostname,
            primaryPin = pin,
            backupPins = backupPins.distinct().filterNot { it == pin },
            firstSeenEpochMillis = firstSeen,
            lastValidatedEpochMillis = now,
            expiresAtEpochMillis = now + defaultExpiryMillis,
        )
        storeRecord(record)
    }

    /**
     * Removes a certificate pin (e.g., if certificate is compromised or changed).
     */
    suspend fun removePin(hostname: String) {
        val key = getPinKey(hostname)
        encryptedPreferences.removeKey(key)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Removed certificate pin for $hostname")
        }
    }

    /**
     * Computes the SHA-256 hash of a certificate's public key.
     */
    fun computeCertificatePin(certificate: Certificate): String {
        require(certificate is X509Certificate) {
            "Certificate must be X509Certificate"
        }

        val publicKeyBytes = certificate.publicKey.encoded
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val hash = digest.digest(publicKeyBytes)

        return android.util.Base64.encodeToString(
            hash,
            android.util.Base64.NO_WRAP,
        )
    }

    /**
     * Validates a certificate chain against stored pins and rotation metadata.
     *
     * @throws PinningValidationException if pins don't match or are expired
     */
    suspend fun validatePins(hostname: String, certificates: List<Certificate>) {
        val record = getStoredPinRecord(hostname) ?: return
        val x509Chain = certificates.filterIsInstance<X509Certificate>()
        val chainPins = x509Chain.mapNotNull { cert ->
            try {
                computeCertificatePin(cert)
            } catch (e: CancellationException) {
                throw e
            }
        }

        val matchedPin = chainPins.firstOrNull { pin ->
            pin == record.primaryPin || record.backupPins.contains(pin)
        }

        if (matchedPin == null) {
            throw PinningValidationException.PinMismatch(
                hostname = hostname,
                pinRecord = record,
                attemptedPins = chainPins,
                certificateDetails = toCertificateDetails(x509Chain),
            )
        }

        val now = timeProvider()
        if (record.isExpired(now)) {
            throw PinningValidationException.PinExpired(
                hostname = hostname,
                pinRecord = record,
                attemptedPins = chainPins,
                certificateDetails = toCertificateDetails(x509Chain),
            )
        }

        // Refresh metadata and optionally rotate the primary pin
        if (matchedPin != record.primaryPin) {
            promotePin(hostname, matchedPin, chainPins, record)
        } else {
            refreshValidation(hostname, record, chainPins)
        }
    }

    /**
     * Creates an OkHttp CertificatePinner for a specific hostname.
     */
    fun createPinner(hostname: String, pins: List<String>): CertificatePinner {
        val builder = CertificatePinner.Builder()

        pins.forEach { pin ->
            builder.add(hostname, "sha256/$pin")
        }

        return builder.build()
    }

    /**
     * Extracts hostname from a Jellyfin server URL.
     */
    fun extractHostname(serverUrl: String): String {
        return try {
            val url = java.net.URL(serverUrl)
            url.host
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Returns all pinned certificates, sorted by hostname.
     */
    suspend fun getPinnedCertificates(): List<PinnedCertificateRecord> {
        return try {
            val entries = encryptedPreferences.getEntriesWithPrefix(PIN_PREFIX)
            entries.mapNotNull { (hostname, raw) ->
                parsePinRecord(hostname, raw)?.also { record ->
                    if (!raw.trimStart().startsWith("{")) {
                        storeRecord(record)
                    }
                }
            }.sortedBy { it.hostname }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Allows a temporary trust decision for the given pins.
     */
    suspend fun allowTemporaryTrust(hostname: String, pins: List<String>) {
        val distinctPins = pins.distinct()
        if (distinctPins.isEmpty()) {
            Log.w(TAG, "allowTemporaryTrust called with no pins for hostname: $hostname; ignoring.")
            return
        }
        overrideMutex.withLock {
            val expiry = timeProvider() + TEMP_TRUST_DURATION_MS
            temporaryOverrides[hostname] = TemporaryPinOverride(
                hostname = hostname,
                acceptedPins = distinctPins,
                expiresAtEpochMillis = expiry,
            )
        }
    }

    /**
     * Checks if the chain matches a temporary trust decision, cleaning up expired entries.
     */
    suspend fun isTemporarilyTrusted(hostname: String, chainPins: List<String>): Boolean {
        return overrideMutex.withLock {
            val now = timeProvider()
            temporaryOverrides.entries.removeIf { it.value.expiresAtEpochMillis <= now }
            val override = temporaryOverrides[hostname] ?: return@withLock false
            override.acceptedPins.any { chainPins.contains(it) }
        }
    }

    /**
     * Clears a temporary trust entry after a successful override.
     */
    suspend fun clearTemporaryTrust(hostname: String) {
        overrideMutex.withLock {
            temporaryOverrides.remove(hostname)
        }
    }

    /**
     * Clears all stored certificate pins.
     * SECURITY WARNING: Only call this during app reset or if user explicitly requests it.
     */
    suspend fun clearAllPins() {
        val entries = encryptedPreferences.getEntriesWithPrefix(PIN_PREFIX)
        entries.keys.forEach { hostname ->
            removePin(hostname)
        }
        overrideMutex.withLock {
            temporaryOverrides.clear()
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Cleared all certificate pins (${entries.size} hosts)")
        }
    }

    internal suspend fun toCertificateDetails(chain: List<X509Certificate>): List<CertificateDetails> {
        return chain.mapNotNull { cert ->
            try {
                val pin = computeCertificatePin(cert)
                cert.toCertificateDetails(pin)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    internal suspend fun promotePin(
        hostname: String,
        newPrimary: String,
        chainPins: List<String>,
        existingRecord: PinnedCertificateRecord,
    ) {
        val now = timeProvider()
        val backups = mergeBackups(existingRecord, chainPins, newPrimary)
        val rotated = existingRecord.copy(
            primaryPin = newPrimary,
            backupPins = backups,
            lastValidatedEpochMillis = now,
            expiresAtEpochMillis = now + defaultExpiryMillis,
        )
        storeRecord(rotated)
    }

    internal suspend fun refreshValidation(
        hostname: String,
        record: PinnedCertificateRecord,
        chainPins: List<String>,
    ) {
        val now = timeProvider()
        val updated = record.copy(
            backupPins = mergeBackups(record, chainPins, record.primaryPin),
            lastValidatedEpochMillis = now,
            expiresAtEpochMillis = now + defaultExpiryMillis,
        )
        storeRecord(updated)
    }

    private fun mergeBackups(
        record: PinnedCertificateRecord,
        chainPins: List<String>,
        primaryPin: String,
    ): List<String> {
        return (record.backupPins + chainPins + record.primaryPin)
            .distinct()
            .filterNot { it == primaryPin }
    }

    private suspend fun storeRecord(record: PinnedCertificateRecord) {
        val serialized = serializeRecord(record)
        encryptedPreferences.putEncryptedString(getPinKey(record.hostname), serialized)

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Stored certificate pin for ${record.hostname} (backups=${record.backupPins.size}, expires=${record.expiresAtEpochMillis})",
            )
        }
    }

    private fun serializeRecord(record: PinnedCertificateRecord): String {
        val json = JSONObject()
        json.put("v", 1)
        json.put("pin", record.primaryPin)
        json.put("backups", JSONArray(record.backupPins))
        json.put("firstSeen", record.firstSeenEpochMillis)
        json.put("lastValidated", record.lastValidatedEpochMillis)
        json.put("expiresAt", record.expiresAtEpochMillis)
        return json.toString()
    }

    private fun parsePinRecord(hostname: String, raw: String): PinnedCertificateRecord? {
        return try {
            if (raw.trimStart().startsWith("{")) {
                val json = JSONObject(raw)
                val primaryPin = json.optString("pin", "").ifBlank { return null }
                val backupPins = json.optJSONArray("backups")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optString(index, null)
                    }
                } ?: emptyList()
                val firstSeen = json.optLong("firstSeen", timeProvider())
                val lastValidated = json.optLong("lastValidated", firstSeen)
                val expiresAt = json.optLong("expiresAt", firstSeen + defaultExpiryMillis)

                PinnedCertificateRecord(
                    hostname = hostname,
                    primaryPin = primaryPin,
                    backupPins = backupPins,
                    firstSeenEpochMillis = firstSeen,
                    lastValidatedEpochMillis = lastValidated,
                    expiresAtEpochMillis = expiresAt,
                )
            } else {
                val now = timeProvider()
                PinnedCertificateRecord(
                    hostname = hostname,
                    primaryPin = raw,
                    backupPins = emptyList(),
                    firstSeenEpochMillis = now,
                    lastValidatedEpochMillis = now,
                    expiresAtEpochMillis = now + defaultExpiryMillis,
                )
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    private fun getPinKey(hostname: String): String {
        return "$PIN_PREFIX$hostname"
    }
}

/**
 * Certificate trust decision for Trust-on-First-Use (TOFU) model.
 */
data class CertificateTrustDecision(
    val hostname: String,
    val certificate: X509Certificate,
    val pin: String,
    val isFirstConnection: Boolean,
    val shouldTrust: Boolean = false,
)
