package dev.justinsmith.interval.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.justinsmith.interval.R
import dev.justinsmith.interval.ShortcutActivity
import dev.justinsmith.interval.data.WeekStandingSource
import dev.justinsmith.interval.nav.Shortcut
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Spacing
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The home-screen widget: the week's ring as History draws it (this week's workouts of both kinds over the goal
 * set on Plan, or the count alone with no goal), the streak under it, and Workout and Lift, which start one as
 * the launcher's shortcuts do (through [ShortcutActivity], so a workout already running is kept). Its words are
 * the host's resources, its colours the workout page's ([PhaseColors]) on every theme: a widget sits on the
 * wallpaper, as the workout sits on its dark ground. The standing comes from the container's
 * [WeekStandingSource], which `IntervalApplication` started before any receiver ran; it draws once the stores are
 * read, Glance's loading layout showing until then. One layout per size: a row for a short widget, the ring
 * over the buttons for a tall one, the words beside the ring for a wide one.
 */
class WeekWidget :
    GlanceAppWidget(),
    KoinComponent {
    private val source: WeekStandingSource by inject()

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(WidgetSizes.wideFrom / 2, WidgetSizes.tallFrom / 2),
            DpSize(WidgetSizes.wideFrom / 2, WidgetSizes.tallFrom),
            DpSize(WidgetSizes.wideFrom, WidgetSizes.tallFrom),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val standing = source.standing.filterNotNull().first()
        // Drawn once, before the content: Glance composes it for every size, and the sizes share each bitmap.
        val ring = weekRing(context, standing, WidgetSizes.ring, WidgetSizes.ringStroke)
        val ringCompact = weekRing(context, standing, WidgetSizes.ringCompact, WidgetSizes.ringStrokeCompact)
        val workout = shortcut(context, Shortcut.StartWorkout)
        val lift = shortcut(context, Shortcut.LiftSession)
        provideContent { WeekWidgetContent(standing, ring, ringCompact, workout, lift) }
    }

    /** What the launcher's shortcut sends; the data keeps the two actions' pending intents apart. */
    private fun shortcut(context: Context, shortcut: Shortcut): Action =
        actionStartActivity(
            Intent(context, ShortcutActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("interval://shortcut/${shortcut.id}"))
                .putExtra(ShortcutActivity.EXTRA_SHORTCUT, shortcut.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
}

@Composable
private fun WeekWidgetContent(
    standing: WeekStanding,
    ring: Bitmap,
    ringCompact: Bitmap,
    workout: Action,
    lift: Action,
) {
    val size = LocalSize.current
    Box(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_card)).padding(Spacing.space12),
        contentAlignment = Alignment.Center,
    ) {
        when {
            size.height < WidgetSizes.tallFrom -> CompactRow(standing, ringCompact, workout, lift)
            size.width < WidgetSizes.wideFrom -> TallColumn(standing, ring, workout, lift)
            else -> WideRow(standing, ring, workout, lift)
        }
    }
}

@Composable
private fun CompactRow(standing: WeekStanding, ring: Bitmap, workout: Action, lift: Action) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Ring(standing, ring, WidgetSizes.ringCompact, WidgetSizes.figureCompact)
        Spacer(GlanceModifier.defaultWeight())
        // Too narrow for words: the shortcuts' glyphs, each a round button.
        IconButton(WorkoutLook, R.drawable.widget_glyph_workout, workout)
        Spacer(GlanceModifier.width(Spacing.space8))
        IconButton(LiftLook, R.drawable.widget_glyph_lift, lift)
    }
}

@Composable
private fun TallColumn(standing: WeekStanding, ring: Bitmap, workout: Action, lift: Action) {
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(standing, ring, WidgetSizes.ring, WidgetSizes.figure)
        Spacer(GlanceModifier.height(Spacing.space8))
        Words(standing, TextAlign.Center)
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            StartButton(WorkoutLook, workout, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(Spacing.space8))
            StartButton(LiftLook, lift, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun WideRow(standing: WeekStanding, ring: Bitmap, workout: Action, lift: Action) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Ring(standing, ring, WidgetSizes.ring, WidgetSizes.figure)
        Spacer(GlanceModifier.width(Spacing.space16))
        Words(standing, TextAlign.Start, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(Spacing.space16))
        Column(GlanceModifier.width(WidgetSizes.buttonWide)) {
            StartButton(WorkoutLook, workout, GlanceModifier.fillMaxWidth())
            Spacer(GlanceModifier.height(Spacing.space8))
            StartButton(LiftLook, lift, GlanceModifier.fillMaxWidth())
        }
    }
}

/** The ring with its figure over it: "3/4", or the count alone with no goal. */
@Composable
private fun Ring(standing: WeekStanding, bitmap: Bitmap, diameter: Dp, figure: TextUnit) {
    val resources = LocalContext.current.resources
    val goal = standing.goal
    val description = if (goal != null) {
        resources.getString(R.string.widget_goal_cd, standing.count, goal)
    } else {
        resources.getQuantityString(R.plurals.widget_count, standing.count, standing.count)
    }
    val words = goal?.let { resources.getString(R.string.widget_goal_ring, standing.count, it) } ?: "${standing.count}"
    Box(
        GlanceModifier.size(diameter).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.size(diameter))
        Text(
            words,
            style = TextStyle(
                color = ColorProvider(PhaseColors.onGround),
                fontSize = figure,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Where the week stands in words, and the streak when there is one, lined up as [align] says. */
@Composable
private fun Words(standing: WeekStanding, align: TextAlign, modifier: GlanceModifier = GlanceModifier) {
    val resources = LocalContext.current.resources
    val goal = standing.goal
    val caption = when {
        goal == null -> resources.getString(R.string.widget_this_week)
        standing.goalMet -> resources.getString(R.string.widget_goal_met)
        else -> (goal - standing.count).let { resources.getQuantityString(R.plurals.widget_goal_left, it, it) }
    }
    val horizontal = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
    Column(modifier, horizontalAlignment = horizontal) {
        Text(
            caption,
            style = TextStyle(
                ColorProvider(PhaseColors.onGround),
                WidgetSizes.caption,
                FontWeight.Medium,
                textAlign = align,
            ),
        )
        if (standing.streak > 0) {
            Text(
                resources.getQuantityString(R.plurals.widget_streak, standing.streak, standing.streak),
                style = TextStyle(ColorProvider(PhaseColors.onGroundDim), WidgetSizes.caption, textAlign = align),
            )
        }
    }
}

/** A start button's words and colours: the shortcut's label and its long form, the fill and the ink on it. */
private class StartLook(
    @StringRes val label: Int,
    @StringRes val description: Int,
    @DrawableRes val fill: Int,
    val ink: Color,
)

/** Workout is the primary, on the done fill; Lift beside it on the upcoming fill. */
private val WorkoutLook = StartLook(
    R.string.shortcut_start_workout,
    R.string.shortcut_start_workout_long,
    R.drawable.widget_button_primary,
    PhaseColors.ink,
)
private val LiftLook = StartLook(
    R.string.shortcut_lift_session,
    R.string.shortcut_lift_session_long,
    R.drawable.widget_button_secondary,
    PhaseColors.onGround,
)

@Composable
private fun StartButton(look: StartLook, action: Action, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val words = context.getString(look.description)
    Box(
        modifier
            .height(WidgetSizes.button)
            .background(ImageProvider(look.fill))
            .clickable(action)
            .semantics { contentDescription = words },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            context.getString(look.label),
            style = TextStyle(
                ColorProvider(look.ink),
                WidgetSizes.label,
                FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun IconButton(look: StartLook, @DrawableRes glyph: Int, action: Action) {
    val words = LocalContext.current.getString(look.description)
    Box(
        GlanceModifier
            .size(WidgetSizes.button)
            .background(ImageProvider(look.fill))
            .clickable(action)
            .semantics { contentDescription = words },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(glyph), contentDescription = null, modifier = GlanceModifier.size(WidgetSizes.glyph))
    }
}
