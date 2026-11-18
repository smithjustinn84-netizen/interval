@file:OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)

package dev.justinsmith.interval

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import java.awt.image.BufferedImage
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.test.Test

/**
 * Draws the app's `@Preview` functions to PNGs, for whoever can't open the IDE's preview pane (an agent, a
 * review). It does nothing unless asked through a system property, so it costs the test run nothing:
 *
 * ```sh
 * ./kotlin test -m app -p jvm --include-classes '*PreviewRenderTest' --jvm-args='-Dpreviews=HistoryScreen'
 * ```
 *
 * - `-Dpreviews=<part of a name>` or `all`: which previews, by the function's name, case ignored
 * - `-Dpreviews.window=Phone` (the default), or any other [Window], or `all`
 * - `-Dpreviews.dark=true`: the dark theme (a preview that names its own theme keeps it)
 * - `-Dpreviews.out=<dir>`: where the PNGs go; `build/previews` at the project's root otherwise
 *
 * A preview is any top-level composable of no parameters whose name ends in `Preview`, in any module `app`
 * depends on, private or not. The `@Preview` annotation itself is not kept in the class file, so the name is
 * what it goes by. A sheet or dialog is a root of its own and is drawn over the page under it.
 */
class PreviewRenderTest {
    @Test
    fun `draws the previews asked for`() {
        val asked = System.getProperty("previews") ?: return
        val previews = previewFunctions().filter { asked == ALL || it.name.contains(asked, ignoreCase = true) }
        check(previews.isNotEmpty()) { "No preview's name has \"$asked\" in it" }
        val windows = System.getProperty("previews.window", Window.Phone.name).let { name ->
            Window.entries.filter { name == ALL || it.name.equals(name, ignoreCase = true) }
        }
        check(windows.isNotEmpty()) { "No such window; there are ${Window.entries}" }
        val dark = System.getProperty("previews.dark").toBoolean()
        val out = System.getProperty("previews.out")?.let(::File) ?: File(projectRoot(), "build/previews")
        out.mkdirs()
        for (preview in previews) {
            for (window in windows) {
                val name = listOfNotNull(preview.name, window.name.lowercase(), "dark".takeIf { dark })
                val file = File(out, name.joinToString("-") + ".png")
                runUiTest(window) {
                    setContent {
                        // Deprecated as public by mistake, with nothing yet in its place: it is the one way
                        // to say which theme `isSystemInDarkTheme` reads here, rather than the machine's.
                        @Suppress("DEPRECATION")
                        val theme = if (dark) SystemTheme.Dark else SystemTheme.Light
                        @Suppress("DEPRECATION")
                        CompositionLocalProvider(LocalSystemTheme provides theme) {
                            preview.invoke(null, currentComposer, 0)
                        }
                    }
                    waitForIdle()
                    ImageIO.write(capture(window), "png", file)
                }
                println("Preview drawn: ${file.absolutePath}")
            }
        }
    }
}

private const val ALL = "all"
private const val PACKAGES = "dev/justinsmith/"

/** Every root there is (the page, then each sheet or dialog over it), drawn in order onto one image. */
private fun DesktopComposeUiTest.capture(window: Window): BufferedImage {
    val image = BufferedImage(window.width, window.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    val roots = onAllNodes(isRoot())
    for (index in roots.fetchSemanticsNodes().indices) {
        val position = roots[index].fetchSemanticsNode().positionInWindow
        graphics.drawImage(
            roots[index].captureToImage().toAwtImage(),
            position.x.roundToInt(),
            position.y.roundToInt(),
            null,
        )
    }
    graphics.dispose()
    return image
}

/** The composables of no parameters named `…Preview` in the app's own classes, in the order of their names. */
private fun previewFunctions(): List<Method> {
    val loader = Thread.currentThread().contextClassLoader
    return System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
        .flatMap(::classNames)
        .distinct()
        .mapNotNull { runCatching { Class.forName(it, false, loader) }.getOrNull() }
        .flatMap { it.declaredMethods.asList() }
        .filter { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name.endsWith("Preview") &&
                method.parameterTypes.contentEquals(arrayOf(Composer::class.java, Int::class.javaPrimitiveType))
        }
        .onEach { it.isAccessible = true }
        .sortedBy { it.name }
}

/** The file classes (`XKt`, where top-level functions are) under the app's packages in a classpath [entry]. */
private fun classNames(entry: File): List<String> {
    val paths = when {
        entry.isDirectory -> entry.walk().filter {
            it.isFile
        }.map { it.relativeTo(entry).invariantSeparatorsPath }.toList()

        entry.isFile -> ZipFile(entry).use { zip -> zip.entries().asSequence().map { it.name }.toList() }

        else -> emptyList()
    }
    return paths.filter { it.startsWith(PACKAGES) && it.endsWith("Kt.class") }
        .map { it.removeSuffix(".class").replace('/', '.') }
}

/** The directory `project.yaml` is in, from wherever the tests were run. */
private fun projectRoot(): File =
    generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "project.yaml").exists() }
