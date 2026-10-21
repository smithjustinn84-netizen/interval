package dev.justinsmith.interval.data

import androidx.room.Room
import androidx.room.RoomDatabase
import dev.justinsmith.interval.di.appModules
import dev.justinsmith.interval.fakes.fakePlatformModule
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.platform.DATABASE_NAME
import dev.justinsmith.interval.platform.PREFERENCES_NAME
import dev.justinsmith.interval.platform.PlatformStorage
import dev.justinsmith.interval.timer.IntervalConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
 * The real `storageModule` wired as the app wires it, over files in a temp directory. Closing the container
 * and building a new one straight away is what Android does when the Activity finishes and reopens in the
 * same process: the new container must open the same DataStore file and see what the old one saved.
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
    fun `a new container right after the old one closes reads and writes the same files`() = runBlocking {
        repeat(3) { round ->
            val koin = koinApplication {
                val platform = module {
                    includes(fakePlatformModule())
                    single<PlatformStorage> { storage }
                }
                modules(appModules(platform))
            }
            val repository = koin.koin.get<IntervalRepository>()
            withTimeout(5.seconds) { repository.ready.first { it } }
            assertEquals(round, repository.config.value.rounds - DEFAULT_ROUNDS)
            assertEquals(round, repository.customPresets.value.size)

            repository.updateConfig { it.copy(rounds = it.rounds + 1) }
            repository.saveCurrentAsPreset("Round $round")
            withTimeout(5.seconds) {
                while (repository.customPresets.value.size != round + 1) delay(10.milliseconds)
            }
            koin.close()
        }
    }

    private companion object {
        val DEFAULT_ROUNDS = IntervalConfig().rounds
    }
}
