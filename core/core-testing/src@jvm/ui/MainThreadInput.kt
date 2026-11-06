package dev.justinsmith.interval.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import javax.swing.SwingUtilities
import androidx.compose.ui.test.performClick as composeClick

/**
 * Does [input] on the app's main thread, Swing's, where a device delivers a tap or the back gesture. Compose's
 * desktop harness composes there, and runs effects and the semantics' actions (typing, scrolling) there, but it
 * dispatches a pointer event on the thread that asked for it: the test's. What a tap calls would then run
 * beside the main thread's timers, which the app never has to allow for: a workout's tick that read the
 * workout as a skip began put the old interval back over the new one, and the skip was lost. Waiting stays
 * outside, since what a test waits for arrives on this thread.
 *
 * It is the harness's own `runOnUiThread`, which a [SemanticsNodeInteraction] can't reach: inline when already
 * there, else handed to Swing and waited for, a failure thrown as itself.
 */
fun <T> onMainThread(input: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return input()
    val task = FutureTask(input)
    SwingUtilities.invokeAndWait(task)
    return try {
        task.get()
    } catch (failure: ExecutionException) {
        throw failure.cause ?: failure
    }
}

/**
 * Compose's `performClick`, made on the main thread ([onMainThread]). Whole-app tests import this one rather
 * than `androidx.compose.ui.test.performClick`. The node is found first, off it, so the harness's wait for the
 * app to settle isn't made while holding the thread the app settles on.
 */
fun SemanticsNodeInteraction.performClick(): SemanticsNodeInteraction {
    fetchSemanticsNode()
    return onMainThread { composeClick() }
}
