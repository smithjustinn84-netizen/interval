package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.KettlebellReps
import dev.justinsmith.interval.ui.RepPhase
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Fault
import dev.justinsmith.interval.ui.KettlebellPoses as DiagramPoses

/**
 * This exercise as a diagram from the side, with the path its bell takes ([ExerciseRep], drawn by core's
 * `ExerciseDiagram`): the flagship kettlebell lifts have one, which their page shows them by in place of the
 * figure ([demo]), and one of the user's own has that of the built-in it names as its [CustomExercise.figure].
 * Null for a lift none is drawn for yet, which is shown by its figure.
 */
val Exercise.diagram: ExerciseRep?
    get() = builtInDiagrams[(this as? CustomExercise)?.figure ?: this]?.rep

/**
 * Which of this lift's written cues (`cues()`, by its place) each part of its [diagram]'s rep belongs to, so the
 * cue is lit while the rep is in that part and takes the rep there when it is tapped. A part's cue is the one the
 * lift's figure lights over the same stretch. None for one of the user's own: the built-in's cues are not its.
 */
val Exercise.diagramCues: Map<RepPhase, Int>
    get() = (this as? BuiltInExercise)?.let { builtInDiagrams[it]?.cues }.orEmpty()

/**
 * This rep done wrong, as its [fault] makes it look, or null for a fault a diagram can't show or a rep that is not
 * prone to it: as a figure's ([ExerciseDemo.doneWrong]), only squatting the hinge is drawn ([Fault.SquattedHinge]),
 * each hinge the rep rests in squatted instead. Its parts and their times are as they were, so the one player runs
 * either and the rep keeps its place as the fault is shown or hidden.
 */
fun ExerciseRep.doneWrong(fault: Fault): ExerciseRep? = when (fault) {
    Fault.SquattedHinge -> if (keyframes.none { it.pose == DiagramPoses.Hinge }) {
        null
    } else {
        copy(
            keyframes = keyframes.map {
                if (it.pose == DiagramPoses.Hinge) it.copy(pose = DiagramPoses.SquattedHinge) else it
            },
        )
    }

    Fault.LateHand, Fault.DeathGrip, Fault.BentWrist -> null
}

/** A lift's rep with the written cue each of its parts belongs to. */
private class LiftDiagram(val rep: ExerciseRep, val cues: Map<RepPhase, Int>)

/** Both swings' cues are the hike, the snap, then the float or staying square through it. */
private val swing = LiftDiagram(
    KettlebellReps.Swing,
    mapOf(RepPhase.HipSnap to 1, RepPhase.Float to 2, RepPhase.Backswing to 2, RepPhase.HikePass to 0),
)

/** A swing with one hand is a swing with two, seen from the side. */
private val builtInDiagrams: Map<Exercise, LiftDiagram> = mapOf(
    BuiltInExercise.TwoHandSwing to swing,
    BuiltInExercise.OneHandSwing to swing,
    BuiltInExercise.Clean to LiftDiagram(
        KettlebellReps.Clean,
        mapOf(RepPhase.Pull to 0, RepPhase.Rack to 1, RepPhase.Drop to 2, RepPhase.HikePass to 2),
    ),
    BuiltInExercise.Snatch to LiftDiagram(
        KettlebellReps.Snatch,
        mapOf(
            RepPhase.HipSnap to 0,
            RepPhase.HighPull to 0,
            RepPhase.PunchThrough to 1,
            RepPhase.Lockout to 2,
            RepPhase.Drop to 2,
            RepPhase.HikePass to 2,
        ),
    ),
    BuiltInExercise.Press to LiftDiagram(
        KettlebellReps.Press,
        mapOf(RepPhase.Press to 1, RepPhase.Lockout to 2, RepPhase.Lower to 2, RepPhase.Rack to 0),
    ),
    BuiltInExercise.GobletSquat to LiftDiagram(
        KettlebellReps.GobletSquat,
        mapOf(RepPhase.Descend to 1, RepPhase.Bottom to 1, RepPhase.Drive to 2, RepPhase.Stand to 0),
    ),
    BuiltInExercise.GetUp to LiftDiagram(
        KettlebellReps.GetUp,
        mapOf(
            RepPhase.FloorPress to 0,
            RepPhase.RollToElbow to 1,
            RepPhase.TallSit to 1,
            RepPhase.HighBridge to 1,
            RepPhase.SweepToKneeling to 1,
            RepPhase.StandUp to 2,
            RepPhase.StepBack to 2,
            RepPhase.HandDown to 2,
            RepPhase.SweepThrough to 2,
            RepPhase.DownToElbow to 2,
            RepPhase.RollDown to 2,
            RepPhase.LowerBell to 2,
        ),
    ),
    BuiltInExercise.CleanAndPress to LiftDiagram(
        KettlebellReps.CleanAndPress,
        mapOf(
            RepPhase.Pull to 0,
            RepPhase.Rack to 0,
            RepPhase.Press to 1,
            RepPhase.Lockout to 1,
            RepPhase.Lower to 2,
            RepPhase.Drop to 2,
            RepPhase.HikePass to 2,
        ),
    ),
)
