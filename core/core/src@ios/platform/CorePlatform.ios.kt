package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.core.module.Module
import platform.Foundation.NSBundle

@Composable
actual fun rememberCorePlatformModule(): Module = remember {
    corePlatformModule(appInfo = { IosAppInfo() }, sharer = { IosSharer() })
}

/** The bundle's short version string: `MARKETING_VERSION` in the Xcode project. */
private class IosAppInfo : AppInfo {
    override val version: String? =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
}
