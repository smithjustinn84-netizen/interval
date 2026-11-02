package dev.justinsmith.interval.di

import dev.justinsmith.interval.platform.ActiveWorkout
import dev.justinsmith.interval.workout.WorkoutRecorder
import dev.justinsmith.interval.workout.WorkoutSession
import dev.justinsmith.interval.workout.WorkoutTicker
import dev.justinsmith.interval.workout.WorkoutViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The running workout, one per process: its [WorkoutSession], which the platform's off-screen keepers reach as the
 * [ActiveWorkout], with its clock and its recorder; and the page's ViewModel, for the route's id
 * (`parametersOf(id)`).
 */
val workoutModule: Module = module {
    single { WorkoutTicker(get()) }.withOptions {
        onClose { it?.close() }
    }
    single { WorkoutRecorder(get(), get(), get(), get(), get(), get(), get()) }
    single { WorkoutSession(get(), get(), get(), get(), get(), get(), get()) } bind ActiveWorkout::class
    viewModel { (id: Long) -> WorkoutViewModel(id, get(), get()) }
}
