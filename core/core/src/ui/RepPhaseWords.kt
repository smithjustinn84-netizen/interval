package dev.justinsmith.interval.ui

import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.rep_cue_backswing
import dev.justinsmith.core.resources.rep_cue_bottom
import dev.justinsmith.core.resources.rep_cue_descend
import dev.justinsmith.core.resources.rep_cue_down_to_elbow
import dev.justinsmith.core.resources.rep_cue_drive
import dev.justinsmith.core.resources.rep_cue_drop
import dev.justinsmith.core.resources.rep_cue_float
import dev.justinsmith.core.resources.rep_cue_floor_press
import dev.justinsmith.core.resources.rep_cue_hand_down
import dev.justinsmith.core.resources.rep_cue_high_bridge
import dev.justinsmith.core.resources.rep_cue_high_pull
import dev.justinsmith.core.resources.rep_cue_hike_pass
import dev.justinsmith.core.resources.rep_cue_hip_snap
import dev.justinsmith.core.resources.rep_cue_lockout
import dev.justinsmith.core.resources.rep_cue_lower
import dev.justinsmith.core.resources.rep_cue_lower_bell
import dev.justinsmith.core.resources.rep_cue_press
import dev.justinsmith.core.resources.rep_cue_pull
import dev.justinsmith.core.resources.rep_cue_punch_through
import dev.justinsmith.core.resources.rep_cue_rack
import dev.justinsmith.core.resources.rep_cue_roll_down
import dev.justinsmith.core.resources.rep_cue_roll_to_elbow
import dev.justinsmith.core.resources.rep_cue_stand
import dev.justinsmith.core.resources.rep_cue_stand_up
import dev.justinsmith.core.resources.rep_cue_step_back
import dev.justinsmith.core.resources.rep_cue_sweep_through
import dev.justinsmith.core.resources.rep_cue_sweep_to_kneeling
import dev.justinsmith.core.resources.rep_cue_tall_sit
import dev.justinsmith.core.resources.rep_phase_backswing
import dev.justinsmith.core.resources.rep_phase_bottom
import dev.justinsmith.core.resources.rep_phase_descend
import dev.justinsmith.core.resources.rep_phase_down_to_elbow
import dev.justinsmith.core.resources.rep_phase_drive
import dev.justinsmith.core.resources.rep_phase_drop
import dev.justinsmith.core.resources.rep_phase_float
import dev.justinsmith.core.resources.rep_phase_floor_press
import dev.justinsmith.core.resources.rep_phase_hand_down
import dev.justinsmith.core.resources.rep_phase_high_bridge
import dev.justinsmith.core.resources.rep_phase_high_pull
import dev.justinsmith.core.resources.rep_phase_hike_pass
import dev.justinsmith.core.resources.rep_phase_hip_snap
import dev.justinsmith.core.resources.rep_phase_lockout
import dev.justinsmith.core.resources.rep_phase_lower
import dev.justinsmith.core.resources.rep_phase_lower_bell
import dev.justinsmith.core.resources.rep_phase_press
import dev.justinsmith.core.resources.rep_phase_pull
import dev.justinsmith.core.resources.rep_phase_punch_through
import dev.justinsmith.core.resources.rep_phase_rack
import dev.justinsmith.core.resources.rep_phase_roll_down
import dev.justinsmith.core.resources.rep_phase_roll_to_elbow
import dev.justinsmith.core.resources.rep_phase_stand
import dev.justinsmith.core.resources.rep_phase_stand_up
import dev.justinsmith.core.resources.rep_phase_step_back
import dev.justinsmith.core.resources.rep_phase_sweep_through
import dev.justinsmith.core.resources.rep_phase_sweep_to_kneeling
import dev.justinsmith.core.resources.rep_phase_tall_sit
import org.jetbrains.compose.resources.StringResource

// A rep's parts in words: what each is called, and the one thing to get right while doing it.

/** What a part of a rep is called on screen. */
val RepPhase.label: StringResource
    get() = when (this) {
        RepPhase.HikePass -> Res.string.rep_phase_hike_pass
        RepPhase.HipSnap -> Res.string.rep_phase_hip_snap
        RepPhase.Float -> Res.string.rep_phase_float
        RepPhase.Backswing -> Res.string.rep_phase_backswing
        RepPhase.Pull -> Res.string.rep_phase_pull
        RepPhase.Rack -> Res.string.rep_phase_rack
        RepPhase.Drop -> Res.string.rep_phase_drop
        RepPhase.HighPull -> Res.string.rep_phase_high_pull
        RepPhase.PunchThrough -> Res.string.rep_phase_punch_through
        RepPhase.Lockout -> Res.string.rep_phase_lockout
        RepPhase.Press -> Res.string.rep_phase_press
        RepPhase.Lower -> Res.string.rep_phase_lower
        RepPhase.Descend -> Res.string.rep_phase_descend
        RepPhase.Bottom -> Res.string.rep_phase_bottom
        RepPhase.Drive -> Res.string.rep_phase_drive
        RepPhase.Stand -> Res.string.rep_phase_stand
        RepPhase.FloorPress -> Res.string.rep_phase_floor_press
        RepPhase.RollToElbow -> Res.string.rep_phase_roll_to_elbow
        RepPhase.TallSit -> Res.string.rep_phase_tall_sit
        RepPhase.HighBridge -> Res.string.rep_phase_high_bridge
        RepPhase.SweepToKneeling -> Res.string.rep_phase_sweep_to_kneeling
        RepPhase.StandUp -> Res.string.rep_phase_stand_up
        RepPhase.StepBack -> Res.string.rep_phase_step_back
        RepPhase.HandDown -> Res.string.rep_phase_hand_down
        RepPhase.SweepThrough -> Res.string.rep_phase_sweep_through
        RepPhase.DownToElbow -> Res.string.rep_phase_down_to_elbow
        RepPhase.RollDown -> Res.string.rep_phase_roll_down
        RepPhase.LowerBell -> Res.string.rep_phase_lower_bell
    }

/**
 * The one thing to get right in a part of a rep, a few words a lifter can check the diagram against: the shins in
 * a hike pass, the forearm in a rack, the joints stacked in a lockout.
 */
val RepPhase.cue: StringResource
    get() = when (this) {
        RepPhase.HikePass -> Res.string.rep_cue_hike_pass
        RepPhase.HipSnap -> Res.string.rep_cue_hip_snap
        RepPhase.Float -> Res.string.rep_cue_float
        RepPhase.Backswing -> Res.string.rep_cue_backswing
        RepPhase.Pull -> Res.string.rep_cue_pull
        RepPhase.Rack -> Res.string.rep_cue_rack
        RepPhase.Drop -> Res.string.rep_cue_drop
        RepPhase.HighPull -> Res.string.rep_cue_high_pull
        RepPhase.PunchThrough -> Res.string.rep_cue_punch_through
        RepPhase.Lockout -> Res.string.rep_cue_lockout
        RepPhase.Press -> Res.string.rep_cue_press
        RepPhase.Lower -> Res.string.rep_cue_lower
        RepPhase.Descend -> Res.string.rep_cue_descend
        RepPhase.Bottom -> Res.string.rep_cue_bottom
        RepPhase.Drive -> Res.string.rep_cue_drive
        RepPhase.Stand -> Res.string.rep_cue_stand
        RepPhase.FloorPress -> Res.string.rep_cue_floor_press
        RepPhase.RollToElbow -> Res.string.rep_cue_roll_to_elbow
        RepPhase.TallSit -> Res.string.rep_cue_tall_sit
        RepPhase.HighBridge -> Res.string.rep_cue_high_bridge
        RepPhase.SweepToKneeling -> Res.string.rep_cue_sweep_to_kneeling
        RepPhase.StandUp -> Res.string.rep_cue_stand_up
        RepPhase.StepBack -> Res.string.rep_cue_step_back
        RepPhase.HandDown -> Res.string.rep_cue_hand_down
        RepPhase.SweepThrough -> Res.string.rep_cue_sweep_through
        RepPhase.DownToElbow -> Res.string.rep_cue_down_to_elbow
        RepPhase.RollDown -> Res.string.rep_cue_roll_down
        RepPhase.LowerBell -> Res.string.rep_cue_lower_bell
    }
