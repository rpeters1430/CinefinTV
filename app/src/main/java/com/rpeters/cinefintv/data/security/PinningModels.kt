package com.rpeters.cinefintv.data.security

import java.security.cert.X509Certificate

/**
 * Stored certificate pin entry with rotation metadata.
 */
data class PinnedCertificateRecord(
    val hostname: String,
    val primaryPin: String,
    val backupPins: List<String> = emptyList(),
    val firstSeenEpochMillis: Long,
    val lastValidatedEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    fun isExpired(now: Long): Boolean = now >= expiresAtEpochMillis
}

/**
 * Summaries used to render certificate details in UI layers.
 */
data class CertificateDetails(
    val subject: String,
    val issuer: String,
    val validFromEpochMillis: Long,
    val validToEpochMillis: Long,
    val pin: String,
)

/**
 * Temporary override that allows a user to proceed once after a pin mismatch/expiry.
 */
data class TemporaryPinOverride(
    val hostname: String,
    val acceptedPins: List<String>,
    val expiresAtEpochMillis: Long,
)

fun X509Certificate.toCertificateDetails(pin: String): CertificateDetails {
    return CertificateDetails(
        subject = subjectX500Principal.name,
        issuer = issuerX500Principal.name,
        validFromEpochMillis = notBefore.time,
        validToEpochMillis = notAfter.time,
        pin = pin,
    )
}
