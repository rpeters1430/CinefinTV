package com.rpeters.cinefintv.di

import com.rpeters.cinefintv.core.util.PerformanceMonitor
import com.rpeters.cinefintv.data.repository.JellyfinMediaRepository
import com.rpeters.cinefintv.data.repository.common.LibraryHealthChecker
import com.rpeters.cinefintv.data.repository.common.LibraryLoadingManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 4: Performance & Scalability Enhancements - Dependency Injection Module
 *
 * Provides all Phase 4 components for dependency injection including:
 * - Performance monitoring
 * - Optimized media loading
 */
@Module
@InstallIn(SingletonComponent::class)
object Phase4Module {

    @Provides
    @Singleton
    fun providePerformanceMonitor(): PerformanceMonitor {
        return PerformanceMonitor()
    }

    @Provides
    @Singleton
    fun provideLibraryHealthChecker(): LibraryHealthChecker {
        return LibraryHealthChecker()
    }

    @Provides
    @Singleton
    fun provideLibraryLoadingManager(
        mediaRepository: JellyfinMediaRepository,
    ): LibraryLoadingManager {
        return LibraryLoadingManager(mediaRepository)
    }
}
