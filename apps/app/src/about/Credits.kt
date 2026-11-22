package dev.justinsmith.interval.about

import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.license_apache
import dev.justinsmith.interval.resources.license_bsd3
import dev.justinsmith.interval.resources.license_cc0
import dev.justinsmith.interval.resources.license_mit
import dev.justinsmith.interval.resources.license_ofl
import dev.justinsmith.interval.resources.license_polar
import dev.justinsmith.interval.resources.license_public_domain
import org.jetbrains.compose.resources.StringResource

/**
 * A licence the app ships software under, and the resource holding the text every credit under it shares,
 * when there is one. The MIT and BSD texts open with their holder's copyright, so each such credit names
 * its own file instead.
 */
enum class License(val nameRes: StringResource, val file: String?) {
    OpenFont(Res.string.license_ofl, "files/barlow-OFL.txt"),
    Apache(Res.string.license_apache, "files/apache-2.0.txt"),
    Mit(Res.string.license_mit, file = null),
    Bsd3(Res.string.license_bsd3, file = null),
    Polar(Res.string.license_polar, "files/polar-sdk-license.txt"),
    Cc0(Res.string.license_cc0, file = null),
    PublicDomain(Res.string.license_public_domain, file = null),
}

/**
 * Software the app is built with: its [name], who [holder]s its copyright, the [license] it is used
 * under, and the [file] holding that licence's text as it ships with this software (the licence's own,
 * unless the software's notice goes with it). Names and holders are legal notices, reproduced as their
 * projects give them, so they are not translated.
 */
class Credit(val name: String, val holder: String?, val license: License, val file: String? = license.file)

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

/**
 * The libraries in the shipped app, in the order a reader would expect: the platform first, then what
 * the app is written with, then what Android's heart-rate sensor and Health Connect bring with them
 * (Polar's SDK and what it is built on, and Health Connect's Guava). Taken from
 * `./kotlin show dependencies -m androidApp`; a new runtime dependency adds its credit here.
 */
val libraryCredits: List<Credit> = listOf(
    Credit("Kotlin", "JetBrains s.r.o. and Kotlin Programming Language contributors", License.Apache),
    Credit("Compose Multiplatform and Compose Hot Reload", "JetBrains s.r.o.", License.Apache),
    Credit(
        "Jetpack Compose, Navigation, Lifecycle, Room, DataStore, WorkManager, Glance and Health Connect",
        "The Android Open Source Project",
        License.Apache,
    ),
    Credit("kotlinx.coroutines, kotlinx.serialization and atomicfu", "JetBrains s.r.o.", License.Apache),
    Credit("Compose Unstyled", "Composable Horizons", License.Mit, "files/compose-unstyled-MIT.txt"),
    Credit("Koin", "Arnaud Giuliani", License.Apache),
    Credit("Kermit and Stately", "Touchlab, Inc.", License.Apache),
    Credit("Okio", "Square, Inc.", License.Apache),
    Credit("SQLite", holder = null, License.PublicDomain),
    Credit("Protocol Buffers", "Google Inc.", License.Bsd3, "files/protobuf-BSD-3-Clause.txt"),
    Credit("Polar BLE SDK", "Polar Electro Oy", License.Polar),
    Credit("RxJava", "RxJava Contributors", License.Apache),
    Credit("Reactive Streams", holder = null, License.Cc0),
    Credit("Retrofit and OkHttp", "Square, Inc.", License.Apache),
    Credit("Gson and FlatBuffers", "Google Inc.", License.Apache),
    Credit("Apache Commons IO", "The Apache Software Foundation", License.Apache, "files/commons-io-NOTICE.txt"),
    Credit("Guava", "The Guava Authors", License.Apache),
    Credit(
        "Checker Framework qualifiers",
        "the Checker Framework developers",
        License.Mit,
        "files/checker-qual-MIT.txt",
    ),
    Credit("Error Prone, J2ObjC, JSR 305, JSpecify and JetBrains annotations", holder = null, License.Apache),
)
