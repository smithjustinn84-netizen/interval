package dev.justinsmith.bells.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.ProgramDraft
import dev.justinsmith.bells.domain.RepRange
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.bells.domain.asKind
import dev.justinsmith.bells.domain.bandOf
import dev.justinsmith.bells.domain.offeredBands
import dev.justinsmith.bells.domain.offeredExercises
import dev.justinsmith.bells.domain.ownedEquipment
import dev.justinsmith.bells.domain.recentExercises
import dev.justinsmith.bells.domain.standardBells
import dev.justinsmith.bells.domain.withRange
import dev.justinsmith.bells.library.LibraryList
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.block_add_rung
import dev.justinsmith.bells.resources.block_kind
import dev.justinsmith.bells.resources.block_kind_emom
import dev.justinsmith.bells.resources.block_kind_ladder
import dev.justinsmith.bells.resources.block_kind_sets
import dev.justinsmith.bells.resources.block_range
import dev.justinsmith.bells.resources.block_range_bottom
import dev.justinsmith.bells.resources.block_range_hint
import dev.justinsmith.bells.resources.block_range_top
import dev.justinsmith.bells.resources.block_remove_rung
import dev.justinsmith.bells.resources.block_reps
import dev.justinsmith.bells.resources.block_reps_now
import dev.justinsmith.bells.resources.block_rounds_emom
import dev.justinsmith.bells.resources.block_rounds_ladder
import dev.justinsmith.bells.resources.block_rounds_sets
import dev.justinsmith.bells.resources.block_rung
import dev.justinsmith.bells.resources.block_rungs
import dev.justinsmith.bells.resources.editor_fewer_cd
import dev.justinsmith.bells.resources.editor_more_cd
import dev.justinsmith.bells.resources.load_last_time_hint
import dev.justinsmith.bells.resources.log_held
import dev.justinsmith.bells.session.ExerciseCard
import dev.justinsmith.bells.session.SetDraft
import dev.justinsmith.bells.ui.LoadPicker
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.RepeatButton
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import org.jetbrains.compose.resources.stringResource

/** What a block can be made of: the bells and bands to plan with, and the exercises to pick from. */
data class BlockChoices(
    val bells: List<Double>,
    val bands: List<Band>,
    val library: LibraryList,
    /** What the user's barbell weighs empty; null until they have said. */
    val bar: Double? = null,
)

/**
 * What blocks can be made of: the bells owned (every standard size until some are), the bands not archived,
 * the user's [bar], and the exercises offered, the ones in [history] lately first, narrowed by [filters] but
 * never to archived.
 */
internal fun blockChoices(
    owned: List<Double>,
    bands: List<Band>,
    made: List<CustomExercise>,
    filters: ExerciseFilters,
    history: List<SetLog>,
    bar: Double? = null,
): BlockChoices {
    val offered = offeredExercises(made)
    return BlockChoices(
        owned.ifEmpty { standardBells },
        offeredBands(bands),
        LibraryList(
            offered,
            recentExercises(history, offered),
            filters.copy(showArchived = false),
            ownedEquipment(owned, bands, bar),
        ),
        bar,
    )
}

/**
 * What a block is: its exercise (Change asks for another through [onPickExercise], when there is one) and the
 * load it names or leaves to last time. [BlockCounts] follows it, under it or beside it.
 */
@Composable
internal fun BlockLoad(
    block: Block,
    choices: BlockChoices,
    onChange: ((Block) -> Block) -> Unit,
    onPickExercise: (() -> Unit)?,
) {
    ExerciseCard(block.exercise, onChange = onPickExercise)
    if (block.exercise.equipment != Equipment.Mat) {
        LoadPicker(
            block.exercise,
            block.double,
            kg = block.kg,
            band = block.band?.let { bandOf(it, choices.bands) },
            bells = choices.bells,
            bands = choices.bands,
            onKg = { kg -> onChange { it.copy(kg = kg) } },
            onBand = { band -> onChange { it.copy(band = band?.id) } },
            onDouble = { onChange { it.copy(double = !it.double) } },
            lastTime = true,
            bar = choices.bar,
        )
        Text(
            stringResource(Res.string.load_last_time_hint),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
        )
    }
}

/**
 * How much of a block: how it runs (a hold only ever as sets, and fixed with [lockKind]), its rounds from
 * [fewestRounds], and its reps, a hold's length, or a ladder's rungs. The range reps build within is the
 * program's to set, with [rangeEditable]; a session changes only the reps.
 */
@Composable
internal fun BlockCounts(
    block: Block,
    onChange: ((Block) -> Block) -> Unit,
    fewestRounds: Int = 1,
    lockKind: Boolean = false,
    rangeEditable: Boolean = true,
) {
    val held = block.exercise.measure == Measure.Time
    if (!held && !lockKind) {
        GroupLabel(stringResource(Res.string.block_kind))
        SegmentedPicker(BlockKind.entries, block.kind, { kindName(it) }, { k -> onChange { it.asKind(k) } })
    }
    StepperRow(
        roundsName(block.kind),
        block.rounds,
        fewestRounds.coerceAtLeast(1)..maxRounds(block.kind),
        onSet = { n -> onChange { it.copy(rounds = n) } },
    )
    when {
        held -> StepperRow(
            stringResource(Res.string.log_held),
            block.reps,
            SetDraft.SECONDS_STEP..SetDraft.MAX_SECONDS,
            onSet = { n -> onChange { it.copy(reps = n) } },
            step = SetDraft.SECONDS_STEP,
            shown = ::formatClock,
        )

        block.kind == BlockKind.Ladder ->
            RungsEditor(block.rungs, onSet = { rungs -> onChange { it.copy(rungs = rungs) } })

        rangeEditable -> RepsEditor(block, onChange)

        else -> StepperRow(
            stringResource(Res.string.block_reps),
            block.reps,
            1..SetDraft.MAX_REPS,
            onSet = { n -> onChange { it.copy(reps = n) } },
        )
    }
}

/**
 * A counted block's reps: fixed, or built within a range, its bottom and top each a stepper and the reps where
 * it stands kept between them.
 */
@Composable
private fun RepsEditor(block: Block, onChange: ((Block) -> Block) -> Unit) {
    val range = block.ranged
    StepperRow(
        stringResource(if (range != null) Res.string.block_reps_now else Res.string.block_reps),
        block.reps,
        range?.let { it.bottom..it.top } ?: 1..SetDraft.MAX_REPS,
        onSet = { n -> onChange { it.copy(reps = n) } },
    )
    // Work on the mat has no load to go up to, so nothing to build reps towards.
    if (block.exercise.equipment != Equipment.Mat) {
        ToggleRow(
            stringResource(Res.string.block_range),
            stringResource(Res.string.block_range_hint),
            checked = range != null,
            onToggle = { onChange { it.withRange(on = it.ranged == null, maxReps = SetDraft.MAX_REPS) } },
        )
    }
    if (range != null) {
        StepperRow(
            stringResource(Res.string.block_range_bottom),
            range.bottom,
            1 until range.top,
            onSet = { n -> onChange { it.withRange(RepRange(n, range.top)) } },
        )
        StepperRow(
            stringResource(Res.string.block_range_top),
            range.top,
            range.bottom + 1..SetDraft.MAX_REPS,
            onSet = { n -> onChange { it.withRange(RepRange(range.bottom, n)) } },
        )
    }
}

/** A ladder's rungs, a stepper each, with a rung to add at the end or take off it. */
@Composable
private fun RungsEditor(rungs: List<Int>, onSet: (List<Int>) -> Unit) {
    GroupLabel(stringResource(Res.string.block_rungs))
    rungs.forEachIndexed { i, rung ->
        StepperRow(
            stringResource(Res.string.block_rung, i + 1),
            rung,
            1..SetDraft.MAX_REPS,
            onSet = { n -> onSet(rungs.mapIndexed { j, r -> if (j == i) n else r }) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        if (rungs.size < ProgramDraft.MAX_RUNGS) {
            SecondaryButton(
                stringResource(Res.string.block_add_rung),
                { onSet(rungs + (rungs.last() + 1)) },
                Modifier.weight(1f),
            )
        }
        if (rungs.size > 1) {
            SecondaryButton(
                stringResource(Res.string.block_remove_rung),
                { onSet(rungs.dropLast(1)) },
                Modifier.weight(1f),
            )
        }
    }
}

/** A number to step by [step] within [range]: its [label], the value as [shown], and − and +. */
@Composable
internal fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onSet: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    shown: (Int) -> String = Int::toString,
) {
    val palette = IntervalTheme.palette
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Text(label, style = IntervalTheme.type.label, color = palette.text, modifier = Modifier.weight(1f))
        Text(shown(value), style = IntervalTheme.type.value, color = palette.text)
        RepeatButton(
            "−",
            stringResource(Res.string.editor_fewer_cd, label),
            enabled = value > range.first,
            onStep = { onSet((value - step).coerceIn(range)) },
        )
        RepeatButton(
            "+",
            stringResource(Res.string.editor_more_cd, label),
            enabled = value < range.last,
            onStep = { onSet((value + step).coerceIn(range)) },
        )
    }
}

@Composable
private fun kindName(kind: BlockKind): String = stringResource(
    when (kind) {
        BlockKind.Sets -> Res.string.block_kind_sets
        BlockKind.Emom -> Res.string.block_kind_emom
        BlockKind.Ladder -> Res.string.block_kind_ladder
    },
)

@Composable
private fun roundsName(kind: BlockKind): String = stringResource(
    when (kind) {
        BlockKind.Sets -> Res.string.block_rounds_sets
        BlockKind.Emom -> Res.string.block_rounds_emom
        BlockKind.Ladder -> Res.string.block_rounds_ladder
    },
)

/** An EMOM runs no longer than the timer allows; sets and ladders stop at [ProgramDraft.MAX_ROUNDS]. */
private fun maxRounds(kind: BlockKind): Int =
    if (kind == BlockKind.Emom) SetDraft.MAX_MINUTES else ProgramDraft.MAX_ROUNDS
