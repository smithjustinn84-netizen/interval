package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import dev.justinsmith.interval.model.SnatchCount
import kotlin.math.abs

// 15:15's snatches, counted on the summary and seen on Lift's side, beside the driver, which is at its size limit.

private const val ASKED = "About how many snatches a set?"

fun IntervalAcceptanceDriver.seeAskedForSnatches() = see(ASKED)

fun IntervalAcceptanceDriver.seeNotAskedForSnatches() = seeNo(ASKED)

/** A workout run to its end: its summary, saved. */
fun IntervalAcceptanceDriver.seeSummaryFinished() = see("Saved to your log")

/**
 * On the summary: the bell picked, which counts the workout at the count the question opens on, then stepped from
 * there to [perSet] one tap at a time from [from], where it opens with no count said before.
 */
fun IntervalAcceptanceDriver.countSnatches(perSet: Int, kg: Int, from: Int = SnatchCount.USUAL_PER_SET) {
    tap(hasText("$kg kg") and isSelectable())
    repeat(abs(perSet - from)) { tapDescribed(if (perSet > from) "More snatches" else "Fewer snatches") }
    see("$perSet a set")
}

/** The workout History holds was counted at [perSet] snatches a set with a bell of [kg]. */
fun IntervalAcceptanceDriver.workoutCountedAs(perSet: Int, kg: Double) =
    waitFor("the workout counted at $perSet a set at $kg kg") {
        app.repository.history.history.value.any { it.snatches?.perSet == perSet && it.snatches?.bellKg == kg }
    }

/** Lift's Strength pillar: the snatch test, with its best and where that came from. */
fun IntervalAcceptanceDriver.seeSnatchTestBest(best: Int, from: String) {
    scrollTo("Snatch test: 100 in 5 minutes")
    see("Best: $best in 5 minutes, from $from")
}

/** From Strength: the exercise library, then Snatch's page. */
fun IntervalAcceptanceDriver.openSnatchPage() {
    scrollTo("Exercises")
    tap("Exercises")
    see("Search exercises")
    tap(hasText("Snatch") and hasClickAction())
    see("Your history")
}

/** On a lift's page, under its history: its reps in all, laid out further down the page than shows. */
fun IntervalAcceptanceDriver.seeRepsInAll(reps: Int) = see("$reps reps in all")
