package dev.justinsmith.bells.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.block_emom
import dev.justinsmith.bells.resources.block_ladder
import dev.justinsmith.bells.resources.block_sets
import dev.justinsmith.bells.resources.block_sets_sided
import dev.justinsmith.bells.resources.program_clean_press_name
import dev.justinsmith.bells.resources.program_clean_press_summary
import dev.justinsmith.bells.resources.program_daily_name
import dev.justinsmith.bells.resources.program_daily_summary
import dev.justinsmith.bells.resources.program_goblet_row_name
import dev.justinsmith.bells.resources.program_goblet_row_summary
import dev.justinsmith.bells.resources.program_ladders_name
import dev.justinsmith.bells.resources.program_ladders_summary
import org.jetbrains.compose.resources.stringResource

/** A program's name: "Press ladders 1-2-3". */
@Composable
fun programName(id: ProgramId): String = stringResource(
    when (id) {
        ProgramId.DailySwingsGetUps -> Res.string.program_daily_name
        ProgramId.PressLadders -> Res.string.program_ladders_name
        ProgramId.CleanPressEmom -> Res.string.program_clean_press_name
        ProgramId.GobletSquatRow -> Res.string.program_goblet_row_name
    },
)

/** What a program asks for, in a line. */
@Composable
fun programSummary(id: ProgramId): String = stringResource(
    when (id) {
        ProgramId.DailySwingsGetUps -> Res.string.program_daily_summary
        ProgramId.PressLadders -> Res.string.program_ladders_summary
        ProgramId.CleanPressEmom -> Res.string.program_clean_press_summary
        ProgramId.GobletSquatRow -> Res.string.program_goblet_row_summary
    },
)

/** A block's shape: "10 × 10", "5 × 1 a side", "10 every minute for 10 minutes", "3 ladders of 1-2-3 a side". */
@Composable
fun blockLine(block: Block): String = when (block.kind) {
    BlockKind.Sets -> if (block.exercise.perSide) {
        stringResource(Res.string.block_sets_sided, block.rounds, block.reps)
    } else {
        stringResource(Res.string.block_sets, block.rounds, block.reps)
    }

    BlockKind.Emom -> stringResource(Res.string.block_emom, block.rounds, block.reps)

    BlockKind.Ladder -> stringResource(Res.string.block_ladder, block.rounds, block.rungs.joinToString("-"))
}
