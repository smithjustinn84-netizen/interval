package dev.justinsmith.interval.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.barlow_condensed_extrabold
import dev.justinsmith.core.resources.barlow_condensed_semibold
import dev.justinsmith.core.resources.barlow_regular
import dev.justinsmith.core.resources.barlow_semibold
import org.jetbrains.compose.resources.Font

/** Surfaces and text for the setup screen; light and dark variants. */
@Immutable
data class IntervalPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val text: Color,
    val textDim: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    /**
     * The phases as Setup draws them: field stripes, values on their tints, History marks. Light
     * mode deepens work and rest so they read on white; dark mode uses the workout's own
     * [PhaseColors], which are tuned for a near-black ground.
     */
    val prepMark: Color,
    val workMark: Color,
    val restMark: Color,
    /**
     * The Get ready value on its tint. Amber is too light to read on a pale tint, so light mode
     * darkens it; dark mode uses [prepMark] itself.
     */
    val onPrepTint: Color,
) {
    /** Selected preset card fill. */
    val accentTint: Color get() = tint(accent)

    /**
     * Rest on a timeline: a deeper wash of [accent] than [accentTint], so it still shows on a
     * selected preset card. Work is [accent] itself; the two differ in lightness, not hue.
     */
    val timelineRest: Color get() = lerp(surface, accent, TIMELINE_REST_STRENGTH)

    /**
     * A pale wash of [color] over [surface]: the fill behind a value shown in its phase colour, and
     * the selected state of anything accent-coloured. One recipe, so every tint sits the same depth.
     */
    fun tint(color: Color): Color = lerp(surface, color, TINT_STRENGTH)

    /**
     * The lanes behind a Setup tab: their lines sit between the page and a [line], quieter than
     * either border, so the artwork never competes with the text drawn over it.
     */
    val laneLine: Color get() = lerp(background, line, LANE_LINE_STRENGTH)

    /** Every other lane's fill: a whisper of [surface] over the page. */
    val laneFill: Color get() = lerp(background, surface, LANE_FILL_STRENGTH)

    private companion object {
        const val TINT_STRENGTH = 0.1f
        const val LANE_LINE_STRENGTH = 0.6f
        const val TIMELINE_REST_STRENGTH = 0.24f
    }
}

/**
 * The workout's colours (the timer, its summary, an EMOM), following the theme. The phase colour is
 * the ring, the edge band, the phase word and the primary control, so it still reads from across a
 * room without flooding the screen. Dark is [PhaseColors]' near-black "Gym dark"; light keeps the
 * page's ground and deepens the phases, as Setup's marks do.
 */
@Immutable
data class WorkoutColors(
    val prep: Color,
    val work: Color,
    val rest: Color,
    val done: Color,
    /** The ring and band while paused. A paused primary control fills with [onGround] instead. */
    val paused: Color,
    /** The workout screen's background. */
    val ground: Color,
    /** Fill of the secondary controls (End, Skip, Back, +10s, Repeat), stat tiles, and the ring's track. */
    val control: Color,
    /** Upcoming round dots. */
    val upcoming: Color,
    /** Clock, status, finished round dots, and the labels on [control]. */
    val onGround: Color,
    /** Next up, the resting tap hint, and the phase word while paused. */
    val onGroundDim: Color,
    /** The lanes round the ring: their lines, and every other lane's fill. */
    val laneLine: Color,
    val laneFill: Color,
) {
    /**
     * The label on a phase-coloured or [onGround] fill: ink or white, whichever reads better. Worked
     * out from the fill, since fills crossfade between phases and light mode's are dark.
     */
    fun onFill(fill: Color): Color {
        val lum = fill.luminance()
        val inkContrast = (lum + CONTRAST_OFFSET) / (PhaseColors.ink.luminance() + CONTRAST_OFFSET)
        val whiteContrast = (1f + CONTRAST_OFFSET) / (lum + CONTRAST_OFFSET)
        return if (inkContrast >= whiteContrast) PhaseColors.ink else Color.White
    }

    private companion object {
        /** WCAG's flare term in a contrast ratio. */
        const val CONTRAST_OFFSET = 0.05f
    }
}

/**
 * Gym dark: the workout's fixed colours, which [darkWorkoutColors] uses as they are. The splash and
 * the sheet scrim use them in either theme.
 */
object PhaseColors {
    val prep = Color(0xFFF2A81D)
    val work = Color(0xFFFF5A43)
    val rest = Color(0xFF2FD3A0)
    val done = Color(0xFF5B8CFF)

    /** The ring and band while paused. Too dark for [ink], so a paused primary control fills with [onGround]. */
    val paused = Color(0xFF5E6978)

    /** The workout screen's background. */
    val ground = Color(0xFF0B0D10)

    /** Fill of the secondary controls (End, Skip, Back, +10s, Repeat), stat tiles, and the ring's track. */
    val control = Color(0xFF161A20)

    /** Upcoming round dots. */
    val upcoming = Color(0xFF232B35)

    /** Clock, status, finished round dots, and the labels on [control]. */
    val onGround = Color(0xFFF1F4F8)

    /** Next up, the resting tap hint, and the phase word while paused. */
    val onGroundDim = Color(0xFF97A3B3)

    /** Label on a bright fill; [WorkoutColors.onFill] picks it or white. */
    val ink = Color(0xFF0B0D10)

    /** The lanes round the ring: their lines, and every other lane's fill, a whisper of [control] over the ground. */
    val laneLine = control
    val laneFill = lerp(ground, control, LANE_FILL_STRENGTH)
}

internal val lightPalette = IntervalPalette(
    background = Color(0xFFEEF1F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3E8EF),
    text = Color(0xFF141A22),
    textDim = Color(0xFF5E6978),
    line = Color(0xFFD5DCE6),
    accent = Color(0xFF2456E6),
    onAccent = Color.White,
    prepMark = PhaseColors.prep,
    workMark = Color(0xFFD13A26),
    restMark = Color(0xFF0A5E49),
    onPrepTint = Color(0xFFA86A05),
)

/** Gym dark: the workout's near-black ground carried onto Setup, with its bright phase colours. */
internal val darkPalette = IntervalPalette(
    background = PhaseColors.ground,
    surface = PhaseColors.control,
    surfaceVariant = PhaseColors.upcoming,
    text = PhaseColors.onGround,
    textDim = PhaseColors.onGroundDim,
    line = Color(0xFF2C3541),
    accent = Color(0xFF5B8CFF),
    onAccent = PhaseColors.ink,
    prepMark = PhaseColors.prep,
    workMark = PhaseColors.work,
    restMark = PhaseColors.rest,
    onPrepTint = PhaseColors.prep,
)

internal val darkWorkoutColors = WorkoutColors(
    prep = PhaseColors.prep,
    work = PhaseColors.work,
    rest = PhaseColors.rest,
    done = PhaseColors.done,
    paused = PhaseColors.paused,
    ground = PhaseColors.ground,
    control = PhaseColors.control,
    upcoming = PhaseColors.upcoming,
    onGround = PhaseColors.onGround,
    onGroundDim = PhaseColors.onGroundDim,
    laneLine = PhaseColors.laneLine,
    laneFill = PhaseColors.laneFill,
)

/**
 * The light page under the workout, with the phases deepened past Setup's marks: the phase word is
 * text on the ground, and white labels the primary control filled with each.
 */
internal val lightWorkoutColors = WorkoutColors(
    prep = Color(0xFF966003),
    work = Color(0xFFC4361F),
    rest = lightPalette.restMark,
    done = lightPalette.accent,
    paused = lightPalette.textDim,
    ground = lightPalette.background,
    control = lightPalette.surfaceVariant,
    upcoming = lightPalette.line,
    onGround = lightPalette.text,
    onGroundDim = lightPalette.textDim,
    laneLine = lightPalette.laneLine,
    laneFill = lightPalette.laneFill,
)

/**
 * The app's text styles, named after the design system's type tokens. Barlow Condensed for numbers
 * and headings, Barlow for sentences. Eleven roles; every style is built from the theme's `bodyLarge`,
 * so it keeps the line height and tracking unstyled text gets.
 */
@Immutable
data class IntervalType(
    /** The workout countdown; callers size it to the window with `copy(fontSize, lineHeight)`. */
    val clock: TextStyle,
    /** The biggest value on a screen: the Timer tab's total, the edit sheet's value, Done. */
    val display: TextStyle,
    /** Screen titles: "Timer", "Presets". */
    val title: TextStyle,
    /** A big number with a label: the phase word, summary and week stats, value pills, preset totals. */
    val stat: TextStyle,
    /** A value in a row or on a big button: stepper values, History totals, Start workout, Pause. */
    val value: TextStyle,
    /** Workout control labels and small values: Skip, Back, +10s, a chip's total, the − + × glyphs. */
    val control: TextStyle,
    /** Section titles and lines under a big value: "Presets", "workouts", next up, the Done summary. */
    val section: TextStyle,
    /** Names, list-row titles, button labels and group labels: Barlow SemiBold at body size. */
    val label: TextStyle,
    val body: TextStyle,
    /** Hints, dates and descriptions. */
    val meta: TextStyle,
    /** A small label over a value: "Total", "This week", tab labels. */
    val caption: TextStyle,
)

private fun intervalType(body: TextStyle, condensed: FontFamily): IntervalType {
    fun numerals(size: TextUnit) = body.copy(fontFamily = condensed, fontWeight = FontWeight.ExtraBold, fontSize = size)
    fun heading(size: TextUnit) = body.copy(fontFamily = condensed, fontWeight = FontWeight.SemiBold, fontSize = size)
    return IntervalType(
        clock = numerals(240.sp).copy(lineHeight = 240.sp),
        display = numerals(96.sp).copy(lineHeight = 96.sp, letterSpacing = (-1).sp),
        title = numerals(36.sp).copy(lineHeight = 40.sp),
        stat = numerals(40.sp).copy(lineHeight = 40.sp),
        value = numerals(26.sp).copy(lineHeight = 28.sp),
        control = numerals(22.sp),
        section = heading(20.sp),
        label = body.copy(fontWeight = FontWeight.SemiBold),
        body = body,
        meta = body.copy(fontSize = 14.sp),
        caption = body.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    )
}

private val LocalIntervalPalette = staticCompositionLocalOf { lightPalette }
private val LocalWorkoutColors = staticCompositionLocalOf { lightWorkoutColors }
private val LocalIntervalType = staticCompositionLocalOf<IntervalType> {
    error("IntervalType is provided by IntervalTheme")
}

/** The app's single theme entry point: Barlow for text, Barlow Condensed for numbers and headings. */
@Composable
fun IntervalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) darkPalette else lightPalette
    val workout = if (darkTheme) darkWorkoutColors else lightWorkoutColors
    val barlow = FontFamily(
        Font(Res.font.barlow_regular, FontWeight.Normal),
        Font(Res.font.barlow_semibold, FontWeight.SemiBold),
    )
    val condensed = FontFamily(
        Font(Res.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(Res.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
    )
    // Built once per theme and font set, not on every recomposition. The families compare equal from
    // one composition to the next (Font is cached), so they can key what is built from them.
    val colors = remember(darkTheme) {
        val base = if (darkTheme) {
            darkColorScheme(primary = palette.accent, onPrimary = palette.onAccent)
        } else {
            lightColorScheme(primary = palette.accent, onPrimary = palette.onAccent)
        }
        base.copy(
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.textDim,
            outline = palette.line,
        )
    }
    val typography = remember(barlow) {
        Typography().let { t ->
            t.copy(
                bodyLarge = t.bodyLarge.copy(fontFamily = barlow),
                bodyMedium = t.bodyMedium.copy(fontFamily = barlow),
                bodySmall = t.bodySmall.copy(fontFamily = barlow),
                labelLarge = t.labelLarge.copy(fontFamily = barlow),
                labelMedium = t.labelMedium.copy(fontFamily = barlow),
                labelSmall = t.labelSmall.copy(fontFamily = barlow),
                titleMedium = t.titleMedium.copy(fontFamily = barlow),
            )
        }
    }
    val type = remember(typography, condensed) { intervalType(typography.bodyLarge, condensed) }
    CompositionLocalProvider(
        LocalIntervalPalette provides palette,
        LocalWorkoutColors provides workout,
        LocalIntervalType provides type,
    ) {
        // MaterialTheme provides bodyLarge as the default text style, so unstyled Text is Barlow too.
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

/** Theme accessors, mirroring `MaterialTheme.colorScheme`. */
object IntervalTheme {
    val palette: IntervalPalette
        @Composable @ReadOnlyComposable
        get() = LocalIntervalPalette.current

    /** The workout's colours: the timer, its summary, an EMOM. */
    val workout: WorkoutColors
        @Composable @ReadOnlyComposable
        get() = LocalWorkoutColors.current

    val type: IntervalType
        @Composable @ReadOnlyComposable
        get() = LocalIntervalType.current
}

/** How far the lanes' fill leans from the page towards a card, in every palette. */
private const val LANE_FILL_STRENGTH = 0.45f
