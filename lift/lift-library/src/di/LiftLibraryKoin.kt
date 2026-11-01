package dev.justinsmith.lift.di

import dev.justinsmith.lift.library.ExerciseEditorViewModel
import dev.justinsmith.lift.library.ExerciseLibraryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The library's ViewModel and an exercise editor's, which takes the exercise's uid (null for a new one) and
 * name (`parametersOf(uid, name)`). Both keep their state in their entry's `SavedStateHandle`.
 */
val liftLibraryModule: Module = module {
    viewModel { ExerciseLibraryViewModel(get(), get(), get(), get()) }
    viewModel { (uid: String?, name: String) -> ExerciseEditorViewModel(get(), get(), uid, name) }
}
