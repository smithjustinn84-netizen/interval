package dev.justinsmith.interval.platform

import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import kotlin.time.TimeSource

/*
 * Each platform's `corePlatformModule` (Android's of a `Context`, iOS's and the desktop's of nothing) gives the
 * Koin definitions for what both sides take from the platform: the [WallClock], the [IdGenerator] new records are
 * stored under, the monotonic [realElapsedTime] their timers count by, the [TimestampFormatter], [AppInfo], a
 * [Sharer], the [HealthStore] and the [Speaker] both sides speak through. The app's entry point builds it once per
 * process and lists it before either side's modules, so Lift needs nothing of the interval side's to run. Tests use
 * core-testing's `fakeCorePlatformModule`.
 */

/**
 * The definitions every platform shares, over its own [appInfo], [sharer], [health] and [speaker]. The speaker is
 * built on first use, as a screen that cues asks for it, and let go as the container closes.
 */
internal fun sharedCorePlatformModule(
    appInfo: () -> AppInfo,
    sharer: () -> Sharer,
    health: () -> HealthStore = { NoHealthStore },
    speaker: () -> Speaker = { NoSpeaker },
): Module = module {
    single { WallClock(::epochMillisNow) }
    single { IdGenerator.Random }
    // The timers' clock, the workout's and Lift's: time that counts through the device's sleep.
    single<TimeSource.WithComparableMarks> { realElapsedTime }
    single<TimestampFormatter> { systemTimestampFormatter() }
    single<AppInfo> { appInfo() }
    single<Sharer> { sharer() }
    single<HealthStore> { health() }
    single<Speaker> { speaker() }.withOptions {
        onClose { it?.release() }
    }
}
