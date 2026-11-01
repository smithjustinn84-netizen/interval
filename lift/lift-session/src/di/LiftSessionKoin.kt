package dev.justinsmith.lift.di

import dev.justinsmith.lift.history.LiftDetailViewModel
import dev.justinsmith.lift.programs.ProgramCopy
import dev.justinsmith.lift.programs.ProgramEditorViewModel
import dev.justinsmith.lift.programs.ProgramProgressViewModel
import dev.justinsmith.lift.programs.ProgramsViewModel
import dev.justinsmith.lift.session.SessionViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The session's ViewModel, the programs' (their list, editor and progress) and a finished session's. The editor
 * and a finished session take their route's uid (`parametersOf(uid)`); the editor's `SavedStateHandle` comes
 * from its back-stack entry.
 */
val liftSessionModule: Module = module {
    viewModel { SessionViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModelOf(::ProgramsViewModel)
    viewModel { (uid: String?, copy: ProgramCopy?) -> ProgramEditorViewModel(get(), get(), uid, get(), copy) }
    viewModelOf(::ProgramProgressViewModel)
    viewModel { (uid: String) -> LiftDetailViewModel(uid, get(), get()) }
}
