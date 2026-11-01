package dev.justinsmith.interval.di

import dev.justinsmith.interval.platform.corePlatformModule
import dev.justinsmith.interval.platform.platformModule
import dev.justinsmith.lift.platform.liftPlatformModule
import org.koin.core.KoinApplication

/** Starts the app's container from this platform's modules, or returns the one already started. */
fun initAppKoin(): KoinApplication =
    startedKoin() ?: initAppKoin(corePlatformModule(), platformModule(), liftPlatformModule())

internal actual fun processKoin(): KoinApplication = initAppKoin()
