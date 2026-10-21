@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.justinsmith.bells.BellsContent
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.RecordingBeeper
import dev.justinsmith.bells.fakes.bellsTestKoin
import dev.justinsmith.bells.fakes.fakeBellsPlatformModule
import dev.justinsmith.interval.ui.IntervalTheme
import java.util.concurrent.atomic.AtomicLong

/** A phone's window, as Interval's harness sizes it, at density 1 so pixels are dp. */
private const val PHONE_WIDTH = 411
private const val PHONE_HEIGHT = 891

/** Runs [block] in a phone-sized desktop window. */
fun runUiTest(block: suspend DesktopComposeUiTest.() -> Unit) =
    runDesktopComposeUiTest(width = PHONE_WIDTH, height = PHONE_HEIGHT, block = block)

/** Sets [content] inside the theme, Gym dark as Bells always is. */
fun DesktopComposeUiTest.setBellsContent(content: @Composable () -> Unit) {
    setContent { IntervalTheme(darkTheme = true, content = content) }
}

/** How long a view model on the main thread, ticking in real time, may take to show what the test did. */
private const val STATE_TIMEOUT_MILLIS = 5_000L

/**
 * Waits for [text] to show. The real view models emit on `Dispatchers.Main` (Swing's thread), which the
 * test's idling doesn't track, so whole-app tests wait for what the state brings rather than assert at once.
 */
fun DesktopComposeUiTest.waitForText(text: String) =
    waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

/** What a whole-app test drives: the fake storage under the app, its clock, and the beeps it played. */
class BellsAppFixture(val storage: FakeBellsStorage, val clock: AtomicLong, val beeper: RecordingBeeper)

/** The whole app, real ViewModels and repository, over fake storage and platform services. */
fun bellsAppTest(
    storage: FakeBellsStorage = FakeBellsStorage(),
    block: suspend DesktopComposeUiTest.(BellsAppFixture) -> Unit,
) = runUiTest {
    val clock = AtomicLong(0L)
    val beeper = RecordingBeeper()
    val koin = bellsTestKoin(storage, fakeBellsPlatformModule(beeper = beeper), now = clock::get)
    try {
        setContent { BellsContent(koin) }
        block(BellsAppFixture(storage, clock, beeper))
    } finally {
        koin.close()
    }
}
