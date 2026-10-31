package dev.justinsmith.interval.fakes

import dev.justinsmith.interval.di.appModules
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.RecordingBeeper
import dev.justinsmith.lift.fakes.fakeLiftPlatformModule
import dev.justinsmith.lift.fakes.fakeLiftStorageModule
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.TimeSource

/**
 * The app's real container over fake platform services and storage, for `AppContent`, Lift's included.
 * The wall clock stands at [now], or follows [clock] when a test moves it. A running workout counts
 * [timeSource]: real time unless a test passes a `TestTimeSource` to move it by hand.
 */
fun testKoin(
    platformModule: Module = fakePlatformModule(),
    storage: FakeStorage = FakeStorage(),
    now: Long = 0L,
    lift: FakeLiftStorage = FakeLiftStorage(),
    beeper: RecordingBeeper = RecordingBeeper(),
    clock: WallClock = WallClock { now },
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
): KoinApplication = koinApplication {
    modules(
        appModules(
            fakeCorePlatformModule(clock),
            platformModule,
            fakeStorageModule(storage),
            liftPlatform = fakeLiftPlatformModule(beeper),
            liftStorage = fakeLiftStorageModule(lift),
        ) + module {
            single<TimeSource.WithComparableMarks> { timeSource }
        },
    )
}
