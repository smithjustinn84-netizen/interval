package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the contrast the Interval design system promises (WCAG 2: 4.5:1 for text, 3:1 for marks).
 * Every text pair on both screens passes; there are no documented exceptions left.
 */
class ThemeContrastTest {
    private val palettes = mapOf("light" to lightPalette, "dark" to darkPalette)

    private fun contrast(foreground: Color, background: Color): Double {
        val fg = foreground.compositeOver(background).luminance() + 0.05
        val bg = background.luminance() + 0.05
        return max(fg, bg) / min(fg, bg)
    }

    private fun assertContrast(minimum: Double, foreground: Color, background: Color, pair: String) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= minimum, "$pair is ${(ratio * 100).toInt() / 100.0}:1, needs $minimum:1")
    }

    @Test
    fun `setup text reads on every setup ground`() {
        palettes.forEach { (theme, p) ->
            val grounds = mapOf(
                "background" to p.background,
                "surface" to p.surface,
                "surfaceVariant" to p.surfaceVariant,
                "accentTint" to p.accentTint,
            )
            grounds.forEach { (name, ground) ->
                assertContrast(4.5, p.text, ground, "$theme text on $name")
                assertContrast(4.5, p.textDim, ground, "$theme textDim on $name")
            }
        }
    }

    @Test
    fun `labels read on accent`() {
        palettes.forEach { (theme, p) -> assertContrast(4.5, p.onAccent, p.accent, "$theme onAccent on accent") }
    }

    @Test
    fun `accent marks stand out from setup grounds`() {
        palettes.forEach { (theme, p) ->
            assertContrast(3.0, p.accent, p.background, "$theme accent on background")
            assertContrast(3.0, p.accent, p.surface, "$theme accent on surface")
            assertContrast(3.0, p.accent, p.accentTint, "$theme accent on accentTint")
        }
    }

    @Test
    fun `workout text reads where the system says it does`() {
        listOf(PhaseColors.ground, PhaseColors.control).forEach { ground ->
            assertContrast(4.5, PhaseColors.onGround, ground, "onGround on $ground")
            assertContrast(4.5, PhaseColors.onGroundDim, ground, "onGroundDim on $ground")
        }
        // The phase word and the ring sit on the ground; ink labels the primary control filled with them.
        listOf(PhaseColors.prep, PhaseColors.work, PhaseColors.rest, PhaseColors.done).forEach { phase ->
            assertContrast(4.5, phase, PhaseColors.ground, "$phase on ground")
            assertContrast(4.5, PhaseColors.ink, phase, "ink on $phase")
        }
        // Paused, the primary control fills with onGround, since ink fails on the paused grey.
        assertContrast(4.5, PhaseColors.ink, PhaseColors.onGround, "ink on onGround")
        assertContrast(3.0, PhaseColors.paused, PhaseColors.ground, "paused ring on ground")
    }

    @Test
    fun `values read on their phase tints`() {
        palettes.forEach { (theme, p) ->
            assertContrast(3.0, p.workMark, p.tint(p.workMark), "$theme work on its tint")
            assertContrast(3.0, p.restMark, p.tint(p.restMark), "$theme rest on its tint")
            assertContrast(3.0, p.onPrepTint, p.tint(p.prepMark), "$theme onPrepTint on the prep tint")
            assertContrast(4.5, p.text, p.tint(p.workMark), "$theme text on the work tint")
        }
    }

    @Test
    fun `accent tint matches the design system`() {
        assertEquals(Color(0xFFE8EFFF).toArgb(), lightPalette.accentTint.toArgb())
        assertEquals(Color(0xFF1C2433).toArgb(), darkPalette.accentTint.toArgb())
    }
}
