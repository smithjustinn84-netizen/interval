package dev.justinsmith.interval.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.justinsmith.interval.data.AppDatabase
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.PlanStore
import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.data.WorkoutProgressStore
import dev.justinsmith.interval.data.buildAppDatabase
import dev.justinsmith.interval.platform.PlatformStorage
import dev.justinsmith.interval.storage.storageScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The scope storage writes run on. It lives as long as the container, which cancels it on close: drain the
 * repository's writes first ([IntervalRepository.close]).
 */
val StorageScope = named("storage")

/**
 * The settings DataStore's own scope. DataStore wants one active instance per file, which a container that lives
 * as long as the process gives it; closing the container (desktop's exit, a test) cancels the scope, and the file
 * is free once the scope has finished.
 */
val PreferencesScope = named("preferences")

/**
 * The database, its DAOs and the settings DataStore, in the files [PlatformStorage] names. The container lives
 * as long as the process; closing it closes the database and cancels [StorageScope], dropping any write still
 * queued, and lets go of the DataStore's file.
 */
val storageModule: Module = module {
    single<CoroutineScope>(StorageScope) { storageScope(Dispatchers.IO, "StorageScope") }.withOptions {
        onClose { it?.cancel() }
    }
    single<AppDatabase> {
        get<PlatformStorage>().databaseBuilder().buildAppDatabase()
    }.withOptions {
        onClose { it?.close() }
    }
    single { get<AppDatabase>().historyDao() }
    single { get<AppDatabase>().presetDao() }
    single { get<AppDatabase>().plannedDao() }
    single { get<AppDatabase>().importDao() }
    single<CoroutineScope>(PreferencesScope) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }.withOptions {
        onClose { it?.cancel() }
    }
    single<DataStore<Preferences>> {
        val path = get<PlatformStorage>().preferencesPath
        PreferenceDataStoreFactory.createWithPath(scope = get(PreferencesScope), produceFile = { path.toPath() })
    }
}

/**
 * [IntervalRepository] over [storageModule], and each of its repositories as the store its screens take
 * ([SettingsStore] and [WorkoutProgressStore] the settings, [PresetStore], [HistoryStore], [PlanStore]). The wall
 * clock and the ids it takes are core's (`corePlatformModule`).
 */
val dataModule: Module = module {
    single {
        IntervalRepository(
            preferences = get(),
            historyDao = get(),
            presetDao = get(),
            plannedDao = get(),
            importDao = get(),
            scope = get(StorageScope),
            clock = get(),
            ids = get(),
        )
    }
    single<SettingsStore> { get<IntervalRepository>().settings }
    single<WorkoutProgressStore> { get<IntervalRepository>().settings }
    single<PresetStore> { get<IntervalRepository>().presets }
    single<HistoryStore> { get<IntervalRepository>().history }
    single<PlanStore> { get<IntervalRepository>().plans }
}
