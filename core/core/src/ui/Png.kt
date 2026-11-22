package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.ImageBitmap

/** The bitmap as a PNG file's bytes: Skia's encoder on iOS and the desktop, Android's `Bitmap.compress`. */
internal expect fun ImageBitmap.encodePng(): ByteArray
