package dev.justinsmith.bells.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.justinsmith.bells.data.BellsDatabase
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.MIGRATION_3_4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The scope storage writes run on. It lives as long as the container, which cancels it on close. */
val BellsStorageScope = named("bellsStorage")

/** The database and its DAOs, from the platform's builder. Closing the container closes the database. */
val bellsStorageModule: Module = module {
    single<CoroutineScope>(BellsStorageScope) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }.withOptions {
        onClose { it?.cancel() }
    }
    single<BellsDatabase> {
        get<RoomDatabase.Builder<BellsDatabase>>()
            .addMigrations(MIGRATION_3_4)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }.withOptions {
        onClose { it?.close() }
    }
    single { get<BellsDatabase>().sessionDao() }
    single { get<BellsDatabase>().bellDao() }
    single { get<BellsDatabase>().followingDao() }
    single { get<BellsDatabase>().customProgramDao() }
    single { get<BellsDatabase>().customExerciseDao() }
}

val bellsDataModule: Module = module {
    single { BellsRepository(get(), get(), get(), get(), get(), scope = get(BellsStorageScope), clock = get()) }
}

/**
 * Bells' part of the app's container. [platformModule] supplies the database builder and the
 * [dev.justinsmith.bells.platform.Beeper]; [storage] what is built from them. The app's own modules supply
 * the [dev.justinsmith.interval.platform.WallClock], the
 * [dev.justinsmith.interval.platform.TimestampFormatter] and the [dev.justinsmith.bells.data.IntervalActivity].
 */
fun bellsModules(platformModule: Module, storage: Module = bellsStorageModule): List<Module> =
    listOf(platformModule, storage, bellsDataModule)
