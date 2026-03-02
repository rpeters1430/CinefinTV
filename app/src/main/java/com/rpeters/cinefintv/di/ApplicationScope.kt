package com.rpeters.cinefintv.di

import javax.inject.Qualifier

/**
 * Qualifier for application-scoped CoroutineScope.
 * This scope is tied to the application lifecycle and should be used for
 * app-wide background operations that need to complete even if the caller is destroyed.
 *
 * Use cases:
 * - Cache initialization and cleanup
 * - Image loader initialization
 * - Background sync operations
 *
 * Do NOT use for:
 * - UI-related operations (use ViewModelScope)
 * - Short-lived operations (use regular coroutine builders)
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
