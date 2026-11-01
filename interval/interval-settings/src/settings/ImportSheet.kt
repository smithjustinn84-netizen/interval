package dev.justinsmith.interval.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.settings.resources.Res
import dev.justinsmith.interval.settings.resources.common_done
import dev.justinsmith.interval.settings.resources.import_action
import dev.justinsmith.interval.settings.resources.import_already
import dev.justinsmith.interval.settings.resources.import_also_settings
import dev.justinsmith.interval.settings.resources.import_also_settings_hint
import dev.justinsmith.interval.settings.resources.import_cancel
import dev.justinsmith.interval.settings.resources.import_counts_joined
import dev.justinsmith.interval.settings.resources.import_done
import dev.justinsmith.interval.settings.resources.import_done_nothing
import dev.justinsmith.interval.settings.resources.import_exercises
import dev.justinsmith.interval.settings.resources.import_from
import dev.justinsmith.interval.settings.resources.import_from_version
import dev.justinsmith.interval.settings.resources.import_new
import dev.justinsmith.interval.settings.resources.import_nothing_new
import dev.justinsmith.interval.settings.resources.import_plans
import dev.justinsmith.interval.settings.resources.import_presets
import dev.justinsmith.interval.settings.resources.import_programs
import dev.justinsmith.interval.settings.resources.import_sessions
import dev.justinsmith.interval.settings.resources.import_title
import dev.justinsmith.interval.settings.resources.import_too_new
import dev.justinsmith.interval.settings.resources.import_too_new_unnamed
import dev.justinsmith.interval.settings.resources.import_unreadable
import dev.justinsmith.interval.settings.resources.import_workouts
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.ToggleRow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What Import does from Settings: pick a file, import the one under review, or close the sheet. */
class ImportActions(
    val onPick: () -> Unit = {},
    val onConfirm: (withSettings: Boolean) -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * The review sheet for a file picked to import: when it was exported, what in it is new and what is already
 * here, "Also settings", and Import. A file that can't be imported says why, and changes nothing.
 */
@Composable
internal fun ImportSheet(step: ImportStep.Review, actions: ImportActions) {
    val palette = IntervalTheme.palette
    IntervalSheet(title = stringResource(Res.string.import_title), onDismiss = actions.onDismiss) { hide ->
        when (val review = step.review) {
            is ImportReview.Ready -> {
                var withSettings by rememberSaveable { mutableStateOf(false) }
                val from =
                    review.version?.let {
                        stringResource(Res.string.import_from_version, step.exportedOn.orEmpty(), it)
                    }
                        ?: stringResource(Res.string.import_from, step.exportedOn.orEmpty())
                Text(from, style = IntervalTheme.type.label, color = palette.textDim)
                Text(
                    if (review.new.total == 0) {
                        stringResource(Res.string.import_nothing_new)
                    } else {
                        stringResource(Res.string.import_new, countsText(review.new))
                    },
                    style = IntervalTheme.type.body,
                    color = palette.text,
                )
                if (review.alreadyHere > 0) {
                    Text(
                        pluralStringResource(Res.plurals.import_already, review.alreadyHere, review.alreadyHere),
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                    )
                }
                ListCard {
                    ToggleRow(
                        stringResource(Res.string.import_also_settings),
                        stringResource(Res.string.import_also_settings_hint),
                        withSettings,
                        onToggle = { withSettings = !withSettings },
                    )
                }
                AccentButton(
                    stringResource(Res.string.import_action),
                    onClick = {
                        actions.onConfirm(withSettings)
                        hide()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = review.new.total > 0 || withSettings,
                )
                QuietTextButton(stringResource(Res.string.import_cancel), hide, Modifier.fillMaxWidth())
            }

            is ImportReview.TooNew -> Refusal(
                review.version?.let { stringResource(Res.string.import_too_new, it) }
                    ?: stringResource(Res.string.import_too_new_unnamed),
                hide,
            )

            ImportReview.Unreadable -> Refusal(stringResource(Res.string.import_unreadable), hide)
        }
    }
}

/** Why the file can't be imported, and the way out. */
@Composable
private fun Refusal(text: String, hide: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
        Text(text, style = IntervalTheme.type.body, color = IntervalTheme.palette.text)
        AccentButton(stringResource(Res.string.common_done), hide, Modifier.fillMaxWidth())
    }
}

/** What came in, for the notice: "Imported 12 workouts, 3 presets", or that nothing was new. */
@Composable
internal fun importedText(added: ImportCounts): String =
    if (added.total == 0) {
        stringResource(Res.string.import_done_nothing)
    } else {
        stringResource(Res.string.import_done, countsText(added))
    }

/** The kinds with something in them, in words, one after another: "12 workouts, 3 presets, 1 Lift session". */
@Composable
private fun countsText(counts: ImportCounts): String = listOfNotNull(
    counts.workouts.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.import_workouts, it, it) },
    counts.sessions.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.import_sessions, it, it) },
    counts.presets.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.import_presets, it, it) },
    counts.plans.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.import_plans, it, it) },
    counts.exercises.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.import_exercises, it, it) },
    counts.programs.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.import_programs, it, it) },
).reduceOrNull { all, next -> stringResource(Res.string.import_counts_joined, all, next) }.orEmpty()
