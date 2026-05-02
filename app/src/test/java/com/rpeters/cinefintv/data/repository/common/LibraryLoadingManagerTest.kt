package com.rpeters.cinefintv.data.repository.common

import com.rpeters.cinefintv.data.repository.JellyfinMediaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryLoadingManagerTest {

    @Test(timeout = 2_000L)
    fun loadLibraries_concurrentCallersCompleteWithoutDeadlocking() = runTest {
        val mediaRepository = mockk<JellyfinMediaRepository>()
        val manager = LibraryLoadingManager(mediaRepository)
        val enteredRequest = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()

        coEvery { mediaRepository.getUserLibraries(any()) } coAnswers {
            enteredRequest.complete(Unit)
            releaseRequest.await()
            ApiResult.Success(emptyList())
        }

        val first = async { manager.loadLibraries() }
        enteredRequest.await()
        val second = async { manager.loadLibraries() }
        releaseRequest.complete(Unit)

        val results = awaitAll(first, second)

        assertEquals(2, results.size)
        assertTrue(results.all { it is ApiResult.Success })
        coVerify(atLeast = 1) { mediaRepository.getUserLibraries(false) }
    }
}
