package dev.justinsmith.bells.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.block_emom
import dev.justinsmith.bells.resources.block_ladder
import dev.justinsmith.bells.resources.block_ladder_sided
import dev.justinsmith.bells.resources.block_sets
import dev.justinsmith.bells.resources.block_sets_sided
import dev.justinsmith.bells.resources.common_joined
import dev.justinsmith.bells.resources.program_abf_name
import dev.justinsmith.bells.resources.program_abf_summary
import dev.justinsmith.bells.resources.program_beginner_full_body_name
import dev.justinsmith.bells.resources.program_beginner_full_body_summary
import dev.justinsmith.bells.resources.program_clean_press_name
import dev.justinsmith.bells.resources.program_clean_press_summary
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
    },
)

/**
 * A block's shape: "10 × 10", "5 × 1 a side", "10 every minute for 10 minutes", "3 ladders of 1-2-3 a side",
 * "5 ladders of 10-15-25-50".
 */
@Composable
fun blockLine(block: Block): String = when (block.kind) {
    BlockKind.Sets -> if (block.exercise.sided(block.double)) {
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
