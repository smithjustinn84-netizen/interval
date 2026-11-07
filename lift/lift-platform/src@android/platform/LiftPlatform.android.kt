package dev.justinsmith.lift.platform

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.justinsmith.lift.data.LiftDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

/** What Lift needs from Android, over the application's [context]. */
fun liftPlatformModule(context: Context): Module = context.applicationContext.let { app ->
    module {
        single<RoomDatabase.Builder<LiftDatabase>> {
            Room.databaseBuilder<LiftDatabase>(app, app.getDatabasePath(LIFT_DATABASE).absolutePath)
        }
        single<Beeper> { ToneBeeper(get()) }
        single<SessionKeepAlive> { AndroidSessionKeepAlive(app, get()) }
    }
}
