package dev.justinsmith.interval.di

import dev.justinsmith.interval.today.HomeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Today's ViewModel, the first tab's, over History's log; the length picked is in its entry's `SavedStateHandle`. */
val todayModule: Module = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
