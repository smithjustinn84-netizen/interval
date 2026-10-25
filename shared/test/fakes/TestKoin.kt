package dev.justinsmith.interval.fakes

import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.RecordingBeeper
import dev.justinsmith.bells.fakes.fakeBellsPlatformModule
import dev.justinsmith.bells.fakes.fakeBellsStorageModule
import dev.justinsmith.interval.di.appModules
import dev.justinsmith.interval.platform.WallClock
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The app's real container over fake platform services and storage, for `AppContent`, Lift's included.
 * Time stands at [now], or follows [clock] when a test moves it.
 */
fun testKoin(
    platformModule: Module = fakePlatformModule(),
    storage: FakeStorage = FakeStorage(),
    now: Long = 0L,
    lift: FakeBellsStorage = FakeBellsStorage(),
    beeper: RecordingBeeper = RecordingBeeper(),
    clock: WallClock = WallClock { now },
): KoinApplication = koinApplication {
    modules(
        appModules(
            platformModule,
            fakeStorageModule(storage),
            bellsPlatform = fakeBellsPlatformModule(beeper),
            bellsStorage = fakeBellsStorageModule(lift),
        ) + module { single { clock } },
    )
}
