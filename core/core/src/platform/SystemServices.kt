package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Keeps the display awake while this is in the composition. Several callers may hold it at once (a page on
 * its way out and the one replacing it): the display may sleep again only once the last one leaves.
 */
@Composable
expect fun KeepScreenOn()

/**
 * Asks for whatever the platform needs before it can show a notification: Android 13's permission to post
 * them (the system stops asking after two refusals); iOS's authorization, which the system asks for once and
 * then answers from the user's settings. Desktop needs nothing. Asked as a workout starts, for its notification
 * off screen, as a Lift session's first rest starts, for the rest's, and as the plan's reminder is turned on.
 */
@Composable
expect fun AskToShowNotifications()

/**
 * Counts the [KeepScreenOn]s holding one display awake, so one caller leaving doesn't let the screen sleep
 * under another. Main thread only, as composition effects are.
 */
internal class AwakeHolds {
    private var count = 0

    /** Takes a hold; true when it is the first, and the display should now be kept awake. */
    fun acquire(): Boolean = count++ == 0

    /** Gives a hold back; true when it was the last, and the display may sleep again. */
    fun release(): Boolean {
        check(count > 0) { "Released a hold that was never acquired" }
        return --count == 0
    }
}

/**
 * Sets the system bars' icons to suit what is drawn under them: [lightIcons] for dark content (the
 * dark theme, the workout, the intro), whatever the system appearance. Android and iOS; desktop's
 * title bar follows the system.
 */
@Composable
expect fun SystemBarIcons(lightIcons: Boolean)

/**
 * Whether a back swipe previews the page underneath the Material way, shrinking and rounding the
 * page as it follows the finger (Android). Elsewhere navigation keeps its platform default, the
 * native slide on iOS.
 */
expect val materialBackPreview: Boolean

/**
 * Whether pages keep the platform's own push and pop transitions (iOS's native slide). Elsewhere
 * the app's run: Android's default is a slow fade and desktop's is none at all.
 */
expect val platformPageTransitions: Boolean

/**
 * Whether the user asked the system for less motion: Android's "Remove animations" (animator scale
 * 0) or iOS's Reduce Motion. Decorative animation stands still then.
 */
@Composable
expect fun prefersReducedMotion(): Boolean

/**
 * The way from a screen into the platform's [HealthStore]: [connect] opens the permission sheet, which the
 * store hears the answer to through [HealthStore.refresh], and [install] takes the user to where the store
 * is got from while it is [HealthStatus.NotInstalled]. On a platform with no store both do nothing.
 */
interface HealthConnector {
    fun connect()

    fun install()
}

/** The [HealthConnector] for [store], remembered for the screen that asks. Android's opens Health Connect's sheet. */
@Composable
expect fun rememberHealthConnector(store: HealthStore): HealthConnector

/**
 * The platform's [HealthStore] by its name, which the screens put into their words: Health Connect on Android,
 * Apple Health on iOS. Desktop has none to name, and says Android's, as its tests read.
 */
expect val healthStoreName: StringResource

/** Where there is no store to connect: desktop. */
internal object NoHealthConnector : HealthConnector {
    override fun connect() = Unit

    override fun install() = Unit
}

/** A [FileOpener] standing in for the platform's picker, as tests provide one; null, as in the app, for theirs. */
val LocalFileOpener = staticCompositionLocalOf<FileOpener?> { null }

/**
 * The way from a screen to a file of the user's: the function returned asks for one of the mime types it is
 * given, and the file picked comes to [onOpen], read and in hand; nothing does when the user backs out. Android's
 * is the system's document picker, whose answer reaches the screen as it then stands, even one recreated behind
 * the picker; iOS's the document picker; desktop's a file dialog.
 */
@Composable
fun rememberFileOpener(onOpen: (ShareableFile) -> Unit): (List<String>) -> Unit {
    val standIn = LocalFileOpener.current
    val opened by rememberUpdatedState(onOpen)
    val platform = rememberPlatformFileOpener { opened(it) }
    val scope = rememberCoroutineScope()
    return remember(standIn, platform) {
        if (standIn == null) {
            platform
        } else {
            { types: List<String> ->
                scope.launch { standIn.open(types)?.let(opened) }
                Unit
            }
        }
    }
}

/** The platform's side of [rememberFileOpener]. */
@Composable
internal expect fun rememberPlatformFileOpener(onOpen: (ShareableFile) -> Unit): (List<String>) -> Unit
