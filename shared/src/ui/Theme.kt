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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.barlow_condensed_extrabold
import dev.justinsmith.interval.resources.barlow_condensed_semibold
import dev.justinsmith.interval.resources.barlow_regular
import dev.justinsmith.interval.resources.barlow_semibold
import dev.justinsmith.interval.timer.Phase
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
) {
    /** Selected preset card fill. */
    val accentTint: Color get() = lerp(surface, accent, 0.1f)
}

/** Full-screen workout colors. The same in light and dark: the whole screen is the signal. */
object PhaseColors {
    val prep = Color(0xFFE59A12)
    val work = Color(0xFFE8402F)
    val rest = Color(0xFF159A72)
    val done = Color(0xFF2456E6)
    val paused = Color(0xFF2A3038)

    /** Clock, Done, progress fill, finished round dots, and the fill of the primary control. */
    val onPhase = Color.White

    /** Phase label and the Done summary. */
    val onPhase90 = Color.White.copy(alpha = 0.9f)

    /** Status and next-up lines. */
    val onPhase85 = Color.White.copy(alpha = 0.85f)

    /** The resting tap hint. */
    val onPhase65 = Color.White.copy(alpha = 0.65f)

    /** Upcoming round dots and the ring around the current one. */
    val dotHalo = Color.White.copy(alpha = 0.35f)
    val progressTrack = Color.White.copy(alpha = 0.25f)

    /** Fill of the secondary controls: End, Skip, Back, +10s, Restart. */
    val glass = Color.White.copy(alpha = 0.18f)

    /** Label on an [onPhase] fill; only ever on white, so it doesn't follow the theme. */
    val ink = Color(0xFF141A22)

    fun of(phase: Phase): Color = when (phase) {
        Phase.Prep -> prep
        Phase.Work -> work
        Phase.Rest -> rest
        Phase.Done -> done
    }
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
)

internal val darkPalette = IntervalPalette(
    background = Color(0xFF0F1318),
    surface = Color(0xFF1A2028),
    surfaceVariant = Color(0xFF232B35),
    text = Color(0xFFF1F4F8),
    textDim = Color(0xFF97A3B3),
    line = Color(0xFF2C3541),
    accent = Color(0xFF356AFF),
    onAccent = Color.White,
)

/**
 * The app's text styles, named after the design system's type tokens. Barlow Condensed for numbers
 * and headings, Barlow for sentences. Every style is built from the theme's `bodyLarge` (or
 * `labelLarge` for button labels), so it keeps the line height and tracking unstyled text gets.
 */
@Immutable
data class IntervalType(
    /** The workout countdown; callers size it to the window with `copy(fontSize, lineHeight)`. */
    val clock: TextStyle,
    val done: TextStyle,
    val phaseLabelLandscape: TextStyle,
    val phaseLabel: TextStyle,
    val screenTitle: TextStyle,
    val stepperValue: TextStyle,
    val startLabel: TextStyle,
    val primaryControl: TextStyle,
    val sectionTitle: TextStyle,
    val control: TextStyle,
    val nextUp: TextStyle,
    val summary: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val total: TextStyle,
    val meta: TextStyle,
    val buttonLabel: TextStyle,
    val presetDescription: TextStyle,
    val hint: TextStyle,
    /** The − and + on a stepper. */
    val stepperGlyph: TextStyle,
    /** The × on a custom preset. */
    val deleteGlyph: TextStyle,
    /** The Timer tab's big session total. */
    val heroTotal: TextStyle,
    /** The value being edited in the edit sheet. */
    val sheetValue: TextStyle,
    /** Tab labels in the navigation bar. */
    val navLabel: TextStyle,
    /** "Built-in", "Today", "Cues": the label over a group of list rows. */
    val groupLabel: TextStyle,
)

private fun intervalType(body: TextStyle, label: TextStyle, condensed: FontFamily): IntervalType {
    fun numerals(size: TextUnit) = body.copy(fontFamily = condensed, fontWeight = FontWeight.ExtraBold, fontSize = size)
    fun heading(size: TextUnit) = body.copy(fontFamily = condensed, fontWeight = FontWeight.SemiBold, fontSize = size)
    return IntervalType(
        clock = numerals(240.sp).copy(lineHeight = 240.sp),
        done = numerals(96.sp),
        phaseLabelLandscape = heading(40.sp),
        phaseLabel = heading(34.sp),
        screenTitle = numerals(32.sp),
        stepperValue = numerals(27.sp),
        startLabel = numerals(26.sp),
        primaryControl = numerals(22.sp),
        sectionTitle = numerals(20.sp),
        control = numerals(19.sp),
        nextUp = body.copy(fontSize = 20.sp),
        summary = body.copy(fontSize = 18.sp),
        cardTitle = body.copy(fontWeight = FontWeight.SemiBold),
        body = body,
        total = body.copy(fontSize = 15.sp),
        meta = body.copy(fontSize = 14.sp),
        buttonLabel = label.copy(fontWeight = FontWeight.SemiBold),
        presetDescription = body.copy(fontSize = 13.5.sp),
        hint = body.copy(fontSize = 13.sp),
        stepperGlyph = body.copy(fontSize = 24.sp),
        deleteGlyph = body.copy(fontSize = 18.sp),
        heroTotal = numerals(64.sp).copy(lineHeight = 64.sp),
        sheetValue = numerals(80.sp).copy(lineHeight = 80.sp),
        navLabel = body.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
        groupLabel = body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

private val LocalIntervalPalette = staticCompositionLocalOf { lightPalette }
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
    val barlow = FontFamily(
        Font(Res.font.barlow_regular, FontWeight.Normal),
        Font(Res.font.barlow_semibold, FontWeight.SemiBold),
    )
    val condensed = FontFamily(
        Font(Res.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(Res.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
    )
    val base = if (darkTheme) {
        darkColorScheme(primary = palette.accent, onPrimary = palette.onAccent)
    } else {
        lightColorScheme(primary = palette.accent, onPrimary = palette.onAccent)
    }
    val colors = base.copy(
        background = palette.background,
        onBackground = palette.text,
        surface = palette.surface,
        onSurface = palette.text,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.textDim,
        outline = palette.line,
    )
    val typography = Typography().let { t ->
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
    val type = intervalType(typography.bodyLarge, typography.labelLarge, condensed)
    CompositionLocalProvider(LocalIntervalPalette provides palette, LocalIntervalType provides type) {
        // MaterialTheme provides bodyLarge as the default text style, so unstyled Text is Barlow too.
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

/** Theme accessors, mirroring `MaterialTheme.colorScheme`. */
object IntervalTheme {
    val palette: IntervalPalette
        @Composable @ReadOnlyComposable
        get() = LocalIntervalPalette.current

    val type: IntervalType
        @Composable @ReadOnlyComposable
        get() = LocalIntervalType.current
}
