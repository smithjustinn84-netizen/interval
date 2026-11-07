package dev.justinsmith.interval.platform

import org.koin.core.module.Module

/** What both sides take from the desktop, which has no health store and no voice. */
fun corePlatformModule(): Module =
    sharedCorePlatformModule(appInfo = { DesktopAppInfo() }, sharer = { DesktopSharer() })

/** jpackage stamps the version into a packaged app; a run from source carries none. */
private class DesktopAppInfo : AppInfo {
    override val version: String? = System.getProperty("jpackage.app-version")
}
