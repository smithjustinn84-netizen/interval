package dev.justinsmith.interval.platform

import android.content.Context
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module

/**
 * Android's heart-rate sensor, over the application's [context]: one [PolarClassSensor] a process, the
 * [ClassSensor] a class is recorded on and the [LiveHeartRate] a workout or a session reads, shut down with the
 * container.
 */
fun polarSensorModule(context: Context): Module = context.applicationContext.let { app ->
    module {
        single { PolarClassSensor(app, get()) }.withOptions {
            onClose { it?.close() }
        }
        single<ClassSensor> { get<PolarClassSensor>() }
        single<LiveHeartRate> { get<PolarClassSensor>() }
    }
}
