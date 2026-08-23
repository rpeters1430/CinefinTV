package com.rpeters.cinefintv.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.rpeters.cinefintv.ui.components.SessionRestoringScreen
import com.rpeters.cinefintv.ui.player.PlayerScreen
import com.rpeters.cinefintv.ui.player.audio.AudioPlayerScreen
import com.rpeters.cinefintv.ui.screens.auth.AuthViewModel
import com.rpeters.cinefintv.ui.screens.auth.LoginScreen
import com.rpeters.cinefintv.ui.screens.auth.ServerConnectionScreen
import com.rpeters.cinefintv.ui.screens.auth.ServerDiscoveryScreen
import com.rpeters.cinefintv.ui.screens.auth.ServerDiscoveryViewModel
import com.rpeters.cinefintv.ui.screens.detail.MovieDetailScreen
import com.rpeters.cinefintv.ui.screens.detail.SeasonScreen
import com.rpeters.cinefintv.ui.screens.detail.CollectionDetailScreen
import com.rpeters.cinefintv.ui.screens.detail.PlaylistDetailScreen
import com.rpeters.cinefintv.ui.screens.detail.TvShowDetailScreen
import com.rpeters.cinefintv.ui.screens.home.HomeScreen
import com.rpeters.cinefintv.ui.screens.library.MovieLibraryScreen
import com.rpeters.cinefintv.ui.screens.library.CollectionLibraryScreen
import com.rpeters.cinefintv.ui.screens.library.PlaylistLibraryScreen
import com.rpeters.cinefintv.ui.screens.library.TvShowLibraryScreen
import com.rpeters.cinefintv.ui.screens.music.MusicScreen
import com.rpeters.cinefintv.ui.screens.person.PersonScreen
import com.rpeters.cinefintv.ui.screens.search.SearchScreen
import com.rpeters.cinefintv.ui.screens.profile.ProfilePickerScreen
import com.rpeters.cinefintv.ui.screens.settings.SettingsScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@UnstableApi
@Composable
fun CinefinTvNavGraph(
    backStack: NavBackStack<NavKey>,
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    val voiceSearchNavVM: VoiceSearchNavViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        voiceSearchNavVM.pendingQuery.collect {
            val current = backStack.lastOrNull() as? NavDestination
            if (current !is Search) {
                backStack.navigateToTopLevelDestination(Search)
            }
        }
    }
    val saveableStateHolder = rememberSaveableStateHolder()
    val viewModelStoreOwner = requireNotNull(LocalViewModelStoreOwner.current)
    val entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(saveableStateHolder),
        rememberViewModelStoreNavEntryDecorator<NavKey>(viewModelStoreOwner),
    )

    if (!authUiState.isSessionChecked) {
        SessionRestoringScreen()
        return
    }

    val current = backStack.lastOrNull() as? NavDestination
    LaunchedEffect(current, authUiState.showProfilePickerOnStart) {
        if (current != ProfilePicker && current != null && authUiState.showProfilePickerOnStart) {
            authViewModel.dismissProfilePickerOnStart()
        }
    }

    if (authUiState.showProfilePickerOnStart && current == Home) {
        LaunchedEffect(Unit) {
            backStack.clear()
            backStack.add(ProfilePicker)
        }
        SessionRestoringScreen()
        return
    }

    LaunchedEffect(authUiState.connectedServerUrl) {
        val current = backStack.lastOrNull() as? NavDestination
        if (authUiState.connectedServerUrl != null &&
            (current == ServerConnection || current == ServerDiscovery)
        ) {
            backStack.add(Login)
        }
    }

    LaunchedEffect(authUiState.loginSucceeded) {
        val current = backStack.lastOrNull() as? NavDestination
        if (!authUiState.loginSucceeded || current == Home) {
            return@LaunchedEffect
        }

        authViewModel.resetLoginSuccess()
        // Clear backstack and go home
        backStack.clear()
        backStack.add(Home)
    }

    LaunchedEffect(authUiState.isSessionActive) {
        val current = backStack.lastOrNull() as? NavDestination
        val isOnAuthRoute = current == ServerConnection || current == Login
        if (!authUiState.isSessionActive || !isOnAuthRoute) {
            return@LaunchedEffect
        }

        backStack.clear()
        backStack.add(Home)
    }

    val cinefinEntryProvider: (NavKey) -> NavEntry<NavKey> = { key ->
        val destination = key as NavDestination
        NavEntry(key) {
            when (destination) {
                is ServerConnection -> {
                    ServerConnectionScreen(
                        serverUrl = authUiState.serverUrlInput,
                        isLoading = authUiState.isTestingConnection,
                        errorMessage = authUiState.connectionError,
                        onServerUrlChange = { authViewModel.updateServerUrlInput(it) },
                        onContinue = { authViewModel.testServerConnection() },
                        onDiscoverServers = { backStack.add(ServerDiscovery) },
                    )
                }
                is ServerDiscovery -> {
                    val discoveryViewModel: ServerDiscoveryViewModel = hiltViewModel()
                    val discoveryUiState by discoveryViewModel.uiState.collectAsStateWithLifecycle()
                    ServerDiscoveryScreen(
                        uiState = discoveryUiState,
                        onServerSelected = { url ->
                            authViewModel.connectDiscoveredServer(url)
                        },
                        onEnterManually = { backStack.pop() },
                    )
                }
                is Login -> {
                    LoginScreen(
                        serverUrl = authUiState.connectedServerUrl ?: authUiState.serverUrlInput,
                        isAuthenticating = authUiState.isAuthenticating,
                        errorMessage = authUiState.loginError,
                        isQuickConnectEnabled = authUiState.isQuickConnectEnabled,
                        isQuickConnectLoading = authUiState.isQuickConnectLoading,
                        quickConnectCode = authUiState.quickConnectCode,
                        quickConnectPollStatus = authUiState.quickConnectPollStatus,
                        quickConnectError = authUiState.quickConnectError,
                        onLogin = { username, password ->
                            authViewModel.login(username, password)
                        },
                        onUseQuickConnect = {
                            authViewModel.startQuickConnect()
                        },
                        onGenerateNewCode = {
                            authViewModel.generateNewQuickConnectCode()
                        },
                        onLeaveScreen = {
                            authViewModel.stopQuickConnect()
                        },
                        onBack = {
                            authViewModel.returnToServerEntry()
                            backStack.pop()
                        },
                    )
                }
                is Home -> {
                    HomeScreen(
                        onOpenItem = { item ->
                            backStack.add(
                                routeForBrowsableItem(
                                    itemId = item.id,
                                    itemType = item.itemType,
                                    collectionType = item.collectionType,
                                )
                            )
                        },
                        onPlayItem = { itemId ->
                            backStack.add(Player(itemId))
                        },
                        onOpenSeries = { seriesId ->
                            backStack.add(TvShowDetail(seriesId))
                        },
                        onOpenSeason = { seasonId ->
                            backStack.add(SeasonDetail(seasonId))
                        },
                    )
                }
                is Search -> {
                    SearchScreen(
                        onOpenItem = { item ->
                            backStack.add(
                                routeForBrowsableItem(
                                    itemId = item.id,
                                    itemType = item.itemType,
                                    collectionType = item.collectionType,
                                )
                            )
                        },
                    )
                }
                is LibraryMovies -> {
                    MovieLibraryScreen(
                        onOpenItem = { item ->
                            backStack.add(MovieDetail(item.id))
                        }
                    )
                }
                is LibraryTvShows -> {
                    TvShowLibraryScreen(
                        onOpenItem = { item ->
                            backStack.add(TvShowDetail(item.id))
                        }
                    )
                }
                is LibraryCollections -> {
                    CollectionLibraryScreen(
                        onOpenItem = { item ->
                            backStack.add(CollectionDetail(item.id))
                        },
                        onPlayItem = { item ->
                            backStack.add(Player(item.id))
                        }
                    )
                }
                is LibraryMusic -> {
                    MusicScreen(
                        onPlayTrack = { request ->
                            backStack.add(AudioPlayer(request.trackId, request.queueIds))
                        },
                    )
                }
                is LibraryPlaylists -> {
                    PlaylistLibraryScreen(
                        onOpenItem = { item ->
                            backStack.add(PlaylistDetail(item.id))
                        },
                    )
                }
                is Settings -> {
                    SettingsScreen(
                        onNavigateToProfilePicker = { backStack.add(ProfilePicker) },
                    )
                }
                is ProfilePicker -> {
                    ProfilePickerScreen(
                        onProfileSwitched = {
                            authViewModel.dismissProfilePickerOnStart()
                            if (backStack.size > 1) {
                                backStack.pop()
                            } else {
                                backStack.clear()
                                backStack.add(Home)
                            }
                        },
                        onAddProfile = {
                            authViewModel.dismissProfilePickerOnStart()
                            backStack.clear()
                            backStack.add(ServerConnection)
                        },
                        onBack = {
                            authViewModel.dismissProfilePickerOnStart()
                            if (backStack.size > 1) {
                                backStack.pop()
                            } else {
                                backStack.clear()
                                backStack.add(Home)
                            }
                        },
                    )
                }
                is MovieDetail -> {
                    MovieDetailScreen(
                        itemId = destination.itemId,
                        onPlayMovie = { itemId ->
                            backStack.add(Player(itemId))
                        },
                        onOpenMovie = { itemId ->
                            backStack.add(MovieDetail(itemId))
                        },
                        onOpenPerson = { personId ->
                            backStack.add(PersonDetail(personId))
                        },
                        onBack = { backStack.pop() },
                    )
                }
                is TvShowDetail -> {
                    TvShowDetailScreen(
                        itemId = destination.itemId,
                        onPlayEpisode = { episodeId ->
                            backStack.add(Player(episodeId))
                        },
                        onOpenSeason = { seasonId ->
                            backStack.add(SeasonDetail(seasonId))
                        },
                        onOpenShow = { seriesId ->
                            backStack.add(TvShowDetail(seriesId))
                        },
                        onOpenPerson = { personId ->
                            backStack.add(PersonDetail(personId))
                        },
                        onBack = { backStack.pop() },
                    )
                }
                is SeasonDetail -> {
                    SeasonScreen(
                        itemId = destination.itemId,
                        onOpenEpisode = { episodeId ->
                            backStack.add(Player(episodeId))
                        },
                        onBack = { backStack.pop() },
                    )
                }
                is CollectionDetail -> {
                    CollectionDetailScreen(
                        itemId = destination.itemId,
                        onOpenItem = { id, type ->
                            backStack.add(routeForLinkedDetailItem(id, type))
                        },
                        onPlayItem = { itemId ->
                            backStack.add(Player(itemId))
                        },
                        onBack = { backStack.pop() },
                    )
                }
                is PlaylistDetail -> {
                    PlaylistDetailScreen(
                        itemId = destination.itemId,
                        onOpenItem = { id, type ->
                            backStack.add(routeForLinkedDetailItem(id, type))
                        },
                        onPlayTrack = { request ->
                            backStack.add(AudioPlayer(request.trackId, request.queueIds))
                        },
                        onPlayVideo = { itemId, queueIds ->
                            backStack.add(Player(itemId, queueIds = queueIds))
                        },
                        onBack = { backStack.pop() },
                    )
                }
                is PersonDetail -> {
                    PersonScreen(
                        personId = destination.personId,
                        onOpenItem = { id, type ->
                            backStack.add(routeForLinkedDetailItem(id, type))
                        },
                        onBack = { backStack.pop() },
                    )
                }
                is Player -> {
                    PlayerScreen(
                        itemId = destination.itemId,
                        startPositionMs = destination.startPositionMs,
                        queueIds = destination.queueIds.orEmpty(),
                        onBack = { backStack.pop() },
                        onOpenItem = { nextItemId ->
                            backStack.add(Player(nextItemId, queueIds = destination.queueIds))
                        },
                    )
                }
                is AudioPlayer -> {
                    AudioPlayerScreen(
                        itemId = destination.itemId,
                        queueIds = destination.queueIds,
                        onBack = { backStack.pop() },
                    )
                }
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.pop() },
        entryDecorators = entryDecorators,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
        },
        entryProvider = cinefinEntryProvider,
    )
}

// Only pops when there is more than one entry, preventing the back stack from going empty.
private fun NavBackStack<NavKey>.pop() {
    if (size > 1) removeAt(size - 1)
}

