package dev.justinsmith.interval.ui

/** Angles in degrees, as Compose's arcs and rotations take them. */
object Angles {
    const val FULL_TURN = 360f
    const val HALF_TURN = 180f
    const val QUARTER_TURN = 90f

    /** Where a dial starts: arcs measure from three o'clock, so twelve is a quarter turn back. */
    const val TWELVE_O_CLOCK = -QUARTER_TURN
}
