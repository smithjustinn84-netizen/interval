package dev.justinsmith.interval.detekt

import dev.detekt.cli.CliRunner
import dev.detekt.tooling.api.InvalidConfig
import dev.detekt.tooling.api.IssuesFound
import dev.detekt.tooling.api.UnexpectedError
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path

/**
 * Runs detekt (core rules plus Compose rules) over the module. Backs the `detekt` check, so
 * `./kotlin check` fails on any finding (unless the module sets `warningsOnly: true`).
 *
 * Calls [CliRunner] rather than detekt-cli's `main`, which ends in `exitProcess` and would take the
 * toolchain JVM down with it.
 */
@TaskAction
fun detekt(
    @Input moduleRoot: Path,
    @Input config: Path,
    @Output reportDir: Path,
    warningsOnly: Boolean,
) {
    val html = reportDir.resolve("detekt.html")
    val args = arrayOf(
        "--input", moduleRoot.toString(),
        "--excludes", "**/build/**",
        "--config", config.toString(),
        "--build-upon-default-config",
        // Every module sits in a group folder (apps/, core/, interval/, lift/, tooling/), so paths in
        // reports and detekt.yml's excludes start at the module's own name. Taking the base from here
        // rather than as an @Input keeps the whole repo (build/, .git/, .jj/) out of the task's inputs.
        "--base-path", moduleRoot.parent.toString(),
        // Light mode parses without type resolution: a KMP module has no single classpath to resolve
        // against. Rules that need types are skipped.
        "--analysis-mode", "light",
        "--parallel",
        "--report", "html:$html",
        "--report", "sarif:${reportDir.resolve("detekt.sarif")}",
    )
    when (val error = CliRunner().run(args, System.out, System.err).error) {
        null -> Unit

        is IssuesFound -> {
            val summary = "detekt: ${error.message} Report: $html"
            if (warningsOnly) println("WARNING: $summary") else error(summary)
        }

        is InvalidConfig -> error("detekt: invalid config. ${error.message}")

        is UnexpectedError -> throw IllegalStateException("detekt failed unexpectedly.", error.cause)
    }
}
