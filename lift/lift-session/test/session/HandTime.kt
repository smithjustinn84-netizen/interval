package dev.justinsmith.lift.session

import dev.justinsmith.interval.platform.WallClock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * Time a test moves by hand, for a timer's [RunClock]: [now] is the clock's monotonic reading, and its wall clock
 * reads [EPOCH] + [now].
 */
class HandTime(start: Long = 0L) {
    private val time = TestTimeSource()
    val clock = RunClock(WallClock { EPOCH + now }, time)

    var now: Long = 0L
        set(value) {
            time += (value - field).milliseconds
            field = value
        }

    init {
        now = start
    }

    companion object {
        const val EPOCH = 1_000_000L
    }
}
