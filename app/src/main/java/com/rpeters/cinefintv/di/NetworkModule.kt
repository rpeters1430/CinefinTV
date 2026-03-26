package com.rpeters.cinefintv.di

import android.content.Context
import coil3.ImageLoader
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.OptInAppExperimentalApis
import com.rpeters.cinefintv.data.DeviceCapabilities
import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.playback.EnhancedPlaybackManager
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import com.rpeters.cinefintv.data.repository.JellyfinStreamRepository
import com.rpeters.cinefintv.network.CachePolicyInterceptor
import com.rpeters.cinefintv.network.ConnectivityChecker
import com.rpeters.cinefintv.network.DeviceIdentityProvider
import com.rpeters.cinefintv.network.JellyfinAuthInterceptor
import com.rpeters.cinefintv.network.NetworkStateInterceptor
import com.rpeters.cinefintv.utils.DevicePerformanceProfile
import com.rpeters.cinefintv.utils.ImageLoadingOptimizer
import com.rpeters.cinefintv.utils.withStrictModeTagger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        connectivityChecker: ConnectivityChecker,
        authInterceptor: JellyfinAuthInterceptor,
        sslSocketFactory: javax.net.ssl.SSLSocketFactory,
        pinningTrustManager: com.rpeters.cinefintv.data.security.PinningTrustManager,
        hostnameVerifier: com.rpeters.cinefintv.data.security.PinningHostnameVerifier,
    ): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = okhttp3.Cache(cacheDir, 150L * 1024 * 1024) // 150 MB

        // Configure modern TLS versions and cipher suites to prevent connection aborts
        // during TLS handshake. This addresses issues where client and server cannot
        // agree on TLS version or cipher suite.
        val modernTls = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
            .build()

        // Fallback for older servers that may not support TLS 1.3
        val compatibleTls = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1)
            .build()

        val builder = OkHttpClient.Builder()
            .withStrictModeTagger()
            .cache(cache)
            // Add network state monitoring first to catch connectivity issues early
            .addInterceptor(NetworkStateInterceptor(connectivityChecker))
            .addInterceptor(authInterceptor)
            .authenticator(authInterceptor)
            .addInterceptor(CachePolicyInterceptor(connectivityChecker))
            // SECURITY: Add certificate pinning
            .sslSocketFactory(sslSocketFactory, pinningTrustManager)
            .hostnameVerifier(hostnameVerifier)
            // Configure TLS versions to avoid handshake failures
            .connectionSpecs(listOf(modernTls, compatibleTls, ConnectionSpec.CLEARTEXT))

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                // Keep header visibility for debugging, but redact sensitive auth data.
                level = HttpLoggingInterceptor.Level.HEADERS
                redactHeader("Authorization")
                redactHeader("X-Emby-Token")
                redactHeader("X-Emby-Authorization")
                redactHeader("Cookie")
                redactHeader("Set-Cookie")
            }
            builder.addInterceptor(
                loggingInterceptor,
            )
        }

        return builder
            .connectionPool(okhttp3.ConnectionPool(5, 10, TimeUnit.MINUTES))
            // Increase connect timeout to handle slower/unstable networks
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // Enable automatic retry on connection failures (including SocketException)
            .retryOnConnectionFailure(true)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }

    @Provides
    @Singleton
    fun provideJellyfinAuthInterceptor(
        authRepositoryProvider: Provider<JellyfinAuthRepository>,
        deviceIdentityProvider: DeviceIdentityProvider,
    ): JellyfinAuthInterceptor {
        return JellyfinAuthInterceptor(authRepositoryProvider, deviceIdentityProvider)
    }

    @Provides
    @Singleton
    @OptIn(OptInAppExperimentalApis::class)
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        devicePerformanceProfile: DevicePerformanceProfile,
    ): ImageLoader {
        return ImageLoadingOptimizer.buildImageLoader(context, okHttpClient, devicePerformanceProfile)
    }

    @Provides
    @Singleton
    fun provideJellyfinSdk(
        @ApplicationContext context: Context,
        deviceIdentityProvider: DeviceIdentityProvider,
    ): Jellyfin {
        return createJellyfin {
            clientInfo = ClientInfo(
                name = deviceIdentityProvider.clientName(),
                version = deviceIdentityProvider.clientVersion(),
            )
            deviceInfo = DeviceInfo(
                id = deviceIdentityProvider.deviceId(),
                name = deviceIdentityProvider.deviceName(),
            )
            this.context = context
        }
    }

    @Provides
    @Singleton
    fun provideOptimizedClientFactory(
        @ApplicationContext context: Context,
        jellyfin: Jellyfin,
        authRepositoryProvider: Provider<JellyfinAuthRepository>,
    ): OptimizedClientFactory {
        return OptimizedClientFactory(context, jellyfin, authRepositoryProvider)
    }

    @Provides
    @Singleton
    fun provideJellyfinCache(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: kotlinx.coroutines.CoroutineScope,
    ): JellyfinCache {
        return JellyfinCache(context, applicationScope)
    }

    @Provides
    @Singleton
    fun provideDeviceCapabilities(
        @ApplicationContext context: Context,
    ): DeviceCapabilities {
        return DeviceCapabilities(context)
    }

    @Provides
    @Singleton
    fun provideDevicePerformanceProfile(
        @ApplicationContext context: Context,
    ): DevicePerformanceProfile {
        return DevicePerformanceProfile.detect(context)
    }

    @Provides
    @Singleton
    fun provideEnhancedPlaybackManager(
        @ApplicationContext context: Context,
        repository: JellyfinRepository,
        streamRepository: JellyfinStreamRepository,
        deviceCapabilities: DeviceCapabilities,
        connectivityChecker: ConnectivityChecker,
        playbackPreferencesRepository: PlaybackPreferencesRepository,
    ): EnhancedPlaybackManager {
        return EnhancedPlaybackManager(
            context,
            repository,
            streamRepository,
            deviceCapabilities,
            connectivityChecker,
            playbackPreferencesRepository,
        )
    }

    @Provides
    @Singleton
    fun provideJellyfinRepository(
        sessionManager: com.rpeters.cinefintv.data.session.JellyfinSessionManager,
        secureCredentialManager: com.rpeters.cinefintv.data.SecureCredentialManager,
        @ApplicationContext context: Context,
        deviceCapabilities: DeviceCapabilities,
        authRepository: JellyfinAuthRepository,
        streamRepository: JellyfinStreamRepository,
        mediaRepository: com.rpeters.cinefintv.data.repository.JellyfinMediaRepository,
        searchRepository: com.rpeters.cinefintv.data.repository.JellyfinSearchRepository,
        connectivityChecker: ConnectivityChecker,
        analyticsHelper: com.rpeters.cinefintv.utils.AnalyticsHelper,
    ): JellyfinRepository {
        return JellyfinRepository(
            sessionManager,
            secureCredentialManager,
            context,
            deviceCapabilities,
            authRepository,
            streamRepository,
            mediaRepository,
            searchRepository,
            connectivityChecker,
            analyticsHelper,
        )
    }

    @Provides
    @Singleton
    fun provideTimeProvider(): () -> Long {
        return System::currentTimeMillis
    }
}
