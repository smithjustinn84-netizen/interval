package dev.justinsmith.interval.platform

import org.koin.core.module.Module
import platform.Foundation.NSBundle

/** What both sides take from iOS: Apple Health is the health store. */
fun corePlatformModule(): Module = sharedCorePlatformModule(
    appInfo = { IosAppInfo() },
    sharer = { IosSharer() },
    health = { HealthKitStore() },
)

/** The bundle's short version string: `MARKETING_VERSION` in the Xcode project. */
private class IosAppInfo : AppInfo {
    override val version: String? =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
}
