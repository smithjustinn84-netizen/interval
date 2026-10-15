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
import androidx.compose.ui.test.v2.runDesktopComposeUiTest

/** The window sizes the previews use (`PreviewFormFactors`), at density 1 so pixels are dp. */
enum class Window(val width: Int, val height: Int) {
    Phone(width = 411, height = 891),
    PhoneSideways(width = 891, height = 411),
    Tablet(width = 1280, height = 800),
}

/** Runs [block] in a desktop window of [window]'s size. */
fun runUiTest(window: Window = Window.Phone, block: suspend DesktopComposeUiTest.() -> Unit) =
    runDesktopComposeUiTest(width = window.width, height = window.height, block = block)

/** Sets [content] inside the app theme, light, so tests don't depend on the machine's appearance. */
fun DesktopComposeUiTest.setThemedContent(content: @Composable () -> Unit) {
    setContent { IntervalTheme(darkTheme = false, content = content) }
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
