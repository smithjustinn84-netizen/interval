package dev.justinsmith.interval.lint

import org.jetbrains.amper.plugins.Configurable

/** Settings a consuming module sets under `plugins: lint-plugin:` in its module.yaml. */
@Configurable
interface Settings {
    /** Report violations without failing the `lint` check. */
    val warningsOnly: Boolean get() = false
}
