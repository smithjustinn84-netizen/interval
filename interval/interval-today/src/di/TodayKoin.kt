package dev.justinsmith.interval.di

import dev.justinsmith.interval.sensor.ClassSensorViewModel
import dev.justinsmith.interval.today.HomeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Today's ViewModels: the first tab's, over History's log (the length picked is in its entry's `SavedStateHandle`),
 * and the card's that records a class on a sensor and syncs it.
 */
val todayModule: Module = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ClassSensorViewModel(get(), get(), get(), get(), get()) }
}
