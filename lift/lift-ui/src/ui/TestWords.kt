package dev.justinsmith.lift.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.StrengthTest
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.domain.TestMeasure
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.test_abc_name
import dev.justinsmith.lift.ui.resources.test_abc_protocol
import dev.justinsmith.lift.ui.resources.test_plank_name
import dev.justinsmith.lift.ui.resources.test_plank_protocol
import dev.justinsmith.lift.ui.resources.test_press_name
import dev.justinsmith.lift.ui.resources.test_press_protocol
import dev.justinsmith.lift.ui.resources.test_pull_up_name
import dev.justinsmith.lift.ui.resources.test_pull_up_protocol
import dev.justinsmith.lift.ui.resources.test_push_up_name
import dev.justinsmith.lift.ui.resources.test_push_up_protocol
import dev.justinsmith.lift.ui.resources.test_result_complexes
import dev.justinsmith.lift.ui.resources.test_result_reps
import dev.justinsmith.lift.ui.resources.test_result_with
import dev.justinsmith.lift.ui.resources.test_snatch_name
import dev.justinsmith.lift.ui.resources.test_snatch_protocol
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A test's name: "Snatch test". Its session is headed by it wherever a program's name would be. */
@Composable
fun testName(id: TestId): String = stringResource(
    when (id) {
        TestId.SnatchTest -> Res.string.test_snatch_name
        TestId.ArmorBuilding -> Res.string.test_abc_name
        TestId.PressReps -> Res.string.test_press_name
        TestId.PushUpReps -> Res.string.test_push_up_name
        TestId.PullUpReps -> Res.string.test_pull_up_name
        TestId.PlankHold -> Res.string.test_plank_name
    },
)

/** How a test is done, in a line or two: what counts, and when it is over. */
@Composable
fun testProtocol(id: TestId): String = stringResource(
    when (id) {
        TestId.SnatchTest -> Res.string.test_snatch_protocol
        TestId.ArmorBuilding -> Res.string.test_abc_protocol
        TestId.PressReps -> Res.string.test_press_protocol
        TestId.PushUpReps -> Res.string.test_push_up_protocol
        TestId.PullUpReps -> Res.string.test_pull_up_protocol
        TestId.PlankHold -> Res.string.test_plank_protocol
    },
)

/** What an attempt at [test] came to, in its own count: "97 reps", "14 complexes", a hold "1:35". */
@Composable
fun testCount(test: StrengthTest, result: Int): String = when {
    test.measure == TestMeasure.LongestHold -> formatClock(result)

    test.exercise == BuiltInExercise.ArmorBuildingComplex ->
        pluralStringResource(Res.plurals.test_result_complexes, result, result)

    else -> pluralStringResource(Res.plurals.test_result_reps, result, result)
}

/**
 * An attempt's result with the load it was done at, which is what it compares by: "97 reps with 24 kg"; the count
 * alone with none to name (bodyweight with nothing added, the mat).
 */
@Composable
fun testResultLine(test: StrengthTest, result: Int, load: Load): String {
    val count = testCount(test, result)
    val with = loadLine(load).takeIf { load.kg > 0 || load is Load.Banded }
    return if (with == null) count else stringResource(Res.string.test_result_with, count, with)
}
