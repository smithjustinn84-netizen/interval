package dev.justinsmith.bells.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.BlockChange
import dev.justinsmith.bells.domain.PlanChanges
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.SaveScope
import dev.justinsmith.bells.domain.copyName
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.change_line
import dev.justinsmith.bells.resources.changes_ask_text
import dev.justinsmith.bells.resources.changes_copy_hint
import dev.justinsmith.bells.resources.changes_copy_hint_last
import dev.justinsmith.bells.resources.changes_copy_name
import dev.justinsmith.bells.resources.changes_drop
import dev.justinsmith.bells.resources.changes_every_day
import dev.justinsmith.bells.resources.changes_every_day_hint
import dev.justinsmith.bells.resources.changes_keep
import dev.justinsmith.bells.resources.changes_kept
import dev.justinsmith.bells.resources.changes_kept_copy
import dev.justinsmith.bells.resources.changes_kept_copy_last
import dev.justinsmith.bells.resources.changes_next_time
import dev.justinsmith.bells.resources.changes_save
import dev.justinsmith.bells.resources.changes_save_copy
import dev.justinsmith.bells.resources.changes_this_day
import dev.justinsmith.bells.resources.changes_this_day_hint
import dev.justinsmith.bells.resources.changes_title
import dev.justinsmith.bells.resources.changes_title_next
import dev.justinsmith.bells.resources.changes_today_only
import dev.justinsmith.bells.resources.topped_out_hint
import dev.justinsmith.bells.resources.topped_out_title
import dev.justinsmith.bells.ui.blockLine
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.programName
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import org.jetbrains.compose.resources.stringResource

/**
 * What was chosen on the changes card. Held by [SummaryScreen] above its layout, so turning the phone, which
 * moves the card to the other column, keeps the choices.
 */
internal class ChangesChoices(
    val dismissed: MutableState<Boolean>,
    // Saved by name: an enum is not saveable on every platform.
    val scopeName: MutableState<String>,
    // The steps up left out, by place, as text: a plain string saves on every platform.
    val declined: MutableState<String>,
)

@Composable
internal fun rememberChangesChoices() = ChangesChoices(
    dismissed = rememberSaveable { mutableStateOf(false) },
    scopeName = rememberSaveable { mutableStateOf(SaveScope.ThisDay.name) },
    declined = rememberSaveable { mutableStateOf("") },
)

/** The steps up of [changes] the card has switched on, by place. */
private fun ChangesChoices.taken(changes: PlanChanges): Set<Int> {
    val left = declined.value.split(',').mapNotNull { it.toIntOrNull() }.toSet()
    return changes.nudges.indices.toSet() - left
}

/** Whether the card still offers something to keep that was neither saved nor set aside with Today only. */
internal fun ChangesChoices.undecided(finished: Finished): Boolean {
    val changes = finished.changes ?: return false
    if (finished.kept != null || dismissed.value) return false
    return changes.changes.isNotEmpty() || taken(changes).isNotEmpty()
}

/**
 * A program day's changes, offered to keep: each block as programmed and as it was done, and Next time, the
 * steps up the day earned, each to take or leave; how far to keep them (this day, or every block of the
 * exercise), then Save, in place for a program the user made or in a copy of a built-in, or Today only. Once
 * kept, where they went.
 */
@Composable
internal fun ChangesCard(finished: Finished, choices: ChangesChoices, onKeep: (SaveScope, String, Set<Int>) -> Unit) {
    val changes = finished.changes ?: return
    var dismissed by choices.dismissed
    var scopeName by choices.scopeName
    var declined by choices.declined
    val taken = choices.taken(changes)
    val left = changes.nudges.indices.toSet() - taken
    val name = programName(changes.program)
    val copy = copyName(stringResource(Res.string.changes_copy_name, name), finished.taken)
    if (dismissed) return
    GroupLabel(
        stringResource(if (changes.changes.isEmpty()) Res.string.changes_title_next else Res.string.changes_title),
        Modifier.padding(top = Spacing.space16),
    )
    ListCard {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            changes.changes.forEach { change -> ChangeLine(change, finished.bands) }
            if (finished.kept != null) {
                KeptLine(changes, name, copy)
                return@Column
            }
            NextTime(changes.nudges, taken, finished.bands, onToggle = { i ->
                declined = (if (i in left) left - i else left + i).joinToString(",")
            })
            if (changes.changes.isNotEmpty() || taken.isNotEmpty()) {
                val scope = SaveScope.valueOf(scopeName)
                SaveChoices(changes, scope, name, onScope = { scopeName = it.name })
                SecondaryButton(
                    if (changes.program.key is ProgramKey.BuiltIn) {
                        stringResource(Res.string.changes_save_copy, copy)
                    } else {
                        stringResource(Res.string.changes_save, name)
                    },
                    { onKeep(scope, copy, taken) },
                    Modifier.fillMaxWidth().padding(top = Spacing.space8),
                )
            }
            QuietTextButton(
                stringResource(Res.string.changes_today_only),
                { dismissed = true },
                Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** The steps up the day earned, each with a switch: on, it goes in the program for next time. */
@Composable
private fun NextTime(nudges: List<BlockChange>, taken: Set<Int>, bands: List<Band>, onToggle: (Int) -> Unit) {
    if (nudges.isEmpty()) return
    Text(
        stringResource(Res.string.changes_next_time),
        style = IntervalTheme.type.caption,
        color = IntervalTheme.palette.textDim,
        modifier = Modifier.padding(top = Spacing.space8),
    )
    nudges.forEachIndexed { i, nudge ->
        ToggleRow(
            liftName(nudge.to.exercise, nudge.to.double),
            stringResource(Res.string.change_line, blockLine(nudge.from, bands), blockLine(nudge.to, bands)),
            checked = i in taken,
            onToggle = { onToggle(i) },
        )
    }
}

/** How far to keep the changes, what that means, and for a built-in, that they go in a copy. */
@Composable
private fun SaveChoices(changes: PlanChanges, scope: SaveScope, name: String, onScope: (SaveScope) -> Unit) {
    val palette = IntervalTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        SegmentedPicker(SaveScope.entries, scope, label = { scopeLabel(it) }, onSelect = onScope)
        Text(
            if (scope == SaveScope.ThisDay) {
                stringResource(Res.string.changes_this_day_hint, changes.dayIndex + 1)
            } else {
                stringResource(Res.string.changes_every_day_hint)
            },
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
        if (changes.program.key is ProgramKey.BuiltIn) {
            Text(
                if (changes.goesOn) {
                    stringResource(Res.string.changes_copy_hint, name, changes.dayNumber + 1)
                } else {
                    stringResource(Res.string.changes_copy_hint_last, name)
                },
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
    }
}

@Composable
private fun scopeLabel(scope: SaveScope): String = stringResource(
    when (scope) {
        SaveScope.ThisDay -> Res.string.changes_this_day
        SaveScope.EveryDay -> Res.string.changes_every_day
    },
)

/** Where the changes were kept: the program itself, or a copy followed from the next day. */
@Composable
private fun KeptLine(changes: PlanChanges, name: String, copy: String) {
    Text(
        when {
            changes.program.key !is ProgramKey.BuiltIn -> stringResource(Res.string.changes_kept, name)
            changes.goesOn -> stringResource(Res.string.changes_kept_copy, copy, changes.dayNumber + 1)
            else -> stringResource(Res.string.changes_kept_copy_last, copy)
        },
        style = IntervalTheme.type.label,
        color = IntervalTheme.palette.accent,
    )
}

/**
 * The blocks [finished] at the top of their range with nothing heavier to go to: each lift, as done, and that a
 * heavier bell is what comes next.
 */
@Composable
internal fun ToppedOutCard(finished: Finished) {
    if (finished.toppedOut.isEmpty()) return
    val palette = IntervalTheme.palette
    GroupLabel(stringResource(Res.string.topped_out_title), Modifier.padding(top = Spacing.space16))
    ListCard {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            finished.toppedOut.forEach { block ->
                Column {
                    Text(liftName(block.exercise, block.double), style = IntervalTheme.type.label, color = palette.text)
                    Text(blockLine(block, finished.bands), style = IntervalTheme.type.meta, color = palette.textDim)
                }
            }
            Text(stringResource(Res.string.topped_out_hint), style = IntervalTheme.type.meta, color = palette.textDim)
        }
    }
}

/** One block changed: its lift, then as programmed → as done; a swap names both lifts. */
@Composable
private fun ChangeLine(change: BlockChange, bands: List<Band>) {
    val palette = IntervalTheme.palette
    val (from, to) = change.from to change.to
    Column {
        Text(
            if (change.swapped) {
                stringResource(
                    Res.string.change_line,
                    liftName(from.exercise, from.double),
                    liftName(to.exercise, to.double),
                )
            } else {
                liftName(to.exercise, to.double)
            },
            style = IntervalTheme.type.label,
            color = palette.text,
        )
        Text(
            stringResource(Res.string.change_line, blockLine(from, bands), blockLine(to, bands)),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
    }
}

/**
 * "Keep today's changes?", asked as the summary is left with them neither saved nor set aside: Keep saves them
 * with [onKeep] as the card has them (its scope, its steps up), Drop does not; either way [onLeave] follows.
 */
@Composable
internal fun UndecidedChangesDialog(
    finished: Finished,
    choices: ChangesChoices,
    onKeep: (SaveScope, String, Set<Int>) -> Unit,
    onLeave: () -> Unit,
) {
    val changes = finished.changes ?: return
    val copy = copyName(stringResource(Res.string.changes_copy_name, programName(changes.program)), finished.taken)
    ConfirmDialog(
        title = stringResource(Res.string.changes_title),
        text = stringResource(Res.string.changes_ask_text),
        confirm = stringResource(Res.string.changes_keep),
        dismiss = stringResource(Res.string.changes_drop),
        onConfirm = {
            onKeep(SaveScope.valueOf(choices.scopeName.value), copy, choices.taken(changes))
            onLeave()
        },
        onDismiss = onLeave,
    )
}
