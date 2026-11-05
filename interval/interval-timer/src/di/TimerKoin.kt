package dev.justinsmith.interval.di

import dev.justinsmith.interval.home.TimerViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The Timer's ViewModel. */
val timerModule: Module = module {
    viewModelOf(::TimerViewModel)
}
