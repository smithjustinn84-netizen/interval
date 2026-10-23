package dev.justinsmith.bells.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.bandOf
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.block_emom
import dev.justinsmith.bells.resources.block_hold
import dev.justinsmith.bells.resources.block_hold_sided
import dev.justinsmith.bells.resources.block_ladder
import dev.justinsmith.bells.resources.block_ladder_sided
import dev.justinsmith.bells.resources.block_sets
import dev.justinsmith.bells.resources.block_sets_sided
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.load_added
import dev.justinsmith.bells.resources.load_double
import dev.justinsmith.bells.resources.load_kg
import dev.justinsmith.bells.resources.log_added_none
import dev.justinsmith.bells.resources.program_abf_name
import dev.justinsmith.bells.resources.program_abf_summary
import dev.justinsmith.bells.resources.program_bands_bells_name
import dev.justinsmith.bells.resources.program_bands_bells_summary
import dev.justinsmith.bells.resources.program_beginner_full_body_name
import dev.justinsmith.bells.resources.program_beginner_full_body_summary
import dev.justinsmith.bells.resources.program_bells_bodyweight_name
import dev.justinsmith.bells.resources.program_bells_bodyweight_summary
import dev.justinsmith.bells.resources.program_bodyweight_basics_name
import dev.justinsmith.bells.resources.program_bodyweight_basics_summary
import dev.justinsmith.bells.resources.program_bodyweight_emom_name
import dev.justinsmith.bells.resources.program_bodyweight_emom_summary
import dev.justinsmith.bells.resources.program_clean_press_name
import dev.justinsmith.bells.resources.program_clean_press_summary
import dev.justinsmith.bells.resources.program_core_mat_name
import dev.justinsmith.bells.resources.program_core_mat_summary
import dev.justinsmith.bells.resources.program_countdown_name
import dev.justinsmith.bells.resources.program_countdown_summary
import dev.justinsmith.bells.resources.program_daily_name
import dev.justinsmith.bells.resources.program_daily_summary
import dev.justinsmith.bells.resources.program_day
import dev.justinsmith.bells.resources.program_days_count
import dev.justinsmith.bells.resources.program_easy_strength_name
import dev.justinsmith.bells.resources.program_easy_strength_summary
import dev.justinsmith.bells.resources.program_first_hundred_name
import dev.justinsmith.bells.resources.program_first_hundred_summary
import dev.justinsmith.bells.resources.program_foundations_name
import dev.justinsmith.bells.resources.program_foundations_summary
import dev.justinsmith.bells.resources.program_get_up_practice_name
import dev.justinsmith.bells.resources.program_get_up_practice_summary
import dev.justinsmith.bells.resources.program_goblet_row_name
import dev.justinsmith.bells.resources.program_goblet_row_summary
import dev.justinsmith.bells.resources.program_grappler_strength_name
import dev.justinsmith.bells.resources.program_grappler_strength_summary
import dev.justinsmith.bells.resources.program_grip_pull_name
import dev.justinsmith.bells.resources.program_grip_pull_summary
import dev.justinsmith.bells.resources.program_iron_cardio_name
import dev.justinsmith.bells.resources.program_iron_cardio_summary
import dev.justinsmith.bells.resources.program_ladders_name
import dev.justinsmith.bells.resources.program_ladders_summary
import dev.justinsmith.bells.resources.program_long_cycle_name
import dev.justinsmith.bells.resources.program_long_cycle_summary
import dev.justinsmith.bells.resources.program_program_minimum_name
import dev.justinsmith.bells.resources.program_program_minimum_summary
import dev.justinsmith.bells.resources.program_rite_of_passage_name
import dev.justinsmith.bells.resources.program_rite_of_passage_summary
import dev.justinsmith.bells.resources.program_rounds_name
import dev.justinsmith.bells.resources.program_rounds_summary
import dev.justinsmith.bells.resources.program_schedule
import dev.justinsmith.bells.resources.program_shoulder_armor_name
import dev.justinsmith.bells.resources.program_shoulder_armor_summary
import dev.justinsmith.bells.resources.program_simple_sinister_name
import dev.justinsmith.bells.resources.program_simple_sinister_summary
import dev.justinsmith.bells.resources.program_snatch_test_name
import dev.justinsmith.bells.resources.program_snatch_test_summary
import dev.justinsmith.bells.resources.program_ten_thousand_name
import dev.justinsmith.bells.resources.program_ten_thousand_summary
import dev.justinsmith.interval.timer.formatClock
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A program's name: "Press ladders 1-2-3", or the one its maker gave it. */
@Composable
fun programName(program: Program): String = when (val key = program.key) {
    is ProgramKey.BuiltIn -> programName(key.id)
    is ProgramKey.Custom -> program.name.orEmpty()
}

/** Where a program day falls in its program: "Day 4 of 40 · week 2". */
@Composable
fun programDayLine(day: ProgramDay): String =
    stringResource(Res.string.program_day, day.number, day.program.totalDays, day.week)

/** The lifts a program day does, in its order: "Two-hand swing · Press · Two bells". */
@Composable
fun programDayLifts(day: ProgramDay): String = day.blocks
    .map { liftName(it.exercise, it.double) }
    .distinct()
    .reduceOrNull { line, lift -> stringResource(Res.string.common_joined, line, lift) }
    .orEmpty()

/** A built-in program's name. */
@Composable
fun programName(id: ProgramId): String = stringResource(
    when (id) {
        ProgramId.DailySwingsGetUps -> Res.string.program_daily_name
        ProgramId.PressLadders -> Res.string.program_ladders_name
        ProgramId.CleanPressEmom -> Res.string.program_clean_press_name
        ProgramId.GobletSquatRow -> Res.string.program_goblet_row_name
        ProgramId.EasyStrength -> Res.string.program_easy_strength_name
        ProgramId.ArmorBuildingFormula -> Res.string.program_abf_name
        ProgramId.TenThousandSwings -> Res.string.program_ten_thousand_name
        ProgramId.SwingSquatCountdown -> Res.string.program_countdown_name
        ProgramId.SimpleAndSinister -> Res.string.program_simple_sinister_name
        ProgramId.ProgramMinimum -> Res.string.program_program_minimum_name
        ProgramId.RiteOfPassage -> Res.string.program_rite_of_passage_name
        ProgramId.IronCardio -> Res.string.program_iron_cardio_name
        ProgramId.SnatchTestPrep -> Res.string.program_snatch_test_name
        ProgramId.LongCycleEmom -> Res.string.program_long_cycle_name
        ProgramId.GrapplerStrength -> Res.string.program_grappler_strength_name
        ProgramId.RoundConditioning -> Res.string.program_rounds_name
        ProgramId.GripAndPull -> Res.string.program_grip_pull_name
        ProgramId.ShoulderArmor -> Res.string.program_shoulder_armor_name
        ProgramId.KettlebellFoundations -> Res.string.program_foundations_name
        ProgramId.FirstHundredSwings -> Res.string.program_first_hundred_name
        ProgramId.BeginnerFullBody -> Res.string.program_beginner_full_body_name
        ProgramId.GetUpPractice -> Res.string.program_get_up_practice_name
        ProgramId.BodyweightBasics -> Res.string.program_bodyweight_basics_name
        ProgramId.BodyweightEmom -> Res.string.program_bodyweight_emom_name
        ProgramId.BellsAndBodyweight -> Res.string.program_bells_bodyweight_name
        ProgramId.BandsAndBells -> Res.string.program_bands_bells_name
        ProgramId.CoreOnTheMat -> Res.string.program_core_mat_name
    },
)

/**
 * What a program asks for, in a line; for one the user made, its days and schedule: "2 days · 3 a week for
 * 6 weeks".
 */
@Composable
fun programSummary(program: Program): String = when (val key = program.key) {
    is ProgramKey.BuiltIn -> programSummary(key.id)

    is ProgramKey.Custom -> stringResource(
        Res.string.common_joined,
        pluralStringResource(Res.plurals.program_days_count, program.days.size, program.days.size),
        pluralStringResource(Res.plurals.program_schedule, program.weeks, program.weeks, program.daysPerWeek),
    )
}

@Composable
private fun programSummary(id: ProgramId): String = stringResource(
    when (id) {
        ProgramId.DailySwingsGetUps -> Res.string.program_daily_summary
        ProgramId.PressLadders -> Res.string.program_ladders_summary
        ProgramId.CleanPressEmom -> Res.string.program_clean_press_summary
        ProgramId.GobletSquatRow -> Res.string.program_goblet_row_summary
        ProgramId.EasyStrength -> Res.string.program_easy_strength_summary
        ProgramId.ArmorBuildingFormula -> Res.string.program_abf_summary
        ProgramId.TenThousandSwings -> Res.string.program_ten_thousand_summary
        ProgramId.SwingSquatCountdown -> Res.string.program_countdown_summary
        ProgramId.SimpleAndSinister -> Res.string.program_simple_sinister_summary
        ProgramId.ProgramMinimum -> Res.string.program_program_minimum_summary
        ProgramId.RiteOfPassage -> Res.string.program_rite_of_passage_summary
        ProgramId.IronCardio -> Res.string.program_iron_cardio_summary
        ProgramId.SnatchTestPrep -> Res.string.program_snatch_test_summary
        ProgramId.LongCycleEmom -> Res.string.program_long_cycle_summary
        ProgramId.GrapplerStrength -> Res.string.program_grappler_strength_summary
        ProgramId.RoundConditioning -> Res.string.program_rounds_summary
        ProgramId.GripAndPull -> Res.string.program_grip_pull_summary
        ProgramId.ShoulderArmor -> Res.string.program_shoulder_armor_summary
        ProgramId.KettlebellFoundations -> Res.string.program_foundations_summary
        ProgramId.FirstHundredSwings -> Res.string.program_first_hundred_summary
        ProgramId.BeginnerFullBody -> Res.string.program_beginner_full_body_summary
        ProgramId.GetUpPractice -> Res.string.program_get_up_practice_summary
        ProgramId.BodyweightBasics -> Res.string.program_bodyweight_basics_summary
        ProgramId.BodyweightEmom -> Res.string.program_bodyweight_emom_summary
        ProgramId.BellsAndBodyweight -> Res.string.program_bells_bodyweight_summary
        ProgramId.BandsAndBells -> Res.string.program_bands_bells_summary
        ProgramId.CoreOnTheMat -> Res.string.program_core_mat_summary
    },
)

/**
 * A block's shape: "10 × 10", "5 × 1 a side", "3 × 0:30" for a hold, "10 every minute for 10 minutes",
 * "3 ladders of 1-2-3 a side", "5 ladders of 10-15-25-50"; then the load it names, if it does: "5 × 10 · 24 kg",
 * "3 × 15 · Medium", unless not [withLoad]. [bands] are the user's, to name a band by.
 */
@Composable
fun blockLine(block: Block, bands: List<Band> = emptyList(), withLoad: Boolean = true): String {
    val line = blockCount(block)
    val load = blockLoad(block, bands)?.takeIf { withLoad } ?: return line
    return stringResource(Res.string.common_joined, line, load)
}

/** The load [block] names in words, "24 kg", "2 × 16 kg", "+10 kg" or a band's name; null for none named. */
@Composable
fun blockLoad(block: Block, bands: List<Band> = emptyList()): String? = when (block.exercise.equipment) {
    Equipment.Kettlebell -> block.kg?.let {
        stringResource(if (block.double) Res.string.load_double else Res.string.load_kg, formatKg(it))
    }

    Equipment.Bodyweight -> block.kg?.let {
        if (it > 0) stringResource(Res.string.load_added, formatKg(it)) else stringResource(Res.string.log_added_none)
    }

    Equipment.Band -> block.band?.let { bandName(bandOf(it, bands)) }

    Equipment.Mat -> null
}

@Composable
private fun blockCount(block: Block): String = when (block.kind) {
    BlockKind.Sets -> if (block.exercise.measure == Measure.Time) {
        stringResource(
            if (block.exercise.sided(block.double)) Res.string.block_hold_sided else Res.string.block_hold,
            block.rounds,
            formatClock(block.reps),
        )
    } else if (block.exercise.sided(block.double)) {
        stringResource(Res.string.block_sets_sided, block.rounds, block.reps)
    } else {
        stringResource(Res.string.block_sets, block.rounds, block.reps)
    }

    BlockKind.Emom -> pluralStringResource(Res.plurals.block_emom, block.rounds, block.rounds, block.reps)

    BlockKind.Ladder -> pluralStringResource(
        if (block.exercise.sided(block.double)) Res.plurals.block_ladder_sided else Res.plurals.block_ladder,
        block.rounds,
        block.rounds,
        block.rungs.joinToString("-"),
    )
}
