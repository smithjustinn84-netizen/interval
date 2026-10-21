package dev.justinsmith.bells.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.justinsmith.bells.data.BellsDatabase
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.siblingSync
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.epochMillisNow
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
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }.withOptions {
        onClose { it?.close() }
    }
    single { get<BellsDatabase>().sessionDao() }
    single { get<BellsDatabase>().bellDao() }
    single { get<BellsDatabase>().siblingDao() }
    single { get<BellsDatabase>().followingDao() }
    single { get<BellsDatabase>().settingsDao() }
}

val bellsDataModule: Module = module {
    single { WallClock(::epochMillisNow) }
    single { BellsRepository(get(), get(), get(), get(), get(), scope = get(BellsStorageScope), clock = get()) }
    // Started with the container, so it publishes as soon as the sessions are read.
    single(createdAtStart = true) { get<BellsRepository>().siblingSync(feed = get(), scope = get(BellsStorageScope)) }
}

/** [platformModule] supplies the database builder, the feed and the formatter; [storage] what is built from them. */
fun bellsModules(platformModule: Module, storage: Module = bellsStorageModule): List<Module> =
    listOf(platformModule, storage, bellsDataModule)
