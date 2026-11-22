package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

internal actual fun ImageBitmap.encodePng(): ByteArray {
    val image = Image.makeFromBitmap(asSkiaBitmap())
    return checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "Skia encoded no PNG" }.bytes
}
