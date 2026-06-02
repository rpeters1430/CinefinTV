package com.rpeters.cinefintv.data.repository

import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.common.DispatcherProvider
import com.rpeters.cinefintv.data.repository.common.BaseJellyfinRepository
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import com.rpeters.cinefintv.ui.player.SkipRange
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class IntroSkipperResponse(
    @SerialName("Valid") val valid: Boolean = false,
    @SerialName("Introduction") val introduction: IntroSkipperSegment? = null,
    @SerialName("Credits") val credits: IntroSkipperSegment? = null,
)

@Serializable
data class IntroSkipperSegment(
    @SerialName("Start") val start: Double,
    @SerialName("End") val end: Double,
)

data class IntroSkipperSegments(
    val intro: SkipRange?,
    val credits: SkipRange?,
)

@Singleton
open class IntroSkipperRepository @Inject constructor(
    authRepository: JellyfinAuthRepository,
    sessionManager: JellyfinSessionManager,
    cache: JellyfinCache,
    dispatchers: DispatcherProvider,
    private val okHttpClient: OkHttpClient,
) : BaseJellyfinRepository(authRepository, sessionManager, cache, dispatchers) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Executes an HTTP call. Separated to allow overriding in tests without Firebase instrumentation. */
    protected open fun executeCall(request: Request): Response = okHttpClient.newCall(request).execute()

    suspend fun getSegments(itemId: String): IntroSkipperSegments? = withContext(dispatchers.io) {
        try {
            val server = validateServer()
            val url = "${server.url}/Episode/$itemId/IntroTimestamps"
            val request = Request.Builder()
                .url(url)
                .header("X-Emby-Token", server.accessToken ?: "")
                .build()

            val body = executeCall(request).use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body.string()
            }

            val parsed = json.decodeFromString<IntroSkipperResponse>(body)
            if (!parsed.valid) return@withContext null

            IntroSkipperSegments(
                intro = parsed.introduction?.toSkipRange(),
                credits = parsed.credits?.toSkipRange(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.w("IntroSkipperRepository", "Failed to fetch timestamps for $itemId: ${e.message}")
            null
        }
    }

    private fun IntroSkipperSegment.toSkipRange() = SkipRange(
        startMs = (start * 1000).toLong(),
        endMs = (end * 1000).toLong(),
    )
}
