package com.rpeters.cinefintv.data.repository

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple coordinator that exposes the different repository components.
 * This allows consumers to receive a single dependency when multiple
 * repositories are required together.
 */
@Singleton
class JellyfinRepositoryCoordinator @Inject constructor(
    val media: JellyfinMediaRepository,
    private val lazyUser: Lazy<JellyfinUserRepository>,
    private val lazySearch: Lazy<JellyfinSearchRepository>,
    private val lazyStream: Lazy<JellyfinStreamRepository>,
    val auth: JellyfinAuthRepository,
) {
    val user: JellyfinUserRepository get() = lazyUser.get()
    val search: JellyfinSearchRepository get() = lazySearch.get()
    val stream: JellyfinStreamRepository get() = lazyStream.get()
}
