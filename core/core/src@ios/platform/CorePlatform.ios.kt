package dev.justinsmith.interval.platform

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle

/**
 * What both sides take from iOS: Apple Health is the health store, the speech synthesizer the voice, AVAudio the
 * tones. The Live Activities are a [LiveActivityRelay] the app points at Swift's once it starts (`WidgetFeed.kt`).
 */
fun corePlatformModule(): Module = module {
    includes(
        sharedCorePlatformModule(
            appInfo = { IosAppInfo() },
            sharer = { IosSharer() },
            health = { HealthKitStore() },
            speaker = { IosSpeaker() },
            tones = { IosTonePlayer() },
        ),
    )
    single { LiveActivityRelay() }
    single<LiveActivities> { get<LiveActivityRelay>() }
}

/** The bundle's short version string: `MARKETING_VERSION` in the Xcode project. */
private class IosAppInfo : AppInfo {
    override val version: String? =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
}
