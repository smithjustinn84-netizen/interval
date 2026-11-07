@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.lift.platform

import androidx.room.Room
import androidx.room.RoomDatabase
import dev.justinsmith.lift.data.LiftDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** What Lift needs from iOS. */
fun liftPlatformModule(): Module = module {
    // Application Support rather than Documents, which the user can see when file sharing is on.
    single<RoomDatabase.Builder<LiftDatabase>> {
        val directory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )?.path
        Room.databaseBuilder<LiftDatabase>(name = "${requireNotNull(directory)}/$LIFT_DATABASE")
    }
    single<Beeper> { ToneBeeper(get()) }
    single<SessionKeepAlive> { IosSessionKeepAlive(get()) }.withOptions {
        onClose { (it as? IosSessionKeepAlive)?.release() }
    }
}
