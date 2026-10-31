package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin definitions for what both sides take from the platform: the [WallClock], the [TimestampFormatter],
 * [AppInfo] and a [Sharer]. The app lists it before either side's modules, so Lift needs nothing of the
 * interval side's to run. Built in composition because Android needs a `Context`. Tests use core-testing's
 * `fakeCorePlatformModule`.
 */
@Composable
expect fun rememberCorePlatformModule(): Module

/** The definitions every platform shares, over its own [appInfo] and [sharer]. */
internal fun corePlatformModule(appInfo: () -> AppInfo, sharer: () -> Sharer): Module = module {
    single { WallClock(::epochMillisNow) }
    single<TimestampFormatter> { systemTimestampFormatter() }
    single<AppInfo> { appInfo() }
    single<Sharer> { sharer() }
}
