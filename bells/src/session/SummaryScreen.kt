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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.summary_done
import dev.justinsmith.bells.resources.summary_shared
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.IntervalTheme
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
