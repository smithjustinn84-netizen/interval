package dev.justinsmith.bells.ui

import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.exercise_air_squat
import dev.justinsmith.bells.resources.exercise_armor_building_complex
import dev.justinsmith.bells.resources.exercise_band_biceps_curl
import dev.justinsmith.bells.resources.exercise_band_chest_press
import dev.justinsmith.bells.resources.exercise_band_clamshell
import dev.justinsmith.bells.resources.exercise_band_face_pull
import dev.justinsmith.bells.resources.exercise_band_glute_bridge
import dev.justinsmith.bells.resources.exercise_band_good_morning
import dev.justinsmith.bells.resources.exercise_band_lat_pulldown
import dev.justinsmith.bells.resources.exercise_band_lateral_walk
import dev.justinsmith.bells.resources.exercise_band_overhead_press
import dev.justinsmith.bells.resources.exercise_band_pallof_press
import dev.justinsmith.bells.resources.exercise_band_pull_apart
import dev.justinsmith.bells.resources.exercise_band_seated_row
import dev.justinsmith.bells.resources.exercise_band_squat
import dev.justinsmith.bells.resources.exercise_band_triceps_pushdown
import dev.justinsmith.bells.resources.exercise_band_woodchop
import dev.justinsmith.bells.resources.exercise_bench_dip
import dev.justinsmith.bells.resources.exercise_bottoms_up_press
import dev.justinsmith.bells.resources.exercise_bulgarian_split_squat
import dev.justinsmith.bells.resources.exercise_burpee
import dev.justinsmith.bells.resources.exercise_calf_raise
import dev.justinsmith.bells.resources.exercise_chin_up
import dev.justinsmith.bells.resources.exercise_clean
import dev.justinsmith.bells.resources.exercise_criss_cross
import dev.justinsmith.bells.resources.exercise_dead_bug
import dev.justinsmith.bells.resources.exercise_deadlift
import dev.justinsmith.bells.resources.exercise_double_leg_stretch
import dev.justinsmith.bells.resources.exercise_front_squat
import dev.justinsmith.bells.resources.exercise_get_up
import dev.justinsmith.bells.resources.exercise_glute_bridge
import dev.justinsmith.bells.resources.exercise_goblet_squat
import dev.justinsmith.bells.resources.exercise_halo
import dev.justinsmith.bells.resources.exercise_high_pull
import dev.justinsmith.bells.resources.exercise_hollow_hold
import dev.justinsmith.bells.resources.exercise_hundred
import dev.justinsmith.bells.resources.exercise_incline_push_up
import dev.justinsmith.bells.resources.exercise_inverted_row
import dev.justinsmith.bells.resources.exercise_iron_cardio
import dev.justinsmith.bells.resources.exercise_jerk
import dev.justinsmith.bells.resources.exercise_leg_pull_front
import dev.justinsmith.bells.resources.exercise_long_cycle
import dev.justinsmith.bells.resources.exercise_mountain_climber
import dev.justinsmith.bells.resources.exercise_one_hand_swing
import dev.justinsmith.bells.resources.exercise_pike_push_up
import dev.justinsmith.bells.resources.exercise_plank
import dev.justinsmith.bells.resources.exercise_press
import dev.justinsmith.bells.resources.exercise_pull_up
import dev.justinsmith.bells.resources.exercise_push_up
import dev.justinsmith.bells.resources.exercise_racked_lunge
import dev.justinsmith.bells.resources.exercise_reverse_lunge
import dev.justinsmith.bells.resources.exercise_roll_up
import dev.justinsmith.bells.resources.exercise_rolling_like_a_ball
import dev.justinsmith.bells.resources.exercise_row
import dev.justinsmith.bells.resources.exercise_saw
import dev.justinsmith.bells.resources.exercise_scissors
import dev.justinsmith.bells.resources.exercise_seal
import dev.justinsmith.bells.resources.exercise_shoulder_bridge
import dev.justinsmith.bells.resources.exercise_side_kick_series
import dev.justinsmith.bells.resources.exercise_side_plank
import dev.justinsmith.bells.resources.exercise_single_leg_circles
import dev.justinsmith.bells.resources.exercise_single_leg_glute_bridge
import dev.justinsmith.bells.resources.exercise_single_leg_kick
import dev.justinsmith.bells.resources.exercise_single_leg_stretch
import dev.justinsmith.bells.resources.exercise_snatch
import dev.justinsmith.bells.resources.exercise_spine_stretch_forward
import dev.justinsmith.bells.resources.exercise_split_squat
import dev.justinsmith.bells.resources.exercise_step_up
import dev.justinsmith.bells.resources.exercise_swan
import dev.justinsmith.bells.resources.exercise_swimming
import dev.justinsmith.bells.resources.exercise_teaser
import dev.justinsmith.bells.resources.exercise_two_hand_swing
import dev.justinsmith.bells.resources.exercise_wall_sit
import dev.justinsmith.bells.resources.exercise_windmill
import org.jetbrains.compose.resources.StringResource

/** A built-in exercise's name in the app's strings. */
internal fun BuiltInExercise.label(): StringResource = when (this) {
    BuiltInExercise.TwoHandSwing -> Res.string.exercise_two_hand_swing
    BuiltInExercise.OneHandSwing -> Res.string.exercise_one_hand_swing
    BuiltInExercise.Clean -> Res.string.exercise_clean
    BuiltInExercise.Press -> Res.string.exercise_press
    BuiltInExercise.Snatch -> Res.string.exercise_snatch
    BuiltInExercise.GetUp -> Res.string.exercise_get_up
    BuiltInExercise.GobletSquat -> Res.string.exercise_goblet_squat
    BuiltInExercise.Row -> Res.string.exercise_row
    BuiltInExercise.Jerk -> Res.string.exercise_jerk
    BuiltInExercise.LongCycle -> Res.string.exercise_long_cycle
    BuiltInExercise.Halo -> Res.string.exercise_halo
    BuiltInExercise.FrontSquat -> Res.string.exercise_front_squat
    BuiltInExercise.ArmorBuildingComplex -> Res.string.exercise_armor_building_complex
    BuiltInExercise.Deadlift -> Res.string.exercise_deadlift
    BuiltInExercise.HighPull -> Res.string.exercise_high_pull
    BuiltInExercise.RackedLunge -> Res.string.exercise_racked_lunge
    BuiltInExercise.Windmill -> Res.string.exercise_windmill
    BuiltInExercise.BottomsUpPress -> Res.string.exercise_bottoms_up_press
    BuiltInExercise.IronCardio -> Res.string.exercise_iron_cardio
    BuiltInExercise.PushUp -> Res.string.exercise_push_up
    BuiltInExercise.InclinePushUp -> Res.string.exercise_incline_push_up
    BuiltInExercise.PikePushUp -> Res.string.exercise_pike_push_up
    BuiltInExercise.BenchDip -> Res.string.exercise_bench_dip
    BuiltInExercise.PullUp -> Res.string.exercise_pull_up
    BuiltInExercise.ChinUp -> Res.string.exercise_chin_up
    BuiltInExercise.InvertedRow -> Res.string.exercise_inverted_row
    BuiltInExercise.AirSquat -> Res.string.exercise_air_squat
    BuiltInExercise.SplitSquat -> Res.string.exercise_split_squat
    BuiltInExercise.ReverseLunge -> Res.string.exercise_reverse_lunge
    BuiltInExercise.BulgarianSplitSquat -> Res.string.exercise_bulgarian_split_squat
    BuiltInExercise.StepUp -> Res.string.exercise_step_up
    BuiltInExercise.GluteBridge -> Res.string.exercise_glute_bridge
    BuiltInExercise.SingleLegGluteBridge -> Res.string.exercise_single_leg_glute_bridge
    BuiltInExercise.WallSit -> Res.string.exercise_wall_sit
    BuiltInExercise.CalfRaise -> Res.string.exercise_calf_raise
    BuiltInExercise.Plank -> Res.string.exercise_plank
    BuiltInExercise.SidePlank -> Res.string.exercise_side_plank
    BuiltInExercise.HollowHold -> Res.string.exercise_hollow_hold
    BuiltInExercise.DeadBug -> Res.string.exercise_dead_bug
    BuiltInExercise.Burpee -> Res.string.exercise_burpee
    BuiltInExercise.MountainClimber -> Res.string.exercise_mountain_climber
    BuiltInExercise.BandPullApart -> Res.string.exercise_band_pull_apart
    BuiltInExercise.BandFacePull -> Res.string.exercise_band_face_pull
    BuiltInExercise.BandSeatedRow -> Res.string.exercise_band_seated_row
    BuiltInExercise.BandLatPulldown -> Res.string.exercise_band_lat_pulldown
    BuiltInExercise.BandChestPress -> Res.string.exercise_band_chest_press
    BuiltInExercise.BandOverheadPress -> Res.string.exercise_band_overhead_press
    BuiltInExercise.BandBicepsCurl -> Res.string.exercise_band_biceps_curl
    BuiltInExercise.BandTricepsPushdown -> Res.string.exercise_band_triceps_pushdown
    BuiltInExercise.BandPallofPress -> Res.string.exercise_band_pallof_press
    BuiltInExercise.BandWoodchop -> Res.string.exercise_band_woodchop
    BuiltInExercise.BandSquat -> Res.string.exercise_band_squat
    BuiltInExercise.BandGoodMorning -> Res.string.exercise_band_good_morning
    BuiltInExercise.BandGluteBridge -> Res.string.exercise_band_glute_bridge
    BuiltInExercise.BandLateralWalk -> Res.string.exercise_band_lateral_walk
    BuiltInExercise.BandClamshell -> Res.string.exercise_band_clamshell
    BuiltInExercise.Hundred -> Res.string.exercise_hundred
    BuiltInExercise.RollUp -> Res.string.exercise_roll_up
    BuiltInExercise.SingleLegCircles -> Res.string.exercise_single_leg_circles
    BuiltInExercise.RollingLikeABall -> Res.string.exercise_rolling_like_a_ball
    BuiltInExercise.SingleLegStretch -> Res.string.exercise_single_leg_stretch
    BuiltInExercise.DoubleLegStretch -> Res.string.exercise_double_leg_stretch
    BuiltInExercise.Scissors -> Res.string.exercise_scissors
    BuiltInExercise.CrissCross -> Res.string.exercise_criss_cross
    BuiltInExercise.SpineStretchForward -> Res.string.exercise_spine_stretch_forward
    BuiltInExercise.Saw -> Res.string.exercise_saw
    BuiltInExercise.Swan -> Res.string.exercise_swan
    BuiltInExercise.SingleLegKick -> Res.string.exercise_single_leg_kick
    BuiltInExercise.SideKickSeries -> Res.string.exercise_side_kick_series
    BuiltInExercise.Teaser -> Res.string.exercise_teaser
    BuiltInExercise.Swimming -> Res.string.exercise_swimming
    BuiltInExercise.ShoulderBridge -> Res.string.exercise_shoulder_bridge
    BuiltInExercise.Seal -> Res.string.exercise_seal
    BuiltInExercise.LegPullFront -> Res.string.exercise_leg_pull_front
}
