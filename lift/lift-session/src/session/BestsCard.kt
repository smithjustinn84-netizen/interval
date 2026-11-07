package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import dev.justinsmith.lift.session.resources.summary_density
import dev.justinsmith.lift.session.resources.summary_density_best
import dev.justinsmith.lift.session.resources.summary_milestone
import dev.justinsmith.lift.session.resources.summary_snatch_test
import dev.justinsmith.lift.ui.exerciseName
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Whether the session set a lifetime mark (a snatch test or ABC best, or a milestone of reps crossed) or did a
 * density block, whose rounds are said against its best.
 */
internal val Finished.hasBests: Boolean
    get() = snatchTest != null || armorBuilding != null || milestones.isNotEmpty() || densities.isNotEmpty()

/**
 * The session's lifetime marks on a card of their own under "Bests", a line each with a check: the snatch
 * test or Armor Building Complex it beat every session before at, and each milestone of reps it crossed
 * ("1000 in all: Two-hand swing"). A density block says its rounds ("12 rounds in 20 min"), checked as a new best
 * when it beat the block's ("New best: 15 rounds in 20 min"). Nothing when it set none, which is most sessions.
 */
@Composable
internal fun BestsCard(finished: Finished, modifier: Modifier = Modifier) {
    if (!finished.hasBests) return
    val densities = finished.densities.map { done ->
        val words = if (done.newBest) Res.plurals.summary_density_best else Res.plurals.summary_density
        pluralStringResource(words, done.rounds, done.rounds, done.block.rounds) to done.newBest
    }
    val marks = listOfNotNull(
        finished.snatchTest?.let { stringResource(Res.string.summary_snatch_test, it) },
        finished.armorBuilding?.let { stringResource(Res.string.summary_abc_best, it) },
    ) + finished.milestones.map { (exercise, count) ->
        stringResource(Res.string.summary_milestone, count, exerciseName(exercise))
    }
    val lines = densities + marks.map { it to true }
    Column(modifier) {
        GroupLabel(stringResource(Res.string.summary_bests))
        ListCard {
            lines.forEachIndexed { i, (line, checked) ->
                if (i > 0) RowDivider()
                BestRow(line, checked)
            }
        }
    }
}

/** A line of the card, with a check when it is a mark set; a density block's rounds short of its best has none. */
@Composable
private fun BestRow(line: String, checked: Boolean) {
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
        if (checked) {
            Icon(IntervalIcons.Check, null, tint = palette.accent, modifier = Modifier.size(Sizes.chipIcon))
        } else {
            Spacer(Modifier.size(Sizes.chipIcon))
        }
        Text(line, style = IntervalTheme.type.label, color = palette.text)
    }
}
