package dev.justinsmith.interval.di

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.justinsmith.interval.data.AppDatabase
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.PlatformStorage
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.epochMillisNow
import dev.justinsmith.interval.platform.rememberPlatformModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.Path.Companion.toPath
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** The scope storage writes run on. It lives as long as the container, which cancels it on close. */
val StorageScope = named("storage")

/**
 * The database, its DAOs and the settings DataStore, in the files [PlatformStorage] names. Closing the
 * container closes the database and cancels [StorageScope], dropping any write still queued. The DataStore
 * outlives the container: see [preferencesAt].
 */
val storageModule: Module = module {
    single<CoroutineScope>(StorageScope) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }.withOptions {
        onClose { it?.cancel() }
    }
    single<AppDatabase> {
        get<PlatformStorage>().databaseBuilder()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }.withOptions {
        onClose { it?.close() }
    }
    single { get<AppDatabase>().historyDao() }
    single { get<AppDatabase>().presetDao() }
    single { get<AppDatabase>().plannedDao() }
    single<DataStore<Preferences>> { preferencesAt(get<PlatformStorage>().preferencesPath) }
}

private val preferenceStores = mutableMapOf<String, DataStore<Preferences>>()

/**
 * The process's one DataStore for [path]. DataStore allows a single active instance per file, and only
 * releases the file once its scope has finished cancelling, which a new container can beat: on Android the
 * container is rebuilt as soon as the Activity reopens in the same process. So each store lives as long
 * as the process. Containers are built in composition, on the main thread, so the map needs no lock.
 */
private fun preferencesAt(path: String): DataStore<Preferences> = preferenceStores.getOrPut(path) {
    PreferenceDataStoreFactory.createWithPath(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { path.toPath() },
    )
}

/** One Koin module per domain; add new ones to [appModules]. */
val dataModule: Module = module {
    single { WallClock(::epochMillisNow) }
    single {
        IntervalRepository(
            settings = get(),
            historyDao = get(),
            presetDao = get(),
            plannedDao = get(),
            scope = get(StorageScope),
            clock = get(),
        )
    }
}

/**
 * [platformModule] supplies storage locations, the cue player and timestamp formatter; [storage] the
 * database and DataStore built from them (tests swap in fakes).
 */
fun appModules(platformModule: Module, storage: Module = storageModule): List<Module> =
    listOf(platformModule, storage, dataModule)

/**
 * Keeps the Koin container alive across Android configuration changes and closes it when the app
 * goes away. `setContent` re-runs on rotation, so a plain `remember` would build a fresh container
 * while entry-scoped ViewModels still held the old one's instances.
 */
private class KoinHolder(val app: KoinApplication) : ViewModel() {
    override fun onCleared() = app.close()
}

/**
 * The app's container, isolated rather than started globally: `startKoin` would need an Android
 * `Application` class and initialization in each entry point, and this way `App()` stays
 * parameterless on every platform.
 */
@Composable
fun rememberAppKoin(): KoinApplication {
    val platformModule = rememberPlatformModule()
    return viewModel { KoinHolder(koinApplication { modules(appModules(platformModule)) }) }.app
}
