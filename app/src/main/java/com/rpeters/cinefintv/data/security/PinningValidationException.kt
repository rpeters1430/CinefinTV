package com.rpeters.cinefintv.data.security

import java.security.cert.CertificateException

sealed class PinningValidationException(
    val hostname: String,
    val pinRecord: PinnedCertificateRecord?,
    val attemptedPins: List<String>,
    val certificateDetails: List<CertificateDetails>,
    message: String,
) : CertificateException(message) {

    class PinMismatch(
        hostname: String,
        pinRecord: PinnedCertificateRecord?,
        attemptedPins: List<String>,
        certificateDetails: List<CertificateDetails>,
    ) : PinningValidationException(
        hostname = hostname,
        pinRecord = pinRecord,
        attemptedPins = attemptedPins,
        certificateDetails = certificateDetails,
        message = "Certificate pin mismatch for $hostname",
    )

    class PinExpired(
        hostname: String,
        pinRecord: PinnedCertificateRecord?,
        attemptedPins: List<String>,
        certificateDetails: List<CertificateDetails>,
    ) : PinningValidationException(
        hostname = hostname,
        pinRecord = pinRecord,
        attemptedPins = attemptedPins,
        certificateDetails = certificateDetails,
        message = "Stored certificate pin expired for $hostname",
    )
}
