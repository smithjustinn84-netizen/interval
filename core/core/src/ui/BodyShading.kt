package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.hypot

// Theme-aware studio directional lighting and volumetric shading helpers for the sculpted athletic figure.
// Light falls consistently from an elevated front-top key light across light, dark, workout, and share-card surfaces.

/**
 * Derived studio lighting stops around [ExerciseDemoColors.body] and [ExerciseDemoColors.far], updated in place
 * inside [BodyPen] to keep per-frame rendering lean across light, dark, workout, and watermark surfaces.
 */
@Suppress("detekt.MagicNumber") // Calibrated tonal shading ratios for light-on-dark and dark-on-light surfaces.
internal class BodyTone {
    var bodyLit = Color.Transparent
        private set
    var bodyMid = Color.Transparent
        private set
    var bodyShade = Color.Transparent
        private set
    var farLit = Color.Transparent
        private set
    var farMid = Color.Transparent
        private set
    var farShade = Color.Transparent
        private set
    var rim = Color.Transparent
        private set
    var occlusion = Color.Transparent
        private set
    var shoeSole = Color.Transparent
        private set
    var isLightBody = false
        private set

    private var lastBody = Color.Unspecified
    private var lastFar = Color.Unspecified

    fun update(body: Color, far: Color) {
        if (body == lastBody && far == lastFar) return
        lastBody = body
        lastFar = far
        isLightBody = body.luminance() > MID_LUMINANCE
        bodyMid = body
        farMid = far
        if (isLightBody) {
            // Warm ivory/alabaster figure on deep pine ground: bright top-front rim, sculpted warm-dim core shadow.
            bodyLit = lerp(body, Color.White, LIGHT_BODY_HIGHLIGHT)
            bodyShade = lerp(body, far, LIGHT_BODY_SHADE)
            farLit = lerp(far, body, FAR_HIGHLIGHT_LIGHT)
            farShade = lerp(far, Color.Black, FAR_SHADE_DARK)
            rim = Color.White.copy(alpha = LIGHT_RIM_ALPHA)
            occlusion = Color.Black.copy(alpha = LIGHT_OCCLUSION_ALPHA)
            shoeSole = lerp(body, far, SOLE_LIGHT_MIX)
        } else {
            // Deep pine-ink figure on warm parchment ground: satin highlight along lit contours, deep ink core shade.
            bodyLit = lerp(body, Color.White, DARK_BODY_HIGHLIGHT)
            bodyShade = lerp(body, Color.Black, DARK_BODY_SHADE)
            farLit = lerp(far, Color.White, FAR_HIGHLIGHT_DARK)
            farShade = lerp(far, body, FAR_SHADE_LIGHT)
            rim = Color.White.copy(alpha = DARK_RIM_ALPHA)
            occlusion = Color.Black.copy(alpha = DARK_OCCLUSION_ALPHA)
            shoeSole = lerp(body, Color.White, SOLE_DARK_MIX)
        }
    }

    private companion object {
        const val MID_LUMINANCE = 0.5f
        const val LIGHT_BODY_HIGHLIGHT = 0.24f
        const val LIGHT_BODY_SHADE = 0.36f
        const val FAR_HIGHLIGHT_LIGHT = 0.14f
        const val FAR_SHADE_DARK = 0.24f
        const val LIGHT_RIM_ALPHA = 0.32f
        const val LIGHT_OCCLUSION_ALPHA = 0.34f
        const val SOLE_LIGHT_MIX = 0.45f

        const val DARK_BODY_HIGHLIGHT = 0.22f
        const val DARK_BODY_SHADE = 0.26f
        const val FAR_HIGHLIGHT_DARK = 0.16f
        const val FAR_SHADE_LIGHT = 0.28f
        const val DARK_RIM_ALPHA = 0.18f
        const val DARK_OCCLUSION_ALPHA = 0.26f
        const val SOLE_DARK_MIX = 0.28f
    }
}

/**
 * Fills [path] with studio cylindrical shading across the axis from [a] to [b] (cross-section half-width [radius]).
 * Uses [pen]'s blend mode when cutting cutaway halos, flat tone in Thumb mode, and continuous C-infinity smooth
 * directional gradients in Detail and Compact modes so rotating limbs never flip or pop.
 */
internal fun DrawScope.fillSculptedLimb(
    pen: BodyPen,
    path: Path,
    a: Offset,
    b: Offset,
    radius: Float,
) {
    if (pen.flat) {
        drawPath(path, pen.color, blendMode = pen.blend)
        return
    }
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len = hypot(dx, dy)
    if (len <= MIN_AXIS) {
        fillSculptedForm(pen, path, (a + b) * HALF, radius)
        return
    }
    val px = -dy / len
    val py = dx / len
    val align = (px * STUDIO_LIGHT_X + py * STUDIO_LIGHT_Y).coerceIn(-1f, 1f)
    val t = (align + 1f) * HALF
    val lit = if (pen.isFar) pen.tone.farLit else pen.tone.bodyLit
    val mid = if (pen.isFar) pen.tone.farMid else pen.tone.bodyMid
    val shade = if (pen.isFar) pen.tone.farShade else pen.tone.bodyShade
    val startColor = lerp(shade, lit, t)
    val endColor = lerp(lit, shade, t)
    val cx = (a.x + b.x) * HALF
    val cy = (a.y + b.y) * HALF
    val span = radius * GRADIENT_REACH
    val brush = Brush.linearGradient(
        0f to startColor,
        HALF to mid,
        1f to endColor,
        start = Offset(cx + px * span, cy + py * span),
        end = Offset(cx - px * span, cy - py * span),
    )
    drawPath(path, brush)
}

/**
 * Fills a compact rounded anatomical mass (head, fist, shoe) centered near [center] with directional studio shading
 * along the elevated front-top key light vector.
 */
internal fun DrawScope.fillSculptedForm(
    pen: BodyPen,
    path: Path,
    center: Offset,
    radius: Float,
) {
    if (pen.flat) {
        drawPath(path, pen.color, blendMode = pen.blend)
        return
    }
    val lit = if (pen.isFar) pen.tone.farLit else pen.tone.bodyLit
    val mid = if (pen.isFar) pen.tone.farMid else pen.tone.bodyMid
    val shade = if (pen.isFar) pen.tone.farShade else pen.tone.bodyShade
    val span = radius.coerceAtLeast(MIN_AXIS) * GRADIENT_REACH
    val brush = Brush.linearGradient(
        0f to lit,
        FORM_MID_STOP to mid,
        1f to shade,
        start = Offset(center.x + STUDIO_LIGHT_X * span, center.y + STUDIO_LIGHT_Y * span),
        end = Offset(center.x - STUDIO_LIGHT_X * span, center.y - STUDIO_LIGHT_Y * span),
    )
    drawPath(path, brush)
}

/**
 * Draws a whisper-thin specular rim stroke along a key-lit silhouette contour in Detail/Compact modes (skipped when
 * cutting, outlining, on recessed far limbs, or in Thumb mode).
 */
internal fun DrawScope.drawStudioRim(pen: BodyPen, path: Path, strokePx: Float) {
    if (pen.flat || pen.isFar || strokePx <= 0f) return
    drawPath(
        path,
        pen.tone.rim,
        style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * Soft directional occlusion shadow cast onto recessed anatomy (far thigh emerging from behind the pelvis, deep hip
 * flexion fold, or kettlebell resting against the forearm).
 */
internal fun DrawScope.drawBodyOcclusion(
    pen: BodyPen,
    center: Offset,
    radius: Float,
    strength: Float = 1f,
) {
    if (pen.flat || radius <= MIN_AXIS) return
    val tint = pen.tone.occlusion
    val toneScale = if (pen.tone.isLightBody) LIGHT_BODY_OCCLUSION_SCALE else 1f
    val alpha = (tint.alpha * strength * toneScale).coerceIn(0f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to tint.copy(alpha = alpha),
            OCCLUSION_MID to tint.copy(alpha = alpha * OCCLUSION_MID_ALPHA),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * Elevated front-top studio key light unit direction in Y-down screen coordinates (~55 deg elevation, facing right).
 */
internal const val STUDIO_LIGHT_X = 0.56f
internal const val STUDIO_LIGHT_Y = -0.828f

private const val HALF = 0.5f
private const val MIN_AXIS = 0.5f
private const val GRADIENT_REACH = 1.15f
private const val FORM_MID_STOP = 0.52f
private const val OCCLUSION_MID = 0.55f
private const val OCCLUSION_MID_ALPHA = 0.35f
private const val LIGHT_BODY_OCCLUSION_SCALE = 0.58f
