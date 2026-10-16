package dev.justinsmith.interval

import androidx.compose.ui.window.ComposeUIViewController
import dev.justinsmith.interval.platform.StatusBarHost

// PascalCase because Swift calls it by this name (`ViewControllerKt.ViewController()` in iosApp.swift).
@Suppress("ktlint:standard:function-naming", "detekt.FunctionNaming")
fun ViewController() = StatusBarHost(ComposeUIViewController { App() })
