package dev.justinsmith.interval.di

import dev.justinsmith.interval.workout.WorkoutViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The running workout's ViewModel, for the route's id (`parametersOf(id)`). */
val workoutModule: Module = module {
    viewModel { (id: Long) -> WorkoutViewModel(id, get(), get(), get(), get(), get(), get(), get(), get()) }
}
