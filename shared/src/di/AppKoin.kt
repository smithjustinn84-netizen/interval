package dev.justinsmith.interval.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.di.bellsModules
import dev.justinsmith.bells.di.bellsStorageModule
import dev.justinsmith.bells.platform.rememberBellsPlatformModule
import dev.justinsmith.interval.export.AppDataExporter
import dev.justinsmith.interval.platform.rememberPlatformModule
import dev.justinsmith.interval.settings.DataExporter
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * [platformModule] supplies storage locations, the cue player, the timestamp formatter and the plan's
 * reminders; [storage] the database and DataStore built from them (tests swap in fakes); [historyModule]
 * keeps those reminders in step with the plan. [bellsPlatform] and [bellsStorage] do the
 * same for Lift, whose own database sits beside this one. The app adds the export, which reads both.
 */
fun appModules(
    platformModule: Module,
    storage: Module = storageModule,
    bellsPlatform: Module,
    bellsStorage: Module = bellsStorageModule,
): List<Module> =
    listOf(platformModule, storage, dataModule, historyModule) + bellsModules(bellsPlatform, bellsStorage) + appModule

/** What only the app can build: the export, over both sides' storage. */
private val appModule = module {
    single<DataExporter> { AppDataExporter(get(), get(), get(), get(), get()) }
}

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
    val bellsPlatform = rememberBellsPlatformModule()
    return viewModel {
        KoinHolder(koinApplication { modules(appModules(platformModule, bellsPlatform = bellsPlatform)) })
    }.app
}
