package dev.justinsmith.interval

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import dev.justinsmith.interval.nav.Shortcut
import dev.justinsmith.interval.platform.StatusBarHost

/** A Home Screen quick action to open, from [openShortcut]; null once the app has opened it. */
private var shortcut by mutableStateOf<Shortcut?>(null)

// PascalCase because Swift calls it by this name (`ViewControllerKt.ViewController()` in iosApp.swift).
@Suppress("ktlint:standard:function-naming", "detekt.FunctionNaming")
fun ViewController() = StatusBarHost(ComposeUIViewController { App(shortcut, onShortcutOpen = { shortcut = null }) })

/**
 * Opens the quick action whose type is [type], a [Shortcut.id] (`UIApplicationShortcutItems` in Info.plist), as
 * Android's `MainActivity` opens a launcher shortcut: at launch or with the app running. Called by the scene
 * delegate on the main thread. False for a type this version doesn't know.
 */
fun openShortcut(type: String): Boolean {
    val opened = Shortcut.of(type) ?: return false
    shortcut = opened
    return true
}
