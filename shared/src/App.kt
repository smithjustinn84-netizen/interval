package dev.justinsmith.interval

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.justinsmith.interval.di.rememberAppKoin
import dev.justinsmith.interval.nav.Setup
import dev.justinsmith.interval.nav.Workout
import dev.justinsmith.interval.setup.SetupRoute
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.workout.WorkoutRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.koin.compose.KoinIsolatedContext

/**
 * Every [NavKey] the back stack can hold, registered explicitly so saving and restoring works on
 * every platform (native has no reflection-based serialization).
 */
private val navConfiguration = SavedStateConfiguration {
    serializersModule = serializersModule + SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Setup::class)
            subclass(Workout::class)
        }
    }
}

/** The root composable every platform entry point hosts. */
@Composable
@DevelopmentEntryPoint
fun App() {
    KoinIsolatedContext(context = rememberAppKoin()) {
        IntervalTheme {
            val backStack = rememberNavBackStack(navConfiguration, Setup)
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                // Passing this replaces NavDisplay's default list, so the saveable-state holder is
                // repeated. The ViewModel store decorator goes last: it needs the holder above it.
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<Setup> {
                        // A double tap on Start must not stack two workouts.
                        SetupRoute(onStart = { if (backStack.lastOrNull() != Workout) backStack.add(Workout) })
                    }
                    entry<Workout> { WorkoutRoute(onExit = { backStack.removeLastOrNull() }) }
                },
            )
        }
    }
}
