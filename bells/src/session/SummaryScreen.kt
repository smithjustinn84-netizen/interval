package dev.justinsmith.bells.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.kg_value
import dev.justinsmith.bells.resources.record_line
import dev.justinsmith.bells.resources.session_moved
import dev.justinsmith.bells.resources.session_time
import dev.justinsmith.bells.resources.summary_done
import dev.justinsmith.bells.resources.summary_records
import dev.justinsmith.bells.resources.summary_reps
import dev.justinsmith.bells.resources.summary_shared
import dev.justinsmith.bells.ui.StatTile
import dev.justinsmith.bells.ui.exerciseName
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import org.jetbrains.compose.resources.stringResource

/** A finished session: Done in the done colour, its time, weight moved and reps, and any records it set. */
@Composable
fun SummaryScreen(finished: Finished, onDone: () -> Unit, modifier: Modifier = Modifier) {
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
                    color = PhaseColors.done,
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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                    StatTile(
                        stringResource(Res.string.session_time),
                        formatClock(session.elapsedSeconds(0)),
                        Modifier.weight(1f),
                    )
                    StatTile(stringResource(Res.string.summary_reps), session.reps.toString(), Modifier.weight(1f))
                }
                StatTile(
                    stringResource(Res.string.session_moved),
                    stringResource(Res.string.kg_value, formatKg(session.tonnageKg)),
                    Modifier.fillMaxWidth(),
                )
                if (finished.records.isNotEmpty()) {
                    GroupLabel(stringResource(Res.string.summary_records))
                    ListCard {
                        finished.records.forEachIndexed { i, record ->
                            if (i > 0) RowDivider()
                            Column(Modifier.padding(Spacing.space16)) {
                                Text(
                                    exerciseName(record.exercise),
                                    style = IntervalTheme.type.label,
                                    color = palette.text,
                                )
                                Text(
                                    stringResource(
                                        Res.string.record_line,
                                        formatKg(record.heaviestKg),
                                        record.repsAtHeaviest,
                                        record.mostReps,
                                        formatKg(record.mostRepsKg),
                                    ),
                                    style = IntervalTheme.type.meta,
                                    color = palette.textDim,
                                )
                            }
                        }
                    }
                }
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
