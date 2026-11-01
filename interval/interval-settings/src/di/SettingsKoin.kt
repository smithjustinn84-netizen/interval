package dev.justinsmith.interval.di

import dev.justinsmith.interval.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The Settings page's ViewModel. The export and import it takes are the app's (`appModules`). */
val settingsModule: Module = module {
    viewModelOf(::SettingsViewModel)
}
