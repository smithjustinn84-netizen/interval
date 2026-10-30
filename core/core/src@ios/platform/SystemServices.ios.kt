@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
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

// The idle timer is one app-wide flag, so one count for the app.
private val awakeHolds = AwakeHolds()

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        if (awakeHolds.acquire()) UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { if (awakeHolds.release()) UIApplication.sharedApplication.idleTimerDisabled = false }
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
