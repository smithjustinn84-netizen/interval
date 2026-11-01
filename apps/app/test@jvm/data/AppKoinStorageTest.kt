package dev.justinsmith.interval.data

import androidx.room.Room
import androidx.room.RoomDatabase
import dev.justinsmith.interval.di.PreferencesScope
import dev.justinsmith.interval.di.appModules
import dev.justinsmith.interval.di.drainStorage
import dev.justinsmith.interval.fakes.fakeCorePlatformModule
import dev.justinsmith.interval.fakes.fakePlatformModule
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.platform.DATABASE_NAME
import dev.justinsmith.interval.platform.PREFERENCES_NAME
import dev.justinsmith.interval.platform.PlatformStorage
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.lift.fakes.fakeLiftPlatformModule
import dev.justinsmith.lift.fakes.fakeLiftStorageModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The real `storageModule` wired as the app wires it, over files in a temp directory. Each round is a process:
 * one container for its life, closed as it ends (desktop's exit), and the next one opens the same database and
 * DataStore files and sees what the last one saved.
 */
class AppKoinStorageTest {
    private val directory: File = Files.createTempDirectory("interval-koin").toFile()

    private val storage = object : PlatformStorage {
        override fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> =
            Room.databaseBuilder<AppDatabase>(name = directory.resolve(DATABASE_NAME).absolutePath)

        override val preferencesPath: String = directory.resolve(PREFERENCES_NAME).absolutePath
    }

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `each new process reads what the last one saved`() = runBlocking {
        repeat(3) { round ->
            val koin = koinApplication {
                val platform = module {
                    includes(fakePlatformModule())
                    single<PlatformStorage> { storage }
                }
                // Lift's storage is faked: this is about the interval side's files.
                modules(
                    appModules(
                        fakeCorePlatformModule(),
                        platform,
                        liftPlatform = fakeLiftPlatformModule(),
                        liftStorage = fakeLiftStorageModule(),
                    ),
                )
            }
            val repository = koin.koin.get<IntervalRepository>()
            withTimeout(5.seconds) { repository.ready.first { it } }
            assertEquals(round, repository.config.value.rounds - DEFAULT_ROUNDS)
            // The presets are read by ready; the user's alone follow them a moment after.
            assertEquals(round, repository.presets.value.count { it.custom })

            repository.updateConfig { it.copy(rounds = it.rounds + 1) }
            repository.saveCurrentAsPreset("Round $round")
            withTimeout(5.seconds) {
                while (repository.customPresets.value.size != round + 1) delay(10.milliseconds)
            }
            // Lands the writes, as desktop does before its container closes, and waits for the DataStore to let
            // go of its file, as the end of a process would.
            koin.drainStorage()
            val preferences = koin.koin.get<CoroutineScope>(PreferencesScope).coroutineContext.job
            koin.close()
            preferences.join()
        }
    }

    private companion object {
        val DEFAULT_ROUNDS = IntervalConfig().rounds
    }
}
