@file:OptIn(KoinExperimentalAPI::class)

package dev.justinsmith.interval.di

import androidx.lifecycle.SavedStateHandle
import dev.justinsmith.interval.fakes.fakeCorePlatformModule
import dev.justinsmith.interval.fakes.fakePlatformModule
import dev.justinsmith.interval.fakes.fakeStorageModule
import dev.justinsmith.interval.history.WorkoutDetailViewModel
import dev.justinsmith.interval.workout.WorkoutViewModel
import dev.justinsmith.lift.fakes.fakeLiftPlatformModule
import dev.justinsmith.lift.fakes.fakeLiftStorageModule
import dev.justinsmith.lift.history.LiftDetailViewModel
import dev.justinsmith.lift.library.ExerciseEditorViewModel
import dev.justinsmith.lift.programs.ProgramCopy
import dev.justinsmith.lift.programs.ProgramEditorViewModel
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * The app's whole graph, checked without running it: every definition's constructor finds what it takes, so a
 * ViewModel given a new dependency nobody binds fails here rather than on its screen. Over the fakes for what
 * the platform and storage give, as `testKoin()` is.
 */
class VerifyAppModulesTest {
    @Test
    fun `every definition finds what it takes`() {
        val app = module {
            includes(
                appModules(
                    fakeCorePlatformModule(),
                    fakePlatformModule(),
                    fakeStorageModule(),
                    liftPlatform = fakeLiftPlatformModule(),
                    liftStorage = fakeLiftStorageModule(),
                ),
            )
        }
        app.verify(
            // Defaulted constructor parameters (a tick's length, a dispatcher) the definitions leave out, and the
            // entry's SavedStateHandle that `koinViewModel` supplies.
            extraTypes = listOf(Long::class, CoroutineDispatcher::class, SavedStateHandle::class),
            // What each Route passes with `parametersOf`.
            injections = injectedParameters(
                definition<WorkoutViewModel>(Long::class, String::class),
                definition<WorkoutDetailViewModel>(Long::class),
                definition<LiftDetailViewModel>(String::class),
                definition<ProgramEditorViewModel>(String::class, ProgramCopy::class),
                definition<ExerciseEditorViewModel>(String::class),
            ),
        )
    }
}
