package dev.justinsmith.interval.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.nav_history
import dev.justinsmith.interval.resources.nav_today
import dev.justinsmith.interval.resources.nav_train
import dev.justinsmith.interval.resources.train_conditioning
import dev.justinsmith.interval.resources.train_mobility
import dev.justinsmith.interval.resources.train_strength
import dev.justinsmith.interval.resources.welcome_example_detail
import dev.justinsmith.interval.resources.welcome_example_minutes
import dev.justinsmith.interval.resources.welcome_example_program
import dev.justinsmith.interval.resources.welcome_example_sport
import dev.justinsmith.interval.resources.welcome_example_start
import dev.justinsmith.interval.resources.welcome_example_up_next
import dev.justinsmith.interval.resources.welcome_example_week
import dev.justinsmith.interval.resources.welcome_example_week_count
import dev.justinsmith.interval.resources.welcome_log_keeps
import dev.justinsmith.interval.resources.welcome_log_lead
import dev.justinsmith.interval.resources.welcome_log_legend_done
import dev.justinsmith.interval.resources.welcome_log_legend_planned
import dev.justinsmith.interval.resources.welcome_log_legend_today
import dev.justinsmith.interval.resources.welcome_log_title
import dev.justinsmith.interval.resources.welcome_today_lead
import dev.justinsmith.interval.resources.welcome_today_time
import dev.justinsmith.interval.resources.welcome_today_title
import dev.justinsmith.interval.resources.welcome_train_conditioning
import dev.justinsmith.interval.resources.welcome_train_lead
import dev.justinsmith.interval.resources.welcome_train_mobility
import dev.justinsmith.interval.resources.welcome_train_strength
import dev.justinsmith.interval.resources.welcome_train_title
import dev.justinsmith.interval.ui.AppSizes
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.WeekdayForm
import dev.justinsmith.interval.ui.weekdayText
import org.jetbrains.compose.resources.stringResource

// The welcome's three pages, a tab each. Each shows what is on its tab as an example drawn from the tab's own
// parts: read, not pressed, so none of it takes a tap and what only draws (a Start, a picker) is left unspoken.

/** The page of tab [page], 1 to [WELCOME_TABS]: the tab as the bar shows it selected, what it is for, an example. */
@Composable
internal fun TabPage(page: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.space4)
            .padding(top = Spacing.space16, bottom = Spacing.space24),
        verticalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        when (page) {
            1 -> TodayPage()
            2 -> TrainPage()
            else -> LogPage()
        }
    }
}

/** Today: it leads with what is next, and offers what fits a few minutes. */
@Composable
private fun ColumnScope.TodayPage() {
    PageHead(IntervalIcons.Today, stringResource(Res.string.nav_today), stringResource(Res.string.welcome_today_title))
    Lead(stringResource(Res.string.welcome_today_lead))
    ListCard(Modifier.semantics(mergeDescendants = true) {}) {
        val palette = IntervalTheme.palette
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(
                stringResource(Res.string.welcome_example_up_next),
                style = IntervalTheme.type.caption,
                color = palette.textDim,
            )
            Text(
                stringResource(Res.string.welcome_example_program),
                style = IntervalTheme.type.value,
                color = palette.text,
            )
            Text(
                stringResource(Res.string.welcome_example_detail),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
            Row(
                Modifier
                    .padding(top = Spacing.space8)
                    .fillMaxWidth()
                    .height(Sizes.save)
                    .clip(Corners.radius18)
                    .background(palette.accent)
                    .clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(IntervalIcons.Play, null, tint = palette.onAccent, modifier = Modifier.size(Sizes.icon))
                Text(
                    stringResource(Res.string.welcome_example_start),
                    style = IntervalTheme.type.value,
                    color = palette.onAccent,
                )
            }
        }
    }
    Lead(stringResource(Res.string.welcome_today_time), Modifier.padding(top = Spacing.space12))
    Segments(ExampleMinutes.map { stringResource(Res.string.welcome_example_minutes, it) }, selected = 3)
}

/** Train: its three pillars, and what each holds. */
@Composable
private fun ColumnScope.TrainPage() {
    val palette = IntervalTheme.palette
    PageHead(IntervalIcons.Bell, stringResource(Res.string.nav_train), stringResource(Res.string.welcome_train_title))
    Lead(stringResource(Res.string.welcome_train_lead))
    val strength = stringResource(Res.string.train_strength)
    val conditioning = stringResource(Res.string.train_conditioning)
    val mobility = stringResource(Res.string.train_mobility)
    Segments(listOf(strength, conditioning, mobility), selected = 0)
    ListCard {
        // Each in the colour its work is marked with everywhere: the accent, the work mark, the rest mark.
        Pillar(palette.accent, strength, stringResource(Res.string.welcome_train_strength))
        RowDivider()
        Pillar(palette.workMark, conditioning, stringResource(Res.string.welcome_train_conditioning))
        RowDivider()
        Pillar(palette.restMark, mobility, stringResource(Res.string.welcome_train_mobility))
    }
}

/** Log: a week of dots, how to read one, and where they are kept. */
@Composable
private fun ColumnScope.LogPage() {
    val palette = IntervalTheme.palette
    PageHead(
        IntervalIcons.History,
        stringResource(Res.string.nav_history),
        stringResource(Res.string.welcome_log_title),
    )
    Lead(stringResource(Res.string.welcome_log_lead))
    ListCard(Modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            Column {
                Text(
                    stringResource(Res.string.welcome_example_week),
                    style = IntervalTheme.type.caption,
                    color = palette.textDim,
                )
                Text(
                    stringResource(Res.string.welcome_example_week_count),
                    style = IntervalTheme.type.label,
                    color = palette.text,
                )
            }
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                ExampleWeek.forEachIndexed { index, day -> ExampleDot(index + 1, day, Modifier.weight(1f)) }
            }
        }
    }
    Row(
        Modifier.padding(horizontal = Spacing.space4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
    ) {
        Legend(ExampleDay.Strength, stringResource(Res.string.welcome_log_legend_done))
        Legend(ExampleDay.Today, stringResource(Res.string.welcome_log_legend_today))
        Legend(ExampleDay.Planned, stringResource(Res.string.welcome_log_legend_planned))
    }
    Lead(stringResource(Res.string.welcome_log_keeps), Modifier.padding(top = Spacing.space12))
}

/** A page's head: its tab as the bar shows it selected, the icon on the accent beside its name, over the title. */
@Composable
private fun PageHead(icon: ImageVector, tab: String, title: String, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(AppSizes.welcomeTabWidth, AppSizes.welcomeTabHeight)
                    .clip(Corners.full)
                    .background(palette.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = palette.onAccent, modifier = Modifier.size(Sizes.icon))
            }
            Text(tab, style = IntervalTheme.type.caption, color = palette.text)
        }
        Text(title, style = IntervalTheme.type.title, color = palette.text, modifier = Modifier.semantics { heading() })
    }
}

/** A line of the page's own words, over the example it leads into. */
@Composable
private fun Lead(text: String, modifier: Modifier = Modifier) {
    Text(text, style = IntervalTheme.type.body, color = IntervalTheme.palette.textDim, modifier = modifier)
}

/** A segmented picker as it stands with [selected] chosen: drawn, not pressed, so unspoken. */
@Composable
private fun Segments(labels: List<String>, selected: Int, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surfaceVariant)
            .padding(Spacing.space4)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.segment)
                    .clip(Corners.radius12)
                    .background(if (index == selected) palette.surface else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = IntervalTheme.type.label,
                    color = palette.text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** A pillar of Train: its stripe, its name, and what it holds. */
@Composable
private fun Pillar(color: Color, name: String, holds: String, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StripeMark(color, height = IntervalSizes.historyMarkHeight)
        Column {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            Text(holds, style = IntervalTheme.type.meta, color = palette.textDim)
        }
    }
}

/** A day of the example week, as Today's dots draw one. */
private enum class ExampleDay(val sport: Boolean = false) {
    Strength,
    FlowAndSport(sport = true),
    Today(sport = true),
    Planned,
    Rest,
}

/** Monday to Sunday: two sessions and a flow before class done, class again today, a session still planned. */
private val ExampleWeek = listOf(
    ExampleDay.Strength,
    ExampleDay.FlowAndSport,
    ExampleDay.Strength,
    ExampleDay.Today,
    ExampleDay.Planned,
    ExampleDay.Rest,
    ExampleDay.Rest,
)

/** The lengths Today's pick by time offers. */
private val ExampleMinutes = listOf(5, 10, 15, 20, 30)

/** A day's dot over its initial and, on a day with one, the sport's name. */
@Composable
private fun ExampleDot(weekday: Int, day: ExampleDay, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Dot(day, Modifier.size(Sizes.weekBarMark))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                weekdayText(weekday, WeekdayForm.Initial),
                style = IntervalTheme.type.caption,
                color = if (day == ExampleDay.Today) palette.accent else palette.textDim,
            )
            Text(
                if (day.sport) stringResource(Res.string.welcome_example_sport) else "",
                style = IntervalTheme.type.caption,
                color = if (day == ExampleDay.FlowAndSport) palette.text else palette.textDim,
                maxLines = 1,
            )
        }
    }
}

/**
 * The dot alone: filled and checked once done, ringed in the text colour today, outlined in the accent while
 * planned.
 */
@Composable
private fun Dot(day: ExampleDay, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val done = day == ExampleDay.Strength || day == ExampleDay.FlowAndSport
    val fill = when (day) {
        ExampleDay.Strength -> palette.accent
        ExampleDay.FlowAndSport -> palette.restMark
        else -> palette.surfaceVariant
    }
    val ring = when (day) {
        ExampleDay.Today -> Modifier.border(Sizes.border, palette.text, CircleShape)
        ExampleDay.Planned -> Modifier.border(Sizes.border, palette.accent, CircleShape)
        else -> Modifier
    }
    Box(modifier.clip(CircleShape).background(fill).then(ring), contentAlignment = Alignment.Center) {
        if (done) {
            Icon(IntervalIcons.Check, null, tint = palette.onAccent, modifier = Modifier.fillMaxSize(CHECK_SHARE))
        }
    }
}

/** A dot in small beside what it means. */
@Composable
private fun Legend(day: ExampleDay, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(day, Modifier.size(Sizes.chipIcon))
        Text(text, style = IntervalTheme.type.meta, color = IntervalTheme.palette.textDim)
    }
}

/** How much of its dot a check takes. */
private const val CHECK_SHARE = 0.6f
