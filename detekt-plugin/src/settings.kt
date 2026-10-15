package dev.justinsmith.interval.detekt

import org.jetbrains.amper.plugins.Configurable

/**
 * Settings a consuming module sets under `plugins: detekt-plugin:` in its module.yaml. A copy of the
 * lint plugin's, because a configurable type cannot be shared across plugins.
 */
@Configurable
interface Settings {
    /** Report findings without failing the `detekt` check. */
    val warningsOnly: Boolean get() = false
}
