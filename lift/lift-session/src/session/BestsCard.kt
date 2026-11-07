package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.summary_abc_best
import dev.justinsmith.lift.session.resources.summary_bests
import dev.justinsmith.lift.session.resources.summary_milestone
import dev.justinsmith.lift.session.resources.summary_snatch_test
import dev.justinsmith.lift.ui.exerciseName
import org.jetbrains.compose.resources.stringResource

/** Whether the session set a lifetime mark: a snatch test or ABC best, or a milestone of reps crossed. */
internal val Finished.hasBests: Boolean
    get() = snatchTest != null || armorBuilding != null || milestones.isNotEmpty()

/**
 * The session's lifetime marks on a card of their own under "Bests", a line each with a check: the snatch
 * test or Armor Building Complex it beat every session before at, and each milestone of reps it crossed
 * ("1000 in all: Two-hand swing"). Nothing when it set none, which is most sessions.
 */
@Composable
internal fun BestsCard(finished: Finished, modifier: Modifier = Modifier) {
    if (!finished.hasBests) return
    val lines = listOfNotNull(
        finished.snatchTest?.let { stringResource(Res.string.summary_snatch_test, it) },
        finished.armorBuilding?.let { stringResource(Res.string.summary_abc_best, it) },
    ) + finished.milestones.map { (exercise, count) ->
        stringResource(Res.string.summary_milestone, count, exerciseName(exercise))
    }
    Column(modifier) {
        GroupLabel(stringResource(Res.string.summary_bests))
        ListCard {
            lines.forEachIndexed { i, line ->
                if (i > 0) RowDivider()
                BestRow(line)
            }
        }
    }
}

@Composable
private fun BestRow(line: String) {
    val palette = IntervalTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space8)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Icon(IntervalIcons.Check, null, tint = palette.accent, modifier = Modifier.size(Sizes.chipIcon))
        Text(line, style = IntervalTheme.type.label, color = palette.text)
    }
}
