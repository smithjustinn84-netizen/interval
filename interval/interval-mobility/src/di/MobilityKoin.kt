package dev.justinsmith.interval.di

import dev.justinsmith.interval.mobility.FlowCopy
import dev.justinsmith.interval.mobility.FlowEditorViewModel
import dev.justinsmith.interval.mobility.MobilityViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The Mobility page's ViewModel, and the flow editor's, for the flow its route names or the shipped one it copies
 * (`parametersOf(id, copy)`), its draft in the entry's `SavedStateHandle`.
 */
val mobilityModule: Module = module {
    viewModelOf(::MobilityViewModel)
    viewModel { (id: Long?, copy: FlowCopy?) -> FlowEditorViewModel(get(), get(), get(), id, copy, get()) }
}
