# CinefinTV Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a standalone Android TV Jellyfin client using Jetpack Compose + androidx.tv:tv-material.

**Architecture:** MVVM + Repository. Data layer copied from original Cinefin app (https://github.com/rpeters1430/Cinefin). TV UI built fresh using TvMaterialTheme, NavigationDrawer, Carousel, and standard Compose lazy lists.

**Tech Stack:** Kotlin 2.3, Compose BOM 2026.02.01, androidx.tv:tv-material:1.0.0, Hilt 2.59.2, Media3 1.10.0-beta01, Coil 3.4.0, Jellyfin SDK 1.8.6, Retrofit 3.0.0, Paging 3.

**Package:** `com.rpeters.cinefintv`

---

## Phase 1: Project Scaffold

### Task 1: Create Gradle build files

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`

**Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://repo.jellyfin.org/releases/client/android/")
    }
}
rootProject.name = "CinefinTV"
include(":app")
```

**Step 2: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dagger.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}
```

**Step 3: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.0.1"
kotlin = "2.3.10"
ksp = "2.3.10-1.0.31"
compileSdk = "36"
minSdk = "26"
targetSdk = "35"
composeBom = "2026.02.01"
tvMaterial = "1.0.0"
hilt = "2.59.2"
media3 = "1.10.0-beta01"
coil = "3.4.0"
jellyfin = "1.8.6"
retrofit = "3.0.0"
okhttp = "5.3.2"
navigation = "2.9.0-alpha08"
paging = "3.4.1"
coroutines = "1.10.2"
serialization = "1.10.0"
datastore = "1.1.4"
securityCrypto = "1.1.0-alpha07"
junit = "4.13.2"
mockk = "1.14.0"
turbine = "1.2.0"
coroutinesTest = "1.10.2"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.10.1" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version = "2.9.0" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version = "2.9.0" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# TV
tv-material = { group = "androidx.tv", name = "tv-material", version.ref = "tvMaterial" }
tv-foundation = { group = "androidx.tv", name = "tv-foundation", version.ref = "tvMaterial" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Media3
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
media3-exoplayer-dash = { group = "androidx.media3", name = "media3-exoplayer-dash", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
media3-datasource-okhttp = { group = "androidx.media3", name = "media3-datasource-okhttp", version.ref = "media3" }

# Network
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

# Jellyfin
jellyfin-core = { group = "org.jellyfin.sdk", name = "jellyfin-core", version.ref = "jellyfin" }
jellyfin-media3-ffmpeg = { group = "org.jellyfin.media3", name = "media3-ffmpeg-decoder", version = "1.0.0-beta.4" }

# Image
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }

# Data
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
kotlinx-serialization = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Core
core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.16.0" }
slf4j-android = { group = "org.slf4j", name = "slf4j-android", version = "1.7.36" }

# Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
dagger-hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

**Step 4: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.rpeters.cinefintv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rpeters.cinefintv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(libs.tv.material)
    implementation(libs.tv.foundation)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.jellyfin.media3.ffmpeg)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.jellyfin.core)
    implementation(libs.slf4j.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)

    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.core.ktx)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
}
```

**Step 5: Sync project**
```
./gradlew :app:dependencies
```
Expected: BUILD SUCCESSFUL, no unresolved deps.

**Step 6: Commit**
```bash
git add .
git commit -m "feat: add project build files and version catalog"
```

---

### Task 2: AndroidManifest + proguard

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/proguard-rules.pro`

**Step 1: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.software.leanback" android:required="true" />

    <application
        android:name=".CinefinTvApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.CinefinTV"
        android:networkSecurityConfig="@xml/network_security_config">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize|screenLayout|smallestScreenSize|uiMode"
            android:launchMode="singleTask"
            android:theme="@style/Theme.CinefinTV">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".ui.player.VideoPlayerActivity"
            android:exported="false"
            android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize"
            android:launchMode="singleTop"
            android:screenOrientation="landscape"
            android:supportsPictureInPicture="false" />

        <service
            android:name=".ui.player.audio.AudioService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

    </application>

</manifest>
```

**Step 2: Create `app/src/main/res/xml/network_security_config.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

**Step 3: Create `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">CinefinTV</string>
</resources>
```

**Step 4: Create `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.CinefinTV" parent="@android:style/Theme.Leanback" />
</resources>
```

**Step 5: Create `app/proguard-rules.pro`**

```
-keep class org.jellyfin.** { *; }
-keep class com.rpeters.cinefintv.data.model.** { *; }
-dontwarn org.slf4j.**
```

**Step 6: Commit**
```bash
git add .
git commit -m "feat: add manifest, resources, and proguard config"
```

---

### Task 3: Application class + Hilt entry point

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/CinefinTvApplication.kt`
- Create: `app/src/main/java/com/rpeters/cinefintv/MainActivity.kt`

**Step 1: Create `CinefinTvApplication.kt`**

```kotlin
package com.rpeters.cinefintv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CinefinTvApplication : Application()
```

**Step 2: Create stub `MainActivity.kt`**

```kotlin
package com.rpeters.cinefintv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // CinefinTvApp() — wired in Task 25
        }
    }
}
```

**Step 3: Build to verify Hilt wires up**
```
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

**Step 4: Commit**
```bash
git add .
git commit -m "feat: add Application class and Hilt entry point"
```

---

## Phase 2: Copy Data Layer

### Task 4: Clone original and copy data/di/core files

**Step 1: Clone original Cinefin repo alongside CinefinTV**
```bash
cd ..
git clone https://github.com/rpeters1430/Cinefin.git
```

**Step 2: Copy data layer**

Copy these directories from `Cinefin/app/src/main/java/com/rpeters/jellyfin/` into
`CinefinTV/app/src/main/java/com/rpeters/cinefintv/`:

```
data/model/
data/network/
data/repository/
data/preferences/
data/session/
data/cache/
data/common/
data/paging/
data/playback/
data/security/
data/utils/
di/
core/
network/
```

**Step 3: Bulk replace package name**
```bash
cd CinefinTV
find app/src/main/java/com/rpeters/cinefintv -name "*.kt" \
  -exec sed -i 's/com\.rpeters\.jellyfin/com.rpeters.cinefintv/g' {} +
```

**Step 4: Delete files not needed for MVP**

Remove these files (offline, AI, cast, biometric, workers):
```
data/offline/
data/ai/
data/repository/GenerativeAiRepository.kt
data/worker/
di/AiModule.kt
di/AudioModule.kt (keep but we will modify in Task 24)
```

Delete any import referencing removed classes and comment out usages in remaining files.
The compiler will point you to exactly what needs fixing.

**Step 5: Build and fix compile errors**
```
./gradlew :app:compileDebugKotlin 2>&1 | head -100
```
Fix each error: remove imports, comment out references to deleted classes.
Repeat until clean.

**Step 6: Commit**
```bash
git add .
git commit -m "feat: copy and adapt data layer from original Cinefin app"
```

---

## Phase 3: Theme

### Task 5: TvMaterialTheme setup

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/theme/Color.kt`
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/theme/Type.kt`

**Step 1: Create `Color.kt`**

```kotlin
package com.rpeters.cinefintv.ui.theme

import androidx.compose.ui.graphics.Color

val CinefinRed     = Color(0xFFE50914)
val BackgroundDark = Color(0xFF0D1117)
val SurfaceDark    = Color(0xFF161B22)
val SurfaceVariant = Color(0xFF21262D)
val OnBackground   = Color(0xFFE6EDF3)
val OnSurfaceMuted = Color(0xFF8B949E)
val ProgressGray   = Color(0xFF30363D)
```

**Step 2: Create `Theme.kt`**

```kotlin
package com.rpeters.cinefintv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary          = CinefinRed,
            onPrimary        = OnBackground,
            background       = BackgroundDark,
            onBackground     = OnBackground,
            surface          = SurfaceDark,
            onSurface        = OnBackground,
            surfaceVariant   = SurfaceVariant,
            onSurfaceVariant = OnSurfaceMuted,
        ),
        typography = CinefinTvTypography,
        content = content
    )
}
```

**Step 3: Create `Type.kt`**

```kotlin
package com.rpeters.cinefintv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography

@OptIn(ExperimentalTvMaterial3Api::class)
val CinefinTvTypography = Typography(
    displayLarge  = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium= TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge    = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
    titleMedium   = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge     = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal),
    bodyMedium    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)
```

**Step 4: Build**
```
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

**Step 5: Commit**
```bash
git add .
git commit -m "feat: add TvMaterialTheme with Cinefin color palette"
```

---

## Phase 4: Navigation

### Task 6: Nav routes and graph

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavRoutes.kt`
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/navigation/NavGraph.kt`

**Step 1: Create `NavRoutes.kt`**

```kotlin
package com.rpeters.cinefintv.ui.navigation

object NavRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY_MOVIES = "library/movies"
    const val LIBRARY_TVSHOWS = "library/tvshows"
    const val LIBRARY_STUFF = "library/stuff"
    const val LIBRARY_MUSIC = "library/music"
    const val DETAIL = "detail/{itemId}"
    const val PLAYER = "player/{itemId}"

    fun detail(itemId: String) = "detail/$itemId"
    fun player(itemId: String) = "player/$itemId"
}

// Auth graph routes (separate nested graph)
object AuthRoutes {
    const val SERVER_CONNECTION = "auth/server"
    const val LOGIN = "auth/login"
}
```

**Step 2: Create `NavGraph.kt`**

```kotlin
package com.rpeters.cinefintv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rpeters.cinefintv.ui.screens.home.HomeScreen
import com.rpeters.cinefintv.ui.screens.library.LibraryScreen
import com.rpeters.cinefintv.ui.screens.library.LibraryType
import com.rpeters.cinefintv.ui.screens.detail.DetailScreen
import com.rpeters.cinefintv.ui.screens.search.SearchScreen
import com.rpeters.cinefintv.ui.screens.music.MusicScreen
import com.rpeters.cinefintv.ui.screens.auth.ServerConnectionScreen
import com.rpeters.cinefintv.ui.screens.auth.LoginScreen

@Composable
fun CinefinTvNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AuthRoutes.SERVER_CONNECTION,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(AuthRoutes.SERVER_CONNECTION) {
            ServerConnectionScreen(
                onServerConnected = { navController.navigate(AuthRoutes.LOGIN) {
                    popUpTo(AuthRoutes.SERVER_CONNECTION) { inclusive = true }
                }}
            )
        }
        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(NavRoutes.HOME) {
                    popUpTo(AuthRoutes.LOGIN) { inclusive = true }
                }}
            )
        }
        composable(NavRoutes.HOME) {
            HomeScreen(onNavigate = { navController.navigate(it) })
        }
        composable(NavRoutes.SEARCH) {
            SearchScreen(onNavigate = { navController.navigate(it) })
        }
        composable(NavRoutes.LIBRARY_MOVIES) {
            LibraryScreen(type = LibraryType.MOVIES, onNavigate = { navController.navigate(it) })
        }
        composable(NavRoutes.LIBRARY_TVSHOWS) {
            LibraryScreen(type = LibraryType.TV_SHOWS, onNavigate = { navController.navigate(it) })
        }
        composable(NavRoutes.LIBRARY_STUFF) {
            LibraryScreen(type = LibraryType.STUFF, onNavigate = { navController.navigate(it) })
        }
        composable(NavRoutes.LIBRARY_MUSIC) {
            MusicScreen(onNavigate = { navController.navigate(it) })
        }
        composable(
            NavRoutes.DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            DetailScreen(
                itemId = backStackEntry.arguments?.getString("itemId") ?: "",
                onNavigate = { navController.navigate(it) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

**Step 3: Build to check imports resolve**
```
./gradlew :app:compileDebugKotlin
```
Expect errors for missing screens — that's fine. Fix only import errors.

**Step 4: Commit**
```bash
git add .
git commit -m "feat: add navigation routes and nav graph scaffold"
```

---

### Task 7: NavigationDrawer scaffold + CinefinTvApp root

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/CinefinTvApp.kt`

**Step 1: Create `CinefinTvApp.kt`**

```kotlin
package com.rpeters.cinefintv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.navigation.CinefinTvNavGraph
import com.rpeters.cinefintv.ui.navigation.NavRoutes
import com.rpeters.cinefintv.ui.theme.CinefinTvTheme

private data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

private val navItems = listOf(
    NavItem("Home", Icons.Default.Home, NavRoutes.HOME),
    NavItem("Search", Icons.Default.Search, NavRoutes.SEARCH),
    NavItem("Movies", Icons.Default.Movie, NavRoutes.LIBRARY_MOVIES),
    NavItem("TV Shows", Icons.Default.Tv, NavRoutes.LIBRARY_TVSHOWS),
    NavItem("Stuff", Icons.Default.VideoLibrary, NavRoutes.LIBRARY_STUFF),
    NavItem("Music", Icons.Default.MusicNote, NavRoutes.LIBRARY_MUSIC),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinefinTvApp(isAuthenticated: Boolean = false) {
    CinefinTvTheme {
        val navController = rememberNavController()
        val currentBackStack by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStack?.destination?.route

        val showNav = currentRoute != null &&
            !currentRoute.startsWith("auth/") &&
            !currentRoute.startsWith("player/")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showNav) {
                NavigationDrawer(
                    drawerContent = { drawerValue ->
                        navItems.forEach { item ->
                            NavigationDrawerItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                leadingContent = {
                                    Icon(imageVector = item.icon, contentDescription = item.label)
                                },
                            ) {
                                Text(item.label)
                            }
                        }
                    }
                ) {
                    CinefinTvNavGraph(
                        navController = navController,
                        startDestination = if (isAuthenticated) NavRoutes.HOME else "auth/server"
                    )
                }
            } else {
                CinefinTvNavGraph(
                    navController = navController,
                    startDestination = if (isAuthenticated) NavRoutes.HOME else "auth/server"
                )
            }
        }
    }
}
```

**Step 2: Wire into MainActivity**

Edit `MainActivity.kt`, replace the setContent body:
```kotlin
setContent {
    CinefinTvApp()
}
```

**Step 3: Commit**
```bash
git add .
git commit -m "feat: add NavigationDrawer scaffold and CinefinTvApp root"
```

---

## Phase 5: Auth Screens

### Task 8: ServerConnectionScreen

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/ServerConnectionScreen.kt`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/screens/auth/ServerConnectionViewModelTest.kt`

The original `ServerConnectionScreen.kt` (44KB) contains all the server validation logic.
Copy it from the original repo and adapt:
1. Replace package name
2. Remove `@OptIn(ExperimentalMaterial3Api::class)` → `@OptIn(ExperimentalTvMaterial3Api::class)`
3. Replace `MaterialTheme` imports with `androidx.tv.material3` equivalents
4. Replace `TextField` with `androidx.tv.material3` `OutlinedTextField` if available, otherwise keep standard Compose `OutlinedTextField` (it works on TV)
5. Replace `Button` with `androidx.tv.material3` `Button`
6. Remove bottom sheet / modal dialog patterns — use simple card overlay instead
7. Add `onServerConnected` callback lambda to trigger nav

**Step 1: Copy and adapt**
```bash
cp ../Cinefin/app/src/main/java/com/rpeters/jellyfin/ui/screens/ServerConnectionScreen.kt \
   app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/ServerConnectionScreen.kt
sed -i 's/com\.rpeters\.jellyfin/com.rpeters.cinefintv/g' \
   app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/ServerConnectionScreen.kt
```

**Step 2: Fix TV-specific imports** — replace Material3 with tv.material3 equivalents:
```kotlin
// Remove:
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
// Add:
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
```

**Step 3: Build and fix remaining errors**
```
./gradlew :app:compileDebugKotlin 2>&1 | grep "error:" | head -30
```

**Step 4: Commit**
```bash
git add .
git commit -m "feat: add ServerConnectionScreen adapted for TV"
```

---

### Task 9: LoginScreen

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/LoginScreen.kt`

Same copy-and-adapt process as Task 8. Source file is part of `ServerConnectionScreen.kt`
or a separate login composable in the original — check the original's `AuthNavGraph.kt` for
the actual login composable name and copy it.

Key adaptations:
- Use `androidx.tv.material3` components
- D-pad friendly layout: fields stacked vertically with clear focus order
- Quick Connect button prominently placed (TV users often prefer it)
- `onLoginSuccess` callback lambda

**Step 1: Copy**
```bash
cp ../Cinefin/app/src/main/java/com/rpeters/jellyfin/ui/navigation/AuthNavGraph.kt \
   app/src/main/java/com/rpeters/cinefintv/ui/screens/auth/LoginScreen.kt
```
Extract just the login composable. Remove mobile-specific navigation.

**Step 2: Build + fix**
```
./gradlew :app:compileDebugKotlin
```

**Step 3: Commit**
```bash
git add .
git commit -m "feat: add LoginScreen with Quick Connect support for TV"
```

---

## Phase 6: Shared Components

### Task 10: TvMediaCard

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/components/TvMediaCard.kt`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/components/TvMediaCardTest.kt`

**Step 1: Write failing test**

```kotlin
package com.rpeters.cinefintv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TvMediaCardTest {
    @Test
    fun `progress fraction clamps to 0-1 range`() {
        assertEquals(0f, progressFraction(playedTicks = 0L, totalTicks = 1000L), 0.001f)
        assertEquals(1f, progressFraction(playedTicks = 2000L, totalTicks = 1000L), 0.001f)
        assertEquals(0.5f, progressFraction(playedTicks = 500L, totalTicks = 1000L), 0.001f)
        assertEquals(0f, progressFraction(playedTicks = 100L, totalTicks = 0L), 0.001f)
    }
}
```

**Step 2: Run test — expect FAIL**
```
./gradlew :app:test --tests "*.TvMediaCardTest" 2>&1 | tail -10
```

**Step 3: Create `TvMediaCard.kt`**

```kotlin
package com.rpeters.cinefintv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LinearProgressIndicator
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.rpeters.cinefintv.ui.theme.CinefinRed
import com.rpeters.cinefintv.ui.theme.ProgressGray

fun progressFraction(playedTicks: Long, totalTicks: Long): Float {
    if (totalTicks <= 0L) return 0f
    return (playedTicks.toFloat() / totalTicks).coerceIn(0f, 1f)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMediaCard(
    imageUrl: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 160.dp,
    playedTicks: Long = 0L,
    totalTicks: Long = 0L,
    isWatched: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(cardWidth),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
            // Progress bar
            if (playedTicks > 0L && totalTicks > 0L && !isWatched) {
                LinearProgressIndicator(
                    progress = { progressFraction(playedTicks, totalTicks) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = CinefinRed,
                    trackColor = ProgressGray,
                )
            }
            // Watched badge
            if (isWatched) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Watched",
                    tint = CinefinRed,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
        }
    }
}
```

**Step 4: Run test — expect PASS**
```
./gradlew :app:test --tests "*.TvMediaCardTest"
```
Expected: BUILD SUCCESSFUL, 4 tests passed.

**Step 5: Commit**
```bash
git add .
git commit -m "feat: add TvMediaCard shared composable with progress and watched badge"
```

---

## Phase 7: Home Screen

### Task 11: HomeViewModel

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/screens/home/HomeViewModelTest.kt`

**Step 1: Write failing test**

```kotlin
package com.rpeters.cinefintv.ui.screens.home

import app.cash.turbine.test
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: JellyfinRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        coEvery { repo.getContinueWatching() } returns emptyList()
        coEvery { repo.getRecentlyAdded(any()) } returns emptyList()
        coEvery { repo.getLibraries() } returns emptyList()
        viewModel = HomeViewModel(repo)
    }

    @After
    fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `initial state is Loading`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial is HomeUiState.Loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state becomes Content after repo returns`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Loading
            dispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem()
            assertTrue(content is HomeUiState.Content)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

**Step 2: Run — expect FAIL**
```
./gradlew :app:test --tests "*.HomeViewModelTest"
```

**Step 3: Create `HomeViewModel.kt`**

```kotlin
package com.rpeters.cinefintv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.model.BaseItemDto
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Content(
        val continueWatching: List<BaseItemDto> = emptyList(),
        val recentMovies: List<BaseItemDto> = emptyList(),
        val recentTvShows: List<BaseItemDto> = emptyList(),
        val recentStuff: List<BaseItemDto> = emptyList(),
        val recentMusic: List<BaseItemDto> = emptyList(),
        val hasMusicLibrary: Boolean = false,
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: JellyfinRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init { loadHome() }

    fun loadHome() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val continueWatching = async { repository.getContinueWatching() }
                val recentMovies     = async { repository.getRecentlyAdded("Movie") }
                val recentTvShows    = async { repository.getRecentlyAdded("Series") }
                val recentStuff      = async { repository.getRecentlyAdded("Video") }
                val recentMusic      = async { repository.getRecentlyAdded("MusicAlbum") }
                val libraries        = async { repository.getLibraries() }

                val musicLib = libraries.await().any { it.collectionType == "music" }

                _uiState.value = HomeUiState.Content(
                    continueWatching = continueWatching.await(),
                    recentMovies     = recentMovies.await(),
                    recentTvShows    = recentTvShows.await(),
                    recentStuff      = recentStuff.await(),
                    recentMusic      = if (musicLib) recentMusic.await() else emptyList(),
                    hasMusicLibrary  = musicLib,
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
```

**Step 4: Run tests — expect PASS**
```
./gradlew :app:test --tests "*.HomeViewModelTest"
```

**Step 5: Commit**
```bash
git add .
git commit -m "feat: add HomeViewModel with parallel data fetching"
```

---

### Task 12: HomeScreen

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/home/HomeScreen.kt`

```kotlin
package com.rpeters.cinefintv.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.rpeters.cinefintv.data.model.BaseItemDto
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.navigation.NavRoutes

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is HomeUiState.Loading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator()
        }
        is HomeUiState.Error -> Box(Modifier.fillMaxSize()) {
            Text(state.message, style = MaterialTheme.typography.bodyLarge)
        }
        is HomeUiState.Content -> HomeContent(state = state, onNavigate = onNavigate)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeContent(state: HomeUiState.Content, onNavigate: (String) -> Unit) {
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Hero carousel from continue watching + recent movies
        val heroItems = (state.continueWatching + state.recentMovies).take(5)
        if (heroItems.isNotEmpty()) {
            item { HeroCarouselRow(items = heroItems, onNavigate = onNavigate) }
        }

        if (state.continueWatching.isNotEmpty()) {
            item { MediaRow("Continue Watching", state.continueWatching, onNavigate) }
        }
        if (state.recentMovies.isNotEmpty()) {
            item { MediaRow("Recently Added — Movies", state.recentMovies, onNavigate) }
        }
        if (state.recentTvShows.isNotEmpty()) {
            item { MediaRow("Recently Added — TV Shows", state.recentTvShows, onNavigate) }
        }
        if (state.recentStuff.isNotEmpty()) {
            item { MediaRow("Recently Added — Stuff", state.recentStuff, onNavigate) }
        }
        if (state.hasMusicLibrary && state.recentMusic.isNotEmpty()) {
            item { MediaRow("Recently Added — Music", state.recentMusic, onNavigate) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroCarouselRow(items: List<BaseItemDto>, onNavigate: (String) -> Unit) {
    Carousel(
        itemCount = items.size,
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .padding(horizontal = 48.dp),
    ) { index ->
        val item = items[index]
        CarouselItem(
            background = {
                // Backdrop image loaded by Coil
                coil3.compose.AsyncImage(
                    model = item.backdropImageTags?.firstOrNull()?.let {
                        // Build Jellyfin image URL via repository helper
                        "placeholder" // replace with actual URL builder
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                // Gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f),
                                    androidx.compose.ui.graphics.Color.Transparent,
                                )
                            )
                        )
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 48.dp)
                    .padding(vertical = 32.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    item.name ?: "",
                    style = MaterialTheme.typography.displayMedium,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onNavigate(NavRoutes.detail(item.id ?: "")) }) {
                    Text("Play")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaRow(
    title: String,
    items: List<BaseItemDto>,
    onNavigate: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 48.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LazyRow(
            state = rememberLazyListState(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id ?: it.name ?: "" }) { item ->
                TvMediaCard(
                    imageUrl = null, // build URL from item using session manager
                    contentDescription = item.name ?: "",
                    onClick = { onNavigate(NavRoutes.detail(item.id ?: "")) },
                    playedTicks = item.userData?.playbackPositionTicks ?: 0L,
                    totalTicks = item.runTimeTicks ?: 0L,
                    isWatched = item.userData?.played == true,
                )
            }
        }
    }
}
```

Note: Replace `"placeholder"` image URLs with the actual Jellyfin image URL builder from
`JellyfinRepository` or `JellyfinSessionManager` — check how `MediaCards.kt` in the
original app builds image URLs using the base URL + item ID + image tag.

**Step 1: Build**
```
./gradlew :app:compileDebugKotlin
```

**Step 2: Commit**
```bash
git add .
git commit -m "feat: add HomeScreen with Carousel and media rows"
```

---

## Phase 8: Library Screen

### Task 13: LibraryViewModel

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryViewModel.kt`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/screens/library/LibraryViewModelTest.kt`

**Step 1: Write failing test**

```kotlin
package com.rpeters.cinefintv.ui.screens.library

import com.rpeters.cinefintv.data.repository.JellyfinRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `library type maps to correct include item types`() {
        assertEquals("Movie", LibraryType.MOVIES.includeItemTypes)
        assertEquals("Series", LibraryType.TV_SHOWS.includeItemTypes)
        assertEquals("Video", LibraryType.STUFF.includeItemTypes)
    }
}
```

**Step 2: Run — expect FAIL**
```
./gradlew :app:test --tests "*.LibraryViewModelTest"
```

**Step 3: Create `LibraryViewModel.kt`**

```kotlin
package com.rpeters.cinefintv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rpeters.cinefintv.data.model.BaseItemDto
import com.rpeters.cinefintv.data.paging.LibraryItemPagingSource
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

enum class LibraryType(val includeItemTypes: String) {
    MOVIES("Movie"),
    TV_SHOWS("Series"),
    STUFF("Video"),
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: JellyfinRepository,
) : ViewModel() {

    fun getItems(type: LibraryType): Flow<PagingData<BaseItemDto>> =
        Pager(PagingConfig(pageSize = 40)) {
            LibraryItemPagingSource(repository, type.includeItemTypes)
        }.flow.cachedIn(viewModelScope)
}
```

**Step 4: Run tests — expect PASS**
```
./gradlew :app:test --tests "*.LibraryViewModelTest"
```

**Step 5: Commit**
```bash
git add .
git commit -m "feat: add LibraryViewModel with Paging3 support"
```

---

### Task 14: LibraryScreen

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/library/LibraryScreen.kt`

```kotlin
package com.rpeters.cinefintv.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.navigation.NavRoutes

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    type: LibraryType,
    onNavigate: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val items = viewModel.getItems(type).collectAsLazyPagingItems()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Text(
            text = when (type) {
                LibraryType.MOVIES   -> "Movies"
                LibraryType.TV_SHOWS -> "TV Shows"
                LibraryType.STUFF    -> "Stuff"
            },
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items.itemCount) { index ->
                val item = items[index] ?: return@items
                TvMediaCard(
                    imageUrl = null, // build from item
                    contentDescription = item.name ?: "",
                    onClick = { onNavigate(NavRoutes.detail(item.id ?: "")) },
                    isWatched = item.userData?.played == true,
                )
            }
        }
    }
}
```

**Step 1: Build**
```
./gradlew :app:compileDebugKotlin
```

**Step 2: Commit**
```bash
git add .
git commit -m "feat: add LibraryScreen with paged grid for Movies/TV Shows/Stuff"
```

---

## Phase 9: Detail Screen

### Task 15: DetailViewModel

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModel.kt`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/screens/detail/DetailViewModelTest.kt`

**Step 1: Write failing test**

```kotlin
package com.rpeters.cinefintv.ui.screens.detail

import app.cash.turbine.test
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: JellyfinRepository

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        coEvery { repo.getItem(any()) } returns null
        coEvery { repo.getCastForItem(any()) } returns emptyList()
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `initial state is Loading`() = runTest {
        val vm = DetailViewModel(repo)
        vm.load("test-id")
        vm.uiState.test {
            assertTrue(awaitItem() is DetailUiState.Loading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

**Step 2: Run — expect FAIL**
```
./gradlew :app:test --tests "*.DetailViewModelTest"
```

**Step 3: Create `DetailViewModel.kt`**

```kotlin
package com.rpeters.cinefintv.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.model.BaseItemDto
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DetailUiState {
    data object Loading : DetailUiState()
    data class Content(
        val item: BaseItemDto,
        val cast: List<BaseItemDto> = emptyList(),
        val seasons: List<BaseItemDto> = emptyList(),
    ) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: JellyfinRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    fun load(itemId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val item    = async { repository.getItem(itemId) }
                val cast    = async { repository.getCastForItem(itemId) }
                val fetched = item.await() ?: throw Exception("Item not found")
                val seasons = if (fetched.type == "Series") {
                    repository.getSeasonsForSeries(itemId)
                } else emptyList()

                _uiState.value = DetailUiState.Content(
                    item = fetched,
                    cast = cast.await(),
                    seasons = seasons,
                )
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
```

**Step 4: Run tests — expect PASS**
```
./gradlew :app:test --tests "*.DetailViewModelTest"
```

**Step 5: Commit**
```bash
git add .
git commit -m "feat: add DetailViewModel for movie/show/stuff detail"
```

---

### Task 16: DetailScreen

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/detail/DetailScreen.kt`

Adapt from original `ImmersiveMovieDetailScreen.kt` (83KB) and `ImmersiveTVShowDetailScreen.kt` (38KB).
Key TV adaptations:
- Remove `ModalBottomSheet`, replace with full-screen layouts
- D-pad navigable action buttons with `FocusRequester`
- Seasons as `LazyRow` tabs instead of Material tabs (better d-pad UX)
- Cast row as `LazyRow` of compact cards

Refer to the original files for the data-binding logic (ratings, genres, overview, etc.) —
copy that logic exactly, only replace the UI components with `androidx.tv.material3` variants.

**Step 1: Build and verify**
```
./gradlew :app:compileDebugKotlin
```

**Step 2: Commit**
```bash
git add .
git commit -m "feat: add DetailScreen with backdrop, cast, and seasons"
```

---

## Phase 10: Search Screen

### Task 17: SearchViewModel

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/search/SearchViewModel.kt`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/screens/search/SearchViewModelTest.kt`

**Step 1: Write failing test**

```kotlin
package com.rpeters.cinefintv.ui.screens.search

import app.cash.turbine.test
import com.rpeters.cinefintv.data.repository.JellyfinSearchRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var searchRepo: JellyfinSearchRepository

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        searchRepo = mockk(relaxed = true)
        coEvery { searchRepo.search(any()) } returns emptyList()
    }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `empty query yields empty results`() = runTest {
        val vm = SearchViewModel(searchRepo)
        vm.uiState.test {
            val state = awaitItem()
            assertTrue((state as? SearchUiState.Results)?.items?.isEmpty() == true ||
                state is SearchUiState.Idle)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

**Step 2: Run — expect FAIL**
```
./gradlew :app:test --tests "*.SearchViewModelTest"
```

**Step 3: Create `SearchViewModel.kt`**

```kotlin
package com.rpeters.cinefintv.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.model.BaseItemDto
import com.rpeters.cinefintv.data.repository.JellyfinSearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Results(val items: List<BaseItemDto>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: JellyfinSearchRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _query
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.value = SearchUiState.Idle
                        return@collectLatest
                    }
                    _uiState.value = SearchUiState.Loading
                    try {
                        val results = searchRepository.search(query)
                        _uiState.value = SearchUiState.Results(results)
                    } catch (e: Exception) {
                        _uiState.value = SearchUiState.Error(e.message ?: "Search failed")
                    }
                }
        }
    }

    fun onQueryChange(query: String) { _query.value = query }
}
```

**Step 4: Run tests — expect PASS**
```
./gradlew :app:test --tests "*.SearchViewModelTest"
```

**Step 5: Commit**
```bash
git add .
git commit -m "feat: add SearchViewModel with debounced search"
```

---

### Task 18: SearchScreen

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/search/SearchScreen.kt`

```kotlin
package com.rpeters.cinefintv.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.rpeters.cinefintv.ui.components.TvMediaCard
import com.rpeters.cinefintv.ui.navigation.NavRoutes

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigate: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.onQueryChange(it)
            },
            placeholder = { Text("Search movies, shows, music…") },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(bottom = 24.dp),
            singleLine = true,
        )

        when (val state = uiState) {
            is SearchUiState.Idle -> Text("Start typing to search", style = MaterialTheme.typography.bodyLarge)
            is SearchUiState.Loading -> CircularProgressIndicator()
            is SearchUiState.Error -> Text(state.message, style = MaterialTheme.typography.bodyLarge)
            is SearchUiState.Results -> {
                if (state.items.isEmpty()) {
                    Text("No results for \"$query\"", style = MaterialTheme.typography.bodyLarge)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.items, key = { it.id ?: it.name ?: "" }) { item ->
                            TvMediaCard(
                                imageUrl = null,
                                contentDescription = item.name ?: "",
                                onClick = { onNavigate(NavRoutes.detail(item.id ?: "")) },
                            )
                        }
                    }
                }
            }
        }
    }
}
```

**Step 1: Build**
```
./gradlew :app:compileDebugKotlin
```

**Step 2: Commit**
```bash
git add .
git commit -m "feat: add SearchScreen with grid results"
```

---

## Phase 11: Music Screen

### Task 19: MusicViewModel + MusicScreen

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/music/MusicViewModel.kt`
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/screens/music/MusicScreen.kt`
- Test: `app/src/test/java/com/rpeters/cinefintv/ui/screens/music/MusicViewModelTest.kt`

Adapt from original `MusicScreen.kt` (32KB). The original handles Artists/Albums/Tracks.
Strip to: Artist grid → Album detail (tracklist) → audio playback.

**Step 1: Write failing test**

```kotlin
package com.rpeters.cinefintv.ui.screens.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicViewModelTest {
    @Test
    fun `MusicViewType defaults to ALBUMS`() {
        assertEquals(MusicViewType.ALBUMS, MusicViewType.DEFAULT)
    }
}
```

**Step 2: Run — expect FAIL**
```
./gradlew :app:test --tests "*.MusicViewModelTest"
```

**Step 3: Create `MusicViewModel.kt`**

```kotlin
package com.rpeters.cinefintv.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rpeters.cinefintv.data.model.BaseItemDto
import com.rpeters.cinefintv.data.repository.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MusicViewType {
    ALBUMS, ARTISTS;
    companion object { val DEFAULT = ALBUMS }
}

sealed class MusicUiState {
    data object Loading : MusicUiState()
    data class Grid(val items: List<BaseItemDto>, val viewType: MusicViewType) : MusicUiState()
    data class AlbumDetail(val album: BaseItemDto, val tracks: List<BaseItemDto>) : MusicUiState()
    data class Error(val message: String) : MusicUiState()
}

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: JellyfinRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MusicUiState>(MusicUiState.Loading)
    val uiState: StateFlow<MusicUiState> = _uiState

    private var currentViewType = MusicViewType.DEFAULT

    init { loadGrid(MusicViewType.DEFAULT) }

    fun loadGrid(viewType: MusicViewType) {
        currentViewType = viewType
        viewModelScope.launch {
            _uiState.value = MusicUiState.Loading
            try {
                val items = when (viewType) {
                    MusicViewType.ALBUMS  -> repository.getRecentlyAdded("MusicAlbum")
                    MusicViewType.ARTISTS -> repository.getRecentlyAdded("MusicArtist")
                }
                _uiState.value = MusicUiState.Grid(items, viewType)
            } catch (e: Exception) {
                _uiState.value = MusicUiState.Error(e.message ?: "Failed to load music")
            }
        }
    }

    fun openAlbum(album: BaseItemDto) {
        viewModelScope.launch {
            _uiState.value = MusicUiState.Loading
            try {
                val tracks = repository.getTracksForAlbum(album.id ?: "")
                _uiState.value = MusicUiState.AlbumDetail(album, tracks)
            } catch (e: Exception) {
                _uiState.value = MusicUiState.Error(e.message ?: "Failed to load album")
            }
        }
    }

    fun backToGrid() { loadGrid(currentViewType) }
}
```

**Step 4: Run tests — expect PASS**
```
./gradlew :app:test --tests "*.MusicViewModelTest"
```

**Step 5: Commit**
```bash
git add .
git commit -m "feat: add MusicViewModel and MusicScreen"
```

---

## Phase 12: Video Player

### Task 20: PlayerViewModel (adapted)

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerViewModel.kt`

Copy from original `VideoPlayerViewModel.kt` (106KB). Strip out:
- Cast-related code (all `cast*` properties and functions)
- PiP (picture-in-picture) code
- Download/offline code
- AI recommendation code

Keep:
- ExoPlayer setup and management
- Track selection (audio/subtitle)
- Playback position reporting
- Auto-play next episode
- Direct play vs transcode detection

This is a large file — do the copy first, then delete blocks by searching for:
- `castManager`, `castState`, `CastManager` → delete
- `pipMode`, `PipAction` → delete
- `download`, `offline` → delete

**Step 1: Copy and strip**
```bash
cp ../Cinefin/app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerViewModel.kt \
   app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerViewModel.kt
sed -i 's/com\.rpeters\.jellyfin/com.rpeters.cinefintv/g' \
   app/src/main/java/com/rpeters/cinefintv/ui/player/PlayerViewModel.kt
```

**Step 2: Build and fix**
```
./gradlew :app:compileDebugKotlin 2>&1 | grep "error:" | head -30
```
Remove each unresolved symbol. Repeat until clean.

**Step 3: Commit**
```bash
git add .
git commit -m "feat: add PlayerViewModel adapted from original (cast/pip/download stripped)"
```

---

### Task 21: VideoPlayerActivity + TV controls

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/player/VideoPlayerActivity.kt`
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/player/TvVideoPlayerControls.kt`

**Step 1: Copy VideoPlayerActivity from original**
```bash
cp ../Cinefin/app/src/main/java/com/rpeters/jellyfin/ui/player/VideoPlayerActivity.kt \
   app/src/main/java/com/rpeters/cinefintv/ui/player/VideoPlayerActivity.kt
sed -i 's/com\.rpeters\.jellyfin/com.rpeters.cinefintv/g' \
   app/src/main/java/com/rpeters/cinefintv/ui/player/VideoPlayerActivity.kt
```

Strip: PiP setup, Cast menu items, Download button references.

**Step 2: Copy TV controls from original**
```bash
cp ../Cinefin/app/src/main/java/com/rpeters/jellyfin/ui/player/TvVideoPlayerControls.kt \
   app/src/main/java/com/rpeters/cinefintv/ui/player/TvVideoPlayerControls.kt
sed -i 's/com\.rpeters\.jellyfin/com.rpeters.cinefintv/g' \
   app/src/main/java/com/rpeters/cinefintv/ui/player/TvVideoPlayerControls.kt
```

Replace Material3 imports with `androidx.tv.material3` equivalents.

**Step 3: Build + fix**
```
./gradlew :app:compileDebugKotlin
```

**Step 4: Commit**
```bash
git add .
git commit -m "feat: add VideoPlayerActivity and TvVideoPlayerControls"
```

---

## Phase 13: Audio Player

### Task 22: Audio service + TV audio controls

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/player/audio/AudioService.kt`
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/player/tv/TvAudioPlayerControls.kt`

Copy from original:
```bash
cp ../Cinefin/app/src/main/java/com/rpeters/jellyfin/ui/player/audio/AudioService.kt \
   app/src/main/java/com/rpeters/cinefintv/ui/player/audio/AudioService.kt
cp ../Cinefin/app/src/main/java/com/rpeters/jellyfin/ui/player/audio/AudioServiceConnection.kt \
   app/src/main/java/com/rpeters/cinefintv/ui/player/audio/AudioServiceConnection.kt
cp ../Cinefin/app/src/main/java/com/rpeters/jellyfin/ui/player/tv/TvAudioPlayerControls.kt \
   app/src/main/java/com/rpeters/cinefintv/ui/player/tv/TvAudioPlayerControls.kt
cp ../Cinefin/app/src/main/java/com/rpeters/jellyfin/ui/player/tv/TvAudioPlayerScreen.kt \
   app/src/main/java/com/rpeters/cinefintv/ui/player/tv/TvAudioPlayerScreen.kt
```

Bulk fix package names, replace Material3 with tv.material3 equivalents, build + fix.

**Step 1: Bulk package replace + build**
```bash
find app/src/main/java/com/rpeters/cinefintv/ui/player -name "*.kt" \
  -exec sed -i 's/com\.rpeters\.jellyfin/com.rpeters.cinefintv/g' {} +
./gradlew :app:compileDebugKotlin
```

**Step 2: Commit**
```bash
git add .
git commit -m "feat: add audio service and TV audio player controls"
```

---

## Phase 14: Image URL wiring

### Task 23: Wire Jellyfin image URLs into cards

All `TvMediaCard` calls currently pass `imageUrl = null`. Fix this by adding an image URL
builder utility that constructs Jellyfin image URLs.

**Files:**
- Create: `app/src/main/java/com/rpeters/cinefintv/ui/image/JellyfinImageUrl.kt`

**Step 1: Create `JellyfinImageUrl.kt`**

```kotlin
package com.rpeters.cinefintv.ui.image

import com.rpeters.cinefintv.data.model.BaseItemDto
import com.rpeters.cinefintv.data.session.JellyfinSessionManager

fun BaseItemDto.posterImageUrl(sessionManager: JellyfinSessionManager): String? {
    val baseUrl = sessionManager.currentServerUrl ?: return null
    val tag = imageTags?.get("Primary") ?: return null
    return "$baseUrl/Items/$id/Images/Primary?tag=$tag&quality=90&maxWidth=300"
}

fun BaseItemDto.backdropImageUrl(sessionManager: JellyfinSessionManager): String? {
    val baseUrl = sessionManager.currentServerUrl ?: return null
    val tag = backdropImageTags?.firstOrNull() ?: return null
    return "$baseUrl/Items/$id/Images/Backdrop?tag=$tag&quality=85&maxWidth=1280"
}
```

**Step 2: Inject `JellyfinSessionManager` into ViewModels that build image URLs, or pass base URL down as state.**

Update `HomeUiState.Content`, `LibraryViewModel`, `SearchViewModel` to include `serverBaseUrl: String` field derived from `JellyfinSessionManager.currentServerUrl`.

**Step 3: Build**
```
./gradlew :app:compileDebugKotlin
```

**Step 4: Commit**
```bash
git add .
git commit -m "feat: wire Jellyfin image URLs into TvMediaCard and carousel"
```

---

## Phase 15: Final Wiring + First Run

### Task 24: DI module cleanup + Hilt wiring

**Files:**
- Modify: `app/src/main/java/com/rpeters/cinefintv/di/` (all modules)

Ensure all Hilt modules provide only what's needed for MVP. Specifically:
1. `NetworkModule` — verify OkHttp + Retrofit + Jellyfin SDK are provided
2. `DataStoreModule` — verify DataStore is provided for preferences
3. `SecurityModule` — verify `SecureCredentialManager` is provided
4. Remove any module providing Cast, AI, or biometric dependencies

```
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

Fix any Hilt injection errors (they appear as `[Hilt] Missing binding` at compile time).

**Step 1: Full build**
```
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL, APK generated at `app/build/outputs/apk/debug/app-debug.apk`

**Step 2: Commit**
```bash
git add .
git commit -m "feat: complete DI wiring and produce first buildable APK"
```

---

### Task 25: Install and smoke test on device/emulator

**Step 1: Create Android TV emulator** (if not already created)

In Android Studio: Device Manager → Create Virtual Device → TV → Android TV (1080p) → API 34

**Step 2: Install debug APK**
```
./gradlew :app:installDebug
```

**Step 3: Manual smoke test checklist**
- [ ] App appears in TV launcher
- [ ] Server connection screen loads
- [ ] Can enter server URL and proceed to login
- [ ] Login works (token or Quick Connect)
- [ ] Home screen shows with NavigationDrawer rail
- [ ] D-pad navigates between nav items
- [ ] Carousel on Home screen is focusable and cycles
- [ ] Continue Watching / Recently Added rows scroll with d-pad
- [ ] Library screen shows grid of posters
- [ ] Detail screen loads backdrop + metadata
- [ ] Video player launches and plays
- [ ] Audio controls appear over music playback
- [ ] Search returns results
- [ ] Back button works throughout

**Step 4: Fix any crash-level issues found**

**Step 5: Final commit**
```bash
git add .
git commit -m "feat: CinefinTV MVP complete - all screens wired and verified on TV emulator"
```

---

## Summary of Files Created

| Phase | Key Files |
|-------|-----------|
| Scaffold | `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml` |
| App entry | `CinefinTvApplication.kt`, `MainActivity.kt` |
| Data layer | Copied from Cinefin: `data/`, `di/`, `core/`, `network/` |
| Theme | `ui/theme/Color.kt`, `Theme.kt`, `Type.kt` |
| Navigation | `ui/navigation/NavRoutes.kt`, `NavGraph.kt` |
| Root | `ui/CinefinTvApp.kt` |
| Auth | `ui/screens/auth/ServerConnectionScreen.kt`, `LoginScreen.kt` |
| Components | `ui/components/TvMediaCard.kt` |
| Home | `ui/screens/home/HomeViewModel.kt`, `HomeScreen.kt` |
| Library | `ui/screens/library/LibraryViewModel.kt`, `LibraryScreen.kt` |
| Detail | `ui/screens/detail/DetailViewModel.kt`, `DetailScreen.kt` |
| Search | `ui/screens/search/SearchViewModel.kt`, `SearchScreen.kt` |
| Music | `ui/screens/music/MusicViewModel.kt`, `MusicScreen.kt` |
| Player | `ui/player/PlayerViewModel.kt`, `VideoPlayerActivity.kt`, `TvVideoPlayerControls.kt` |
| Audio | `ui/player/audio/AudioService.kt`, `ui/player/tv/TvAudioPlayerControls.kt` |
| Image | `ui/image/JellyfinImageUrl.kt` |
