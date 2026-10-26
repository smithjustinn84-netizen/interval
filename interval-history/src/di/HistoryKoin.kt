package dev.justinsmith.interval.di

import dev.justinsmith.interval.plan.PlanReminderScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What History runs for the life of the container: the [PlanReminderScheduler], built with the container
 * (`createdAtStart`) so the plan's reminders are kept in step from the app's first moment, on the storage scope,
 * which closing the container cancels.
 */
val historyModule: Module = module {
    single(createdAtStart = true) { PlanReminderScheduler(get(), get(), get(), get(), get(StorageScope)) }
}
