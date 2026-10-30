package dev.justinsmith.interval.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.RepositoryIntervalActivity
import dev.justinsmith.interval.export.AppDataExporter
import dev.justinsmith.interval.platform.rememberPlatformModule
import dev.justinsmith.interval.settings.DataExporter
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.di.liftModules
import dev.justinsmith.lift.di.liftStorageModule
import dev.justinsmith.lift.platform.rememberLiftPlatformModule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

/**
 * [platformModule] supplies storage locations, the cue player, the timestamp formatter and the plan's
 * reminders; [storage] the database and DataStore built from them (tests swap in fakes); [historyModule]
 * keeps those reminders in step with the plan. [liftPlatform] and [liftStorage] do the
 * same for Lift, whose own database sits beside this one. The app adds the export, which reads both.
 */
fun appModules(
    platformModule: Module,
    storage: Module = storageModule,
    liftPlatform: Module,
    liftStorage: Module = liftStorageModule,
): List<Module> =
    listOf(platformModule, storage, dataModule, historyModule) + liftModules(liftPlatform, liftStorage) + appModule

/**
 * What only the app can build, seeing both sides: the export, over both sides' storage, and what Lift reads of
 * the interval side (the workouts for its week, and the goal set on Plan).
 */
private val appModule = module {
    single<DataExporter> { AppDataExporter(get(), get(), get(), get(), get()) }
    single<IntervalActivity> { RepositoryIntervalActivity(get(), get(StorageScope)) }
}

/**
 * Keeps the Koin container alive across Android configuration changes and closes it when the app
 * goes away: the Activity finishing, or the desktop window closing. `setContent` re-runs on rotation, so a
 * plain `remember` would build a fresh container while entry-scoped ViewModels still held the old one's
 * instances.
 */
private class KoinHolder(val app: KoinApplication) : ViewModel() {
    override fun onCleared() {
        // Closing cancels the storage scopes, which drops any write still queued: a workout recorded or a
        // setting changed a moment ago. So both repositories land theirs first. This blocks the main thread,
        // but only on the storage dispatcher (never on Main), only as the app goes, and for a bounded time.
        runBlocking {
            withTimeoutOrNull(DRAIN_TIMEOUT) {
                app.koin.get<IntervalRepository>().close()
                app.koin.get<LiftRepository>().close()
            }
        }
        app.close()
    }
}

/** Longer than any write takes; a write stuck past it is lost, as it was before writes were drained. */
private val DRAIN_TIMEOUT = 2.seconds

/**
 * The app's container, isolated rather than started globally: `startKoin` would need an Android
 * `Application` class and initialization in each entry point, and this way `App()` stays
 * parameterless on every platform.
 */
@Composable
fun rememberAppKoin(): KoinApplication {
    val platformModule = rememberPlatformModule()
    val liftPlatform = rememberLiftPlatformModule()
    return viewModel {
        KoinHolder(koinApplication { modules(appModules(platformModule, liftPlatform = liftPlatform)) })
    }.app
}
