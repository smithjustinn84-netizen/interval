@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.uikit.LocalUIViewController
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.apple_health
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import platform.Foundation.NSURL
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

// The idle timer is one app-wide flag, so one count for the app.
private val awakeHolds = AwakeHolds()

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        if (awakeHolds.acquire()) UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { if (awakeHolds.release()) UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

/**
 * iOS: authorization to show notifications, asked for when a workout starts, a Lift session first rests or a
 * reminder is turned on. The system puts the question to the user once, and from then on answers from Settings
 * without asking, so this asks each time rather than keeping count. A refusal costs the notifications only: the
 * workout and the rest run on either way, and the reminders are scheduled into nothing.
 */
@Composable
actual fun AskToShowNotifications() {
    LaunchedEffect(Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }
}

@Composable
actual fun SystemBarIcons(lightIcons: Boolean) {
    val host = LocalUIViewController.current.parentViewController as? StatusBarHost
    SideEffect { host?.lightIcons = lightIcons }
}

/**
 * Hosts [content] and sets the status bar style for it, as [SystemBarIcons] asks. iOS takes the
 * style from the window's root controller, so this must be the root: SwiftUI's host would ignore it.
 * Light icons to begin with, matching the launch screen and the intro.
 */
class StatusBarHost(private val content: UIViewController) : UIViewController(nibName = null, bundle = null) {
    var lightIcons = true
        set(value) {
            if (field == value) return
            field = value
            setNeedsStatusBarAppearanceUpdate()
        }

    override fun preferredStatusBarStyle(): UIStatusBarStyle =
        if (lightIcons) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(content)
        content.view.setFrame(view.bounds)
        content.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(content.view)
        content.didMoveToParentViewController(this)
    }
}

@Composable
actual fun prefersReducedMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }

actual val materialBackPreview: Boolean = false

actual val platformPageTransitions: Boolean = true

actual val healthStoreName: StringResource = Res.string.apple_health

/**
 * Apple Health's permission sheet, asked through [HealthKitStore.connect]. HealthKit shows it once per question,
 * so a user who refused the writes is taken to the Health app, the one place they can be allowed now; the store
 * reads its status again as the app comes back. There is nothing to install: Health is part of iOS.
 */
@Composable
actual fun rememberHealthConnector(store: HealthStore): HealthConnector {
    val scope = rememberCoroutineScope()
    return remember(store, scope) {
        val healthKit = store as? HealthKitStore ?: return@remember NoHealthConnector
        object : HealthConnector {
            override fun connect() {
                scope.launch { if (healthKit.connect()) openHealthApp() }
            }

            override fun install() = Unit
        }
    }
}

/** The Health app, where the app's permissions are under its name. */
private fun openHealthApp() {
    val health = NSURL.URLWithString(HEALTH_APP) ?: return
    UIApplication.sharedApplication.openURL(health, options = emptyMap<Any?, Any>(), completionHandler = null)
}

private const val HEALTH_APP = "x-apple-health://"
