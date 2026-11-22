package dev.justinsmith.lift.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import dev.justinsmith.interval.ui.Burst
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Shockwave
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.interval.ui.rememberArrival
import dev.justinsmith.interval.ui.risingEnter
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.session_edit_set_cd
import dev.justinsmith.lift.session.resources.session_remove_set_cd
import dev.justinsmith.lift.session.resources.session_set_removed
import dev.justinsmith.lift.ui.RecordTag
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.setLine
import org.jetbrains.compose.resources.stringResource

/** What a logged set's row does: × removes it (the row words the notice), a tap opens it to change. */
internal class SetActions(val onRemove: (SetLog, removedText: String) -> Unit, val onEdit: (SetLog) -> Unit)

/**
 * The sets logged so far, by lift, those in [recordSetIds] tagged and those in [ladderTopIds] struck as they land.
 * Given the sets alone, not the session's state, so a change to the draft regroups nothing.
 */
@Composable
internal fun LoggedSets(
    sets: List<SetLog>,
    recordSetIds: Set<Long>,
    actions: SetActions,
    ladderTopIds: Set<Long> = emptySet(),
) {
    // The sets on show when the screen opened stand still; one logged since rises into its card, and one that
    // beat a record has its tag stamp in and the accent ring out from behind it, led by a shockwave. (A set
    // edited into a record keeps its id, so it is tagged without the ceremony.) One that topped a ladder has the
    // shockwave alone: the ladder climbed is a moment, and no record.
    val atOpen = remember { sets.map { it.id }.toSet() }
    val byLift = remember(sets) { sets.groupBy { it.exercise to it.double } }
    byLift.forEach { (lift, done) ->
        GroupLabel(liftName(lift.first, lift.second))
        ListCard(Modifier.animateContentSize()) {
            done.forEachIndexed { i, set ->
                key(set.id) {
                    AnimatedVisibility(rememberArrival(set.id !in atOpen), enter = risingEnter()) {
                        Column {
                            if (i > 0) RowDivider()
                            val fresh = set.id !in atOpen
                            SetRow(
                                set,
                                mark = when {
                                    set.id in recordSetIds -> if (fresh) SetMark.RecordNow else SetMark.Record
                                    fresh && set.id in ladderTopIds -> SetMark.LadderTopped
                                    else -> SetMark.None
                                },
                                onRemove = { actions.onRemove(set, it) },
                                onEdit = { actions.onEdit(set) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What a logged set's row marks: a record standing, one set just now, or a ladder topped just now. */
private enum class SetMark { None, Record, RecordNow, LadderTopped }

/**
 * A set logged: a tap opens it to change, × removes it; one that set a record is tagged, and one that set it
 * just now ([SetMark.RecordNow]) has the tag stamp in over a ring of the accent led by its shockwave, clipped to
 * its card by design. One that topped a ladder just now has the shockwave alone.
 */
@Composable
private fun SetRow(
    set: SetLog,
    mark: SetMark,
    onRemove: (removedText: String) -> Unit,
    onEdit: () -> Unit,
) {
    val record = mark == SetMark.Record || mark == SetMark.RecordNow
    val stamp = mark == SetMark.RecordNow
    val line = setLine(set)
    val edit = stringResource(Res.string.session_edit_set_cd, line)
    val removed = stringResource(Res.string.session_set_removed, line)
    val interaction = remember { MutableInteractionSource() }
    Box {
        if (stamp) {
            Burst(
                listOf(IntervalTheme.palette.accent),
                Modifier.matchParentSize(),
                delayMillis = Motion.ENTER_MILLIS,
                stroke = Sizes.rowBurstStroke,
                shock = true,
            )
        }
        if (mark == SetMark.LadderTopped) {
            Shockwave(
                IntervalTheme.palette.accent,
                Modifier.matchParentSize(),
                delayMillis = Motion.ENTER_MILLIS,
                stroke = Sizes.rowShockwaveStroke,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.listRow)
                .clickable(
                    interaction,
                    LocalIndication.current,
                    onClickLabel = edit,
                    role = Role.Button,
                    onClick = onEdit,
                )
                .pressScale(interaction)
                .padding(start = Spacing.space16, end = Spacing.space4, top = Spacing.space8, bottom = Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                line,
                style = IntervalTheme.type.label,
                color = IntervalTheme.palette.text,
                modifier = Modifier.weight(1f),
            )
            if (record) RecordTag(Modifier.padding(end = Spacing.space8), stamp = stamp)
            IconAction(
                IntervalIcons.Close,
                stringResource(Res.string.session_remove_set_cd, line),
                onClick = { onRemove(removed) },
                haptic = HapticFeedbackType.SegmentTick,
            )
        }
    }
}
