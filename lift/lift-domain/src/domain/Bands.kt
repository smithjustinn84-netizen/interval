package dev.justinsmith.bells.domain

/** The colours bands come in, as the packets mark them. */
enum class BandColour { Yellow, Red, Green, Blue, Black, Purple, Orange, Grey }

/**
 * A resistance band of the user's: one of the five every user starts with, its [id] a [BandLevel] by name
 * and its [name] blank until they rename it, or one they added. [position] orders them, lightest first, so a
 * later band is a heavier one. An [archived] band is no longer offered, though its sets keep it.
 */
data class Band(
    val id: String,
    val name: String,
    val colour: BandColour,
    val position: Int,
    val archived: Boolean = false,
) {
    /** The band every user starts with that this is, if it is one: its words while it has no name. */
    val level: BandLevel? get() = BandLevel.entries.firstOrNull { it.name == id }

    companion object {
        fun of(level: BandLevel): Band = Band(level.name, "", level.colour, level.ordinal)

        /** The band the Log sheet starts on. */
        val Default: Band = of(BandLevel.Medium)
    }
}

/** The colour each of the bands every user starts with comes in, as the common packets run. */
val BandLevel.colour: BandColour get() = when (this) {
    BandLevel.ExtraLight -> BandColour.Yellow
    BandLevel.Light -> BandColour.Red
    BandLevel.Medium -> BandColour.Green
    BandLevel.Heavy -> BandColour.Blue
    BandLevel.ExtraHeavy -> BandColour.Black
}

/** The user's bands, lightest first: the five every user starts with, as [changed] leaves them, then theirs. */
fun bands(changed: List<Band>): List<Band> {
    val byId = changed.associateBy { it.id }
    val starting = BandLevel.entries.map { byId[it.name] ?: Band.of(it) }
    return (starting + changed.filter { it.level == null }).sortedWith(compareBy<Band> { it.position }.thenBy { it.id })
}

/** The band [id] among [bands], or one standing in for a band no longer there, heaviest of all. */
fun bandOf(id: String, bands: List<Band>): Band =
    bands.firstOrNull { it.id == id } ?: BandLevel.entries.firstOrNull { it.name == id }?.let(Band::of)
        ?: Band(id, "", BandColour.Grey, Int.MAX_VALUE)

/**
 * [bands] with [id] moved [by] places (lighter when negative), every band renumbered from 0 in its new order;
 * unchanged at either end.
 */
fun List<Band>.moved(id: String, by: Int): List<Band> {
    val from = indexOfFirst { it.id == id }
    val to = from + by
    if (from < 0 || to !in indices) return this
    val order = toMutableList().apply { add(to, removeAt(from)) }
    return order.mapIndexed { i, band -> band.copy(position = i) }
}

/** The position after every one of [bands]: where a band added goes, heaviest. */
fun nextBandPosition(bands: List<Band>): Int = (bands.maxOfOrNull { it.position } ?: -1) + 1

/** The bands the Log sheet offers: those not archived. */
fun offeredBands(bands: List<Band>): List<Band> = bands.filterNot { it.archived }

/** The longest name a band of the user's takes. */
const val MAX_BAND_NAME = 24
