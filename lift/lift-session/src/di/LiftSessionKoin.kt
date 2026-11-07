package dev.justinsmith.lift.di

import dev.justinsmith.lift.history.LiftDetailViewModel
import dev.justinsmith.lift.platform.ActiveTimers
import dev.justinsmith.lift.programs.ProgramCopy
import dev.justinsmith.lift.programs.ProgramEditorViewModel
import dev.justinsmith.lift.programs.ProgramProgressViewModel
import dev.justinsmith.lift.programs.ProgramsViewModel
import dev.justinsmith.lift.session.EasierDay
import dev.justinsmith.lift.session.FinishSession
import dev.justinsmith.lift.session.SessionHealth
import dev.justinsmith.lift.session.SessionTimers
import dev.justinsmith.lift.session.SessionViewModel
import dev.justinsmith.lift.session.TodayPlanEditor
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The session's timers, one for the process (the platform reaches them as [ActiveTimers]), and its health store
 * writer, which outlives a summary ([SessionHealth]); the session's ViewModel with the parts it is made of (today's
 * [TodayPlanEditor] and the [FinishSession] behind its summary, one each),
 * the programs' (their list, editor and progress) and a finished session's. The editor
 * and a finished session take their route's uid (`parametersOf(uid)`); the editor's `SavedStateHandle` comes
 * from its back-stack entry.
 */
val liftSessionModule: Module = module {
    single { SessionTimers(get(), get(), get(), get(), get()) }.withOptions {
        onClose { it?.close() }
    } bind ActiveTimers::class
    single { SessionHealth(get(), get()) }.withOptions {
        onClose { it?.close() }
    }
    factory { TodayPlanEditor(get(), get(), get()) }
    factory { FinishSession(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { EasierDay(get(), get(), get(), get(), get(), get(), get()) }
    viewModel {
        SessionViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModelOf(::ProgramsViewModel)
    viewModel { (uid: String?, copy: ProgramCopy?) ->
        ProgramEditorViewModel(get(), get(), get(), get(), get(), uid, get(), copy)
    }
    viewModelOf(::ProgramProgressViewModel)
    viewModel { (uid: String) -> LiftDetailViewModel(uid, get(), get(), get(), get()) }
}
