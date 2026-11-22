package dev.justinsmith.interval.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WorkoutEngine
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.SnatchAsk
import dev.justinsmith.interval.ui.SnatchQuestion
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.risingEnter
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.summary_hand_care
import org.jetbrains.compose.resources.stringResource

/**
 * The summary's snatches: "About how many snatches a set?" ([ask], counted through [onCount]), then how to look after
 * the hands, as Lift's summary says it, rising in once the count and the day's others come to enough ([handCare]).
 * Under the summary's lines, as wide as its content, on the card the heart rate takes above it.
 */
@Composable
internal fun SummarySnatches(
    ask: SnatchAsk,
    handCare: Boolean,
    onCount: (SnatchCount?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val workout = IntervalTheme.workout
    Column(
        modifier
            .padding(top = Spacing.space24)
            .widthIn(max = Sizes.content)
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(workout.control)
            .border(Sizes.hairline, workout.upcoming, Corners.radius18)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SnatchQuestion(ask, onCount = onCount)
        AnimatedVisibility(handCare, enter = risingEnter()) {
            Text(
                stringResource(Res.string.summary_hand_care),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.workout.onGroundDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.space12),
            )
        }
    }
}

/** The summary of a workout whose snatches are counted, the count open and the hands' line under it. */
@PreviewFormFactors
@Composable
private fun SummarySnatchesPreview() {
    val done = generateSequence(WorkoutEngine.start(IntervalConfig()).state) { WorkoutEngine.advance(it).state }
        .first { it.isDone }
    val counted = SnatchCount(perSet = 8, bellKg = 16.0)
    val bells = listOf(12.0, 16.0, 20.0)
    WorkoutPreview(
        done.toUiState(running = false, elapsedSeconds = done.config.totalSeconds).copy(
            snatches = SnatchAsk(counted, opensOn = counted, bells = bells),
            handCare = true,
        ),
    )
}
