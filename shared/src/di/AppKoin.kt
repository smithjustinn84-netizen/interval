package dev.justinsmith.interval.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.di.bellsModules
import dev.justinsmith.bells.di.bellsStorageModule
import dev.justinsmith.bells.platform.rememberBellsPlatformModule
import dev.justinsmith.interval.platform.rememberPlatformModule
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication

/**
 * [platformModule] supplies storage locations, the cue player and timestamp formatter; [storage] the
 * database and DataStore built from them (tests swap in fakes). [bellsPlatform] and [bellsStorage] do the
 * same for Lift, whose own database sits beside this one.
 */
fun appModules(
    platformModule: Module,
    storage: Module = storageModule,
    bellsPlatform: Module,
    bellsStorage: Module = bellsStorageModule,
): List<Module> = listOf(platformModule, storage, dataModule) + bellsModules(bellsPlatform, bellsStorage)

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
