package com.rpeters.cinefintv.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.rpeters.cinefintv"
private const val IDLE_TIMEOUT_MS = 8_000L

/**
 * Generates a baseline profile for the app's startup and primary navigation paths.
 *
 * Run with:
 *   ./gradlew :macrobenchmark:generateBaselineProfile
 *
 * The resulting baseline-prof.txt is written into app/src/main/ automatically
 * when using the AGP baseline profile plugin. Copy it there manually if needed.
 *
 * Requires a physical device or emulator with API 28+ and a rooted/eng build
 * (or use a device with profileinstaller + shell access via adb).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Wait for the app to settle — either on the login screen or home screen.
        device.waitForIdle(IDLE_TIMEOUT_MS)

        // If the home screen loaded (session was already saved), scroll the content
        // shelf to warm the LazyRow composition paths.
        val homeContent = device.wait(
            Until.findObject(By.scrollable(true)),
            IDLE_TIMEOUT_MS,
        )
        if (homeContent != null) {
            // Scroll to trigger prefetch and warm paging / image loading code paths.
            repeat(3) {
                device.waitForIdle(1_000L)
            }
        }
    }
}

/**
 * Startup benchmark — measures cold start time under different compilation modes.
 * Run separately from the profile generator.
 *
 *   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
 *       -P android.testInstrumentationRunnerArguments.class=\
 *       com.rpeters.cinefintv.macrobenchmark.StartupBenchmark
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupPartialWithBaselineProfile() = startup(
        CompilationMode.Partial(baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Require),
    )

    @Test
    fun startupFullAot() = startup(CompilationMode.Full())

    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = compilationMode,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle(IDLE_TIMEOUT_MS)
    }
}
