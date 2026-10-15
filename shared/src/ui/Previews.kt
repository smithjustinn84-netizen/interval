package dev.justinsmith.interval.ui

import androidx.compose.ui.tooling.preview.Preview

/**
 * A screen at each major form factor, sized as Android Studio's reference devices. Sizes rather than
 * `device` specs, so the IntelliJ preview of common code renders them too.
 */
@Preview(name = "Phone", widthDp = 411, heightDp = 891)
@Preview(name = "Phone, sideways", widthDp = 891, heightDp = 411)
@Preview(name = "Foldable", widthDp = 673, heightDp = 841)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800)
@Preview(name = "Desktop", widthDp = 1920, heightDp = 1080)
annotation class PreviewFormFactors
