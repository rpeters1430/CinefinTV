package com.rpeters.cinefintv.di

import com.rpeters.cinefintv.core.util.PerformanceMonitor
import com.rpeters.cinefintv.data.repository.JellyfinMediaRepository
import com.rpeters.cinefintv.data.repository.common.LibraryHealthChecker
import com.rpeters.cinefintv.data.repository.common.LibraryLoadingManager
// SharedAppStateManager will be provided once the UI viewmodel layer is copied (Task 11+)
// import com.rpeters.cinefintv.ui.viewmodel.common.SharedAppStateManager
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
 * Note: SharedAppStateManager provision moved here once UI layer is available (Task 24 cleanup)
 */
@Module
@InstallIn(SingletonComponent::class)
object Phase4Module {

    // TODO Task 24: restore provideSharedAppStateManager() once UI layer is copied
    // @Provides
    // @Singleton
    // fun provideSharedAppStateManager(): SharedAppStateManager {
    //     return SharedAppStateManager()
    // }

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
