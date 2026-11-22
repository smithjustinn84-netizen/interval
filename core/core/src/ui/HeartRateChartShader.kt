package dev.justinsmith.interval.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.justinsmith.interval.platform.HeartRate
import kotlin.math.pow

/**
 * Unclamped canvas Y coordinates (in pixels, smaller Y = higher BPM) for the four boundaries separating
 * Zones 1–5. Keeping off-screen cutoffs unclamped lets the shader classify the entire visible span accurately
 * even when a workout stays within a single upper or lower training zone.
 */
@Immutable
internal data class HeartChartThresholdsY(
    val zone2: Float,
    val zone3: Float,
    val zone4: Float,
    val zone5: Float,
)

/** Maps [bpm] linearly into canvas Y coordinates without clamping, matching the chart's stroke inset. */
internal fun heartChartY(bpm: Float, minY: Float, maxY: Float, height: Float, strokeWidth: Float): Float {
    val span = (maxY - minY).coerceAtLeast(MIN_BPM_SPAN_EPSILON)
    val share = (bpm - minY) / span
    return strokeWidth / 2f + (height - strokeWidth) * (1f - share)
}

/** Maps [bpm] into canvas Y coordinates clamped inside the drawable stroke band. */
internal fun clampedHeartChartY(bpm: Float, minY: Float, maxY: Float, height: Float, strokeWidth: Float): Float {
    val span = (maxY - minY).coerceAtLeast(MIN_BPM_SPAN_EPSILON)
    val share = ((bpm - minY) / span).coerceIn(0f, 1f)
    return strokeWidth / 2f + (height - strokeWidth) * (1f - share)
}

/** Projects [thresholds] from BPM into unclamped canvas Y coordinates for the shader and guide lines. */
internal fun heartChartThresholdsY(
    thresholds: HeartZoneThresholds,
    minY: Float,
    maxY: Float,
    height: Float,
    strokeWidth: Float,
): HeartChartThresholdsY = HeartChartThresholdsY(
    zone2 = heartChartY(thresholds.zone2, minY, maxY, height, strokeWidth),
    zone3 = heartChartY(thresholds.zone3, minY, maxY, height, strokeWidth),
    zone4 = heartChartY(thresholds.zone4, minY, maxY, height, strokeWidth),
    zone5 = heartChartY(thresholds.zone5, minY, maxY, height, strokeWidth),
)

/** Contiguous index ranges of at least two valid readings in [samples], skipping sensor gaps. */
internal fun validSampleRuns(samples: List<Int>): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    for (index in 0..<samples.size) {
        if (samples[index] in HeartRate.BPM_RANGE) {
            if (start < 0) start = index
        } else if (start >= 0) {
            if (index - 1 > start) runs.add(start..<index)
            start = -1
        }
    }
    if (start >= 0 && samples.lastIndex > start) {
        runs.add(start..samples.lastIndex)
    }
    return runs
}

/** Open polyline path through [runs] of [samples], broken across sensor gaps. */
internal fun buildHeartLinePath(
    samples: List<Int>,
    runs: List<IntRange>,
    leftInset: Float,
    step: Float,
    yFor: (Float) -> Float,
): Path {
    val path = Path()
    for (r in 0..<runs.size) {
        val run = runs[r]
        for (index in run) {
            val x = leftInset + index * step
            val y = yFor(samples[index].toFloat())
            if (index == run.first) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }
    return path
}

/** Closed area path beneath each valid run in [runs], anchored cleanly down to [baselineY]. */
internal fun buildHeartFillPath(
    samples: List<Int>,
    runs: List<IntRange>,
    leftInset: Float,
    step: Float,
    baselineY: Float,
    yFor: (Float) -> Float,
): Path {
    val path = Path()
    for (r in 0..<runs.size) {
        val run = runs[r]
        for (index in run) {
            val x = leftInset + index * step
            val y = yFor(samples[index].toFloat())
            if (index == run.first) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.lineTo(leftInset + run.last * step, baselineY)
        path.lineTo(leftInset + run.first * step, baselineY)
        path.close()
    }
    return path
}

/**
 * Binds all chart zone-band uniforms on [program] and returns its current [Brush]. Called from `drawWithCache`
 * so draw frames allocate nothing inside `DrawScope`.
 */
internal fun bindHeartChartShader(
    program: ShaderProgram,
    size: Size,
    density: Float,
    thresholdsY: HeartChartThresholdsY,
    zones: HeartZoneColors,
    surfaceColor: Color,
): Brush {
    program.set("uSize", size.width, size.height)
    program.set("uDensity", density)
    program.set("uThresholds", thresholdsY.zone2, thresholdsY.zone3, thresholdsY.zone4, thresholdsY.zone5)
    program.set("uZone1", zones.zone1)
    program.set("uZone2", zones.zone2)
    program.set("uZone3", zones.zone3)
    program.set("uZone4", zones.zone4)
    program.set("uZone5", zones.zone5)
    program.set("uSurface", surfaceColor)
    return program.brush()
}

/**
 * Pure-Compose vertical gradient fallback matching [HEART_RATE_CHART_SHADER]'s zone tinting and baseline
 * attenuation when runtime shaders are unavailable (Android < 13).
 */
internal fun heartChartFallbackBrush(
    height: Float,
    thresholdsY: HeartChartThresholdsY,
    zones: HeartZoneColors,
    surfaceColor: Color,
): Brush {
    val safeHeight = height.coerceAtLeast(1f)
    val isLight = surfaceColor.luminance() > LIGHT_SURFACE_LUMINANCE
    val feather = (safeHeight * FALLBACK_FEATHER_RATIO).coerceAtLeast(1f)
    val stops = Array(FALLBACK_STOP_COUNT) { index ->
        val fraction = index.toFloat() / (FALLBACK_STOP_COUNT - 1)
        val y = fraction * safeHeight
        val v = (1f - fraction).coerceIn(0f, 1f)
        val t2 = smoothstep(thresholdsY.zone2 + feather, thresholdsY.zone2 - feather, y)
        val t3 = smoothstep(thresholdsY.zone3 + feather, thresholdsY.zone3 - feather, y)
        val t4 = smoothstep(thresholdsY.zone4 + feather, thresholdsY.zone4 - feather, y)
        val t5 = smoothstep(thresholdsY.zone5 + feather, thresholdsY.zone5 - feather, y)
        var rgb = zones.zone1
        rgb = lerp(rgb, zones.zone2, t2)
        rgb = lerp(rgb, zones.zone3, t3)
        rgb = lerp(rgb, zones.zone4, t4)
        rgb = lerp(rgb, zones.zone5, t5)
        val zoneNorm = (t2 + t3 + t4 + t5) * ZONE_NORM_SCALE
        val atten = v.coerceAtLeast(POW_EPSILON).pow(VERTICAL_ATTEN_EXPONENT) *
            smoothstep(0f, BASELINE_TOE_END, v)
        val peak = if (isLight) {
            lerpFloat(LIGHT_PEAK_MIN, LIGHT_PEAK_MAX, zoneNorm)
        } else {
            lerpFloat(DARK_PEAK_MIN, DARK_PEAK_MAX, zoneNorm)
        }
        val alpha = (atten * peak).coerceIn(0f, 1f)
        val blended = lerp(surfaceColor, rgb, smoothstep(0f, SURFACE_BLEND_END, v))
        fraction to blended.copy(alpha = alpha)
    }
    return Brush.verticalGradient(colorStops = stops, startY = 0f, endY = safeHeight)
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val denom = edge1 - edge0
    if (denom == 0f) return if (x < edge0) 0f else 1f
    val t = ((x - edge0) / denom).coerceIn(0f, 1f)
    return t * t * (SMOOTHSTEP_COEFF - 2f * t)
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

/**
 * Shared SkSL / AGSL fragment shader for the zone-banded area fill beneath the heart-rate curve.
 * Shifts tint across the four horizontal BPM zone thresholds from `HeartZoneScale`, evolves from a calm
 * scanline wash in recovery zones into a crisp dot-matrix telemetry pattern in high-effort zones, marks
 * each threshold crossing with a subtle interior seam, and attenuates smoothly toward the chart baseline
 * against the host surface color from `Theme.kt`. Returns premultiplied RGBA.
 */
internal const val HEART_RATE_CHART_SHADER: String = SKSL_LIGHT + """
uniform float2 uSize;
uniform float uDensity;
uniform float4 uThresholds;
uniform float4 uZone1;
uniform float4 uZone2;
uniform float4 uZone3;
uniform float4 uZone4;
uniform float4 uZone5;
uniform float4 uSurface;

half4 main(float2 fragCoord) {
    float h = max(uSize.y, 1.0);
    float density = max(uDensity, 1.0);
    float v = clamp(1.0 - fragCoord.y / h, 0.0, 1.0);

    float feather = max(min(2.0 * density, h * 0.06), 1.0);
    float t2 = smoothstep(uThresholds.x + feather, uThresholds.x - feather, fragCoord.y);
    float t3 = smoothstep(uThresholds.y + feather, uThresholds.y - feather, fragCoord.y);
    float t4 = smoothstep(uThresholds.z + feather, uThresholds.z - feather, fragCoord.y);
    float t5 = smoothstep(uThresholds.w + feather, uThresholds.w - feather, fragCoord.y);

    float3 zoneRgb = uZone1.rgb;
    zoneRgb = mix(zoneRgb, uZone2.rgb, t2);
    zoneRgb = mix(zoneRgb, uZone3.rgb, t3);
    zoneRgb = mix(zoneRgb, uZone4.rgb, t4);
    zoneRgb = mix(zoneRgb, uZone5.rgb, t5);

    float zoneLevel = t2 + t3 + t4 + t5;
    float zoneNorm = zoneLevel * 0.25;

    float seamHalf = max(0.75 * density, 0.75);
    float s2 = 1.0 - smoothstep(0.0, seamHalf, abs(fragCoord.y - uThresholds.x));
    float s3 = 1.0 - smoothstep(0.0, seamHalf, abs(fragCoord.y - uThresholds.y));
    float s4 = 1.0 - smoothstep(0.0, seamHalf, abs(fragCoord.y - uThresholds.z));
    float s5 = 1.0 - smoothstep(0.0, seamHalf, abs(fragCoord.y - uThresholds.w));
    float seam = max(max(s2, s3), max(s4, s5));

    float2 dp = fragCoord / density;
    float2 cell = fract(dp / 3.0) - 0.5;
    float scan = 1.0 - smoothstep(0.14, 0.34, abs(cell.y));
    float dotRadius = mix(0.20, 0.31, zoneNorm);
    float dots = 1.0 - smoothstep(dotRadius - 0.11, dotRadius + 0.11, length(cell));
    float dotBlend = smoothstep(1.1, 3.2, zoneLevel);
    float textureMask = mix(scan, dots, dotBlend);

    float surfaceLum = dot(uSurface.rgb, float3(0.2126, 0.7152, 0.0722));
    float isLight = smoothstep(0.35, 0.65, surfaceLum);

    float atten = pow(max(v, 0.0001), 0.78) * smoothstep(0.0, 0.08, v);
    float basePeak = mix(mix(0.28, 0.52, zoneNorm), mix(0.22, 0.40, zoneNorm), isLight);
    float texWeight = mix(0.08, 0.46, zoneNorm);
    float texMod = mix(1.0 - texWeight * 0.42, 1.0 + texWeight * 0.54, textureMask);

    float alpha = atten * basePeak * texMod + seam * atten * mix(0.16, 0.13, isLight);
    alpha = clamp(alpha, 0.0, mix(0.68, 0.54, isLight));

    float gain = (1.0 - isLight) * (0.24 * zoneNorm * textureMask + 0.18 * seam);
    float3 litRgb = exposed(zoneRgb, gain);
    float3 finalRgb = mix(uSurface.rgb, litRgb, smoothstep(0.0, 0.24, v));
    finalRgb = clamp(finalRgb + grain(fragCoord) / 255.0, 0.0, 1.0);

    return half4(finalRgb * alpha, alpha);
}
"""

private const val MIN_BPM_SPAN_EPSILON = 0.001f
private const val LIGHT_SURFACE_LUMINANCE = 0.5f
private const val FALLBACK_STOP_COUNT = 16
private const val FALLBACK_FEATHER_RATIO = 0.04f
private const val ZONE_NORM_SCALE = 0.25f
private const val VERTICAL_ATTEN_EXPONENT = 0.78f
private const val BASELINE_TOE_END = 0.08f
private const val SURFACE_BLEND_END = 0.24f
private const val DARK_PEAK_MIN = 0.28f
private const val DARK_PEAK_MAX = 0.52f
private const val LIGHT_PEAK_MIN = 0.22f
private const val LIGHT_PEAK_MAX = 0.40f
private const val POW_EPSILON = 0.0001f
private const val SMOOTHSTEP_COEFF = 3f
