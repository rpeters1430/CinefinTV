package com.rpeters.cinefintv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JellyfinServer(
    val id: String,
    val name: String,
    val url: String,
    val isConnected: Boolean = false,
    val version: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val accessToken: String? = null,
    val loginTimestamp: Long? = null,
    // Normalized server URL for credential lookups
    @SerialName("originalServerUrl")
    val normalizedUrl: String? = null,
)

@Serializable
data class ServerInfo(
    @SerialName("Id") val id: String,
    @SerialName("ServerName") val name: String,
    @SerialName("Version") val version: String,
    @SerialName("OperatingSystem") val operatingSystem: String? = null,
    @SerialName("LocalAddress") val localAddress: String? = null,
    @SerialName("ProductName") val productName: String? = null,
    @SerialName("StartupWizardCompleted") val startupWizardCompleted: Boolean? = null,
)

@Serializable
data class AuthenticationResult(
    val user: User,
    val sessionInfo: SessionInfo,
    val accessToken: String,
    val serverId: String,
)

@Serializable
data class User(
    val id: String,
    val name: String,
    val hasPassword: Boolean,
    val hasConfiguredPassword: Boolean,
    val hasConfiguredEasyPassword: Boolean,
    val enableAutoLogin: Boolean? = null,
    val lastLoginDate: String? = null,
    val lastActivityDate: String? = null,
    val configuration: UserConfiguration? = null,
    val policy: UserPolicy? = null,
    val primaryImageTag: String? = null,
)

@Serializable
data class UserConfiguration(
    val playDefaultAudioTrack: Boolean = true,
    val subtitleLanguagePreference: String? = null,
    val displayMissingEpisodes: Boolean = false,
    val groupedFolders: List<String> = emptyList(),
    val subtitleMode: String = "Default",
    val displayCollectionsView: Boolean = false,
    val enableLocalPassword: Boolean = false,
    val orderedViews: List<String> = emptyList(),
    val latestItemsExcludes: List<String> = emptyList(),
    val myMediaExcludes: List<String> = emptyList(),
    val hidePlayedInLatest: Boolean = true,
    val rememberAudioSelections: Boolean = true,
    val rememberSubtitleSelections: Boolean = true,
    val enableNextEpisodeAutoPlay: Boolean = true,
)

@Serializable
data class UserPolicy(
    val isAdministrator: Boolean = false,
    val isHidden: Boolean = false,
    val isDisabled: Boolean = false,
    val maxParentalRating: Int? = null,
    val blockedTags: List<String> = emptyList(),
    val enableUserPreferenceAccess: Boolean = true,
    val accessSchedules: List<String> = emptyList(),
    val blockUnratedItems: List<String> = emptyList(),
    val enableRemoteControlOfOtherUsers: Boolean = false,
    val enableSharedDeviceControl: Boolean = true,
    val enableRemoteAccess: Boolean = true,
    val enableLiveTvManagement: Boolean = true,
    val enableLiveTvAccess: Boolean = true,
    val enableMediaPlayback: Boolean = true,
    val enableAudioPlaybackTranscoding: Boolean = true,
    val enableVideoPlaybackTranscoding: Boolean = true,
    val enablePlaybackRemuxing: Boolean = true,
    val forceRemoteSourceTranscoding: Boolean = false,
    val enableContentDeletion: Boolean = false,
    val enableContentDeletionFromFolders: List<String> = emptyList(),
    val enableContentDownloading: Boolean = true,
    val enableSyncTranscoding: Boolean = true,
    val enableMediaConversion: Boolean = true,
    val enabledDevices: List<String> = emptyList(),
    val enableAllDevices: Boolean = true,
    val enabledChannels: List<String> = emptyList(),
    val enableAllChannels: Boolean = true,
    val enabledFolders: List<String> = emptyList(),
    val enableAllFolders: Boolean = true,
    val invalidLoginAttemptCount: Int = 0,
    val loginAttemptsBeforeLockout: Int = 3,
    val maxActiveSessions: Int = 0,
    val enablePublicSharing: Boolean = true,
    val blockedMediaFolders: List<String> = emptyList(),
    val blockedChannels: List<String> = emptyList(),
    val remoteClientBitrateLimit: Int = 0,
    val authenticationProviderId: String = "Jellyfin.Server.Implementations.Users.DefaultAuthenticationProvider",
    val passwordResetProviderId: String = "Jellyfin.Server.Implementations.Users.DefaultPasswordResetProvider",
    val syncPlayAccess: String = "CreateAndJoinGroups",
)

@Serializable
data class SessionInfo(
    val playState: PlayState? = null,
    val additionalUsers: List<String> = emptyList(),
    val capabilities: ClientCapabilities? = null,
    val remoteEndPoint: String? = null,
    val playableMediaTypes: List<String> = emptyList(),
    val id: String,
    val userId: String,
    val userName: String? = null,
    val client: String? = null,
    val lastActivityDate: String,
    val lastPlaybackCheckIn: String? = null,
    val deviceName: String? = null,
    val deviceType: String? = null,
    val nowPlayingItem: String? = null,
    val deviceId: String? = null,
    val applicationVersion: String? = null,
    val transcodingInfo: String? = null,
    val isActive: Boolean = true,
    val supportsMediaControl: Boolean = false,
    val supportsRemoteControl: Boolean = false,
    val nowViewingItem: String? = null,
    val hasCustomDeviceName: Boolean = false,
    val playlistItemId: String? = null,
    val serverId: String? = null,
    val userPrimaryImageTag: String? = null,
    val supportedCommands: List<String> = emptyList(),
)

@Serializable
data class PlayState(
    val canSeek: Boolean = false,
    val isPaused: Boolean = false,
    val isMuted: Boolean = false,
    val repeatMode: String = "RepeatNone",
    val shuffleMode: String = "Sorted",
    val maxStreamingBitrate: Int = 0,
    val positionTicks: Long = 0,
    val playbackStartTimeTicks: Long = 0,
    val volumeLevel: Int = 100,
    val brightness: Int = 100,
    val aspectRatio: String? = null,
    val playMethod: String = "Transcode",
    val liveStreamId: String? = null,
    val playSessionId: String? = null,
    val repeatState: String = "RepeatNone",
    val subtitleOffset: Int = 0,
    val playbackRate: Double = 1.0,
)

@Serializable
data class ClientCapabilities(
    val playableMediaTypes: List<String> = emptyList(),
    val supportedCommands: List<String> = emptyList(),
    val supportsMediaControl: Boolean = false,
    val supportsContentUploading: Boolean = false,
    val messageCallbackUrl: String? = null,
    val supportsPersistedIdentifier: Boolean = true,
    val supportsSync: Boolean = false,
    val deviceProfile: String? = null,
    val appStoreUrl: String? = null,
    val iconUrl: String? = null,
)
