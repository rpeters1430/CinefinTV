package com.rpeters.cinefintv.ui.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.rpeters.cinefintv.data.PlaybackPositionStore
import com.rpeters.cinefintv.data.playback.AdaptiveBitrateMonitor
import com.rpeters.cinefintv.data.playback.EnhancedPlaybackManager
import com.rpeters.cinefintv.data.playback.PlaybackResult
import com.rpeters.cinefintv.data.playback.RecommendationSeverity
import com.rpeters.cinefintv.data.preferences.PlaybackPreferencesRepository
import com.rpeters.cinefintv.data.preferences.ResumePlaybackMode
import com.rpeters.cinefintv.data.preferences.SubtitleAppearancePreferencesRepository
import com.rpeters.cinefintv.data.preferences.TranscodingQuality
import com.rpeters.cinefintv.data.repository.JellyfinRepositoryCoordinator
import com.rpeters.cinefintv.data.repository.common.ApiResult
import com.rpeters.cinefintv.utils.SecureLogger
import com.rpeters.cinefintv.utils.canResume
import com.rpeters.cinefintv.utils.getDisplayTitle
import com.rpeters.cinefintv.utils.getEpisodeCode
import com.rpeters.cinefintv.utils.getYear
import com.rpeters.cinefintv.utils.isWatched
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
    private val repositories: JellyfinRepositoryCoordinator,
    private val enhancedPlaybackManager: EnhancedPlaybackManager,
    private val adaptiveBitrateMonitor: AdaptiveBitrateMonitor,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val subtitleAppearancePreferencesRepository: SubtitleAppearancePreferencesRepository,
    @param:ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val updateBus: com.rpeters.cinefintv.data.common.MediaUpdateBus,
) : ViewModel() {
    private val playbackSessionId: String = UUID.randomUUID().toString()
    var itemId: String = ""
        private set
    private var requestedStartPositionMs: Long = -1L
    
    private val _uiState = MutableStateFlow(
        PlayerUiState(
            itemId = "",
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var isInitialized = false

    fun init(id: String, start: Long) {
        if (isInitialized && itemId == id) return
        isInitialized = true
        itemId = id
        requestedStartPositionMs = start
        resolvedItemId = id
        _uiState.value = _uiState.value.copy(itemId = id)

        viewModelScope.launch {
            playbackPreferencesRepository.preferences.collectLatest { prefs ->
                _uiState.value = _uiState.value.copy(
                    autoPlayNextEpisode = prefs.autoPlayNextEpisode,
                    transcodingQuality = prefs.transcodingQuality,
                    videoSeekIncrement = prefs.videoSeekIncrement,
                )
            }
        }
        viewModelScope.launch {
            subtitleAppearancePreferencesRepository.preferencesFlow.collectLatest { preferences ->
                _uiState.value = _uiState.value.copy(subtitleAppearance = preferences)
            }
        }
        viewModelScope.launch {
            adaptiveBitrateMonitor.qualityRecommendation.collectLatest { recommendation ->
                if (recommendation != null && recommendation.severity >= RecommendationSeverity.MEDIUM) {
                    _uiState.value = _uiState.value.copy(
                        transcodingQuality = recommendation.recommendedQuality,
                    )
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

    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private var activeMediaSourceId: String? = null
    private var activePlaySessionId: String? = null
    private var activePlayMethod: PlayMethod = PlayMethod.DIRECT_PLAY
    private var currentItem: BaseItemDto? = null
    private var resolvedItemId: String = ""
    private var playbackStartReported = false
    private var pendingPlaybackStartPositionMs: Long? = null

    private data class ResolvedPlayback(
        val url: String,
        val isHdrPlayback: Boolean,
    )

    private fun syncAdaptiveBitrateMonitorContext(quality: TranscodingQuality = uiState.value.transcodingQuality) {
        adaptiveBitrateMonitor.updatePlaybackContext(
            currentQuality = quality,
            isTranscoding = activePlayMethod != PlayMethod.DIRECT_PLAY,
        )
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
            loadInternal()
        }
    }

    private suspend fun loadInternal(): Boolean {
        resolvedItemId = itemId
        val detailResult = repositories.media.getItemDetails(itemId)
        val requestedItem = (detailResult as? ApiResult.Success)?.data
        val item = resolvePlayableItem(requestedItem) ?: requestedItem
        resolvedItemId = item?.id?.toString()?.takeIf(String::isNotBlank) ?: itemId

        val localPlaybackPositionMs = PlaybackPositionStore.getPlaybackPosition(appContext, resolvedItemId)
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

        val (savedPlaybackPositionMs, shouldShowResumeDialog) = when {
            requestedStartPositionMs >= 0L -> requestedStartPositionMs to false
            resumeMode == ResumePlaybackMode.NEVER -> 0L to false
            resumeMode == ResumePlaybackMode.ASK -> {
                if (potentialResumePositionMs > 0L) {
                    potentialResumePositionMs to true
                } else {
                    0L to false
                }
            }
            resumeMode == ResumePlaybackMode.ALWAYS -> potentialResumePositionMs to false
            else -> 0L to false
        }

        val title = item?.getDisplayTitle() ?: "Now Playing"
        val playbackInfo = runCatching {
            repositories.stream.getPlaybackInfo(
                itemId = resolvedItemId,
                startPositionMs = if (shouldShowResumeDialog) 0L else savedPlaybackPositionMs,
            )
        }.onFailure { e ->
            SecureLogger.e("PlayerViewModel", "getPlaybackInfo failed for item $resolvedItemId", e)
        }.getOrNull()
        val mediaSource = playbackInfo?.mediaSources?.firstOrNull()
        val audioTracks = PlayerMappers.toAudioTrackOptions(mediaSource)
        val subtitleTracks = PlayerMappers.toSubtitleTrackOptions(mediaSource)
        val selectedAudioTrack = audioTracks.resolvePreferredAudioTrack(
            preferredLanguageCode = playbackPrefs.audioLanguage.code,
            defaultStreamIndex = mediaSource?.defaultAudioStreamIndex,
        )
        val selectedSubtitleTrack = subtitleTracks.firstOrNull {
            it.streamIndex == mediaSource?.defaultSubtitleStreamIndex
        }
        activeMediaSourceId = mediaSource?.id
        activePlaySessionId = playbackInfo?.playSessionId

        val resolvedPlayback = resolvePlaybackUrl(
            item = item,
            playbackInfo = playbackInfo,
            audioStreamIndex = selectedAudioTrack?.streamIndex,
            subtitleStreamIndex = selectedSubtitleTrack?.streamIndex,
            startPositionMs = if (shouldShowResumeDialog) 0L else savedPlaybackPositionMs,
        )
        val streamUrl = resolvedPlayback?.url ?: repositories.stream.getStreamUrl(resolvedItemId)

        if (streamUrl == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Unable to create a stream for this item.",
            )
            return false
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
            val nextResult = repositories.media.getNextEpisode(resolvedItemId)
            if (nextResult is ApiResult.Success) {
                val nextEpisode = nextResult.data
                if (nextEpisode != null) {
                    nextEpisodeId = nextEpisode.id.toString()
                    nextEpisodeTitle = nextEpisode.getDisplayTitle()
                    nextEpisodeThumbnailUrl = repositories.stream.getImageUrl(nextEpisode.id.toString())
                }
            }
        }

        val trickplayManifest = repositories.stream.getTrickplayManifest(resolvedItemId)
        val trickplayBaseUrl = if (trickplayManifest != null) repositories.stream.getTrickplayBaseUrl(resolvedItemId) else null

        _uiState.value = _uiState.value.copy(
            itemId = resolvedItemId,
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
            selectedAudioTrack = selectedAudioTrack,
            selectedSubtitleTrack = selectedSubtitleTrack,
            chapters = chapters,
            introSkipRange = introSkipRange,
            creditsSkipRange = creditsSkipRange,
            isHdrPlayback = resolvedPlayback?.isHdrPlayback ?: false,
            trickplayManifest = trickplayManifest,
            trickplayBaseUrl = trickplayBaseUrl,
            // Chapters are instant (already loaded); episodes/recommendations load below.
            contentRow = if (chapters.size >= 2) PlayerContentRow.Chapters(chapters) else null,
            isLoading = false,
        )

        // Load episode list or recommendations after unblocking playback.
        if (chapters.size < 2) {
            val contentRow = loadContentRow(isEpisodicContent, item, resolvedItemId)
            if (contentRow != null) {
                _uiState.value = _uiState.value.copy(contentRow = contentRow)
            }
        }

        return true
    }

    private suspend fun resolvePlayableItem(requestedItem: BaseItemDto?): BaseItemDto? {
        requestedItem ?: return null

        return when (requestedItem.type) {
            BaseItemKind.SERIES -> {
                val seriesId = requestedItem.id.toString()
                (repositories.media.getNextUpForSeries(seriesId) as? ApiResult.Success)?.data ?: requestedItem
            }
            BaseItemKind.SEASON -> {
                val seasonId = requestedItem.id.toString()
                val episodes = (repositories.media.getEpisodesForSeason(seasonId) as? ApiResult.Success)
                    ?.data
                    .orEmpty()
                episodes.firstOrNull { it.canResume() } ?:
                    episodes.firstOrNull { !it.isWatched() } ?:
                    episodes.firstOrNull() ?:
                    requestedItem
            }
            else -> requestedItem
        }
    }

    private suspend fun loadContentRow(
        isEpisodicContent: Boolean,
        item: org.jellyfin.sdk.model.api.BaseItemDto?,
        itemId: String,
    ): PlayerContentRow? {
        return when {
            isEpisodicContent -> {
                val seasonId = item?.seasonId?.toString() ?: return null
                val result = repositories.media.getEpisodesForSeason(seasonId)
                (result as? ApiResult.Success)?.data
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { episodes ->
                        PlayerContentRow.Episodes(
                            items = episodes.map { ep ->
                                PlayerContentItem(
                                    id = ep.id.toString(),
                                    title = ep.getDisplayTitle(),
                                    subtitle = ep.getEpisodeCode(),
                                    imageUrl = repositories.stream.getImageUrl(ep.id.toString()),
                                )
                            },
                            currentItemId = itemId,
                        )
                    }
            }
            item != null -> {
                val result = repositories.media.getSimilarMovies(itemId, limit = 15)
                (result as? ApiResult.Success)?.data
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { movies ->
                        PlayerContentRow.Recommendations(
                            items = movies.map { movie ->
                                PlayerContentItem(
                                    id = movie.id.toString(),
                                    title = movie.getDisplayTitle(),
                                    subtitle = movie.getYear()?.toString(),
                                    imageUrl = repositories.stream.getImageUrl(movie.id.toString()),
                                )
                            }
                        )
                    }
            }
            else -> null
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
                    val shouldAsk = uiState.value.shouldShowResumeDialog
                    if (!shouldAsk) {
                        val resumePositionMs = uiState.value.savedPlaybackPositionMs.coerceAtLeast(0L)
                        setMediaItem(MediaItem.fromUri(streamUrl), resumePositionMs)
                        queuePlaybackStartReport(resumePositionMs)
                        prepare()
                        playWhenReady = true
                    } else {
                        setMediaItem(MediaItem.fromUri(streamUrl))
                    }
                }
            }

        newPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && newPlayer.playWhenReady) {
                    flushPendingPlaybackStart()
                }
            }
        })
        
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
            syncAdaptiveBitrateMonitorContext(prefs.transcodingQuality)
            adaptiveBitrateMonitor.startMonitoring(
                exoPlayer = newPlayer,
                scope = viewModelScope,
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
            queuePlaybackStartReport(position)
            prepare()
            playWhenReady = true
        }
    }

    fun selectAudioTrack(track: TrackOption, positionMs: Long, playWhenReady: Boolean) {
        _uiState.value = _uiState.value.copy(selectedAudioTrack = track)
        if (activePlayMethod == org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY) {
            applyTrackSelection(track, uiState.value.selectedSubtitleTrack)
        } else {
            reloadStream(
                positionMs = currentPlaybackPosition(positionMs),
                playWhenReady = currentPlayWhenReady(playWhenReady),
            )
        }
    }

    fun selectSubtitleTrack(track: TrackOption?, positionMs: Long, playWhenReady: Boolean) {
        _uiState.value = _uiState.value.copy(selectedSubtitleTrack = track)
        if (activePlayMethod == org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY) {
            applyTrackSelection(uiState.value.selectedAudioTrack, track)
        } else {
            reloadStream(
                positionMs = currentPlaybackPosition(positionMs),
                playWhenReady = currentPlayWhenReady(playWhenReady),
            )
        }
    }

    private fun applyTrackSelection(audioTrack: TrackOption?, subtitleTrack: TrackOption?) {
        val player = _player ?: return
        val builder = player.trackSelectionParameters.buildUpon()

        // Audio selection (unchanged)
        if (audioTrack != null) {
            builder.setPreferredAudioLanguage(audioTrack.language)
        }

        // Always clear stale subtitle overrides first
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)

        if (subtitleTrack != null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)

            val subtitleIndex = _uiState.value.subtitleTracks.indexOf(subtitleTrack)
            val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

            val override: TrackSelectionOverride? = when {
                textGroups.isEmpty() -> {
                    Log.w("PlayerViewModel", "applyTrackSelection: no text groups in currentTracks, falling back to language preference")
                    null
                }
                subtitleIndex >= 0 && subtitleIndex < textGroups.size -> {
                    // Position-based match (valid for DIRECT_PLAY where order is stable)
                    TrackSelectionOverride(textGroups[subtitleIndex].mediaTrackGroup, 0)
                }
                else -> {
                    // Language-based match fallback
                    textGroups.firstNotNullOfOrNull { group ->
                        (0 until group.length)
                            .firstOrNull { i -> group.getTrackFormat(i).language == subtitleTrack.language }
                            ?.let { TrackSelectionOverride(group.mediaTrackGroup, listOf(it)) }
                    }
                }
            }

            if (override != null) {
                builder.addOverride(override)
            } else {
                // Fallback when no text groups found
                builder.setPreferredTextLanguage(subtitleTrack.language)
                builder.setSelectUndeterminedTextLanguage(true)
            }
        } else {
            // Disable subtitles (clearOverridesOfType already called above)
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            builder.setPreferredTextLanguage(null)
        }

        player.trackSelectionParameters = builder.build()
    }

    private fun reloadStream(positionMs: Long, playWhenReady: Boolean) {
        val currentItemId = uiState.value.itemId
        if (currentItemId.isBlank()) return

        viewModelScope.launch {
            val resolvedPlayback = resolvePlaybackUrl(
                item = currentItem,
                audioStreamIndex = uiState.value.selectedAudioTrack?.streamIndex,
                subtitleStreamIndex = uiState.value.selectedSubtitleTrack?.streamIndex,
                startPositionMs = positionMs,
            )
            val streamUrl = resolvedPlayback?.url ?: repositories.stream.getHlsTranscodeStreamUrl(
                itemId = currentItemId,
                mediaSourceId = activeMediaSourceId,
                playSessionId = activePlaySessionId,
                audioStreamIndex = uiState.value.selectedAudioTrack?.streamIndex,
                subtitleStreamIndex = uiState.value.selectedSubtitleTrack?.streamIndex,
            ) ?: repositories.stream.getStreamUrl(currentItemId)
                ?: return@launch

            _uiState.value = _uiState.value.copy(
                streamUrl = streamUrl,
                isHdrPlayback = resolvedPlayback?.isHdrPlayback ?: false,
            )
            syncAdaptiveBitrateMonitorContext()
            clearPlaybackStartState()
            _player?.apply {
                setMediaItem(MediaItem.fromUri(streamUrl), positionMs.coerceAtLeast(0L))
                queuePlaybackStartReport(positionMs)
                prepare()
                applyTrackSelection(uiState.value.selectedAudioTrack, uiState.value.selectedSubtitleTrack)
                this.playWhenReady = playWhenReady
            }
        }
    }

    private fun currentPlaybackPosition(fallbackPositionMs: Long): Long =
        _player?.currentPosition?.coerceAtLeast(0L) ?: fallbackPositionMs.coerceAtLeast(0L)

    private fun currentPlayWhenReady(fallbackPlayWhenReady: Boolean): Boolean =
        _player?.playWhenReady ?: fallbackPlayWhenReady

    private fun List<TrackOption>.resolvePreferredAudioTrack(
        preferredLanguageCode: String?,
        defaultStreamIndex: Int?,
    ): TrackOption? {
        findByLanguage(preferredLanguageCode)?.let { return it }
        firstOrNull { it.streamIndex == defaultStreamIndex }?.let { return it }
        return firstOrNull()
    }

    private fun List<TrackOption>.findByLanguage(languageCode: String?): TrackOption? {
        val normalizedPreferred = languageCode.normalizeLanguageCode() ?: return null
        return firstOrNull { track ->
            val normalizedTrack = track.language.normalizeLanguageCode()
            normalizedTrack == normalizedPreferred ||
                normalizedTrack?.startsWith("$normalizedPreferred-") == true
        }
    }

    private fun String?.normalizeLanguageCode(): String? = this
        ?.trim()
        ?.lowercase()
        ?.replace('_', '-')
        ?.takeIf { it.isNotBlank() }
        ?.let { code ->
            when (code) {
                "eng" -> "en"
                "spa" -> "es"
                "fre", "fra" -> "fr"
                "ger", "deu" -> "de"
                "ita" -> "it"
                "por" -> "pt"
                "jpn" -> "ja"
                "kor" -> "ko"
                "chi", "zho" -> "zh"
                else -> code
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

    fun setTranscodingQuality(
        quality: TranscodingQuality,
        positionMs: Long,
        playWhenReady: Boolean,
    ) {
        viewModelScope.launch {
            playbackPreferencesRepository.setTranscodingQuality(quality)
            _uiState.value = _uiState.value.copy(transcodingQuality = quality)
            syncAdaptiveBitrateMonitorContext(quality)
            reloadStream(positionMs = positionMs, playWhenReady = playWhenReady)
        }
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

            val currentPos = _player?.currentPosition ?: 0L
            val didLoad = loadInternal()
            if (didLoad) {
                reloadStream(positionMs = currentPos, playWhenReady = true)
            }

            _uiState.value = _uiState.value.copy(isRetrying = false)
        }
    }

    suspend fun getNextEpisodeId(): String? {
        if (!uiState.value.isEpisodicContent || !uiState.value.autoPlayNextEpisode) return null
        return when (val result = repositories.media.getNextEpisode(resolvedItemId)) {
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
        if (resolvedItemId.isBlank()) return

        viewModelScope.launch {
            val isCompleted = durationMs > 0L && positionMs >= (durationMs * COMPLETION_THRESHOLD_PERCENT)
            val persistedPosition = if (isCompleted) 0L else positionMs.coerceAtLeast(0L)
            PlaybackPositionStore.savePlaybackPosition(appContext, resolvedItemId, persistedPosition)

            if (shouldSyncToServer) {
                try {
                    val positionTicks = if (persistedPosition <= 0L) null else persistedPosition * TICKS_PER_MILLISECOND
                    val sessionId = activePlaySessionId?.takeIf { it.isNotBlank() } ?: playbackSessionId
                    if (isCompleted) {
                        repositories.user.reportPlaybackStopped(
                            itemId = resolvedItemId,
                            sessionId = sessionId,
                            positionTicks = positionTicks,
                            mediaSourceId = activeMediaSourceId,
                        )
                    } else {
                        repositories.user.reportPlaybackProgress(
                            itemId = resolvedItemId,
                            sessionId = sessionId,
                            positionTicks = positionTicks,
                            mediaSourceId = activeMediaSourceId,
                            playMethod = activePlayMethod,
                            isPaused = isPaused,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(
                        "PlayerViewModel",
                        "Failed to sync playback position for item $resolvedItemId: ${e.message}",
                        e,
                    )
                }
            }

            // Broadcast after the sync attempt so listeners pull the latest server state.
            updateBus.refreshItem(resolvedItemId)
        }
    }

    private suspend fun resolvePlaybackUrl(
        item: BaseItemDto?,
        playbackInfo: org.jellyfin.sdk.model.api.PlaybackInfoResponse? = null,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startPositionMs: Long = 0L,
    ): ResolvedPlayback? {
        item ?: return null

        return when (
            val playbackResult = enhancedPlaybackManager.getOptimalPlaybackUrl(
                item = item,
                playbackInfo = playbackInfo,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                startPositionMs = startPositionMs,
            )
        ) {
            is PlaybackResult.DirectPlay -> {
                activePlayMethod = PlayMethod.DIRECT_PLAY
                val nextSessionId = playbackResult.playSessionId ?: activePlaySessionId ?: playbackSessionId
                if (nextSessionId != activePlaySessionId) {
                    clearPlaybackStartState()
                }
                activePlaySessionId = nextSessionId
                activeMediaSourceId = playbackResult.mediaSourceId ?: activeMediaSourceId
                syncAdaptiveBitrateMonitorContext()
                ResolvedPlayback(
                    url = playbackResult.url,
                    isHdrPlayback = playbackResult.reasonCodes.contains(HDR_REASON_CODE),
                )
            }
            is PlaybackResult.Transcoding -> {
                activePlayMethod = if (playbackResult.isDirectStream) {
                    PlayMethod.DIRECT_STREAM
                } else {
                    PlayMethod.TRANSCODE
                }
                val nextSessionId = playbackResult.playSessionId ?: activePlaySessionId ?: playbackSessionId
                if (nextSessionId != activePlaySessionId) {
                    clearPlaybackStartState()
                }
                activePlaySessionId = nextSessionId
                activeMediaSourceId = playbackResult.mediaSourceId ?: activeMediaSourceId
                syncAdaptiveBitrateMonitorContext()
                ResolvedPlayback(
                    url = playbackResult.url,
                    isHdrPlayback = playbackResult.reasonCodes.contains(HDR_REASON_CODE),
                )
            }
            is PlaybackResult.Error -> null
        }
    }

    private fun clearPlaybackStartState() {
        playbackStartReported = false
        pendingPlaybackStartPositionMs = null
    }

    private fun queuePlaybackStartReport(positionMs: Long) {
        if (resolvedItemId.isBlank()) return
        pendingPlaybackStartPositionMs = positionMs.coerceAtLeast(0L)
    }

    private fun flushPendingPlaybackStart() {
        val positionMs = pendingPlaybackStartPositionMs ?: return
        reportPlaybackStart(positionMs)
    }

    private fun reportPlaybackStart(positionMs: Long) {
        if (playbackStartReported || resolvedItemId.isBlank()) return

        playbackStartReported = true
        pendingPlaybackStartPositionMs = null
        val sessionId = activePlaySessionId?.takeIf { it.isNotBlank() } ?: playbackSessionId
        val positionTicks = positionMs.takeIf { it > 0L }?.times(TICKS_PER_MILLISECOND)

        viewModelScope.launch {
            repositories.user.reportPlaybackStart(
                itemId = resolvedItemId,
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
        private const val HDR_REASON_CODE = "HDR_PRESERVATION_MODE"
    }
}
