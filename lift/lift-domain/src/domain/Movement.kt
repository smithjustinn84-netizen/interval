package dev.justinsmith.lift.domain

import dev.justinsmith.lift.domain.BuiltInExercise.AirSquat
import dev.justinsmith.lift.domain.BuiltInExercise.ArmorBuildingComplex
import dev.justinsmith.lift.domain.BuiltInExercise.BandBicepsCurl
import dev.justinsmith.lift.domain.BuiltInExercise.BandChestPress
import dev.justinsmith.lift.domain.BuiltInExercise.BandClamshell
import dev.justinsmith.lift.domain.BuiltInExercise.BandFacePull
import dev.justinsmith.lift.domain.BuiltInExercise.BandGluteBridge
import dev.justinsmith.lift.domain.BuiltInExercise.BandGoodMorning
import dev.justinsmith.lift.domain.BuiltInExercise.BandLatPulldown
import dev.justinsmith.lift.domain.BuiltInExercise.BandLateralWalk
import dev.justinsmith.lift.domain.BuiltInExercise.BandOverheadPress
import dev.justinsmith.lift.domain.BuiltInExercise.BandPallofPress
import dev.justinsmith.lift.domain.BuiltInExercise.BandPullApart
import dev.justinsmith.lift.domain.BuiltInExercise.BandSeatedRow
import dev.justinsmith.lift.domain.BuiltInExercise.BandSquat
import dev.justinsmith.lift.domain.BuiltInExercise.BandTricepsPushdown
import dev.justinsmith.lift.domain.BuiltInExercise.BandWoodchop
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellBackSquat
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellBenchPress
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellClean
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellCleanAndJerk
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellCleanPull
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellDeadlift
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellFrontSquat
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellHangClean
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellHangSnatch
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellJerk
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellOverheadPress
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellOverheadSquat
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellPowerClean
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellPowerSnatch
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellPushPress
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellRomanianDeadlift
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellRow
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellSnatch
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellSnatchBalance
import dev.justinsmith.lift.domain.BuiltInExercise.BarbellSnatchPull
import dev.justinsmith.lift.domain.BuiltInExercise.BenchDip
import dev.justinsmith.lift.domain.BuiltInExercise.BottomsUpCarry
import dev.justinsmith.lift.domain.BuiltInExercise.BottomsUpPress
import dev.justinsmith.lift.domain.BuiltInExercise.BulgarianSplitSquat
import dev.justinsmith.lift.domain.BuiltInExercise.Burpee
import dev.justinsmith.lift.domain.BuiltInExercise.CalfRaise
import dev.justinsmith.lift.domain.BuiltInExercise.ChinUp
import dev.justinsmith.lift.domain.BuiltInExercise.Clean
import dev.justinsmith.lift.domain.BuiltInExercise.CleanAndPress
import dev.justinsmith.lift.domain.BuiltInExercise.CrissCross
import dev.justinsmith.lift.domain.BuiltInExercise.DeadBug
import dev.justinsmith.lift.domain.BuiltInExercise.Deadlift
import dev.justinsmith.lift.domain.BuiltInExercise.DoubleLegStretch
import dev.justinsmith.lift.domain.BuiltInExercise.FarmersWalk
import dev.justinsmith.lift.domain.BuiltInExercise.FrontSquat
import dev.justinsmith.lift.domain.BuiltInExercise.GetUp
import dev.justinsmith.lift.domain.BuiltInExercise.GluteBridge
import dev.justinsmith.lift.domain.BuiltInExercise.GobletSquat
import dev.justinsmith.lift.domain.BuiltInExercise.HalfSnatch
import dev.justinsmith.lift.domain.BuiltInExercise.Halo
import dev.justinsmith.lift.domain.BuiltInExercise.HighPull
import dev.justinsmith.lift.domain.BuiltInExercise.HollowHold
import dev.justinsmith.lift.domain.BuiltInExercise.Hundred
import dev.justinsmith.lift.domain.BuiltInExercise.InclinePushUp
import dev.justinsmith.lift.domain.BuiltInExercise.InvertedRow
import dev.justinsmith.lift.domain.BuiltInExercise.IronCardio
import dev.justinsmith.lift.domain.BuiltInExercise.Jerk
import dev.justinsmith.lift.domain.BuiltInExercise.LegPullFront
import dev.justinsmith.lift.domain.BuiltInExercise.LongCycle
import dev.justinsmith.lift.domain.BuiltInExercise.MountainClimber
import dev.justinsmith.lift.domain.BuiltInExercise.OneHandSwing
import dev.justinsmith.lift.domain.BuiltInExercise.PikePushUp
import dev.justinsmith.lift.domain.BuiltInExercise.Plank
import dev.justinsmith.lift.domain.BuiltInExercise.Press
import dev.justinsmith.lift.domain.BuiltInExercise.PullUp
import dev.justinsmith.lift.domain.BuiltInExercise.PushPress
import dev.justinsmith.lift.domain.BuiltInExercise.PushUp
import dev.justinsmith.lift.domain.BuiltInExercise.RackCarry
import dev.justinsmith.lift.domain.BuiltInExercise.RackedLunge
import dev.justinsmith.lift.domain.BuiltInExercise.ReverseLunge
import dev.justinsmith.lift.domain.BuiltInExercise.RollUp
import dev.justinsmith.lift.domain.BuiltInExercise.RollingLikeABall
import dev.justinsmith.lift.domain.BuiltInExercise.Row
import dev.justinsmith.lift.domain.BuiltInExercise.Saw
import dev.justinsmith.lift.domain.BuiltInExercise.Scissors
import dev.justinsmith.lift.domain.BuiltInExercise.Seal
import dev.justinsmith.lift.domain.BuiltInExercise.ShoulderBridge
import dev.justinsmith.lift.domain.BuiltInExercise.SideKickSeries
import dev.justinsmith.lift.domain.BuiltInExercise.SidePlank
import dev.justinsmith.lift.domain.BuiltInExercise.SingleLegCircles
import dev.justinsmith.lift.domain.BuiltInExercise.SingleLegGluteBridge
import dev.justinsmith.lift.domain.BuiltInExercise.SingleLegKick
import dev.justinsmith.lift.domain.BuiltInExercise.SingleLegStretch
import dev.justinsmith.lift.domain.BuiltInExercise.Snatch
import dev.justinsmith.lift.domain.BuiltInExercise.SpineStretchForward
import dev.justinsmith.lift.domain.BuiltInExercise.SplitSquat
import dev.justinsmith.lift.domain.BuiltInExercise.StepUp
import dev.justinsmith.lift.domain.BuiltInExercise.SuitcaseCarry
import dev.justinsmith.lift.domain.BuiltInExercise.Swan
import dev.justinsmith.lift.domain.BuiltInExercise.Swimming
import dev.justinsmith.lift.domain.BuiltInExercise.Teaser
import dev.justinsmith.lift.domain.BuiltInExercise.Thruster
import dev.justinsmith.lift.domain.BuiltInExercise.TwoHandSwing
import dev.justinsmith.lift.domain.BuiltInExercise.WallSit
import dev.justinsmith.lift.domain.BuiltInExercise.Windmill

// How a lift moves, for the coaching that tells a swing from a squat and spares the grip.

/** The movement a lift trains: a swing is a [Hinge], a goblet squat a [Squat], never the one for the other. */
enum class Pattern { Hinge, Squat, Lunge, Press, Pull, Carry, GetUp, Core, Other }

/**
 * How a lift is done: [Ballistic] thrown by the hips, the bell floating at the top and caught (swings, cleans,
 * snatches); [Grind] slow under load (presses, squats, rows, the get-up); [Hold] held still for a time.
 */
enum class Tempo { Ballistic, Grind, Hold }

/**
 * How a lift moves: its [pattern], its [tempo], how hard it works the [grip] (0, none, to [MAX_GRIP]), and how many
 * lifts one logged rep of it is ([perRep]: the Armor Building Complex is 2 cleans, a press and 3 squats, so 6).
 */
data class Movement(val pattern: Pattern, val tempo: Tempo, val grip: Int, val perRep: Int = 1) {
    init {
        require(grip in 0..MAX_GRIP) { "Grip of $grip" }
        require(perRep >= 1) { "$perRep lifts a rep" }
    }

    companion object {
        /** The hardest on the grip: a bell thrown or held by one hand, or upside down. */
        const val MAX_GRIP = 3
    }
}

private fun ballistic(pattern: Pattern, grip: Int, perRep: Int = 1) = Movement(pattern, Tempo.Ballistic, grip, perRep)

private fun grind(pattern: Pattern, grip: Int, perRep: Int = 1) = Movement(pattern, Tempo.Grind, grip, perRep)

private fun hold(pattern: Pattern, grip: Int) = Movement(pattern, Tempo.Hold, grip)

/** How each built-in moves. Every one says, so a built-in added without its movement fails to build. */
val BuiltInExercise.movement: Movement
    get() = when (this) {
        // The bell thrown: the hips drive, the hand floats and catches.
        Snatch, OneHandSwing -> ballistic(Pattern.Hinge, grip = 3)

        TwoHandSwing, Clean, HighPull -> ballistic(Pattern.Hinge, grip = 2)

        HalfSnatch -> ballistic(Pattern.Hinge, grip = 2)

        Jerk -> ballistic(Pattern.Press, grip = 1)

        LongCycle -> ballistic(Pattern.Hinge, grip = 2, perRep = 2)

        IronCardio -> ballistic(Pattern.Hinge, grip = 2, perRep = 3)

        ArmorBuildingComplex -> ballistic(Pattern.Hinge, grip = 2, perRep = 6)

        CleanAndPress -> ballistic(Pattern.Hinge, grip = 2, perRep = 2)

        BarbellSnatch, BarbellPowerSnatch, BarbellHangSnatch, BarbellClean, BarbellPowerClean, BarbellHangClean,
        BarbellSnatchPull, BarbellCleanPull,
        -> ballistic(Pattern.Hinge, grip = 2)

        BarbellCleanAndJerk -> ballistic(Pattern.Hinge, grip = 2, perRep = 2)

        BarbellJerk, BarbellPushPress, PushPress -> ballistic(Pattern.Press, grip = 1)

        Burpee, MountainClimber -> ballistic(Pattern.Other, grip = 0)

        // Slow under the bell.
        Press, BarbellOverheadPress, BarbellBenchPress -> grind(Pattern.Press, grip = 1)

        BottomsUpPress -> grind(Pattern.Press, grip = 3)

        Thruster -> grind(Pattern.Squat, grip = 1, perRep = 2)

        GobletSquat, FrontSquat, BarbellBackSquat, BarbellFrontSquat -> grind(Pattern.Squat, grip = 1)

        BarbellOverheadSquat, BarbellSnatchBalance -> grind(Pattern.Squat, grip = 1)

        Deadlift, BarbellDeadlift, BarbellRomanianDeadlift -> grind(Pattern.Hinge, grip = 2)

        RackedLunge -> grind(Pattern.Lunge, grip = 1)

        Row, BarbellRow -> grind(Pattern.Pull, grip = 2)

        GetUp -> grind(Pattern.GetUp, grip = 1)

        Windmill -> grind(Pattern.Core, grip = 1)

        Halo -> grind(Pattern.Press, grip = 1)

        // Carried and held.
        FarmersWalk, BottomsUpCarry -> hold(Pattern.Carry, grip = 3)

        SuitcaseCarry -> hold(Pattern.Carry, grip = 2)

        RackCarry -> hold(Pattern.Carry, grip = 1)

        WallSit -> hold(Pattern.Squat, grip = 0)

        Plank, SidePlank, HollowHold, Swimming -> hold(Pattern.Core, grip = 0)

        // Bodyweight.
        PushUp, InclinePushUp, PikePushUp, BenchDip -> grind(Pattern.Press, grip = 0)

        PullUp, ChinUp -> grind(Pattern.Pull, grip = 2)

        InvertedRow -> grind(Pattern.Pull, grip = 1)

        AirSquat -> grind(Pattern.Squat, grip = 0)

        SplitSquat, ReverseLunge, BulgarianSplitSquat, StepUp -> grind(Pattern.Lunge, grip = 0)

        GluteBridge, SingleLegGluteBridge, CalfRaise -> grind(Pattern.Hinge, grip = 0)

        DeadBug -> grind(Pattern.Core, grip = 0)

        // Bands: held by the hands, never heavily.
        BandPullApart, BandFacePull, BandSeatedRow, BandLatPulldown -> grind(Pattern.Pull, grip = 1)

        BandChestPress, BandOverheadPress, BandTricepsPushdown -> grind(Pattern.Press, grip = 1)

        BandBicepsCurl -> grind(Pattern.Pull, grip = 1)

        BandPallofPress, BandWoodchop -> grind(Pattern.Core, grip = 1)

        BandSquat -> grind(Pattern.Squat, grip = 0)

        BandGoodMorning, BandGluteBridge -> grind(Pattern.Hinge, grip = 0)

        BandLateralWalk, BandClamshell -> grind(Pattern.Lunge, grip = 0)

        // The mat.
        Hundred, RollUp, SingleLegCircles, RollingLikeABall, SingleLegStretch, DoubleLegStretch, Scissors,
        CrissCross, SpineStretchForward, Saw, Swan, Teaser, Seal, LegPullFront,
        -> grind(Pattern.Core, grip = 0)

        SingleLegKick, SideKickSeries, ShoulderBridge -> grind(Pattern.Hinge, grip = 0)
    }

/**
 * How any lift moves: a built-in's own; one the user made worked out from its equipment and how it is counted, a
 * hold for one timed, else a grind, light on the grip under a bell or a bar.
 */
val Exercise.movement: Movement
    get() = when (this) {
        is BuiltInExercise -> movement

        else -> {
            val grip = if (equipment == Equipment.Kettlebell || equipment == Equipment.Barbell) 1 else 0
            if (measure == Measure.Time) hold(Pattern.Other, grip) else grind(Pattern.Other, grip)
        }
    }
