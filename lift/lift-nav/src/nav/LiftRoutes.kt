package dev.justinsmith.lift.nav

import androidx.navigation3.runtime.NavKey
import dev.justinsmith.interval.nav.Tab
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.subclass

// Every Lift destination is a NavKey. A new one must also be registered in [liftRoutes] and given an
// `entry<…>` in [liftEntries], or it cannot be saved or shown.
//
// Each key keeps the serial name it had before the package was renamed from `dev.justinsmith.bells` (a back
// stack is saved by these names), so one saved by an older build still restores. A key added later needs none.

/** Kettlebell training: this week, the last session, the program followed, and Start session. */
@Serializable
@SerialName("dev.justinsmith.bells.nav.LiftTab")
data object LiftTab : Tab

/**
 * The kettlebell session in progress, full screen, an EMOM's timer included, and its summary. Pushed from
 * Lift.
 */
@Serializable
@SerialName("dev.justinsmith.bells.nav.Lifting")
data object Lifting : NavKey

/** The built-in programs and the user's own, to follow one. Pushed from Lift. */
@Serializable
@SerialName("dev.justinsmith.bells.nav.Programs")
data object Programs : NavKey

/**
 * The program followed: its days done and left, or once it is over what it came to. Pushed from the Lift
 * tab's program card and from Programs.
 */
@Serializable
@SerialName("dev.justinsmith.bells.nav.ProgramProgress")
data object ProgramProgress : NavKey

/** A program the user makes, or changes by its [uid]. Pushed from Programs. */
@Serializable
@SerialName("dev.justinsmith.bells.nav.ProgramEditor")
data class ProgramEditor(val uid: String? = null) : NavKey

/** The bells the user owns and the records set with them. Pushed from Lift. */
@Serializable
@SerialName("dev.justinsmith.bells.nav.OwnedBells")
data object OwnedBells : NavKey

/** Every exercise there is to log: built-in and the user's own, to search and narrow. Pushed from Lift. */
@Serializable
@SerialName("dev.justinsmith.bells.nav.ExerciseLibrary")
data object ExerciseLibrary : NavKey

/**
 * One finished kettlebell session, by its uid: its sets and records, and Delete. Pushed from History, where
 * [fromHistory] opens it beside the log on a wide window, or from Lift, where it is always a page of its own.
 */
@Serializable
@SerialName("dev.justinsmith.bells.nav.LiftDetail")
data class LiftDetail(val uid: String, val fromHistory: Boolean = false) : NavKey

/**
 * Registers every Lift key, for the app's saved back stack: saving and restoring works on every platform
 * only with each subclass listed (native has no reflection-based serialization).
 */
fun PolymorphicModuleBuilder<NavKey>.liftRoutes() {
    subclass(LiftTab::class)
    subclass(Lifting::class)
    subclass(Programs::class)
    subclass(ProgramProgress::class)
    subclass(ProgramEditor::class)
    subclass(OwnedBells::class)
    subclass(ExerciseLibrary::class)
    subclass(LiftDetail::class)
}
