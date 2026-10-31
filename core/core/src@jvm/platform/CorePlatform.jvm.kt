package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.core.module.Module

@Composable
actual fun rememberCorePlatformModule(): Module = remember {
    corePlatformModule(appInfo = { DesktopAppInfo() }, sharer = { DesktopSharer() })
}

/** jpackage stamps the version into a packaged app; a run from source carries none. */
private class DesktopAppInfo : AppInfo {
    override val version: String? = System.getProperty("jpackage.app-version")
}
