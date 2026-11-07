package dev.justinsmith.interval.di

import dev.justinsmith.interval.history.HistoryViewModel
import dev.justinsmith.interval.history.WorkoutDetailViewModel
import dev.justinsmith.interval.plan.PlanReminderScheduler
import dev.justinsmith.interval.plan.PlanViewModel
import dev.justinsmith.interval.platform.PlanReminderSource
import dev.justinsmith.interval.today.HomeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * What History runs for the life of the container, which in the app is the process's: the
 * [PlanReminderScheduler], built with the container (`createdAtStart`) so the plan's reminders are kept in step
 * from the process's first moment, on the storage scope, which closing the container cancels. It is also the
 * [PlanReminderSource] Android's reminder alarm reads as it shows. And the ViewModels of History (its view in the
 * entry's `SavedStateHandle`), Plan and a finished workout (`parametersOf(id)`), and of Today, the first tab, which
 * reads the same log (the length picked in its entry's `SavedStateHandle`).
 */
val historyModule: Module = module {
    single(createdAtStart = true) {
        PlanReminderScheduler(get(), get(), get(), get(), get(), get(StorageScope))
    } bind PlanReminderSource::class
    viewModel { HistoryViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { PlanViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (id: Long) ->
        WorkoutDetailViewModel(id, get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
}
