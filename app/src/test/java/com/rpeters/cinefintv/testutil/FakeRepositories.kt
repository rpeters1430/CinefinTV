package com.rpeters.cinefintv.testutil

import com.rpeters.cinefintv.data.repository.JellyfinAuthRepository
import com.rpeters.cinefintv.data.repository.JellyfinMediaRepository
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.JellyfinSearchRepository
import com.rpeters.cinefintv.data.repository.JellyfinStreamRepository
import io.mockk.every
import io.mockk.mockk

class FakeAuthRepository {
    val instance: JellyfinAuthRepository = mockk(relaxed = true)
}

class FakeHomeRepositories(
    val media: JellyfinMediaRepository = mockk(relaxed = true),
    val stream: JellyfinStreamRepository = mockk(relaxed = true),
) {
    val coordinator: JellyfinRepositoryCoordinator = mockk {
        every { this@mockk.media } returns this@FakeHomeRepositories.media
        every { this@mockk.stream } returns this@FakeHomeRepositories.stream
        every { this@mockk.user } returns mockk(relaxed = true)
        every { this@mockk.search } returns mockk(relaxed = true)
        every { this@mockk.auth } returns mockk(relaxed = true)
    }
}

class FakePlayerRepositories(
    val media: JellyfinMediaRepository = mockk(relaxed = true),
    val stream: JellyfinStreamRepository = mockk(relaxed = true),
) {
    val coordinator: JellyfinRepositoryCoordinator = mockk {
        every { this@mockk.media } returns this@FakePlayerRepositories.media
        every { this@mockk.stream } returns this@FakePlayerRepositories.stream
        every { this@mockk.user } returns mockk(relaxed = true)
        every { this@mockk.search } returns mockk(relaxed = true)
        every { this@mockk.auth } returns mockk(relaxed = true)
    }
}

class FakeMusicRepositories(
    val media: JellyfinMediaRepository = mockk(relaxed = true),
    val stream: JellyfinStreamRepository = mockk(relaxed = true),
) {
    val coordinator: JellyfinRepositoryCoordinator = mockk {
        every { this@mockk.media } returns this@FakeMusicRepositories.media
        every { this@mockk.stream } returns this@FakeMusicRepositories.stream
        every { this@mockk.user } returns mockk(relaxed = true)
        every { this@mockk.search } returns mockk(relaxed = true)
        every { this@mockk.auth } returns mockk(relaxed = true)
    }
}

class FakeSearchRepository {
    val instance: JellyfinSearchRepository = mockk()
}

class FakeEpisodeDetailRepositories(
    val media: JellyfinMediaRepository = mockk(relaxed = true),
    val stream: JellyfinStreamRepository = mockk(relaxed = true),
) {
    val coordinator: JellyfinRepositoryCoordinator = mockk {
        every { this@mockk.media } returns this@FakeEpisodeDetailRepositories.media
        every { this@mockk.stream } returns this@FakeEpisodeDetailRepositories.stream
        every { this@mockk.user } returns mockk(relaxed = true)
        every { this@mockk.search } returns mockk(relaxed = true)
        every { this@mockk.auth } returns mockk(relaxed = true)
    }
}

class FakeMovieDetailRepositories(
    val media: JellyfinMediaRepository = mockk(relaxed = true),
    val stream: JellyfinStreamRepository = mockk(relaxed = true),
) {
    val coordinator: JellyfinRepositoryCoordinator = mockk {
        every { this@mockk.media } returns this@FakeMovieDetailRepositories.media
        every { this@mockk.stream } returns this@FakeMovieDetailRepositories.stream
        every { this@mockk.user } returns mockk(relaxed = true)
        every { this@mockk.search } returns mockk(relaxed = true)
        every { this@mockk.auth } returns mockk(relaxed = true)
    }
}

class FakeTvShowDetailRepositories(
    val media: JellyfinMediaRepository = mockk(relaxed = true),
    val stream: JellyfinStreamRepository = mockk(relaxed = true),
) {
    val coordinator: JellyfinRepositoryCoordinator = mockk {
        every { this@mockk.media } returns this@FakeTvShowDetailRepositories.media
        every { this@mockk.stream } returns this@FakeTvShowDetailRepositories.stream
        every { this@mockk.user } returns mockk(relaxed = true)
        every { this@mockk.search } returns mockk(relaxed = true)
        every { this@mockk.auth } returns mockk(relaxed = true)
    }
}

class FakeSeasonDetailRepositories(
    val media: JellyfinMediaRepository = mockk(relaxed = true),
    val stream: JellyfinStreamRepository = mockk(relaxed = true),
) {
    val coordinator: JellyfinRepositoryCoordinator = mockk {
        every { this@mockk.media } returns this@FakeSeasonDetailRepositories.media
        every { this@mockk.stream } returns this@FakeSeasonDetailRepositories.stream
        every { this@mockk.user } returns mockk(relaxed = true)
        every { this@mockk.search } returns mockk(relaxed = true)
        every { this@mockk.auth } returns mockk(relaxed = true)
    }
}
