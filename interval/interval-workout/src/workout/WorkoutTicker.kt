package dev.justinsmith.interval.workout

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The running workout's clock: from [start] to [stop], every [TICK_MILLIS] it hands on every millisecond since
 * its last tick, read from [timeSource], which counts through the device's sleep. So a workout that ran on while
 * the app was off screen is handed all of that time at once. It ticks in [scope], on the main thread, where the
 * screen's and the notification's actions on the workout run too. It lives as long as the process, [close]d with
 * the container.
 */
class WorkoutTicker(
    private val timeSource: TimeSource.WithComparableMarks,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    /** Calls [onElapsed] with the milliseconds since the last call (or since now) at each tick, until [stop]. */
    fun start(onElapsed: (Long) -> Unit) {
        stop()
        // Marked now, not once the coroutine gets to run: the time until then counts too.
        var last = timeSource.markNow()
        job = scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                val now = timeSource.markNow()
                val elapsed = (now - last).inWholeMilliseconds
                last += elapsed.milliseconds
                onElapsed(elapsed)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** The process is going: nothing ticks again. */
    fun close() = scope.cancel()

    companion object {
        const val TICK_MILLIS = 100L
    }
}
