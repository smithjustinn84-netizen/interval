package dev.justinsmith.interval.about

import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.license_apache
import dev.justinsmith.interval.resources.license_mit
import dev.justinsmith.interval.resources.license_ofl
import dev.justinsmith.interval.resources.license_public_domain
import org.jetbrains.compose.resources.StringResource

/** A licence the app ships software under, and the resource holding its text, when it has one. */
enum class License(val nameRes: StringResource, val file: String?) {
    OpenFont(Res.string.license_ofl, "files/barlow-OFL.txt"),
    Apache(Res.string.license_apache, "files/apache-2.0.txt"),
    Mit(Res.string.license_mit, "files/compose-unstyled-MIT.txt"),
    PublicDomain(Res.string.license_public_domain, file = null),
}

/**
 * Software the app is built with: its [name], who [holder]s its copyright, and the [license] it is used
 * under. Names and holders are legal notices, reproduced as their projects give them, so they are not
 * translated.
 */
class Credit(val name: String, val holder: String?, val license: License)

/**
 * A licence text as a screen can wrap it: the files are hard-wrapped at seventy columns, so each
 * paragraph's lines are joined and its spacing collapsed, while blank lines still separate paragraphs
 * (and rulers, headings and list items, which the texts set off that way).
 */
fun reflow(text: String): String = text
    .split(PARAGRAPH_BREAK)
    .map { paragraph -> paragraph.trim().split(WHITESPACE).joinToString(" ") }
    .filter { it.isNotEmpty() }
    .joinToString("\n\n")

private val PARAGRAPH_BREAK = Regex("""\r?\n(?:[ \t]*\r?\n)+""")
private val WHITESPACE = Regex("""\s+""")

/** The typefaces, shipped as font files. */
val fontCredits: List<Credit> = listOf(
    Credit("Barlow", "The Barlow Project Authors", License.OpenFont),
)

/** The libraries in the shipped app, in the order a reader would expect: the platform first. */
val libraryCredits: List<Credit> = listOf(
    Credit("Kotlin", "JetBrains s.r.o. and Kotlin Programming Language contributors", License.Apache),
    Credit("Compose Multiplatform", "JetBrains s.r.o.", License.Apache),
    Credit(
        "Jetpack Compose, Navigation, Lifecycle, Room and DataStore",
        "The Android Open Source Project",
        License.Apache,
    ),
    Credit("kotlinx.coroutines and kotlinx.serialization", "JetBrains s.r.o.", License.Apache),
    Credit("Compose Unstyled", "Composable Horizons", License.Mit),
    Credit("Koin", "Arnaud Giuliani", License.Apache),
    Credit("Kermit", "Touchlab, Inc.", License.Apache),
    Credit("Okio", "Square, Inc.", License.Apache),
    Credit("SQLite", holder = null, License.PublicDomain),
)
