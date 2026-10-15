package dev.justinsmith.interval.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.platform.epochMillisNow
import dev.justinsmith.interval.platform.rememberPlatformModule
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** One Koin module per domain; add new ones to [appModules]. */
val timerModule: Module = module {
    single { IntervalRepository(store = get(), now = ::epochMillisNow) }
}

/** [platformModule] supplies the key-value store, cue player and timestamp formatter. */
fun appModules(platformModule: Module): List<Module> = listOf(platformModule, timerModule)

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
