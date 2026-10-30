package dev.justinsmith.lift.ui

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.cues_air_squat
import dev.justinsmith.lift.ui.resources.cues_armor_building_complex
import dev.justinsmith.lift.ui.resources.cues_band_biceps_curl
import dev.justinsmith.lift.ui.resources.cues_band_chest_press
import dev.justinsmith.lift.ui.resources.cues_band_clamshell
import dev.justinsmith.lift.ui.resources.cues_band_face_pull
import dev.justinsmith.lift.ui.resources.cues_band_glute_bridge
import dev.justinsmith.lift.ui.resources.cues_band_good_morning
import dev.justinsmith.lift.ui.resources.cues_band_lat_pulldown
import dev.justinsmith.lift.ui.resources.cues_band_lateral_walk
import dev.justinsmith.lift.ui.resources.cues_band_overhead_press
import dev.justinsmith.lift.ui.resources.cues_band_pallof_press
import dev.justinsmith.lift.ui.resources.cues_band_pull_apart
import dev.justinsmith.lift.ui.resources.cues_band_seated_row
import dev.justinsmith.lift.ui.resources.cues_band_squat
import dev.justinsmith.lift.ui.resources.cues_band_triceps_pushdown
import dev.justinsmith.lift.ui.resources.cues_band_woodchop
import dev.justinsmith.lift.ui.resources.cues_barbell_back_squat
import dev.justinsmith.lift.ui.resources.cues_barbell_bench_press
import dev.justinsmith.lift.ui.resources.cues_barbell_clean
import dev.justinsmith.lift.ui.resources.cues_barbell_clean_and_jerk
import dev.justinsmith.lift.ui.resources.cues_barbell_clean_pull
import dev.justinsmith.lift.ui.resources.cues_barbell_deadlift
import dev.justinsmith.lift.ui.resources.cues_barbell_front_squat
import dev.justinsmith.lift.ui.resources.cues_barbell_hang_clean
import dev.justinsmith.lift.ui.resources.cues_barbell_hang_snatch
import dev.justinsmith.lift.ui.resources.cues_barbell_jerk
import dev.justinsmith.lift.ui.resources.cues_barbell_overhead_press
import dev.justinsmith.lift.ui.resources.cues_barbell_overhead_squat
import dev.justinsmith.lift.ui.resources.cues_barbell_power_clean
import dev.justinsmith.lift.ui.resources.cues_barbell_power_snatch
import dev.justinsmith.lift.ui.resources.cues_barbell_push_press
import dev.justinsmith.lift.ui.resources.cues_barbell_romanian_deadlift
import dev.justinsmith.lift.ui.resources.cues_barbell_row
import dev.justinsmith.lift.ui.resources.cues_barbell_snatch
import dev.justinsmith.lift.ui.resources.cues_barbell_snatch_balance
import dev.justinsmith.lift.ui.resources.cues_barbell_snatch_pull
import dev.justinsmith.lift.ui.resources.cues_bench_dip
import dev.justinsmith.lift.ui.resources.cues_bottoms_up_press
import dev.justinsmith.lift.ui.resources.cues_bulgarian_split_squat
import dev.justinsmith.lift.ui.resources.cues_burpee
import dev.justinsmith.lift.ui.resources.cues_calf_raise
import dev.justinsmith.lift.ui.resources.cues_chin_up
import dev.justinsmith.lift.ui.resources.cues_clean
import dev.justinsmith.lift.ui.resources.cues_criss_cross
import dev.justinsmith.lift.ui.resources.cues_dead_bug
import dev.justinsmith.lift.ui.resources.cues_deadlift
import dev.justinsmith.lift.ui.resources.cues_double_leg_stretch
import dev.justinsmith.lift.ui.resources.cues_front_squat
import dev.justinsmith.lift.ui.resources.cues_get_up
import dev.justinsmith.lift.ui.resources.cues_glute_bridge
import dev.justinsmith.lift.ui.resources.cues_goblet_squat
import dev.justinsmith.lift.ui.resources.cues_halo
import dev.justinsmith.lift.ui.resources.cues_high_pull
import dev.justinsmith.lift.ui.resources.cues_hollow_hold
import dev.justinsmith.lift.ui.resources.cues_hundred
import dev.justinsmith.lift.ui.resources.cues_incline_push_up
import dev.justinsmith.lift.ui.resources.cues_inverted_row
import dev.justinsmith.lift.ui.resources.cues_iron_cardio
import dev.justinsmith.lift.ui.resources.cues_jerk
import dev.justinsmith.lift.ui.resources.cues_leg_pull_front
import dev.justinsmith.lift.ui.resources.cues_long_cycle
import dev.justinsmith.lift.ui.resources.cues_mountain_climber
import dev.justinsmith.lift.ui.resources.cues_one_hand_swing
import dev.justinsmith.lift.ui.resources.cues_pike_push_up
import dev.justinsmith.lift.ui.resources.cues_plank
import dev.justinsmith.lift.ui.resources.cues_press
import dev.justinsmith.lift.ui.resources.cues_pull_up
import dev.justinsmith.lift.ui.resources.cues_push_up
import dev.justinsmith.lift.ui.resources.cues_racked_lunge
import dev.justinsmith.lift.ui.resources.cues_reverse_lunge
import dev.justinsmith.lift.ui.resources.cues_roll_up
import dev.justinsmith.lift.ui.resources.cues_rolling_like_a_ball
import dev.justinsmith.lift.ui.resources.cues_row
import dev.justinsmith.lift.ui.resources.cues_saw
import dev.justinsmith.lift.ui.resources.cues_scissors
import dev.justinsmith.lift.ui.resources.cues_seal
import dev.justinsmith.lift.ui.resources.cues_shoulder_bridge
import dev.justinsmith.lift.ui.resources.cues_side_kick_series
import dev.justinsmith.lift.ui.resources.cues_side_plank
import dev.justinsmith.lift.ui.resources.cues_single_leg_circles
import dev.justinsmith.lift.ui.resources.cues_single_leg_glute_bridge
import dev.justinsmith.lift.ui.resources.cues_single_leg_kick
import dev.justinsmith.lift.ui.resources.cues_single_leg_stretch
import dev.justinsmith.lift.ui.resources.cues_snatch
import dev.justinsmith.lift.ui.resources.cues_spine_stretch_forward
import dev.justinsmith.lift.ui.resources.cues_split_squat
import dev.justinsmith.lift.ui.resources.cues_step_up
import dev.justinsmith.lift.ui.resources.cues_swan
import dev.justinsmith.lift.ui.resources.cues_swimming
import dev.justinsmith.lift.ui.resources.cues_teaser
import dev.justinsmith.lift.ui.resources.cues_two_hand_swing
import dev.justinsmith.lift.ui.resources.cues_wall_sit
import dev.justinsmith.lift.ui.resources.cues_windmill
import org.jetbrains.compose.resources.StringArrayResource

/** How to do each built-in, a line each. Every one has its lines, so one added without them fails to build. */
@Suppress("detekt.LongMethod") // One arm for each built-in.
fun BuiltInExercise.cues(): StringArrayResource = when (this) {
    BuiltInExercise.TwoHandSwing -> Res.array.cues_two_hand_swing
    BuiltInExercise.OneHandSwing -> Res.array.cues_one_hand_swing
    BuiltInExercise.Clean -> Res.array.cues_clean
    BuiltInExercise.Press -> Res.array.cues_press
    BuiltInExercise.Snatch -> Res.array.cues_snatch
    BuiltInExercise.GetUp -> Res.array.cues_get_up
    BuiltInExercise.GobletSquat -> Res.array.cues_goblet_squat
    BuiltInExercise.Row -> Res.array.cues_row
    BuiltInExercise.Jerk -> Res.array.cues_jerk
    BuiltInExercise.LongCycle -> Res.array.cues_long_cycle
    BuiltInExercise.Halo -> Res.array.cues_halo
    BuiltInExercise.FrontSquat -> Res.array.cues_front_squat
    BuiltInExercise.ArmorBuildingComplex -> Res.array.cues_armor_building_complex
    BuiltInExercise.Deadlift -> Res.array.cues_deadlift
    BuiltInExercise.HighPull -> Res.array.cues_high_pull
    BuiltInExercise.RackedLunge -> Res.array.cues_racked_lunge
    BuiltInExercise.Windmill -> Res.array.cues_windmill
    BuiltInExercise.BottomsUpPress -> Res.array.cues_bottoms_up_press
    BuiltInExercise.IronCardio -> Res.array.cues_iron_cardio
    BuiltInExercise.BarbellSnatch -> Res.array.cues_barbell_snatch
    BuiltInExercise.BarbellCleanAndJerk -> Res.array.cues_barbell_clean_and_jerk
    BuiltInExercise.BarbellClean -> Res.array.cues_barbell_clean
    BuiltInExercise.BarbellJerk -> Res.array.cues_barbell_jerk
    BuiltInExercise.BarbellPowerSnatch -> Res.array.cues_barbell_power_snatch
    BuiltInExercise.BarbellPowerClean -> Res.array.cues_barbell_power_clean
    BuiltInExercise.BarbellHangSnatch -> Res.array.cues_barbell_hang_snatch
    BuiltInExercise.BarbellHangClean -> Res.array.cues_barbell_hang_clean
    BuiltInExercise.BarbellSnatchPull -> Res.array.cues_barbell_snatch_pull
    BuiltInExercise.BarbellCleanPull -> Res.array.cues_barbell_clean_pull
    BuiltInExercise.BarbellOverheadSquat -> Res.array.cues_barbell_overhead_squat
    BuiltInExercise.BarbellSnatchBalance -> Res.array.cues_barbell_snatch_balance
    BuiltInExercise.BarbellPushPress -> Res.array.cues_barbell_push_press
    BuiltInExercise.BarbellBackSquat -> Res.array.cues_barbell_back_squat
    BuiltInExercise.BarbellFrontSquat -> Res.array.cues_barbell_front_squat
    BuiltInExercise.BarbellDeadlift -> Res.array.cues_barbell_deadlift
    BuiltInExercise.BarbellRomanianDeadlift -> Res.array.cues_barbell_romanian_deadlift
    BuiltInExercise.BarbellBenchPress -> Res.array.cues_barbell_bench_press
    BuiltInExercise.BarbellOverheadPress -> Res.array.cues_barbell_overhead_press
    BuiltInExercise.BarbellRow -> Res.array.cues_barbell_row
    BuiltInExercise.PushUp -> Res.array.cues_push_up
    BuiltInExercise.InclinePushUp -> Res.array.cues_incline_push_up
    BuiltInExercise.PikePushUp -> Res.array.cues_pike_push_up
    BuiltInExercise.BenchDip -> Res.array.cues_bench_dip
    BuiltInExercise.PullUp -> Res.array.cues_pull_up
    BuiltInExercise.ChinUp -> Res.array.cues_chin_up
    BuiltInExercise.InvertedRow -> Res.array.cues_inverted_row
    BuiltInExercise.AirSquat -> Res.array.cues_air_squat
    BuiltInExercise.SplitSquat -> Res.array.cues_split_squat
    BuiltInExercise.ReverseLunge -> Res.array.cues_reverse_lunge
    BuiltInExercise.BulgarianSplitSquat -> Res.array.cues_bulgarian_split_squat
    BuiltInExercise.StepUp -> Res.array.cues_step_up
    BuiltInExercise.GluteBridge -> Res.array.cues_glute_bridge
    BuiltInExercise.SingleLegGluteBridge -> Res.array.cues_single_leg_glute_bridge
    BuiltInExercise.WallSit -> Res.array.cues_wall_sit
    BuiltInExercise.CalfRaise -> Res.array.cues_calf_raise
    BuiltInExercise.Plank -> Res.array.cues_plank
    BuiltInExercise.SidePlank -> Res.array.cues_side_plank
    BuiltInExercise.HollowHold -> Res.array.cues_hollow_hold
    BuiltInExercise.DeadBug -> Res.array.cues_dead_bug
    BuiltInExercise.Burpee -> Res.array.cues_burpee
    BuiltInExercise.MountainClimber -> Res.array.cues_mountain_climber
    BuiltInExercise.BandPullApart -> Res.array.cues_band_pull_apart
    BuiltInExercise.BandFacePull -> Res.array.cues_band_face_pull
    BuiltInExercise.BandSeatedRow -> Res.array.cues_band_seated_row
    BuiltInExercise.BandLatPulldown -> Res.array.cues_band_lat_pulldown
    BuiltInExercise.BandChestPress -> Res.array.cues_band_chest_press
    BuiltInExercise.BandOverheadPress -> Res.array.cues_band_overhead_press
    BuiltInExercise.BandBicepsCurl -> Res.array.cues_band_biceps_curl
    BuiltInExercise.BandTricepsPushdown -> Res.array.cues_band_triceps_pushdown
    BuiltInExercise.BandPallofPress -> Res.array.cues_band_pallof_press
    BuiltInExercise.BandWoodchop -> Res.array.cues_band_woodchop
    BuiltInExercise.BandSquat -> Res.array.cues_band_squat
    BuiltInExercise.BandGoodMorning -> Res.array.cues_band_good_morning
    BuiltInExercise.BandGluteBridge -> Res.array.cues_band_glute_bridge
    BuiltInExercise.BandLateralWalk -> Res.array.cues_band_lateral_walk
    BuiltInExercise.BandClamshell -> Res.array.cues_band_clamshell
    BuiltInExercise.Hundred -> Res.array.cues_hundred
    BuiltInExercise.RollUp -> Res.array.cues_roll_up
    BuiltInExercise.SingleLegCircles -> Res.array.cues_single_leg_circles
    BuiltInExercise.RollingLikeABall -> Res.array.cues_rolling_like_a_ball
    BuiltInExercise.SingleLegStretch -> Res.array.cues_single_leg_stretch
    BuiltInExercise.DoubleLegStretch -> Res.array.cues_double_leg_stretch
    BuiltInExercise.Scissors -> Res.array.cues_scissors
    BuiltInExercise.CrissCross -> Res.array.cues_criss_cross
    BuiltInExercise.SpineStretchForward -> Res.array.cues_spine_stretch_forward
    BuiltInExercise.Saw -> Res.array.cues_saw
    BuiltInExercise.Swan -> Res.array.cues_swan
    BuiltInExercise.SingleLegKick -> Res.array.cues_single_leg_kick
    BuiltInExercise.SideKickSeries -> Res.array.cues_side_kick_series
    BuiltInExercise.Teaser -> Res.array.cues_teaser
    BuiltInExercise.Swimming -> Res.array.cues_swimming
    BuiltInExercise.ShoulderBridge -> Res.array.cues_shoulder_bridge
    BuiltInExercise.Seal -> Res.array.cues_seal
    BuiltInExercise.LegPullFront -> Res.array.cues_leg_pull_front
}
