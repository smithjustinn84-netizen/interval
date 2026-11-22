package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/** Each source compiled once a process, however many draw with it; null for one Skia refused. */
private val effects = mutableMapOf<String, RuntimeEffect?>()

internal actual fun shaderProgram(source: String): ShaderProgram? =
    effects.getOrPut(source) { compiled(source) }?.let { SkslProgram(RuntimeShaderBuilder(it)) }

private fun compiled(source: String): RuntimeEffect? = try {
    RuntimeEffect.makeForShader(source)
} catch (_: RuntimeException) {
    null
}

/** Skia's shaders don't change once made: the builder keeps the uniforms, and each brush is a shader of them. */
private class SkslProgram(private val builder: RuntimeShaderBuilder) : ShaderProgram {
    override fun set(name: String, value: Float) = builder.uniform(name, value)

    override fun set(name: String, x: Float, y: Float) = builder.uniform(name, x, y)

    override fun set(name: String, x: Float, y: Float, z: Float, w: Float) = builder.uniform(name, x, y, z, w)

    override fun brush(): Brush = ShaderBrush(builder.makeShader().asComposeShader())
}
