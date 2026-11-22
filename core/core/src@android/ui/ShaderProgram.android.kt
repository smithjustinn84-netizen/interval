package dev.justinsmith.interval.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush

internal actual fun shaderProgram(source: String): ShaderProgram? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) agslProgram(source) else null

/** [source] as AGSL, or null if the device's compiler refuses it: the caller draws its plain way. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun agslProgram(source: String): ShaderProgram? = try {
    AgslProgram(RuntimeShader(source))
} catch (_: IllegalArgumentException) {
    null
}

/**
 * Android's shader is one object whose uniforms change under it: a paint asks it for its native shader at each
 * draw, and a uniform set since then makes it a new one, so the one brush always paints the latest.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslProgram(private val shader: RuntimeShader) : ShaderProgram {
    private val brush = ShaderBrush(shader)

    override fun set(name: String, value: Float) = shader.setFloatUniform(name, value)

    override fun set(name: String, x: Float, y: Float) = shader.setFloatUniform(name, x, y)

    override fun set(name: String, x: Float, y: Float, z: Float, w: Float) = shader.setFloatUniform(name, x, y, z, w)

    override fun brush(): Brush = brush
}
