package dev.justinsmith.interval

import androidx.compose.ui.window.ComposeUIViewController

// PascalCase because Swift calls it by this name (`ViewControllerKt.ViewController()` in iosApp.swift).
@Suppress("ktlint:standard:function-naming")
fun ViewController() = ComposeUIViewController { App() }
