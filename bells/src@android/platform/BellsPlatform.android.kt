package dev.justinsmith.bells.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.justinsmith.bells.data.BellsDatabase
import dev.justinsmith.core.sync.AndroidSiblingFeed
import dev.justinsmith.core.sync.AppId
import dev.justinsmith.core.sync.SiblingFeed
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.systemTimestampFormatter
import org.koin.core.module.Module
import org.koin.dsl.module

@Composable
actual fun rememberBellsPlatformModule(): Module = LocalContext.current.applicationContext.let { context ->
    remember(context) {
        module {
            single<RoomDatabase.Builder<BellsDatabase>> {
                Room.databaseBuilder<BellsDatabase>(context, context.getDatabasePath(BELLS_DATABASE).absolutePath)
            }
            single<SiblingFeed> { AndroidSiblingFeed(context, AppId.Bells) }
            single<TimestampFormatter> { systemTimestampFormatter() }
        }
    }
}
