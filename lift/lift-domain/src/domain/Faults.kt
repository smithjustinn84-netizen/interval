package dev.justinsmith.lift.domain

/**
 * A fault a lift is prone to, the commonest of a bell thrown or pulled from the floor: [SquattedHinge] (squatting
 * the swing, the knees forward), [DeathGrip] (squeezing through the float), [BentWrist] (the wrist flexed in the
 * hinge) and [LateHand] (on the snatch, the hand punched through only once the bell has landed).
 */
enum class Fault { LateHand, SquattedHinge, DeathGrip, BentWrist }

/**
 * The faults this lift is prone to, the worst first, from how it moves: any loaded kettlebell hinge can be squatted,
 * a thrown one also gripped too hard and the wrist bent in it, and a snatch caught late. None for anything else: a
 * squat, a press, the bar's lifts (whose start the shins-vertical cue doesn't fit) or a lift of the user's own.
 */
val Exercise.faults: List<Fault>
    get() {
        val lift = this as? BuiltInExercise ?: return emptyList()
        val movement = lift.movement
        if (lift.equipment != Equipment.Kettlebell || movement.pattern != Pattern.Hinge || movement.grip == 0) {
            return emptyList()
        }
        return buildList {
            if (lift in SNATCHES) add(Fault.LateHand)
            add(Fault.SquattedHinge)
            if (movement.tempo == Tempo.Ballistic) {
                add(Fault.DeathGrip)
                add(Fault.BentWrist)
            }
        }
    }

/** The bell taken overhead in one move, which the hand must meet before it lands. */
private val SNATCHES = setOf(BuiltInExercise.Snatch, BuiltInExercise.HalfSnatch)
