package dev.justinsmith.interval.lint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.ruleset.standard.StandardRuleSetProvider
import org.jetbrains.amper.plugins.ExecutionAvoidance
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * Reports ktlint violations across the module. Backs the `lint` check, so `./kotlin check` fails
 * when anything is reported (unless the module sets `warningsOnly: true`).
 */
@TaskAction
fun lint(
    @Input moduleRoot: Path,
    warningsOnly: Boolean,
) {
    val engine = ruleEngine()
    var violations = 0
    val files = kotlinFiles(moduleRoot)
    println("ktlint: checking ${files.size} file(s).")
    files.forEach { file ->
        engine.lint(file.asCode()) { error ->
            violations++
            println(error.render(moduleRoot, file))
        }
    }
    if (violations > 0) {
        val summary = "ktlint: $violations violation(s). Run `./kotlin do format` to fix what is auto-correctable."
        if (warningsOnly) println("WARNING: $summary") else error(summary)
    }
}

/**
 * Rewrites the module's sources in place. Exposed as the `format` custom command
 * (`./kotlin do format`).
 *
 * Execution avoidance is off because the task mutates the very files it reads: an up-to-date check
 * keyed on those files would skip the run that is supposed to change them.
 */
@TaskAction(executionAvoidance = ExecutionAvoidance.Disabled)
fun format(@Input moduleRoot: Path) {
    val engine = ruleEngine()
    var changed = 0
    val remaining = mutableListOf<String>()
    kotlinFiles(moduleRoot).forEach { file ->
        val formatted = engine.format(file.asCode()) { AutocorrectDecision.ALLOW_AUTOCORRECT }
        if (formatted != file.readText()) {
            file.writeText(formatted)
            changed++
            println("formatted ${moduleRoot.relativize(file)}")
        }
        // ktlint reports which violations it saw, not which survived, so re-read the written file
        // to find what still needs a human.
        engine.lint(file.asCode()) { error -> remaining += error.render(moduleRoot, file) }
    }
    println("ktlint: $changed file(s) rewritten.")
    if (remaining.isNotEmpty()) {
        println("ktlint: ${remaining.size} violation(s) need fixing by hand:")
        remaining.forEach(::println)
    }
}

/**
 * `Code.fromPath` silently drops the file's trailing newline, which makes `standard:final-newline`
 * fire on every correctly-terminated file. `Code.fromFile` reads the bytes as they are.
 */
private fun Path.asCode() = Code.fromFile(toFile())

/**
 * Rule providers are passed explicitly rather than discovered through ServiceLoader: the plugin runs
 * in Amper's isolated classloader, where service discovery is not something to rely on.
 */
private fun ruleEngine() = KtLintRuleEngine(ruleProviders = StandardRuleSetProvider().getRuleProviders())

/**
 * Every `.kt` file under the module, which covers all of `src/`, the `src@<platform>/` fragments and
 * the test directories in one walk. `build/` is skipped so generated sources are never
 * linted.
 */
@OptIn(ExperimentalPathApi::class)
private fun kotlinFiles(moduleRoot: Path): List<Path> =
    moduleRoot.walk()
        .filter { it.isRegularFile() && it.extension == "kt" }
        .filter { path -> moduleRoot.relativize(path).none { it.toString() == "build" } }
        .sorted()
        .toList()

private fun LintError.render(moduleRoot: Path, file: Path) =
    "  ${moduleRoot.relativize(file)}:$line:$col $detail (${ruleId.value})"
