package dev.justinsmith.bells.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.BlockChange
import dev.justinsmith.bells.domain.PlanChanges
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.SaveScope
import dev.justinsmith.bells.domain.copyName
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.change_line
import dev.justinsmith.bells.resources.changes_copy_hint
import dev.justinsmith.bells.resources.changes_copy_hint_last
import dev.justinsmith.bells.resources.changes_copy_name
import dev.justinsmith.bells.resources.changes_every_day
import dev.justinsmith.bells.resources.changes_every_day_hint
import dev.justinsmith.bells.resources.changes_kept
import dev.justinsmith.bells.resources.changes_kept_copy
import dev.justinsmith.bells.resources.changes_kept_copy_last
import dev.justinsmith.bells.resources.changes_save
import dev.justinsmith.bells.resources.changes_save_copy
import dev.justinsmith.bells.resources.changes_this_day
import dev.justinsmith.bells.resources.changes_this_day_hint
import dev.justinsmith.bells.resources.changes_title
import dev.justinsmith.bells.resources.changes_today_only
import dev.justinsmith.bells.resources.summary_done
import dev.justinsmith.bells.resources.summary_shared
import dev.justinsmith.bells.ui.blockLine
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.programName
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SegmentedPicker
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * A finished session: Done in the done colour, its time, weight moved and reps, and any records it set; for a
 * program day changed in the session, the changes, to keep in the program with [onKeep].
 */
@Composable
fun SummaryScreen(
    finished: Finished,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onKeep: (SaveScope, String) -> Unit = { _, _ -> },
) {
    val palette = IntervalTheme.palette
    val session = finished.session
    Box(modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = Sizes.content).fillMaxSize().safeDrawingPadding().padding(Spacing.space20),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Text(
                    stringResource(Res.string.summary_done),
                    style = IntervalTheme.type.display,
                    color = IntervalTheme.workout.done,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.space24),
                )
                Text(
                    session.title,
                    style = IntervalTheme.type.section,
                    color = palette.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.space16),
                )
                SessionStats(session, finished.records)
                ChangesCard(finished, onKeep)
            }
            Text(
                stringResource(Res.string.summary_shared),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            AccentButton(stringResource(Res.string.summary_done), onDone, Modifier.fillMaxWidth())
        }
    }
}

/**
 * A program day's changes, offered to keep: each block as programmed and as it was done, how far to keep them
 * (this day, or every block of the exercise), then Save, in place for a program the user made or in a copy of
 * a built-in, or Today only. Once kept, where they went.
 */
@Composable
private fun ChangesCard(finished: Finished, onKeep: (SaveScope, String) -> Unit) {
    val changes = finished.changes ?: return
    val palette = IntervalTheme.palette
    var dismissed by rememberSaveable { mutableStateOf(false) }
    // Saved by name: an enum is not saveable on every platform.
    var scopeName by rememberSaveable { mutableStateOf(SaveScope.ThisDay.name) }
    val scope = SaveScope.valueOf(scopeName)
    val program = changes.program
    val name = programName(program)
    val copy = copyName(stringResource(Res.string.changes_copy_name, name), finished.taken)
    val builtIn = program.key is ProgramKey.BuiltIn
    if (dismissed) return
    GroupLabel(stringResource(Res.string.changes_title), Modifier.padding(top = Spacing.space16))
    ListCard {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            changes.changes.forEach { change -> ChangeLine(change, finished.bands) }
            if (finished.kept != null) {
                KeptLine(changes, name, copy)
                return@Column
            }
            SegmentedPicker(
                SaveScope.entries,
                scope,
                label = {
                    stringResource(
                        if (it ==
                            SaveScope.ThisDay
                        ) {
                            Res.string.changes_this_day
                        } else {
                            Res.string.changes_every_day
                        },
                    )
                },
                onSelect = { scopeName = it.name },
            )
            Text(
                if (scope == SaveScope.ThisDay) {
                    stringResource(Res.string.changes_this_day_hint, changes.dayIndex + 1)
                } else {
                    stringResource(Res.string.changes_every_day_hint)
                },
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
            if (builtIn) {
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
            SecondaryButton(
                if (builtIn) {
                    stringResource(Res.string.changes_save_copy, copy)
                } else {
                    stringResource(Res.string.changes_save, name)
                },
                { onKeep(scope, copy) },
                Modifier.fillMaxWidth().padding(top = Spacing.space8),
            )
            QuietTextButton(
                stringResource(Res.string.changes_today_only),
                { dismissed = true },
                Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

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
