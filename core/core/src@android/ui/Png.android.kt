package dev.justinsmith.interval.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

/**
 * Copied into memory first: a captured layer may come back as a hardware bitmap, whose pixels live on the GPU
 * and which `compress` may refuse.
 */
internal actual fun ImageBitmap.encodePng(): ByteArray {
    val bitmap = asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        out.toByteArray()
    }
}

/** PNG is lossless: the quality is ignored, but asked for. */
private const val PNG_QUALITY = 100
