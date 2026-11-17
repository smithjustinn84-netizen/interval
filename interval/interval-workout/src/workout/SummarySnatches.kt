package dev.justinsmith.interval.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.ui.IntervalTheme
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
 * Under the summary's lines, as wide as its content.
 */
@Composable
internal fun SummarySnatches(
    ask: SnatchAsk,
    handCare: Boolean,
    onCount: (SnatchCount?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(top = Spacing.space24).widthIn(max = Sizes.content),
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
