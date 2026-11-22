package dev.justinsmith.interval.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/**
 * Zone-reactive telemetry parameters driving the sensor pulse shader: [beatsPerSecond] sets the continuous
 * ripple cadence, [waveSpan] controls how many concentric wavefronts travel across the badge at once,
 * [intensity] scales ring contrast across zones, and [active] blends between idle breathing (0) and live BPM (1).
 */
@Immutable
internal data class PulseZoneProfile(
    val beatsPerSecond: Float,
    val waveSpan: Float,
    val intensity: Float,
    val active: Float,
)

/** Maps live [bpm] and [zone] (or idle/recording when absent) to its [PulseZoneProfile]. */
internal fun pulseZoneProfile(bpm: Int?, zone: HeartRateZone?, recording: Boolean): PulseZoneProfile {
    if (bpm == null || zone == null || recording) {
        return PulseZoneProfile(
            beatsPerSecond = IDLE_BREATH_HZ,
            waveSpan = IDLE_WAVE_SPAN,
            intensity = IDLE_INTENSITY,
            active = 0f,
        )
    }
    return PulseZoneProfile(
        beatsPerSecond = bpm.coerceIn(MIN_PULSE_BPM, MAX_PULSE_BPM) / SECONDS_PER_MINUTE,
        waveSpan = zone.waveSpan,
        intensity = zone.intensity,
        active = 1f,
    )
}

private val HeartRateZone.waveSpan: Float
    get() = when (this) {
        HeartRateZone.Zone1 -> ZONE1_WAVE_SPAN
        HeartRateZone.Zone2 -> ZONE2_WAVE_SPAN
        HeartRateZone.Zone3 -> ZONE3_WAVE_SPAN
        HeartRateZone.Zone4 -> ZONE4_WAVE_SPAN
        HeartRateZone.Zone5 -> ZONE5_WAVE_SPAN
    }

private val HeartRateZone.intensity: Float
    get() = when (this) {
        HeartRateZone.Zone1 -> ZONE1_INTENSITY
        HeartRateZone.Zone2 -> ZONE2_INTENSITY
        HeartRateZone.Zone3 -> ZONE3_INTENSITY
        HeartRateZone.Zone4 -> ZONE4_INTENSITY
        HeartRateZone.Zone5 -> ZONE5_INTENSITY
    }

/**
 * Continuously integrated beat/breath phase (1.0 per beat or breath cycle), read inside drawing rather than
 * composition so animation frames never trigger recomposition. Changing [beatsPerSecond] smoothly accelerates or
 * slows the phase without jumping. Uses an infinite animation frame clock so Compose UI tests never stall on it,
 * and rests at [INITIAL_PULSE_PHASE] under reduced motion.
 */
@Composable
internal fun rememberPulsePhase(beatsPerSecond: Float, paused: Boolean = false): FloatState {
    val phase = remember { mutableFloatStateOf(INITIAL_PULSE_PHASE) }
    val still = prefersReducedMotion()
    val currentRate by rememberUpdatedState(beatsPerSecond)
    LaunchedEffect(still, paused) {
        if (still) {
            phase.floatValue = INITIAL_PULSE_PHASE
            return@LaunchedEffect
        }
        if (paused) return@LaunchedEffect
        var lastMillis = withInfiniteAnimationFrameMillis { it }
        while (true) {
            val nowMillis = withInfiniteAnimationFrameMillis { it }
            val dtSeconds = (nowMillis - lastMillis).coerceIn(0L, MAX_FRAME_DELTA_MILLIS) / MILLIS_PER_SECOND
            lastMillis = nowMillis
            phase.floatValue = (phase.floatValue + dtSeconds * currentRate) % PHASE_WRAP
        }
    }
    return phase
}

/**
 * Draws the procedural live-BPM concentric ripple shader (or a matching pure-Canvas fallback when the platform
 * has no runtime shader) behind the sensor badge's content. Dynamic inputs are read only inside drawing.
 */
internal fun Modifier.sensorPulseWave(
    phase: () -> Float,
    color: () -> Color,
    waveSpan: () -> Float,
    intensity: () -> Float,
    active: () -> Float,
    centerX: Dp,
    centered: Boolean,
    program: ShaderProgram?,
): Modifier = drawWithCache {
    val coreRadius = Sizes.heartBadgeDot.toPx() / 2f
    val strokeWidth = PULSE_STROKE_DP.dp.toPx()
    val originOffset = centerX.toPx()
    val cx = when {
        centered -> size.width / 2f
        layoutDirection == LayoutDirection.Rtl -> size.width - originOffset
        else -> originOffset
    }
    val cy = size.height / 2f
    val reach = if (centered) {
        min(size.width, size.height) / 2f
    } else {
        val toFarEdge = if (layoutDirection == LayoutDirection.Rtl) cx else size.width - cx
        min(toFarEdge, size.height * RIPPLE_REACH_HEIGHT_RATIO)
    }
    val halfMin = min(size.width, size.height) / 2f
    val idleMaxRadius = min(IDLE_MAX_RADIUS_DP.dp.toPx(), halfMin - strokeWidth).coerceAtLeast(1f)
    val idleMinRadius = min(IDLE_MIN_RADIUS_DP.dp.toPx(), idleMaxRadius * IDLE_MIN_RATIO)
    val geometry = PulseGeometry(
        center = Offset(cx, cy),
        maxRadius = reach.coerceAtLeast(coreRadius + 1f),
        coreRadius = coreRadius,
        idleMinRadius = idleMinRadius,
        idleMaxRadius = idleMaxRadius,
        strokeWidth = strokeWidth,
    )
    onDrawBehind {
        val currentPhase = phase()
        val currentWaveSpan = waveSpan()
        val currentIntensity = intensity()
        val currentActive = active()
        val currentColor = color()
        if (program != null) {
            drawPulseShader(
                program = program,
                geometry = geometry,
                phase = currentPhase,
                waveSpan = currentWaveSpan,
                intensity = currentIntensity,
                active = currentActive,
                color = currentColor,
            )
        } else {
            drawPulseFallback(
                geometry = geometry,
                phase = currentPhase,
                waveSpan = currentWaveSpan,
                intensity = currentIntensity,
                active = currentActive,
                color = currentColor,
            )
        }
    }
}

private class PulseGeometry(
    val center: Offset,
    val maxRadius: Float,
    val coreRadius: Float,
    val idleMinRadius: Float,
    val idleMaxRadius: Float,
    val strokeWidth: Float,
)

private fun DrawScope.drawPulseShader(
    program: ShaderProgram,
    geometry: PulseGeometry,
    phase: Float,
    waveSpan: Float,
    intensity: Float,
    active: Float,
    color: Color,
) {
    program.set("uCenter", geometry.center.x, geometry.center.y)
    program.set("uMaxRadius", geometry.maxRadius)
    program.set("uCoreRadius", geometry.coreRadius)
    program.set("uIdleMinRadius", geometry.idleMinRadius)
    program.set("uIdleMaxRadius", geometry.idleMaxRadius)
    program.set("uPhase", phase)
    program.set("uWaveSpan", waveSpan)
    program.set("uIntensity", intensity)
    program.set("uActive", active)
    program.set("uStrokeWidth", geometry.strokeWidth)
    program.set("uColor", color)
    drawRect(program.brush())
}

/** Pure Compose Canvas fallback matching [SENSOR_PULSE_SHADER] when runtime shaders are unavailable (Android < 13). */
private fun DrawScope.drawPulseFallback(
    geometry: PulseGeometry,
    phase: Float,
    waveSpan: Float,
    intensity: Float,
    active: Float,
    color: Color,
) {
    if (active > VISIBILITY_EPSILON) {
        drawActiveFallback(geometry, phase, waveSpan, intensity, active, color)
    }
    val idleWeight = 1f - active
    if (idleWeight > VISIBILITY_EPSILON) {
        drawIdleFallback(geometry, phase, intensity, idleWeight, color)
    }
}

private fun DrawScope.drawActiveFallback(
    geometry: PulseGeometry,
    phase: Float,
    waveSpan: Float,
    intensity: Float,
    active: Float,
    color: Color,
) {
    val p = phase - floor(phase)
    val beat = smoothstep(0f, BEAT_PEAK_PHASE, p) * (1f - smoothstep(BEAT_PEAK_PHASE, BEAT_END_PHASE, p))
    val coreR = geometry.coreRadius * (1f + BEAT_SCALE_DELTA * beat)
    drawCircle(color.copy(alpha = color.alpha * active), coreR, geometry.center)

    val travel = (geometry.maxRadius - geometry.coreRadius).coerceAtLeast(1f)
    val span = waveSpan.coerceAtLeast(1f)
    val intensityScale = RIPPLE_ALPHA_BASE + RIPPLE_ALPHA_GAIN * intensity
    for (i in 0..<MAX_WAVES) {
        val age = (p + i) / span
        val radius = geometry.coreRadius + age.coerceAtLeast(POW_EPSILON).pow(SPREAD_EXPONENT) * travel
        if (age >= 1f || radius <= coreR) continue
        val width = (geometry.strokeWidth * (1f - STROKE_TAPER * age)).coerceAtLeast(1f)
        val emerge = smoothstep(0f, EMERGE_PHASE, age)
        val atten = (1f - age).coerceAtLeast(POW_EPSILON).pow(ATTENUATION_EXPONENT)
        val alpha = (emerge * atten * intensityScale * active).coerceIn(0f, 1f)
        if (alpha > VISIBILITY_EPSILON) {
            drawCircle(
                color = color.copy(alpha = color.alpha * alpha),
                radius = radius,
                center = geometry.center,
                style = Stroke(width),
            )
        }
    }
}

private fun DrawScope.drawIdleFallback(
    geometry: PulseGeometry,
    phase: Float,
    intensity: Float,
    idleWeight: Float,
    color: Color,
) {
    val breath = HALF - HALF * cos(phase * TWO_PI)
    val idleRadius = geometry.idleMinRadius + (geometry.idleMaxRadius - geometry.idleMinRadius) * breath
    val idleAlpha = ((IDLE_ALPHA_MIN + (IDLE_ALPHA_MAX - IDLE_ALPHA_MIN) * breath) * intensity * idleWeight)
        .coerceIn(0f, 1f)
    if (idleAlpha > VISIBILITY_EPSILON) {
        drawCircle(
            color = color.copy(alpha = color.alpha * idleAlpha),
            radius = idleRadius,
            center = geometry.center,
            style = Stroke((geometry.strokeWidth * IDLE_STROKE_RATIO).coerceAtLeast(1f)),
        )
    }
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (SMOOTHSTEP_COEFF - 2f * t)
}

/**
 * Shared SkSL / AGSL procedural pulse shader: a sharp, systolic-beating core dot emitting crisp anti-aliased
 * concentric acoustic ripples synchronized to live BPM, smoothly attenuating outward, and crossfading into a
 * calm concentric breathing ring when no BLE sensor is connected. Returns premultiplied RGBA.
 */
internal const val SENSOR_PULSE_SHADER: String = """
uniform float2 uCenter;
uniform float uMaxRadius;
uniform float uCoreRadius;
uniform float uIdleMinRadius;
uniform float uIdleMaxRadius;
uniform float uPhase;
uniform float uWaveSpan;
uniform float uIntensity;
uniform float uActive;
uniform float uStrokeWidth;
uniform float4 uColor;

half4 main(float2 fragCoord) {
    float dist = length(fragCoord - uCenter);
    float aa = 0.65;

    float p = fract(uPhase);
    float beat = smoothstep(0.0, 0.09, p) * (1.0 - smoothstep(0.09, 0.36, p));
    float coreR = uCoreRadius * (1.0 + 0.22 * beat);
    float coreAlpha = 1.0 - smoothstep(coreR - aa, coreR + aa, dist);

    float rippleAlpha = 0.0;
    float travel = max(uMaxRadius - uCoreRadius, 1.0);
    float span = max(uWaveSpan, 1.0);
    float intensityScale = 0.28 + 0.56 * uIntensity;
    for (int i = 0; i < 4; i++) {
        float age = (p + float(i)) / span;
        if (age < 1.0) {
            float spread = pow(max(age, 0.0001), 0.88);
            float radius = uCoreRadius + spread * travel;
            float halfW = max(aa, uStrokeWidth * (1.0 - 0.30 * age) * 0.5);
            float ring = 1.0 - smoothstep(halfW - aa, halfW + aa, abs(dist - radius));
            float emerge = smoothstep(0.0, 0.07, age);
            float atten = pow(max(1.0 - age, 0.0001), 1.75);
            rippleAlpha = max(rippleAlpha, ring * emerge * atten * intensityScale);
        }
    }

    float outsideCore = smoothstep(coreR - aa * 0.5, coreR + aa, dist);
    float activeAlpha = max(coreAlpha, rippleAlpha * outsideCore);

    float breath = 0.5 - 0.5 * cos(uPhase * 6.2831853);
    float idleRadius = mix(uIdleMinRadius, uIdleMaxRadius, breath);
    float idleHalfW = max(aa, uStrokeWidth * 0.45);
    float idleRing = 1.0 - smoothstep(idleHalfW - aa, idleHalfW + aa, abs(dist - idleRadius));
    float idleAlpha = idleRing * mix(0.20, 0.54, breath) * uIntensity;

    float finalAlpha = clamp(mix(idleAlpha, activeAlpha, uActive), 0.0, 1.0) * uColor.a;
    return half4(uColor.rgb * finalAlpha, finalAlpha);
}
"""

internal const val INITIAL_PULSE_PHASE = 0.24f
internal const val IDLE_BREATH_HZ = 0.28f

private const val MIN_PULSE_BPM = 40
private const val MAX_PULSE_BPM = 220
private const val SECONDS_PER_MINUTE = 60f
private const val MILLIS_PER_SECOND = 1000f
private const val MAX_FRAME_DELTA_MILLIS = 100L
private const val PHASE_WRAP = 1024f

private const val ZONE1_WAVE_SPAN = 1.35f
private const val ZONE2_WAVE_SPAN = 1.85f
private const val ZONE3_WAVE_SPAN = 2.45f
private const val ZONE4_WAVE_SPAN = 3.10f
private const val ZONE5_WAVE_SPAN = 3.75f

private const val ZONE1_INTENSITY = 0.48f
private const val ZONE2_INTENSITY = 0.58f
private const val ZONE3_INTENSITY = 0.70f
private const val ZONE4_INTENSITY = 0.84f
private const val ZONE5_INTENSITY = 0.96f

private const val IDLE_WAVE_SPAN = 1.0f
private const val IDLE_INTENSITY = 0.42f

private const val PULSE_STROKE_DP = 1.4f
private const val IDLE_MIN_RADIUS_DP = 11.5f
private const val IDLE_MAX_RADIUS_DP = 16.0f
private const val IDLE_MIN_RATIO = 0.72f
private const val RIPPLE_REACH_HEIGHT_RATIO = 1.45f

private const val MAX_WAVES = 4
private const val BEAT_PEAK_PHASE = 0.09f
private const val BEAT_END_PHASE = 0.36f
private const val BEAT_SCALE_DELTA = 0.22f
private const val SPREAD_EXPONENT = 0.88f
private const val STROKE_TAPER = 0.30f
private const val EMERGE_PHASE = 0.07f
private const val ATTENUATION_EXPONENT = 1.75f
private const val RIPPLE_ALPHA_BASE = 0.28f
private const val RIPPLE_ALPHA_GAIN = 0.56f
private const val IDLE_ALPHA_MIN = 0.20f
private const val IDLE_ALPHA_MAX = 0.54f
private const val IDLE_STROKE_RATIO = 0.90f
private const val POW_EPSILON = 0.0001f
private const val VISIBILITY_EPSILON = 0.004f
private const val HALF = 0.5f
private const val SMOOTHSTEP_COEFF = 3f
private const val TWO_PI = (2.0 * PI).toFloat()
