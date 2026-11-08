package dev.justinsmith.lift.domain

/**
 * The two safety lines Lift says before a set (docs/kettlebell-coaching.md, section 2), each in place of the set-up
 * cue that moment would say.
 */
enum class SafetyLine {
    /** "Groove five light first": before a ballistic lift's first set at a bell new to it ([firstAtBell]). */
    GrooveLight,

    /** "Stop a rep early": on a [Readiness.Taxed] day, once a session. */
    StopEarly,
}

/**
 * Whether a set of this lift at [load] is its first at a new bell, so worth grooving light first: a ballistic lift
 * ([Tempo.Ballistic]) at a bell, or a pair, heavier than any of this lift in [history] (a bell it has been done at, or
 * a heavier one, is not new), with a lighter bell among the [bells] to groove with. A lift never done is new at any
 * bell. [history] is every set logged, this session's and 15:15's snatches with them, so a bell met there is no
 * longer new.
 */
fun Exercise.firstAtBell(load: Load, bells: List<Double>, history: Iterable<SetLog>): Boolean =
    movement.tempo == Tempo.Ballistic &&
        load is Load.Bell &&
        bells.any { it < load.kg } &&
        history.none { set ->
            val done = set.load
            set.exercise.key == key && done is Load.Bell && done.double == load.double && done.kg >= load.kg
        }
