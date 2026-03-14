package com.rpeters.cinefintv.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.data.PlaybackPositionStore
import com.rpeters.cinefintv.data.playback.AdaptiveBitrateMonitor
import com.rpeters.cinefintv.data.playback.EnhancedPlaybackManager
import com.rpeters.cinefintv.data.playback.PlaybackResult
import com.rpeters.cinefintv.data.playback.RecommendationSeverity
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
import com.rpeters.cinefintv.data.preferences.ResumePlaybackMode
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.getDisplayTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PlayMethod
import java.util.UUID
import javax.inject.Inject
import kotlin.math.pow

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repositories: JellyfinRepositoryCoordinator,
    private val jellyfinRepository: JellyfinRepository,
    private val enhancedPlaybackManager: EnhancedPlaybackManager,
    private val adaptiveBitrateMonitor: AdaptiveBitrateMonitor,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    @param:ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {
    private val playbackSessionId: String = UUID.randomUUID().toString()
    val itemId: String = savedStateHandle.get<String>("itemId").orEmpty()
    
    private val _uiState = MutableStateFlow(
        PlayerUiState(
            itemId = itemId,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private var activeMediaSourceId: String? = null
    private var activePlaySessionId: String? = null
    private var activePlayMethod: PlayMethod = PlayMethod.DIRECT_PLAY
    private var currentItem: BaseItemDto? = null
    private var playbackStartReported = false

    init {
        viewModelScope.launch {
            playbackPreferencesRepository.preferences.collectLatest { prefs ->
                _uiState.value = _uiState.value.copy(autoPlayNextEpisode = prefs.autoPlayNextEpisode)
            }
        }
        viewModelScope.launch {
            adaptiveBitrateMonitor.qualityRecommendation.collectLatest { recommendation ->
                if (recommendation != null && recommendation.severity == RecommendationSeverity.HIGH) {
                    playbackPreferencesRepository.setTranscodingQuality(recommendation.recommendedQuality)
                    adaptiveBitrateMonitor.clearRecommendation()
                    adaptiveBitrateMonitor.resetBufferingTracking()
                    val player = _player ?: return@collectLatest
                    reloadStream(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        playWhenReady = player.isPlaying,
                    )
                }
            }
        }
        load()
    }

    fun load() {
        if (itemId.isBlank()) {
            _uiState.value = PlayerUiState(
                itemId = itemId,
                isLoading = false,
                errorMessage = "No playable item was provided.",
            )
            return
        }

        viewModelScope.launch {
            val localPlaybackPositionMs = PlaybackPositionStore.getPlaybackPosition(appContext, itemId)
            val detailResult = repositories.media.getItemDetails(itemId)
            val item = (detailResult as? ApiResult.Success)?.data
            currentItem = item
            val chapters = item?.chapters.orEmpty().map { chapter ->
                ChapterMarker(positionMs = chapter.startPositionTicks / TICKS_PER_MILLISECOND, name = chapter.name)
            }
            var introSkipRange: SkipRange? = null
            var creditsSkipRange: SkipRange? = null
            chapters.forEachIndexed { i, chapter ->
                val name = chapter.name?.lowercase() ?: return@forEachIndexed
                val nextStart = chapters.getOrNull(i + 1)?.positionMs
                when {
                    name.contains("intro") || name.contains("opening") ->
                        introSkipRange = SkipRange(startMs = chapter.positionMs, endMs = nextStart)
                    name.contains("credit") || name.contains("outro") ->
                        creditsSkipRange = SkipRange(startMs = chapter.positionMs, endMs = nextStart)
                }
            }
            val serverPlaybackPositionMs = item?.userData?.playbackPositionTicks
                ?.takeIf { it > 0L }
                ?.div(TICKS_PER_MILLISECOND)
                ?: 0L

            val playbackPrefs = playbackPreferencesRepository.preferences.first()
            val resumeMode = playbackPrefs.resumePlaybackMode
            
            val potentialResumePositionMs = maxOf(localPlaybackPositionMs, serverPlaybackPositionMs)
            
            val (savedPlaybackPositionMs, shouldShowResumeDialog) = when (resumeMode) {
                ResumePlaybackMode.NEVER -> 0L to false
                ResumePlaybackMode.ASK -> {
                    if (potentialResumePositionMs > 0L) {
                        potentialResumePositionMs to true
                    } else {
                        0L to false
                    }
                }
                ResumePlaybackMode.ALWAYS -> potentialResumePositionMs to false
            }

            val title = item?.getDisplayTitle() ?: "Now Playing"
            val playbackInfo = runCatching { jellyfinRepository.getPlaybackInfo(itemId) }.getOrNull()
            val mediaSource = playbackInfo?.mediaSources?.firstOrNull()
            val audioTracks = PlayerMappers.toAudioTrackOptions(mediaSource)
            val subtitleTracks = PlayerMappers.toSubtitleTrackOptions(mediaSource)
            activeMediaSourceId = mediaSource?.id
            activePlaySessionId = playbackInfo?.playSessionId

            val streamUrl = resolvePlaybackUrl(
                item = item,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
                startPositionMs = if (shouldShowResumeDialog) 0L else savedPlaybackPositionMs,
            ) ?: repositories.stream.getStreamUrl(itemId)

            if (streamUrl == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unable to create a stream for this item.",
                )
                return@launch
            }

            val logoUrl = if (item != null) {
                item.seriesId?.let { seriesId ->
                    val seriesResult = repositories.media.getSeriesDetails(seriesId.toString())
                    (seriesResult as? ApiResult.Success)?.data?.let { series ->
                        repositories.stream.getLogoUrl(series)
                    }
                } ?: repositories.stream.getLogoUrl(item)
            } else null

            val isEpisodicContent = item?.type == BaseItemKind.EPISODE
            var seasonNumber: Int? = null
            var episodeNumber: Int? = null
            
            if (item != null && isEpisodicContent) {
                seasonNumber = item.parentIndexNumber
                episodeNumber = item.indexNumber
            }

            var nextEpisodeId: String? = null
            var nextEpisodeTitle: String? = null
            var nextEpisodeThumbnailUrl: String? = null

            if (isEpisodicContent) {
                val nextResult = repositories.media.getNextEpisode(itemId)
                if (nextResult is ApiResult.Success) {
                    val nextEpisode = nextResult.data
                    if (nextEpisode != null) {
                        nextEpisodeId = nextEpisode.id.toString()
                        nextEpisodeTitle = nextEpisode.getDisplayTitle()
                        nextEpisodeThumbnailUrl = repositories.stream.getImageUrl(nextEpisode.id.toString())
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                title = title,
                logoUrl = logoUrl,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                streamUrl = streamUrl,
                savedPlaybackPositionMs = savedPlaybackPositionMs,
                shouldShowResumeDialog = shouldShowResumeDialog,
                isEpisodicContent = isEpisodicContent,
                nextEpisodeId = nextEpisodeId,
                nextEpisodeTitle = nextEpisodeTitle,
                nextEpisodeThumbnailUrl = nextEpisodeThumbnailUrl,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                chapters = chapters,
                introSkipRange = introSkipRange,
                creditsSkipRange = creditsSkipRange,
                isLoading = false,
            )
        }
    }

    fun setupPlayer(context: Context): ExoPlayer {
        _player?.let { return it }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50_000, // minBufferMs
                120_000, // maxBufferMs (up to 2 minutes)
                5_000,  // bufferForPlaybackMs (fast start)
                10_000  // bufferForPlaybackAfterRebufferMs (harden against micro-stutter)
            )
            .build()
        
        val factory = DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttpClient))
        val newPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(factory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                val streamUrl = uiState.value.streamUrl
                if (streamUrl != null) {
                    setMediaItem(MediaItem.fromUri(streamUrl))
                    val shouldAsk = uiState.value.shouldShowResumeDialog
                    if (!shouldAsk) {
                        val resumePositionMs = uiState.value.savedPlaybackPositionMs.coerceAtLeast(0L)
                        if (resumePositionMs > 0L) {
                            seekTo(resumePositionMs)
                        }
                        reportPlaybackStart(resumePositionMs)
                        prepare()
                        playWhenReady = true
                    }
                }
            }
        
        newPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
                Log.e("PlayerViewModel", "Analytics: Playback Error [Code: ${error.errorCode}]", error)
            }

            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                if (droppedFrames > 10) {
                    Log.w("PlayerViewModel", "Analytics: Dropped $droppedFrames frames in ${elapsedMs}ms")
                }
            }
        })

        _player = newPlayer
        viewModelScope.launch {
            val prefs = playbackPreferencesRepository.preferences.first()
            adaptiveBitrateMonitor.startMonitoring(
                exoPlayer = newPlayer,
                scope = viewModelScope,
                currentQuality = prefs.transcodingQuality,
                isTranscoding = activePlayMethod != PlayMethod.DIRECT_PLAY,
            )
        }
        return newPlayer
    }

    fun onResumePlayback(resume: Boolean) {
        val position = if (resume) uiState.value.savedPlaybackPositionMs else 0L
        _uiState.value = _uiState.value.copy(shouldShowResumeDialog = false)
        
        _player?.apply {
            if (position > 0L) {
                seekTo(position)
            }
            reportPlaybackStart(position)
            prepare()
            playWhenReady = true
        }
    }

    fun selectAudioTrack(track: TrackOption, positionMs: Long, playWhenReady: Boolean) {
        _uiState.value = _uiState.value.copy(selectedAudioTrack = track)
        reloadStream(positionMs = positionMs, playWhenReady = playWhenReady)
    }

    fun selectSubtitleTrack(track: TrackOption?, positionMs: Long, playWhenReady: Boolean) {
        _uiState.value = _uiState.value.copy(selectedSubtitleTrack = track)
        reloadStream(positionMs = positionMs, playWhenReady = playWhenReady)
    }

    private fun applyTrackSelection(audioTrack: TrackOption?, subtitleTrack: TrackOption?) {
        val player = _player ?: return
        
        val builder = player.trackSelectionParameters.buildUpon()
        
        // Audio selection
        if (audioTrack != null) {
            builder.setPreferredAudioLanguage(audioTrack.language)
        }
        
        // Subtitle selection
        if (subtitleTrack != null) {
            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            builder.setPreferredTextLanguage(subtitleTrack.language)
            builder.setSelectUndeterminedTextLanguage(true)
        } else {
            builder.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
            builder.setPreferredTextLanguage(null)
        }
        
        player.trackSelectionParameters = builder.build()
    }

    private fun reloadStream(positionMs: Long, playWhenReady: Boolean) {
        val currentItemId = uiState.value.itemId
        if (currentItemId.isBlank()) return

        viewModelScope.launch {
            val streamUrl = resolvePlaybackUrl(
                item = currentItem,
                audioStreamIndex = uiState.value.selectedAudioTrack?.streamIndex,
                subtitleStreamIndex = uiState.value.selectedSubtitleTrack?.streamIndex,
            ) ?: repositories.stream.getTranscodedStreamUrl(
                itemId = currentItemId,
                mediaSourceId = activeMediaSourceId,
                playSessionId = activePlaySessionId,
                audioStreamIndex = uiState.value.selectedAudioTrack?.streamIndex,
                subtitleStreamIndex = uiState.value.selectedSubtitleTrack?.streamIndex,
            ) ?: repositories.stream.getStreamUrl(currentItemId)
                ?: return@launch

            _uiState.value = _uiState.value.copy(streamUrl = streamUrl)
            playbackStartReported = false
            _player?.apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                if (positionMs > 0L) {
                    seekTo(positionMs)
                }
                reportPlaybackStart(positionMs)
                prepare()
                applyTrackSelection(uiState.value.selectedAudioTrack, uiState.value.selectedSubtitleTrack)
                this.playWhenReady = playWhenReady
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        adaptiveBitrateMonitor.stopMonitoring()
        _player?.release()
        _player = null
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        viewModelScope.launch {
            playbackPreferencesRepository.setAutoPlayNextEpisode(enabled)
            _uiState.value = _uiState.value.copy(autoPlayNextEpisode = enabled)
        }
    }

    fun onAudioTrackSelected(track: TrackOption?) {
        _uiState.value = _uiState.value.copy(selectedAudioTrack = track)
    }

    fun onSubtitleTrackSelected(track: TrackOption?) {
        _uiState.value = _uiState.value.copy(selectedSubtitleTrack = track)
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
        _player?.setPlaybackSpeed(speed)
    }

    fun onPlayerError(error: PlaybackException) {
        val canRetry = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> true
            else -> false
        }

        if (canRetry && uiState.value.retryCount < MAX_RETRIES) {
            attemptRetry(error)
            return
        }

        val message = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network error. Please check your connection."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                "Codec error. This device may not support this media format."
            else -> "Playback failed: ${error.localizedMessage}"
        }
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isRetrying = false,
            errorMessage = message,
        )
    }

    private fun attemptRetry(error: PlaybackException) {
        val nextRetryCount = uiState.value.retryCount + 1
        Log.w("PlayerViewModel", "Playback error (code ${error.errorCode}). Attempting retry $nextRetryCount/$MAX_RETRIES...")
        
        _uiState.value = _uiState.value.copy(
            isRetrying = true,
            retryCount = nextRetryCount,
            errorMessage = null
        )

        viewModelScope.launch {
            val delayMs = (2.0.pow(nextRetryCount - 1).toLong() * 1000L)
            delay(delayMs)
            
            val player = _player ?: return@launch
            val currentPos = player.currentPosition
            
            load()
            
            reloadStream(positionMs = currentPos, playWhenReady = true)
            
            _uiState.value = _uiState.value.copy(isRetrying = false)
        }
    }

    suspend fun getNextEpisodeId(): String? {
        if (!uiState.value.isEpisodicContent || !uiState.value.autoPlayNextEpisode) return null
        return when (val result = repositories.media.getNextEpisode(itemId)) {
            is ApiResult.Success -> result.data?.id?.toString()
            else -> null
        }
    }

    fun savePlaybackPosition(
        positionMs: Long,
        durationMs: Long,
        isPaused: Boolean = true,
        shouldSyncToServer: Boolean = true,
    ) {
        if (itemId.isBlank()) return

        viewModelScope.launch {
            val isCompleted = durationMs > 0L && positionMs >= (durationMs * COMPLETION_THRESHOLD_PERCENT)
            val persistedPosition = if (isCompleted) 0L else positionMs.coerceAtLeast(0L)
            PlaybackPositionStore.savePlaybackPosition(appContext, itemId, persistedPosition)

            if (shouldSyncToServer) {
                val positionTicks = if (persistedPosition <= 0L) null else persistedPosition * TICKS_PER_MILLISECOND
                val sessionId = activePlaySessionId?.takeIf { it.isNotBlank() } ?: playbackSessionId
                if (isCompleted) {
                    repositories.user.reportPlaybackStopped(
                        itemId = itemId,
                        sessionId = sessionId,
                        positionTicks = positionTicks,
                        mediaSourceId = activeMediaSourceId,
                    )
                } else {
                    repositories.user.reportPlaybackProgress(
                        itemId = itemId,
                        sessionId = sessionId,
                        positionTicks = positionTicks,
                        mediaSourceId = activeMediaSourceId,
                        playMethod = activePlayMethod,
                        isPaused = isPaused,
                    )
                }
            }
        }
    }

    private suspend fun resolvePlaybackUrl(
        item: BaseItemDto?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startPositionMs: Long = 0L,
    ): String? {
        item ?: return null

        return when (
            val playbackResult = enhancedPlaybackManager.getOptimalPlaybackUrl(
                item = item,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                startPositionMs = startPositionMs,
            )
        ) {
            is PlaybackResult.DirectPlay -> {
                activePlayMethod = PlayMethod.DIRECT_PLAY
                val nextSessionId = playbackResult.playSessionId ?: activePlaySessionId ?: playbackSessionId
                if (nextSessionId != activePlaySessionId) {
                    playbackStartReported = false
                }
                activePlaySessionId = nextSessionId
                playbackResult.url
            }
            is PlaybackResult.Transcoding -> {
                activePlayMethod = if (playbackResult.isDirectStream) {
                    PlayMethod.DIRECT_STREAM
                } else {
                    PlayMethod.TRANSCODE
                }
                val nextSessionId = playbackResult.playSessionId ?: activePlaySessionId ?: playbackSessionId
                if (nextSessionId != activePlaySessionId) {
                    playbackStartReported = false
                }
                activePlaySessionId = nextSessionId
                playbackResult.url
            }
            is PlaybackResult.Error -> null
        }
    }

    private fun reportPlaybackStart(positionMs: Long) {
        if (playbackStartReported || itemId.isBlank()) return

        playbackStartReported = true
        val sessionId = activePlaySessionId?.takeIf { it.isNotBlank() } ?: playbackSessionId
        val positionTicks = positionMs.takeIf { it > 0L }?.times(TICKS_PER_MILLISECOND)

        viewModelScope.launch {
            repositories.user.reportPlaybackStart(
                itemId = itemId,
                sessionId = sessionId,
                positionTicks = positionTicks,
                mediaSourceId = activeMediaSourceId,
                playMethod = activePlayMethod,
                isPaused = false,
            )
        }
    }

    companion object {
        private const val COMPLETION_THRESHOLD_PERCENT = 0.95
        private const val TICKS_PER_MILLISECOND = 10_000L
        private const val MAX_RETRIES = 3
    }
}
