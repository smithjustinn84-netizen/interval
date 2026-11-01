package dev.justinsmith.interval.platform

import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.TimeSource

/*
 * Each platform's `corePlatformModule` (Android's of a `Context`, iOS's and the desktop's of nothing) gives the
 * Koin definitions for what both sides take from the platform: the [WallClock], the monotonic [realElapsedTime]
 * their timers count by, the [TimestampFormatter], [AppInfo], a [Sharer] and the [HealthStore]. The app's entry
 * point builds it once per process and lists it before either side's modules, so Lift needs nothing of the
 * interval side's to run. Tests use core-testing's `fakeCorePlatformModule`.
 */

/** The definitions every platform shares, over its own [appInfo], [sharer] and [health]. */
internal fun sharedCorePlatformModule(
    appInfo: () -> AppInfo,
    sharer: () -> Sharer,
    health: () -> HealthStore = { NoHealthStore },
): Module = module {
    single { WallClock(::epochMillisNow) }
    // The timers' clock, the workout's and Lift's: time that counts through the device's sleep.
    single<TimeSource.WithComparableMarks> { realElapsedTime }
    single<TimestampFormatter> { systemTimestampFormatter() }
    single<AppInfo> { appInfo() }
    single<Sharer> { sharer() }
    single<HealthStore> { health() }
}
