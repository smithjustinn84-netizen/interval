package dev.justinsmith.interval

import android.app.Application
import dev.justinsmith.interval.di.initAppKoin

/**
 * Starts the app's container once per process, before any Activity, service or receiver runs, so all of them
 * share it and it outlives each Activity: backing out and reopening keeps the same databases open and the
 * same workout running.
 */
class IntervalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initAppKoin(this)
    }
}
