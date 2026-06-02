# Skip Intro / Credits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the intro-skipper Jellyfin plugin API to provide precise skip ranges, split the skip button UI into intro (bottom-left) and credits (bottom-right), and add per-type auto-skip toggles in Settings.

**Architecture:** A new `IntroSkipperRepository` makes raw HTTP calls to the plugin's `/Episode/{id}/IntroTimestamps` endpoint (same OkHttp pattern as trickplay). A new `IntroSkipPreferencesRepository` stores `autoSkipIntro`/`autoSkipCredits` booleans in the existing `playback_preferences` DataStore. `PlayerViewModel.loadInternal()` calls the plugin after chapter-based detection and prefers its timestamps. Two independent composables replace the single `PlayerSkipActions` — skip intro anchors bottom-left, skip credits bottom-right — each auto-seeking when the matching preference is on.

**Tech Stack:** Kotlin, Hilt, DataStore Preferences, OkHttp, kotlinx.serialization, Compose (TV Material 3), MockK, Robolectric, kotlinx-coroutines-test

---

## File Map

| File | Action |
|---|---|
| `data/preferences/IntroSkipPreferencesRepository.kt` | **Create** — `IntroSkipPreferences` data class + DataStore repo |
| `data/repository/IntroSkipperRepository.kt` | **Create** — HTTP client + JSON parsing, returns `IntroSkipperSegments` |
| `ui/player/PlayerTestTags.kt` | **Modify** — add `SkipIntroAction`, `SkipCreditsAction` constants |
| `ui/player/PlayerModels.kt` | **Modify** — add `autoSkipIntro`, `autoSkipCredits` to `PlayerUiState` |
| `ui/player/PlayerViewModel.kt` | **Modify** — inject two new repos, wire prefs collector, call plugin in `loadInternal()` |
| `ui/player/PlayerScreen.kt` | **Modify** — replace `PlayerSkipActions` with `PlayerSkipIntroAction` + `PlayerSkipCreditsAction` |
| `ui/screens/settings/SettingsScreen.kt` | **Modify** — add `SKIP_INTRO` category + two switch rows |
| `ui/screens/settings/SettingsViewModel.kt` | **Modify** — inject `IntroSkipPreferencesRepository`, wire to `SettingsUiState` |
| `test/.../IntroSkipperRepositoryTest.kt` | **Create** |
| `test/.../PlayerViewModelTest.kt` | **Modify** — add new repo mocks to all ViewModel construction calls |
| `test/.../SettingsViewModelTest.kt` | **Modify** — add new repo mock |

---

## Task 1: IntroSkipPreferences model + repository

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/data/preferences/IntroSkipPreferencesRepository.kt`
- Create: `app/src/test/java/com/rpeters/cinefintv/data/preferences/IntroSkipPreferencesRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/rpeters/cinefintv/data/preferences/IntroSkipPreferencesRepositoryTest.kt
package com.rpeters.cinefintv.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class IntroSkipPreferencesRepositoryTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val tmpFolder = TemporaryFolder()

    private fun buildRepo(): IntroSkipPreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = mainDispatcherRule.testScope,
            produceFile = { tmpFolder.newFile("test_intro_skip.preferences_pb") }
        )
        return IntroSkipPreferencesRepository(dataStore)
    }

    @Test
    fun defaults_areFalse() = runTest {
        val repo = buildRepo()
        val prefs = repo.preferencesFlow.first()
        assertFalse(prefs.autoSkipIntro)
        assertFalse(prefs.autoSkipCredits)
    }

    @Test
    fun setAutoSkipIntro_persistsTrue() = runTest {
        val repo = buildRepo()
        repo.setAutoSkipIntro(true)
        advanceUntilIdle()
        assertTrue(repo.preferencesFlow.first().autoSkipIntro)
    }

    @Test
    fun setAutoSkipCredits_persistsTrue() = runTest {
        val repo = buildRepo()
        repo.setAutoSkipCredits(true)
        advanceUntilIdle()
        assertTrue(repo.preferencesFlow.first().autoSkipCredits)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.data.preferences.IntroSkipPreferencesRepositoryTest"
```

Expected: `FAILED` — `IntroSkipPreferencesRepository` not found.

- [ ] **Step 3: Create the repository**

```kotlin
// app/src/main/java/com/rpeters/cinefintv/data/preferences/IntroSkipPreferencesRepository.kt
package com.rpeters.cinefintv.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.rpeters.cinefintv.di.PlaybackPreferencesDataStore
import com.rpeters.cinefintv.utils.SecureLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class IntroSkipPreferences(
    val autoSkipIntro: Boolean = false,
    val autoSkipCredits: Boolean = false,
) {
    companion object { val DEFAULT = IntroSkipPreferences() }
}

@Singleton
class IntroSkipPreferencesRepository @Inject constructor(
    @PlaybackPreferencesDataStore private val dataStore: DataStore<Preferences>,
) {
    private val autoSkipIntroKey = booleanPreferencesKey("auto_skip_intro")
    private val autoSkipCreditsKey = booleanPreferencesKey("auto_skip_credits")

    val preferencesFlow: Flow<IntroSkipPreferences> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                SecureLogger.e("IntroSkipPreferencesRepository", "Error reading prefs", e)
                emit(emptyPreferences())
            } else throw e
        }
        .map { prefs ->
            IntroSkipPreferences(
                autoSkipIntro = prefs[autoSkipIntroKey] ?: false,
                autoSkipCredits = prefs[autoSkipCreditsKey] ?: false,
            )
        }

    suspend fun setAutoSkipIntro(enabled: Boolean) {
        dataStore.edit { it[autoSkipIntroKey] = enabled }
    }

    suspend fun setAutoSkipCredits(enabled: Boolean) {
        dataStore.edit { it[autoSkipCreditsKey] = enabled }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.data.preferences.IntroSkipPreferencesRepositoryTest"
```

Expected: `3 tests passed`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/data/preferences/IntroSkipPreferencesRepository.kt \
        app/src/test/java/com/rpeters/cinefintv/data/preferences/IntroSkipPreferencesRepositoryTest.kt
git commit -m "feat: add IntroSkipPreferencesRepository with auto-skip booleans"
```

---

## Task 2: IntroSkipperRepository

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/data/repository/IntroSkipperRepository.kt`
- Create: `app/src/test/java/com/rpeters/cinefintv/data/repository/IntroSkipperRepositoryTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/rpeters/cinefintv/data/repository/IntroSkipperRepositoryTest.kt
package com.rpeters.cinefintv.data.repository

import com.rpeters.cinefintv.data.JellyfinServer
import com.rpeters.cinefintv.data.cache.JellyfinCache
import com.rpeters.cinefintv.data.common.DispatcherProvider
import com.rpeters.cinefintv.data.session.JellyfinSessionManager
import com.rpeters.cinefintv.testutil.DeterministicDispatcherProvider
import com.rpeters.cinefintv.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntroSkipperRepositoryTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers: DispatcherProvider = DeterministicDispatcherProvider(testDispatcher)

    private val fakeServer = JellyfinServer(
        id = "server-1",
        name = "Test Server",
        url = "http://localhost:8096",
        accessToken = "test-token",
    )
    private val authRepository: JellyfinAuthRepository = mockk {
        every { getCurrentServer() } returns fakeServer
    }
    private val sessionManager: JellyfinSessionManager = mockk(relaxed = true)
    private val cache: JellyfinCache = mockk(relaxed = true)

    private fun buildResponse(body: String, code: Int = 200): Response {
        val request = Request.Builder().url("http://localhost:8096/Episode/item-1/IntroTimestamps").build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody())
            .build()
    }

    private fun buildRepo(okHttpClient: OkHttpClient): IntroSkipperRepository =
        IntroSkipperRepository(authRepository, sessionManager, cache, dispatchers, okHttpClient)

    @Test
    fun getSegments_parsesIntroAndCredits() = runTest {
        val json = """{"Valid":true,"Introduction":{"Start":15.5,"End":75.2},"Credits":{"Start":1320.0,"End":1380.0}}"""
        val mockCall = mockk<Call> { every { execute() } returns buildResponse(json) }
        val client = mockk<OkHttpClient> { every { newCall(any()) } returns mockCall }

        val result = buildRepo(client).getSegments("item-1")

        assertEquals(15_500L, result?.intro?.startMs)
        assertEquals(75_200L, result?.intro?.endMs)
        assertEquals(1_320_000L, result?.credits?.startMs)
        assertEquals(1_380_000L, result?.credits?.endMs)
    }

    @Test
    fun getSegments_returnsNull_whenValidFalse() = runTest {
        val json = """{"Valid":false}"""
        val mockCall = mockk<Call> { every { execute() } returns buildResponse(json) }
        val client = mockk<OkHttpClient> { every { newCall(any()) } returns mockCall }

        assertNull(buildRepo(client).getSegments("item-1"))
    }

    @Test
    fun getSegments_returnsNull_onHttpError() = runTest {
        val mockCall = mockk<Call> { every { execute() } returns buildResponse("", code = 404) }
        val client = mockk<OkHttpClient> { every { newCall(any()) } returns mockCall }

        assertNull(buildRepo(client).getSegments("item-1"))
    }

    @Test
    fun getSegments_returnsNull_onException() = runTest {
        val mockCall = mockk<Call> { every { execute() } throws RuntimeException("network error") }
        val client = mockk<OkHttpClient> { every { newCall(any()) } returns mockCall }

        assertNull(buildRepo(client).getSegments("item-1"))
    }

    @Test
    fun getSegments_handlesNullCredits() = runTest {
        val json = """{"Valid":true,"Introduction":{"Start":10.0,"End":60.0}}"""
        val mockCall = mockk<Call> { every { execute() } returns buildResponse(json) }
        val client = mockk<OkHttpClient> { every { newCall(any()) } returns mockCall }

        val result = buildRepo(client).getSegments("item-1")
        assertEquals(10_000L, result?.intro?.startMs)
        assertNull(result?.credits)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.data.repository.IntroSkipperRepositoryTest"
```

Expected: `FAILED` — `IntroSkipperRepository` not found.

- [ ] **Step 3: Create the repository**

```kotlin
// app/src/main/java/com/rpeters/cinefintv/data/repository/IntroSkipperRepository.kt
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
class IntroSkipperRepository @Inject constructor(
    authRepository: JellyfinAuthRepository,
    sessionManager: JellyfinSessionManager,
    cache: JellyfinCache,
    dispatchers: DispatcherProvider,
    private val okHttpClient: OkHttpClient,
) : BaseJellyfinRepository(authRepository, sessionManager, cache, dispatchers) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSegments(itemId: String): IntroSkipperSegments? = withContext(dispatchers.io) {
        try {
            val server = validateServer()
            val url = "${server.url}/Episode/$itemId/IntroTimestamps"
            val request = Request.Builder()
                .url(url)
                .header("X-Emby-Token", server.accessToken ?: "")
                .build()

            val body = okHttpClient.newCall(request).execute().use { response ->
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
```

- [ ] **Step 4: Run to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.data.repository.IntroSkipperRepositoryTest"
```

Expected: `5 tests passed`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/data/repository/IntroSkipperRepository.kt \
        app/src/test/java/com/rpeters/cinefintv/data/repository/IntroSkipperRepositoryTest.kt
git commit -m "feat: add IntroSkipperRepository calling intro-skipper plugin API"
```

---

## Task 3: PlayerUiState + PlayerViewModel wiring

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerModels.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerViewModel.kt`
- Modify: `app/src/test/java/com/rpeters/cinefintv/ui/player/PlayerViewModelTest.kt`

- [ ] **Step 1: Add fields to PlayerUiState in PlayerModels.kt**

In `PlayerUiState`, add two fields after `creditsSkipRange`:

```kotlin
// Before (existing line):
val creditsSkipRange: SkipRange? = null,

// After:
val creditsSkipRange: SkipRange? = null,
val autoSkipIntro: Boolean = false,
val autoSkipCredits: Boolean = false,
```

- [ ] **Step 2: Add new constructor parameters to PlayerViewModel**

In `PlayerViewModel`, add two new `@Inject constructor` parameters after `certificatePinningManager`:

```kotlin
// Add after:
private val certificatePinningManager: com.rpeters.cinefintv.data.security.CertificatePinningManager,

// These two lines:
private val introSkipperRepository: com.rpeters.cinefintv.data.repository.IntroSkipperRepository,
private val introSkipPreferencesRepository: com.rpeters.cinefintv.data.preferences.IntroSkipPreferencesRepository,
```

Also add the imports at the top of `PlayerViewModel.kt`:
```kotlin
import com.rpeters.cinefintv.data.repository.IntroSkipperRepository
import com.rpeters.cinefintv.data.preferences.IntroSkipPreferencesRepository
```

- [ ] **Step 3: Add the intro-skip prefs collector to PlayerViewModel.init{}**

Inside the `init` function in `PlayerViewModel`, after the `subtitleAppearancePreferencesRepository` collector block, add:

```kotlin
viewModelScope.launch {
    introSkipPreferencesRepository.preferencesFlow.collectLatest { prefs ->
        _uiState.value = _uiState.value.copy(
            autoSkipIntro = prefs.autoSkipIntro,
            autoSkipCredits = prefs.autoSkipCredits,
        )
    }
}
```

- [ ] **Step 4: Call the plugin in loadInternal()**

In `PlayerViewModel.loadInternal()`, after the `chapters.forEachIndexed` block that sets `introSkipRange` and `creditsSkipRange`, add:

```kotlin
// Prefer intro-skipper plugin timestamps over chapter-based detection when available.
introSkipperRepository.getSegments(resolvedItemId)?.let { segments ->
    introSkipRange = segments.intro ?: introSkipRange
    creditsSkipRange = segments.credits ?: creditsSkipRange
}
```

- [ ] **Step 5: Write failing tests**

Add these two test methods to `PlayerViewModelTest.kt`. Every ViewModel construction call in the test file also needs the two new params — add them to all existing call sites:

```kotlin
// Add to every existing `PlayerViewModel(...)` constructor call in the test file:
introSkipperRepository = mockk(relaxed = true),
introSkipPreferencesRepository = mockk {
    every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
},
```

Then add these new test methods:

```kotlin
@Test
fun load_prefersPluginIntroRange_overChapter() = runTest {
    val fakeRepos = FakePlayerRepositories()
    val introSkipperRepo: IntroSkipperRepository = mockk {
        coEvery { getSegments("item-1") } returns IntroSkipperSegments(
            intro = SkipRange(startMs = 5_000L, endMs = 40_000L),
            credits = null,
        )
    }
    val introSkipPrefsRepo: IntroSkipPreferencesRepository = mockk {
        every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
    }
    every { fakeRepos.stream.getStreamUrl("item-1") } returns "https://stream/item-1"
    every { fakeRepos.stream.getLogoUrl(any()) } returns null
    coEvery { fakeRepos.media.getItemDetails("item-1") } returns ApiResult.Error("not found")
    coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "item-1") } returns 0L

    val viewModel = PlayerViewModel(
        repositories = fakeRepos.coordinator,
        enhancedPlaybackManager = enhancedPlaybackManager,
        adaptiveBitrateMonitor = adaptiveBitrateMonitor,
        playbackPreferencesRepository = playbackPreferencesRepository,
        subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
        appContext = appContext,
        okHttpClient = OkHttpClient(),
        updateBus = updateBus,
        syncPlayRepository = syncPlayRepository,
        certificatePinningManager = certificatePinningManager,
        introSkipperRepository = introSkipperRepo,
        introSkipPreferencesRepository = introSkipPrefsRepo,
    ).apply { init("item-1", -1L) }
    advanceUntilIdle()

    assertEquals(5_000L, viewModel.uiState.value.introSkipRange?.startMs)
    assertEquals(40_000L, viewModel.uiState.value.introSkipRange?.endMs)
}

@Test
fun load_whenPluginReturnsNull_keepsChapterRange() = runTest {
    val fakeRepos = FakePlayerRepositories()
    val introSkipperRepo: IntroSkipperRepository = mockk {
        coEvery { getSegments("item-1") } returns null
    }
    val introSkipPrefsRepo: IntroSkipPreferencesRepository = mockk {
        every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
    }
    every { fakeRepos.stream.getStreamUrl("item-1") } returns "https://stream/item-1"
    every { fakeRepos.stream.getLogoUrl(any()) } returns null
    coEvery { fakeRepos.media.getItemDetails("item-1") } returns ApiResult.Error("not found")
    coEvery { PlaybackPositionStore.getPlaybackPosition(appContext, "item-1") } returns 0L

    val viewModel = PlayerViewModel(
        repositories = fakeRepos.coordinator,
        enhancedPlaybackManager = enhancedPlaybackManager,
        adaptiveBitrateMonitor = adaptiveBitrateMonitor,
        playbackPreferencesRepository = playbackPreferencesRepository,
        subtitleAppearancePreferencesRepository = subtitleAppearancePreferencesRepository,
        appContext = appContext,
        okHttpClient = OkHttpClient(),
        updateBus = updateBus,
        syncPlayRepository = syncPlayRepository,
        certificatePinningManager = certificatePinningManager,
        introSkipperRepository = introSkipperRepo,
        introSkipPreferencesRepository = introSkipPrefsRepo,
    ).apply { init("item-1", -1L) }
    advanceUntilIdle()

    // No chapters on this item → range stays null (chapter fallback also null)
    assertNull(viewModel.uiState.value.introSkipRange)
}
```

Also add these imports to `PlayerViewModelTest.kt`:
```kotlin
import com.rpeters.cinefintv.data.preferences.IntroSkipPreferences
import com.rpeters.cinefintv.data.preferences.IntroSkipPreferencesRepository
import com.rpeters.cinefintv.data.repository.IntroSkipperRepository
import com.rpeters.cinefintv.data.repository.IntroSkipperSegments
import com.rpeters.cinefintv.ui.player.SkipRange
```

- [ ] **Step 6: Run all player ViewModel tests**

```
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.player.PlayerViewModelTest"
```

Expected: all tests pass (the existing ones compile again because all constructor calls now include the two new params).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerModels.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerViewModel.kt \
        app/src/test/java/com/rpeters/cinefintv/ui/player/PlayerViewModelTest.kt
git commit -m "feat: wire IntroSkipperRepository and auto-skip prefs into PlayerViewModel"
```

---

## Task 4: Player UI — split skip actions, auto-skip logic

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerTestTags.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerScreen.kt`

- [ ] **Step 1: Add test tag constants**

In `PlayerTestTags.kt`, add two constants after `SkipActionButton`:

```kotlin
const val SkipIntroAction = "player_skip_intro_action"
const val SkipCreditsAction = "player_skip_credits_action"
```

The existing `SkipAction` constant stays as-is for backwards compat.

- [ ] **Step 2: Add creditsFocusRequester to PlayerPlaybackContent**

In `PlayerPlaybackContent`, after the existing `skipFocusRequester` declaration:

```kotlin
// existing:
val skipFocusRequester = remember { FocusRequester() }

// add:
val creditsFocusRequester = remember { FocusRequester() }
```

- [ ] **Step 3: Replace PlayerSkipActions call with two split composables**

In `PlayerPlaybackContent`, find the existing `PlayerSkipActions(...)` block and replace it with:

```kotlin
// Skip intro — bottom left
PlayerSkipIntroAction(
    player = exoPlayer,
    introRange = introRange,
    autoSkip = uiState.autoSkipIntro,
    controlsVisible = controlsVisible,
    onSkip = { targetMs ->
        exoPlayer.seekTo(targetMs)
        onInteract()
    },
    focusRequester = skipFocusRequester,
    modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(bottom = 96.dp, start = 48.dp)
        .onFocusChanged { overlayActionFocused = it.hasFocus }
)

// Skip credits — bottom right
PlayerSkipCreditsAction(
    player = exoPlayer,
    creditsRange = creditsRange,
    autoSkip = uiState.autoSkipCredits,
    controlsVisible = controlsVisible,
    onSkip = { targetMs ->
        exoPlayer.seekTo(targetMs)
        onInteract()
    },
    focusRequester = creditsFocusRequester,
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 96.dp, end = 48.dp)
        .onFocusChanged { overlayActionFocused = it.hasFocus }
)
```

- [ ] **Step 4: Add PlayerSkipIntroAction composable**

Replace the existing `PlayerSkipActions` private composable with `PlayerSkipIntroAction`:

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerSkipIntroAction(
    player: Player,
    introRange: SkipRange?,
    autoSkip: Boolean,
    controlsVisible: Boolean,
    onSkip: (Long) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var showSkip by remember { mutableStateOf(false) }
    var skipTargetMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player, introRange, autoSkip) {
        var hasAutoSkipped = false
        while (true) {
            val pos = player.currentPosition
            val duration = player.duration.coerceAtLeast(0L)
            val inRange = isInSkipRange(pos, introRange)
            val targetMs = introRange?.endMs?.coerceAtMost(duration) ?: duration

            if (inRange && autoSkip && !hasAutoSkipped) {
                hasAutoSkipped = true
                onSkip(targetMs)
            }
            if (!inRange) hasAutoSkipped = false

            showSkip = inRange
            skipTargetMs = targetMs
            delay(500L)
        }
    }

    LaunchedEffect(showSkip, controlsVisible, autoSkip) {
        if (showSkip && !controlsVisible && !autoSkip) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    AnimatedVisibility(
        visible = showSkip,
        enter = fadeIn() + slideInHorizontally { -it / 2 },
        exit = fadeOut() + slideOutHorizontally { -it / 2 },
        modifier = modifier,
    ) {
        SkipActionCard(
            label = "Skip Intro",
            subtitle = if (autoSkip) "Auto-skipping" else "Press to skip",
            onSkip = { onSkip(skipTargetMs) },
            buttonFocusRequester = focusRequester,
            modifier = Modifier.testTag(PlayerTestTags.SkipIntroAction),
        )
    }
}
```

- [ ] **Step 5: Add PlayerSkipCreditsAction composable**

Add `PlayerSkipCreditsAction` after `PlayerSkipIntroAction`:

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerSkipCreditsAction(
    player: Player,
    creditsRange: SkipRange?,
    autoSkip: Boolean,
    controlsVisible: Boolean,
    onSkip: (Long) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var showSkip by remember { mutableStateOf(false) }
    var skipTargetMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player, creditsRange, autoSkip) {
        var hasAutoSkipped = false
        while (true) {
            val pos = player.currentPosition
            val duration = player.duration.coerceAtLeast(0L)
            val inRange = isInSkipRange(pos, creditsRange)
            val targetMs = creditsRange?.endMs?.coerceAtMost(duration) ?: duration

            if (inRange && autoSkip && !hasAutoSkipped) {
                hasAutoSkipped = true
                onSkip(targetMs)
            }
            if (!inRange) hasAutoSkipped = false

            showSkip = inRange
            skipTargetMs = targetMs
            delay(500L)
        }
    }

    LaunchedEffect(showSkip, controlsVisible, autoSkip) {
        if (showSkip && !controlsVisible && !autoSkip) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    AnimatedVisibility(
        visible = showSkip,
        enter = fadeIn() + slideInHorizontally { it / 2 },
        exit = fadeOut() + slideOutHorizontally { it / 2 },
        modifier = modifier,
    ) {
        SkipActionCard(
            label = "Skip Credits",
            subtitle = if (autoSkip) "Auto-skipping" else "Press to skip",
            onSkip = { onSkip(skipTargetMs) },
            buttonFocusRequester = focusRequester,
            modifier = Modifier.testTag(PlayerTestTags.SkipCreditsAction),
        )
    }
}
```

- [ ] **Step 6: Build to verify no compile errors**

```
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerTestTags.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerScreen.kt
git commit -m "feat: split skip actions into intro (left) and credits (right), add auto-skip"
```

---

## Task 5: Settings screen — Skip Intro category

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/rpeters/cinefintv/ui/screens/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/rpeters/cinefintv/ui/screens/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Write failing SettingsViewModel test**

Add these imports and test method to `SettingsViewModelTest.kt`:

```kotlin
import com.rpeters.cinefintv.data.preferences.IntroSkipPreferences
import com.rpeters.cinefintv.data.preferences.IntroSkipPreferencesRepository
import io.mockk.coVerify
import io.mockk.coEvery
```

```kotlin
@Test
fun setAutoSkipIntro_callsRepository() = runTest {
    val themeRepo = mockk<ThemePreferencesRepository>(relaxed = true)
    val playbackRepo = mockk<PlaybackPreferencesRepository>(relaxed = true)
    val subtitleRepo = mockk<SubtitleAppearancePreferencesRepository>(relaxed = true)
    val libraryRepo = mockk<LibraryActionsPreferencesRepository>(relaxed = true)
    val userRepo = mockk<JellyfinUserRepository>(relaxed = true)
    val introSkipRepo = mockk<IntroSkipPreferencesRepository>(relaxed = true) {
        every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
    }

    every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
    every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
    every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
    every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)
    coEvery { introSkipRepo.setAutoSkipIntro(true) } returns Unit

    val viewModel = SettingsViewModel(themeRepo, playbackRepo, subtitleRepo, libraryRepo, userRepo, introSkipRepo)
    viewModel.setAutoSkipIntro(true)
    advanceUntilIdle()

    coVerify { introSkipRepo.setAutoSkipIntro(true) }
}

@Test
fun setAutoSkipCredits_callsRepository() = runTest {
    val themeRepo = mockk<ThemePreferencesRepository>(relaxed = true)
    val playbackRepo = mockk<PlaybackPreferencesRepository>(relaxed = true)
    val subtitleRepo = mockk<SubtitleAppearancePreferencesRepository>(relaxed = true)
    val libraryRepo = mockk<LibraryActionsPreferencesRepository>(relaxed = true)
    val userRepo = mockk<JellyfinUserRepository>(relaxed = true)
    val introSkipRepo = mockk<IntroSkipPreferencesRepository>(relaxed = true) {
        every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
    }

    every { themeRepo.themePreferencesFlow } returns flowOf(ThemePreferences.DEFAULT)
    every { playbackRepo.preferences } returns flowOf(PlaybackPreferences.DEFAULT)
    every { subtitleRepo.preferencesFlow } returns flowOf(SubtitleAppearancePreferences.DEFAULT)
    every { libraryRepo.preferences } returns flowOf(LibraryActionsPreferences.DEFAULT)
    coEvery { introSkipRepo.setAutoSkipCredits(true) } returns Unit

    val viewModel = SettingsViewModel(themeRepo, playbackRepo, subtitleRepo, libraryRepo, userRepo, introSkipRepo)
    viewModel.setAutoSkipCredits(true)
    advanceUntilIdle()

    coVerify { introSkipRepo.setAutoSkipCredits(true) }
}
```

Also update the existing `init_loadsPreferences` test to pass `introSkipRepo` as the sixth argument:

```kotlin
// In the existing test, add:
val introSkipRepo = mockk<IntroSkipPreferencesRepository>(relaxed = true) {
    every { preferencesFlow } returns flowOf(IntroSkipPreferences.DEFAULT)
}

// And update the SettingsViewModel constructor call:
val viewModel = SettingsViewModel(themeRepo, playbackRepo, subtitleRepo, libraryRepo, userRepo, introSkipRepo)
```

- [ ] **Step 2: Run to verify the new tests fail**

```
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.settings.SettingsViewModelTest"
```

Expected: `FAILED` — `SettingsViewModel` does not accept `introSkipRepo`.

- [ ] **Step 3: Update SettingsUiState**

In `SettingsViewModel.kt`, add the `introSkip` field to `SettingsUiState`:

```kotlin
// Add after libraryActions field:
val introSkip: IntroSkipPreferences = IntroSkipPreferences.DEFAULT,
```

Also add the import:
```kotlin
import com.rpeters.cinefintv.data.preferences.IntroSkipPreferences
import com.rpeters.cinefintv.data.preferences.IntroSkipPreferencesRepository
```

- [ ] **Step 4: Update SettingsViewModel constructor and init**

Add `IntroSkipPreferencesRepository` as the sixth constructor parameter:

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val playbackPreferencesRepository: PlaybackPreferencesRepository,
    private val subtitleAppearancePreferencesRepository: SubtitleAppearancePreferencesRepository,
    private val libraryActionsPreferencesRepository: LibraryActionsPreferencesRepository,
    private val userRepository: JellyfinUserRepository,
    private val introSkipPreferencesRepository: IntroSkipPreferencesRepository,
) : ViewModel()
```

Inside `init {}`, after the existing `combine(...).collect { ... }` launch block, add a second launch:

```kotlin
viewModelScope.launch {
    introSkipPreferencesRepository.preferencesFlow.collectLatest { prefs ->
        _uiState.update { it.copy(introSkip = prefs) }
    }
}
```

Add `collectLatest` import if not present:
```kotlin
import kotlinx.coroutines.flow.collectLatest
```

- [ ] **Step 5: Add setter functions to SettingsViewModel**

After the existing setter functions, add:

```kotlin
fun setAutoSkipIntro(enabled: Boolean) {
    viewModelScope.launch { introSkipPreferencesRepository.setAutoSkipIntro(enabled) }
    _uiState.update { it.copy(introSkip = it.introSkip.copy(autoSkipIntro = enabled)) }
}

fun setAutoSkipCredits(enabled: Boolean) {
    viewModelScope.launch { introSkipPreferencesRepository.setAutoSkipCredits(enabled) }
    _uiState.update { it.copy(introSkip = it.introSkip.copy(autoSkipCredits = enabled)) }
}
```

- [ ] **Step 6: Run SettingsViewModel tests**

```
./gradlew :app:testDebugUnitTest --tests "com.rpeters.cinefintv.ui.screens.settings.SettingsViewModelTest"
```

Expected: all tests pass.

- [ ] **Step 7: Add SKIP_INTRO to SettingsCategory enum in SettingsScreen.kt**

In the `SettingsCategory` enum, add after `ACCOUNT`:

```kotlin
SKIP_INTRO(
    label = "Skip Intro",
    description = "Auto-skip behavior for intros and credits",
    icon = Icons.Default.FastForward,
),
```

Also add the `FastForward` import if needed:
```kotlin
import androidx.compose.material.icons.filled.FastForward
```

- [ ] **Step 8: Add the SKIP_INTRO panel content in the when block**

In `SettingsScreen.kt`, inside the `when (selectedCategory)` block, add after `SettingsCategory.ACCOUNT -> { ... }`:

```kotlin
SettingsCategory.SKIP_INTRO -> {
    CinefinSwitchListItem(
        headline = "Auto-skip intros",
        supporting = "Automatically skip intro segments when detected",
        checked = uiState.introSkip.autoSkipIntro,
        onCheckedChange = viewModel::setAutoSkipIntro,
        modifier = firstSectionItemModifier
    )
    CinefinSwitchListItem(
        headline = "Auto-skip credits",
        supporting = "Automatically skip end credits when detected",
        checked = uiState.introSkip.autoSkipCredits,
        onCheckedChange = viewModel::setAutoSkipCredits,
    )
}
```

- [ ] **Step 9: Build to verify no compile errors**

```
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Run all unit tests**

```
./gradlew :app:testDebugUnitTest
```

Expected: all tests pass.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/rpeters/cinefintv/ui/screens/settings/SettingsScreen.kt \
        app/src/main/java/com/rpeters/cinefintv/ui/screens/settings/SettingsViewModel.kt \
        app/src/test/java/com/rpeters/cinefintv/ui/screens/settings/SettingsViewModelTest.kt
git commit -m "feat: add Skip Intro settings category with auto-skip toggles"
```
