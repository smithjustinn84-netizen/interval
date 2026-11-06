package dev.justinsmith.interval.di

import dev.justinsmith.interval.mobility.MobilityViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The Mobility page's ViewModel. */
val mobilityModule: Module = module {
    viewModelOf(::MobilityViewModel)
}
