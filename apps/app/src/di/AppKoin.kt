package dev.justinsmith.interval.di

import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.RepositoryIntervalActivity
import dev.justinsmith.interval.data.WeekStandingSource
import dev.justinsmith.interval.export.AppDataExporter
import dev.justinsmith.interval.export.AppDataImporter
import dev.justinsmith.interval.settings.DataExporter
import dev.justinsmith.interval.settings.DataImporter
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.di.liftFeatureModules
import dev.justinsmith.lift.di.liftModules
import dev.justinsmith.lift.di.liftStorageModule
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

/**
 * The definitions of the app's container: [initAppKoin]'s in production, `testKoin()`'s over fakes in tests.
 * [corePlatform] supplies what both sides take: the wall clock, the timestamp formatter, the app's version and
 * the sharer. [platformModule] supplies storage locations, the cue player, the background workout and the
 * plan's reminders; [storage] the database and DataStore built from them (tests swap in fakes);
 * [historyModule] keeps those reminders in step with the plan. [liftPlatform] and [liftStorage] do the same
 * for Lift, whose own database sits beside this one. Each feature's module declares its ViewModels. The app
 * adds the export, which reads both.
 */
fun appModules(
    corePlatform: Module,
    platformModule: Module,
    storage: Module = storageModule,
    liftPlatform: Module,
    liftStorage: Module = liftStorageModule,
): List<Module> =
    listOf(corePlatform, platformModule, storage, dataModule, historyModule) +
        intervalFeatureModules +
        liftModules(liftPlatform, liftStorage) +
        liftFeatureModules +
        appModule

/** The ViewModels of the interval side's features; History's are in [historyModule]. */
private val intervalFeatureModules =
    listOf(timerModule, presetsModule, mobilityModule, workoutModule, settingsModule)

/**
 * What only the app can build, seeing both sides: the export and the import, over both sides' storage, what
 * Lift reads of the interval side (the workouts for its week, and the goal set on Plan), and the week as the
 * home-screen widget shows it (built on first use: only Android's host asks for it).
 */
private val appModule = module {
    single<DataExporter> { AppDataExporter(get(), get(), get(), get(), get()) }
    single<DataImporter> { AppDataImporter(get(), get()) }
    single<IntervalActivity> { RepositoryIntervalActivity(get(), get(), get(StorageScope)) }
    single { WeekStandingSource(get(), get(), get(), get(), get(StorageScope)) }
}

/**
 * The process's one container. Each entry point starts it before its first frame (Android's
 * `IntervalApplication`, desktop's `main`; iOS on first use, through [processKoin]), always on the main thread,
 * which is what makes the plain field safe. It is never closed on Android or iOS, whose processes end without
 * notice; desktop closes it with the window ([stopAppKoin]), after [drainStorage].
 */
private var started: KoinApplication? = null

/**
 * Starts the app's container, once per process, from the platform's three modules: [corePlatform] (what both
 * sides take), [platform] (the interval side's) and [liftPlatform]. A second call returns the first container.
 * Tests build their own with `testKoin()` and never call this.
 */
fun initAppKoin(corePlatform: Module, platform: Module, liftPlatform: Module): KoinApplication =
    started ?: startKoin { modules(appModules(corePlatform, platform, liftPlatform = liftPlatform)) }
        .also { started = it }

/**
 * The container [App] composes with when it is given none: the one the entry point started, or on iOS and
 * the desktop one started now (so Hot Reload's entry point, which runs no `main`, still has one).
 */
internal expect fun processKoin(): KoinApplication

/** The container [initAppKoin] started, or null before it ran. */
internal fun startedKoin(): KoinApplication? = started

/**
 * Lands both repositories' queued writes before the container closes, which cancels their storage scopes:
 * a workout recorded or a setting changed a moment ago. For desktop's window closing, the one place the
 * process ends on the app's own terms. Bounded: a write stuck past [DRAIN_TIMEOUT] is lost.
 */
suspend fun KoinApplication.drainStorage() {
    withTimeoutOrNull(DRAIN_TIMEOUT) {
        koin.get<IntervalRepository>().close()
        koin.get<LiftRepository>().close()
    }
}

/** Closes the container [initAppKoin] started: desktop's, as its window closes, after [drainStorage]. */
fun stopAppKoin() {
    stopKoin()
    started = null
}

/** Longer than any write takes. */
private val DRAIN_TIMEOUT = 2.seconds
