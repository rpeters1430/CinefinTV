package com.rpeters.cinefintv.data.repository

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
    val user: JellyfinUserRepository,
    val search: JellyfinSearchRepository,
    val stream: JellyfinStreamRepository,
    val auth: JellyfinAuthRepository,
)
