package dev.justinsmith.interval.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.today.resources.Res
import dev.justinsmith.interval.today.resources.preview_open_cd
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.JoinedText
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLargeText
import org.jetbrains.compose.resources.stringResource

/**
 * What the step to do now is: its caption in the day's [tone], its name with a chevron where it opens a look
 * ([onLook]), the line of what it is and an interval workout's shape.
 */
@Composable
internal fun LeadHeading(item: UpNextItem, tone: Color, onLook: (() -> Unit)?) {
    val palette = IntervalTheme.palette
    val described = stringResource(Res.string.preview_open_cd, upNextTitle(item))
    val tap = if (onLook != null) Modifier.clickable(onClickLabel = described, onClick = onLook) else Modifier
    Column(Modifier.fillMaxWidth().then(tap), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        // In the day's tone, as its number is: the one caption on the card that says "do this now".
        val badge = Modifier.clip(Corners.full).background(palette.tint(tone))
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4)
        Text(leadCaption(item), style = IntervalTheme.type.caption, color = tone, modifier = badge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                upNextTitle(item),
                style = IntervalTheme.type.value,
                color = palette.text,
                modifier = Modifier.weight(1f, fill = false),
            )
            // The look it opens, shown as a pick's row shows it.
            if (onLook != null) {
                Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.chipIcon))
            }
        }
        // A session's about-how-long is on its Start, so its line leads with the day. With large text a day's lifts
        // would push its Start off the first screen: its look lists them.
        JoinedText(
            upNextDetail(item, withLength = false, withLifts = !isLargeText()),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
        StepShape(item, tone, Modifier.padding(top = Spacing.space4))
    }
}
