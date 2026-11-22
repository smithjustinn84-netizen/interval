package dev.justinsmith.interval.di

import android.content.Context
import dev.justinsmith.interval.platform.corePlatformModule
import dev.justinsmith.interval.platform.platformModule
import dev.justinsmith.interval.platform.polarSensorModule
import dev.justinsmith.lift.platform.liftPlatformModule
import org.koin.core.KoinApplication
import org.koin.dsl.module

/**
 * Starts the app's container from Android's modules, over the application's [context]: `IntervalApplication`'s. The
 * interval side's platform is its services and, apart, Polar's heart-rate sensor (`interval-polar-platform`).
 */
fun initAppKoin(context: Context): KoinApplication = initAppKoin(
    corePlatformModule(context),
    module { includes(platformModule(context), polarSensorModule(context)) },
    liftPlatformModule(context),
)

/** Android's `IntervalApplication` starts it before any Activity, service or receiver runs. */
internal actual fun processKoin(): KoinApplication =
    checkNotNull(startedKoin()) {
        "IntervalApplication.onCreate starts the container: is it the manifest's application?"
    }
