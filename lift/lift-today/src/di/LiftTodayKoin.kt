package dev.justinsmith.lift.di

import dev.justinsmith.lift.gear.GearViewModel
import dev.justinsmith.lift.today.TodayViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The Lift tab's ViewModel and the gear's. */
val liftTodayModule: Module = module {
    viewModel { TodayViewModel(get(), get(), get(), get()) }
    viewModelOf(::GearViewModel)
}
