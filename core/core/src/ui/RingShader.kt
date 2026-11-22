package dev.justinsmith.interval.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The ring's band as one shader: the track, the dial's ticks, and the arc over them, each pixel worked out from
 * its distance to the shapes, so every edge is a pixel's ramp wherever it falls and nothing is stepped by the
 * tessellation or the 8-bit stops of a sweep gradient.
 *
 * - The arc runs from twelve o'clock to `sweep` of a turn, round at both ends. Its ink is graded along its
 *   length, the tail giving way to the track's colour and the head the accent at full strength; a short arc
 *   isn't graded, having no length to grade over. A grain of under half a level breaks the grade's bands.
 * - The head carries a needle, a hairline across the band in `mark`, the colour that reads on the accent: where
 *   exactly the time stands against the ticks. On a full turn the head's cap lies over the tail and shades it.
 * - The ticks hang from the band's outer edge, `count` to the turn (a fraction leaves the short gap before
 *   twelve), every `major`th longer and bolder: in `etch` on the track, cut into the arc in `mark`.
 * - `heat` (0 to 1) is the interval's last seconds: the tail's fade lifts, the arc is exposed brighter
 *   ([SKSL_LIGHT]) and the needle grows to the band's width. `beat` (0 to 1) flashes it the same way, most at
 *   the head.
 *
 * Colours are sRGB with their alpha apart; coordinates are pixels.
 */
internal const val RING_SKSL = """
uniform float2 center;
uniform float radius;
uniform float halfWidth;
uniform float hair;
uniform float sweep;
uniform float count;
uniform float major;
uniform float heat;
uniform float beat;
uniform float4 accent;
uniform float4 track;
uniform float4 etch;
uniform float4 mark;

const float TAU = 6.28318530718;

// How much of a pixel lies inside an edge it is d pixels outside of.
float cover(float d) { return clamp(0.5 - d, 0.0, 1.0); }
$SKSL_LIGHT
half4 main(float2 p) {
    float2 d = p - center;
    float r = length(d);
    float off = r - radius;
    float turn = atan(d.x, -d.y) / TAU;
    turn = turn < 0.0 ? turn + 1.0 : turn;
    float band = cover(abs(off) - halfWidth);

    float tick = 0.0;
    if (count > 0.0) {
        float at = turn * count;
        float nearest = min(floor(at + 0.5), floor(count + 0.001));
        float away = abs(at - nearest);
        if (count - at < away) {
            away = count - at;
            nearest = 0.0;
        }
        float isMajor = 1.0 - step(0.5, mod(nearest, major));
        float reach = halfWidth * mix(0.44, 0.8, isMajor);
        float middle = halfWidth * 0.72 - reach * 0.5;
        float across = away / count * TAU * r;
        tick = cover(across - hair * mix(0.5, 0.8, isMajor)) * cover(abs(off - middle) - reach * 0.5)
            * mix(0.6, 1.0, isMajor);
    }

    float angle = sweep * TAU;
    float2 ray = float2(sin(angle), -cos(angle));
    float toHead = length(d - ray * radius);
    float toTail = length(d - float2(0.0, -radius));
    float within = step(turn, sweep);
    float shape = mix(min(toHead, toTail), abs(off), within) - halfWidth;
    float started = step(0.0001, sweep);
    float arc = cover(shape) * started * accent.a;

    float ahead = fract(turn - sweep + 0.5) - 0.5;
    float past = step(0.0, ahead);
    float cap = cover(toHead - halfWidth) * past;
    float along = mix(step(toHead, toTail), clamp(turn / max(sweep, 0.0001), 0.0, 1.0), within);
    along = mix(along, 1.0, cap);

    float fade = 0.38 * smoothstep(0.0, 0.3, sweep) * (1.0 - 0.7 * heat) * pow(1.0 - along, 1.3);
    float3 ink = mix(accent.rgb, track.rgb, fade);
    ink = exposed(ink, 0.18 * heat + beat * mix(0.12, 0.32, along * along * along));
    float shade = (1.0 - smoothstep(0.0, halfWidth, toHead - halfWidth)) * past * (1.0 - cap);
    ink = mix(ink, track.rgb, 0.32 * shade);
    ink = mix(ink, mark.rgb, 0.34 * tick * mark.a);

    float outward = dot(d, ray) - radius;
    float sideways = dot(d, float2(-ray.y, ray.x));
    float span = halfWidth * mix(0.5, 0.86, heat);
    float needle = cover(length(float2(sideways, max(abs(outward) - span, 0.0))) - hair * 0.75);
    ink = mix(ink, mark.rgb, 0.92 * needle * mark.a);

    float3 bed = mix(track.rgb, etch.rgb, 0.3 * tick * etch.a);
    float bedAlpha = track.a * band * (1.0 - arc);
    float alpha = arc + bedAlpha;
    float3 color = ink * arc + bed * bedAlpha;
    color = clamp(color + grain(p) / 255.0 * alpha, 0.0, alpha);
    return half4(color.r, color.g, color.b, alpha);
}
"""

/**
 * Paints the ring's band with [RING_SKSL]: the uniforms set as they stand, then one stroke round the band's
 * circle, so only the band's pixels are shaded.
 */
internal class RingShaderPaint(private val program: ShaderProgram, private val hairline: Float) {
    private var stroke = Stroke(0f)

    /** The dial's [ticks], or none. */
    fun dial(ticks: DialTicks?) {
        program.set("count", ticks?.count ?: 0f)
        program.set("major", ticks?.majorEvery?.toFloat() ?: 1f)
    }

    /** The interval's last seconds: how far into them ([heat]), and the flash of a second arriving ([beat]). */
    fun urgency(heat: Float, beat: Float) {
        program.set("heat", heat)
        program.set("beat", beat)
    }

    /**
     * The arc's [accent] over its [track], the colour the ticks are etched into the track in ([etch]), and the
     * one that reads on the accent ([mark]), the needle's and the ticks' under the arc.
     */
    fun colors(accent: Color, track: Color, etch: Color, mark: Color) {
        program.set("accent", accent)
        program.set("track", track)
        program.set("etch", etch)
        program.set("mark", mark)
    }

    /** The band [width] wide round a circle of [radius] about [center], filled to [sweep] of a turn. */
    fun DrawScope.drawBand(center: Offset, radius: Float, width: Float, sweep: Float) {
        // A pixel wider than the band on each side: the shader ramps its own edges, inside the stroke's.
        val painted = width + EDGE_PIXELS * 2
        if (painted != stroke.width) stroke = Stroke(painted)
        program.set("center", center.x, center.y)
        program.set("radius", radius)
        program.set("halfWidth", width / 2)
        program.set("hair", hairline)
        program.set("sweep", sweep)
        drawCircle(program.brush(), radius, center, style = stroke)
    }
}

private const val EDGE_PIXELS = 1f
