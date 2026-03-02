package com.rpeters.cinefintv.di

import android.content.Context
import com.rpeters.cinefintv.data.security.CertificatePinningManager
import com.rpeters.cinefintv.data.security.EncryptedPreferences
import com.rpeters.cinefintv.data.security.PinningHostnameVerifier
import com.rpeters.cinefintv.data.security.PinningTrustManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.KeyStore
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Security Module - Provides security-related components
 *
 * This module provides:
 * - EncryptedPreferences for secure data storage
 * - CertificatePinningManager for SSL certificate pinning
 * - Custom SSL components for certificate pinning
 * - PinningTrustManager for dynamic TOFU certificate validation
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideEncryptedPreferences(
        @ApplicationContext context: Context,
    ): EncryptedPreferences {
        return EncryptedPreferences(context)
    }

    @Provides
    @Singleton
    fun provideCertificatePinningManager(
        encryptedPreferences: EncryptedPreferences,
        timeProvider: () -> Long,
    ): CertificatePinningManager {
        return CertificatePinningManager(
            encryptedPreferences = encryptedPreferences,
            timeProvider = timeProvider,
        )
    }

    /**
     * Provides the system's default X509TrustManager.
     * This is used as the base validator for standard certificate checks.
     */
    @Provides
    @Singleton
    fun provideSystemTrustManager(): X509TrustManager {
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm(),
        )
        trustManagerFactory.init(null as KeyStore?)

        val trustManagers = trustManagerFactory.trustManagers
        check(trustManagers.size == 1 && trustManagers[0] is X509TrustManager) {
            "Unexpected default trust managers: ${trustManagers.contentToString()}"
        }

        return trustManagers[0] as X509TrustManager
    }

    /**
     * Provides custom PinningTrustManager that adds certificate pinning
     * on top of standard certificate validation.
     */
    @Provides
    @Singleton
    fun providePinningTrustManager(
        systemTrustManager: X509TrustManager,
        certPinningManager: CertificatePinningManager,
    ): PinningTrustManager {
        // Note: onFirstConnection callback could be provided here for UI prompts
        // For now, auto-trust on first connection (TOFU model)
        return PinningTrustManager(
            systemTrustManager = systemTrustManager,
            certPinningManager = certPinningManager,
            onFirstConnection = null, // Auto-trust (TOFU)
        )
    }

    /**
     * Provides SSLContext configured with our custom TrustManager.
     */
    @Provides
    @Singleton
    fun provideSslContext(
        pinningTrustManager: PinningTrustManager,
    ): SSLContext {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(pinningTrustManager), null)
        return sslContext
    }

    /**
     * Provides SSLSocketFactory from our configured SSLContext.
     */
    @Provides
    @Singleton
    fun provideSslSocketFactory(
        sslContext: SSLContext,
    ): SSLSocketFactory {
        return sslContext.socketFactory
    }

    /**
     * Provides custom HostnameVerifier for SSL connections.
     *
     * Note: With X509ExtendedTrustManager, the hostname is automatically
     * passed to checkServerTrusted, so this just provides standard verification.
     */
    @Provides
    @Singleton
    fun provideHostnameVerifier(): PinningHostnameVerifier {
        return PinningHostnameVerifier()
    }
}
