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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
)

/** Full-screen workout colors. The same in light and dark: the whole screen is the signal. */
object PhaseColors {
    val prep = Color(0xFFE59A12)
    val work = Color(0xFFE8402F)
    val rest = Color(0xFF159A72)
    val done = Color(0xFF2456E6)
    val paused = Color(0xFF2A3038)

    fun of(phase: Phase): Color = when (phase) {
        Phase.Prep -> prep
        Phase.Work -> work
        Phase.Rest -> rest
        Phase.Done -> done
    }
}

private val lightPalette = IntervalPalette(
    background = Color(0xFFEEF1F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3E8EF),
    text = Color(0xFF141A22),
    textDim = Color(0xFF5E6978),
    line = Color(0xFFD5DCE6),
    accent = Color(0xFF2456E6),
)

private val darkPalette = IntervalPalette(
    background = Color(0xFF0F1318),
    surface = Color(0xFF1A2028),
    surfaceVariant = Color(0xFF232B35),
    text = Color(0xFFF1F4F8),
    textDim = Color(0xFF97A3B3),
    line = Color(0xFF2C3541),
    accent = Color(0xFF356AFF),
)

private val localPalette = staticCompositionLocalOf { lightPalette }
private val localCondensed = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

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
        darkColorScheme(primary = palette.accent, onPrimary = Color.White)
    } else {
        lightColorScheme(primary = palette.accent, onPrimary = Color.White)
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
    CompositionLocalProvider(localPalette provides palette, localCondensed provides condensed) {
        // MaterialTheme provides bodyLarge as the default text style, so unstyled Text is Barlow too.
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

/** Theme accessors, mirroring `MaterialTheme.colorScheme`. */
object IntervalTheme {
    val palette: IntervalPalette
        @Composable @ReadOnlyComposable
        get() = localPalette.current

    val condensed: FontFamily
        @Composable @ReadOnlyComposable
        get() = localCondensed.current
}
