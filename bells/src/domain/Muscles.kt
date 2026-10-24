package dev.justinsmith.bells.domain

import dev.justinsmith.bells.domain.Muscle.Abs
import dev.justinsmith.bells.domain.Muscle.Adductors
import dev.justinsmith.bells.domain.Muscle.Biceps
import dev.justinsmith.bells.domain.Muscle.Calves
import dev.justinsmith.bells.domain.Muscle.Chest
import dev.justinsmith.bells.domain.Muscle.Forearms
import dev.justinsmith.bells.domain.Muscle.Glutes
import dev.justinsmith.bells.domain.Muscle.Hamstrings
import dev.justinsmith.bells.domain.Muscle.HipFlexors
import dev.justinsmith.bells.domain.Muscle.Lats
import dev.justinsmith.bells.domain.Muscle.LowerBack
import dev.justinsmith.bells.domain.Muscle.Obliques
import dev.justinsmith.bells.domain.Muscle.Quads
import dev.justinsmith.bells.domain.Muscle.RearDelts
import dev.justinsmith.bells.domain.Muscle.Shoulders
import dev.justinsmith.bells.domain.Muscle.Traps
import dev.justinsmith.bells.domain.Muscle.Triceps
import dev.justinsmith.bells.domain.Muscle.UpperBack

/** A muscle, or a group of them, as an exercise's page names what it works. */
enum class Muscle {
    Glutes,
    Hamstrings,
    Quads,
    Calves,
    Adductors,
    HipFlexors,
    LowerBack,
    Abs,
    Obliques,
    Lats,
    UpperBack,
    Traps,
    Chest,
    Shoulders,
    RearDelts,
    Biceps,
    Triceps,

    /** The forearms and the grip. */
    Forearms,
}

/** What an exercise works most ([primary]), and what it works [also]. */
data class Muscles(val primary: List<Muscle>, val also: List<Muscle> = emptyList())

private fun works(vararg primary: Muscle) = Muscles(primary.toList())

private infix fun Muscles.and(also: List<Muscle>) = copy(also = also)

/** What each built-in works. Every one says, so a built-in added without its muscles fails to build. */
val BuiltInExercise.muscles: Muscles
    get() = when (this) {
        BuiltInExercise.TwoHandSwing -> works(Glutes, Hamstrings) and listOf(LowerBack, Abs, Forearms)
        BuiltInExercise.OneHandSwing -> works(Glutes, Hamstrings) and listOf(Obliques, LowerBack, Forearms)
        BuiltInExercise.Clean -> works(Glutes, Hamstrings) and listOf(Biceps, Forearms, Abs)
        BuiltInExercise.Press -> works(Shoulders, Triceps) and listOf(UpperBack, Obliques)
        BuiltInExercise.Snatch -> works(Glutes, Hamstrings, Shoulders) and listOf(Traps, Forearms, Abs)
        BuiltInExercise.GetUp -> works(Shoulders, Abs, Obliques) and listOf(Glutes, Triceps)
        BuiltInExercise.GobletSquat -> works(Quads, Glutes) and listOf(Adductors, Abs, UpperBack)
        BuiltInExercise.Row -> works(Lats, UpperBack) and listOf(Biceps, RearDelts, Forearms)
        BuiltInExercise.Jerk -> works(Shoulders, Triceps, Quads) and listOf(Glutes, Calves)
        BuiltInExercise.LongCycle -> works(Glutes, Shoulders, Quads) and listOf(Hamstrings, Triceps, Forearms)
        BuiltInExercise.Halo -> works(Shoulders) and listOf(UpperBack, Triceps, Abs)
        BuiltInExercise.FrontSquat -> works(Quads, Glutes) and listOf(Abs, UpperBack)
        BuiltInExercise.ArmorBuildingComplex -> works(Glutes, Shoulders, Quads) and listOf(Hamstrings, Triceps, Abs)
        BuiltInExercise.Deadlift -> works(Glutes, Hamstrings) and listOf(LowerBack, Forearms)
        BuiltInExercise.HighPull -> works(Glutes, Hamstrings, Traps) and listOf(RearDelts, UpperBack)
        BuiltInExercise.RackedLunge -> works(Quads, Glutes) and listOf(Adductors, Abs)
        BuiltInExercise.Windmill -> works(Obliques, Shoulders) and listOf(Hamstrings, Glutes)
        BuiltInExercise.BottomsUpPress -> works(Shoulders, Forearms) and listOf(Triceps, Abs)
        BuiltInExercise.IronCardio -> works(Glutes, Shoulders, Quads) and listOf(Hamstrings, Triceps, Abs)
        BuiltInExercise.BarbellSnatch -> works(Glutes, Quads, Hamstrings) and listOf(Traps, Shoulders, UpperBack)
        BuiltInExercise.BarbellCleanAndJerk -> works(Glutes, Quads, Shoulders) and listOf(Hamstrings, Traps, Triceps)
        BuiltInExercise.BarbellClean -> works(Glutes, Quads, Hamstrings) and listOf(Traps, UpperBack)
        BuiltInExercise.BarbellJerk -> works(Shoulders, Triceps, Quads) and listOf(Glutes, Abs)
        BuiltInExercise.BarbellPowerSnatch -> works(Glutes, Hamstrings, Traps) and listOf(Shoulders, UpperBack)
        BuiltInExercise.BarbellPowerClean -> works(Glutes, Hamstrings, Traps) and listOf(Quads, UpperBack)
        BuiltInExercise.BarbellHangSnatch -> works(Glutes, Hamstrings, Traps) and listOf(Shoulders, UpperBack)
        BuiltInExercise.BarbellHangClean -> works(Glutes, Hamstrings, Traps) and listOf(Quads, UpperBack)
        BuiltInExercise.BarbellSnatchPull -> works(Glutes, Hamstrings, Traps) and listOf(LowerBack, Forearms)
        BuiltInExercise.BarbellCleanPull -> works(Glutes, Hamstrings, Traps) and listOf(LowerBack, Forearms)
        BuiltInExercise.BarbellOverheadSquat -> works(Quads, Glutes, Shoulders) and listOf(UpperBack, Abs)
        BuiltInExercise.BarbellSnatchBalance -> works(Quads, Shoulders) and listOf(Glutes, UpperBack, Triceps)
        BuiltInExercise.BarbellPushPress -> works(Shoulders, Triceps) and listOf(Quads, Glutes, UpperBack)
        BuiltInExercise.BarbellBackSquat -> works(Quads, Glutes) and listOf(Adductors, LowerBack, Abs)
        BuiltInExercise.BarbellFrontSquat -> works(Quads, Glutes) and listOf(UpperBack, Abs)
        BuiltInExercise.BarbellDeadlift -> works(Glutes, Hamstrings, LowerBack) and listOf(Quads, Traps, Forearms)
        BuiltInExercise.BarbellRomanianDeadlift -> works(Hamstrings, Glutes) and listOf(LowerBack, Forearms)
        BuiltInExercise.BarbellBenchPress -> works(Chest, Triceps) and listOf(Shoulders)
        BuiltInExercise.BarbellOverheadPress -> works(Shoulders, Triceps) and listOf(UpperBack, Abs)
        BuiltInExercise.BarbellRow -> works(Lats, UpperBack) and listOf(Biceps, RearDelts, LowerBack)
        BuiltInExercise.PushUp -> works(Chest, Triceps) and listOf(Shoulders, Abs)
        BuiltInExercise.InclinePushUp -> works(Chest, Triceps) and listOf(Shoulders)
        BuiltInExercise.PikePushUp -> works(Shoulders, Triceps) and listOf(Chest, UpperBack)
        BuiltInExercise.BenchDip -> works(Triceps) and listOf(Chest, Shoulders)
        BuiltInExercise.PullUp -> works(Lats, UpperBack) and listOf(Biceps, Forearms)
        BuiltInExercise.ChinUp -> works(Lats, Biceps) and listOf(UpperBack, Forearms)
        BuiltInExercise.InvertedRow -> works(UpperBack, Lats) and listOf(Biceps, RearDelts)
        BuiltInExercise.AirSquat -> works(Quads, Glutes) and listOf(Adductors, Calves)
        BuiltInExercise.SplitSquat -> works(Quads, Glutes) and listOf(Adductors)
        BuiltInExercise.ReverseLunge -> works(Quads, Glutes) and listOf(Hamstrings, Adductors)
        BuiltInExercise.BulgarianSplitSquat -> works(Quads, Glutes) and listOf(Adductors, HipFlexors)
        BuiltInExercise.StepUp -> works(Quads, Glutes) and listOf(Hamstrings, Calves)
        BuiltInExercise.GluteBridge -> works(Glutes) and listOf(Hamstrings, LowerBack)
        BuiltInExercise.SingleLegGluteBridge -> works(Glutes) and listOf(Hamstrings, Obliques)
        BuiltInExercise.WallSit -> works(Quads) and listOf(Glutes)
        BuiltInExercise.CalfRaise -> works(Calves)
        BuiltInExercise.Plank -> works(Abs) and listOf(Obliques, Shoulders, Glutes)
        BuiltInExercise.SidePlank -> works(Obliques) and listOf(Abs, Shoulders, Glutes)
        BuiltInExercise.HollowHold -> works(Abs) and listOf(HipFlexors)
        BuiltInExercise.DeadBug -> works(Abs) and listOf(Obliques, HipFlexors)
        BuiltInExercise.Burpee -> works(Quads, Chest) and listOf(Glutes, Shoulders, Triceps, Abs)
        BuiltInExercise.MountainClimber -> works(Abs, HipFlexors) and listOf(Shoulders, Quads)
        BuiltInExercise.BandPullApart -> works(RearDelts, UpperBack) and listOf(Traps)
        BuiltInExercise.BandFacePull -> works(RearDelts, UpperBack) and listOf(Traps, Biceps)
        BuiltInExercise.BandSeatedRow -> works(Lats, UpperBack) and listOf(Biceps, RearDelts)
        BuiltInExercise.BandLatPulldown -> works(Lats) and listOf(Biceps, UpperBack)
        BuiltInExercise.BandChestPress -> works(Chest, Triceps) and listOf(Shoulders)
        BuiltInExercise.BandOverheadPress -> works(Shoulders, Triceps) and listOf(UpperBack)
        BuiltInExercise.BandBicepsCurl -> works(Biceps) and listOf(Forearms)
        BuiltInExercise.BandTricepsPushdown -> works(Triceps)
        BuiltInExercise.BandPallofPress -> works(Obliques, Abs) and listOf(Shoulders)
        BuiltInExercise.BandWoodchop -> works(Obliques, Abs) and listOf(Shoulders, Glutes)
        BuiltInExercise.BandSquat -> works(Quads, Glutes) and listOf(Adductors)
        BuiltInExercise.BandGoodMorning -> works(Hamstrings, Glutes) and listOf(LowerBack)
        BuiltInExercise.BandGluteBridge -> works(Glutes) and listOf(Hamstrings)
        BuiltInExercise.BandLateralWalk -> works(Glutes) and listOf(Quads)
        BuiltInExercise.BandClamshell -> works(Glutes)
        BuiltInExercise.Hundred -> works(Abs) and listOf(HipFlexors, Shoulders)
        BuiltInExercise.RollUp -> works(Abs) and listOf(HipFlexors, LowerBack)
        BuiltInExercise.SingleLegCircles -> works(HipFlexors, Abs) and listOf(Quads, Adductors)
        BuiltInExercise.RollingLikeABall -> works(Abs) and listOf(LowerBack)
        BuiltInExercise.SingleLegStretch -> works(Abs) and listOf(HipFlexors)
        BuiltInExercise.DoubleLegStretch -> works(Abs) and listOf(HipFlexors, Shoulders)
        BuiltInExercise.Scissors -> works(Abs, HipFlexors) and listOf(Hamstrings)
        BuiltInExercise.CrissCross -> works(Obliques, Abs) and listOf(HipFlexors)
        BuiltInExercise.SpineStretchForward -> works(Hamstrings, LowerBack) and listOf(Abs)
        BuiltInExercise.Saw -> works(Obliques, Hamstrings) and listOf(LowerBack, UpperBack)
        BuiltInExercise.Swan -> works(LowerBack, UpperBack) and listOf(Glutes, Shoulders)
        BuiltInExercise.SingleLegKick -> works(Hamstrings, Glutes) and listOf(LowerBack, UpperBack)
        BuiltInExercise.SideKickSeries -> works(Glutes) and listOf(Adductors, Obliques, HipFlexors)
        BuiltInExercise.Teaser -> works(Abs, HipFlexors) and listOf(LowerBack)
        BuiltInExercise.Swimming -> works(LowerBack, Glutes) and listOf(UpperBack, Shoulders, Hamstrings)
        BuiltInExercise.ShoulderBridge -> works(Glutes, Hamstrings) and listOf(LowerBack, Abs)
        BuiltInExercise.Seal -> works(Abs) and listOf(Adductors)
        BuiltInExercise.LegPullFront -> works(Abs, Shoulders) and listOf(Glutes, Triceps)
    }
