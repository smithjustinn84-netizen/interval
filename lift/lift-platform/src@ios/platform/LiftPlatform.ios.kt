@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.lift.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.justinsmith.lift.data.LiftDatabase
import dev.justinsmith.lift.domain.EmomCue
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.module
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@Composable
actual fun rememberLiftPlatformModule(): Module = remember {
    module {
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
        // System sounds, as Interval's cues use.
        single<Beeper> {
            Beeper { cue ->
                val sound: UInt = when (cue) {
                    EmomCue.Countdown -> 1103u
                    EmomCue.Start -> 1005u
                    EmomCue.Finished -> 1025u
                }
                AudioServicesPlaySystemSound(sound)
            }
        }
        single<RestAlerts> { IosRestAlerts() }.withOptions {
            onClose { (it as? IosRestAlerts)?.release() }
        }
    }
}
