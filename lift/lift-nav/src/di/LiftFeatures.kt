package dev.justinsmith.lift.di

import org.koin.core.module.Module

/**
 * The Koin modules of Lift's features: the ViewModels of the Lift tab, the session and programs, and the library.
 * The app lists them beside `liftModules`; Lift's flow tests pass them to `liftTestKoin`.
 */
val liftFeatureModules: List<Module> = listOf(liftTodayModule, liftSessionModule, liftLibraryModule)
