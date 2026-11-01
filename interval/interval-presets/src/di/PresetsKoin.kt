package dev.justinsmith.interval.di

import dev.justinsmith.interval.presets.PresetsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The Presets page's ViewModel. */
val presetsModule: Module = module {
    viewModelOf(::PresetsViewModel)
}
