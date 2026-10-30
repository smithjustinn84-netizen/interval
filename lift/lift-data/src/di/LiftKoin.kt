package dev.justinsmith.lift.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.justinsmith.interval.storage.storageScope
import dev.justinsmith.lift.data.ExerciseFilterStore
import dev.justinsmith.lift.data.LiftActivity
import dev.justinsmith.lift.data.LiftDatabase
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.data.MIGRATION_3_4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The scope storage writes run on. It lives as long as the container, which cancels it on close: drain the
 * repository's writes first ([LiftRepository.close]).
 */
val LiftStorageScope = named("liftStorage")

/** The database and its DAOs, from the platform's builder. Closing the container closes the database. */
val liftStorageModule: Module = module {
    single<CoroutineScope>(LiftStorageScope) { storageScope(Dispatchers.IO, "LiftStorageScope") }.withOptions {
        onClose { it?.cancel() }
    }
    single<LiftDatabase> {
        get<RoomDatabase.Builder<LiftDatabase>>()
            .addMigrations(MIGRATION_3_4)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }.withOptions {
        onClose { it?.close() }
    }
    single { get<LiftDatabase>().sessionDao() }
    single { get<LiftDatabase>().bellDao() }
    single { get<LiftDatabase>().followingDao() }
    single { get<LiftDatabase>().customProgramDao() }
    single { get<LiftDatabase>().customExerciseDao() }
    single { get<LiftDatabase>().bandDao() }
}

val liftDataModule: Module = module {
    single { LiftRepository(get(), get(), get(), get(), get(), get(), scope = get(LiftStorageScope), clock = get()) }
        .bind<LiftActivity>()
    single { ExerciseFilterStore() }
}

/**
 * Lift's part of the app's container. [platformModule] supplies the database builder, the `Beeper` and the
 * `RestAlerts` (`lift-platform`'s `rememberLiftPlatformModule`); [storage] what is built from them. The
 * app's own modules supply the [dev.justinsmith.interval.platform.WallClock], the
 * [dev.justinsmith.interval.platform.TimestampFormatter] and the [dev.justinsmith.lift.data.IntervalActivity].
 */
fun liftModules(platformModule: Module, storage: Module = liftStorageModule): List<Module> =
    listOf(platformModule, storage, liftDataModule)
