@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** The window sizes the previews use (`PreviewFormFactors`), at density 1 so pixels are dp. */
enum class Window(val width: Int, val height: Int) {
    Phone(width = 411, height = 891),
    PhoneSideways(width = 891, height = 411),
    Tablet(width = 1280, height = 800),
    TabletPortrait(width = 800, height = 1280),

    /** The desktop app's first window: wide enough for Settings beside the Timer, but only just. */
    Desktop(width = 1024, height = 720),
}

/** How long a UI test may run before it fails: Compose's own default, a minute. */
val UI_TEST_TIMEOUT: Duration = 1.minutes

/**
 * How long an acceptance journey may run. A journey is dozens of steps in one test, each waiting on the real
 * view models; it takes seconds on a laptop but has run past [UI_TEST_TIMEOUT] on a busy CI runner.
 */
val JOURNEY_TIMEOUT: Duration = 3.minutes

/**
 * Runs [block] in a desktop window of [window]'s size, failing it after [timeout]. It is the v2
 * `runDesktopComposeUiTest` written out, because that wraps the test in a `runTest` of its own with the
 * coroutines default of a minute, which ends any test before a longer `testTimeout` can matter.
 */
fun runUiTest(
    window: Window = Window.Phone,
    timeout: Duration = UI_TEST_TIMEOUT,
    block: suspend DesktopComposeUiTest.() -> Unit,
) = runTest(timeout = timeout) {
    val test = DesktopComposeUiTest(
        width = window.width,
        height = window.height,
        testTimeout = timeout,
        useStandardTestDispatcherForComposition = true,
    )
    test.runTest { test.block() }
}

/** Sets [content] inside the app theme, light unless [dark], so tests don't depend on the machine's appearance. */
fun DesktopComposeUiTest.setThemedContent(dark: Boolean = false, content: @Composable () -> Unit) {
    setContent { IntervalTheme(darkTheme = dark, content = content) }
}

/** How long a view model on the main thread, ticking in real time, may take to show what the test did. */
private const val STATE_TIMEOUT_MILLIS = 5_000L

/**
 * Waits for [text] to show. The real view models emit on `Dispatchers.Main` (Swing's thread), which the
 * test's idling doesn't track, so whole-app tests wait for what the state brings rather than assert at once.
 */
fun DesktopComposeUiTest.waitForText(text: String) =
    waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

/**
 * Scrolls the page's grid, which composes only what is in view, until [text] is among what it composes.
 * It waits for the page to come, as [waitForText] does.
 */
fun DesktopComposeUiTest.scrollToText(text: String) =
    waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) {
        runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text)) }.isSuccess
    }

/**
 * Saves and restores [setContent]'s `rememberSaveable` state, as a process death or (on Android) a
 * configuration change would. Stands in for `StateRestorationTester`, whose desktop
 * `emulateSaveAndRestore()` throws `NotImplementedError`. The content leaves the composition and
 * comes back at the same place, so its saveable keys (composite key hashes) match.
 */
class StateRestoration(private val test: DesktopComposeUiTest) {
    private var registry by mutableStateOf(SaveableStateRegistry(restoredValues = null) { true })
    private var shown by mutableStateOf(true)

    fun setContent(content: @Composable () -> Unit) {
        test.setContent {
            if (shown) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    IntervalTheme(darkTheme = false, content = content)
                }
            }
        }
    }

    /** Saves the state, removes the content and composes it again from what was saved. */
    fun emulateSaveAndRestore() {
        test.runOnIdle {
            val saved = registry.performSave()
            shown = false
            registry = SaveableStateRegistry(saved) { true }
        }
        test.waitForIdle()
        test.runOnIdle { shown = true }
        test.waitForIdle()
    }
}
