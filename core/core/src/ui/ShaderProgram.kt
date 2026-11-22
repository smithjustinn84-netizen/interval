package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces

/**
 * A fragment shader written in the language SkSL and AGSL share, compiled once and painted with as its uniforms
 * stand. Compose has no runtime shader of its own in common code, so each platform makes one from what it
 * already draws with: Skia's on iOS and the desktop, Android's own from 13.
 *
 * Its uniforms are set where it is drawn, then [brush] taken, each frame: a change of uniform is a draw, never a
 * recomposition.
 */
internal interface ShaderProgram {
    fun set(name: String, value: Float)

    fun set(name: String, x: Float, y: Float)

    fun set(name: String, x: Float, y: Float, z: Float, w: Float)

    /** The shader as its uniforms now stand, to paint with at once. */
    fun brush(): Brush
}

/** [color] as a `float4` uniform: sRGB, its alpha apart (the shader multiplies it in). */
internal fun ShaderProgram.set(name: String, color: Color) {
    val srgb = color.convert(ColorSpaces.Srgb)
    set(name, srgb.red, srgb.green, srgb.blue, srgb.alpha)
}

/**
 * [source] compiled, or null where the platform has no runtime shaders (Android before 13) or won't take the
 * source: whoever draws with it keeps a plain way to draw too.
 */
internal expect fun shaderProgram(source: String): ShaderProgram?

/**
 * What the app's shaders share, for each to put before its own source.
 *
 * `grain` is interleaved gradient noise about zero, a different half level for each pixel: added to a colour
 * in 255ths it breaks the bands an 8-bit surface cuts a slow gradient into.
 *
 * `exposed` is a colour with `gain` more light on it, as a longer exposure would have it: brighter in
 * proportion, and some of what a channel has past full spills into the others, so a colour already near full goes
 * toward white where a deep one only grows more vivid. It is how an accent is made hotter on either theme's
 * ground without a second colour to go to.
 */
internal const val SKSL_LIGHT = """
float grain(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))) - 0.5; }

float3 exposed(float3 color, float gain) {
    float3 lit = color * (1.0 + gain);
    float over = max(max(max(lit.r, lit.g), lit.b) - 1.0, 0.0);
    return clamp(lit + 0.6 * over, 0.0, 1.0);
}
"""
