package dev.justinsmith.interval.platform

/**
 * Bluetooth's standard Heart Rate service, which every chest strap and armband that streams the heart rate speaks
 * (Polar's, Garmin's, Wahoo's and the rest): the ids a phone looks for and the bytes they send, read with nothing of
 * the platform's, so both sides' Bluetooth hands them here.
 */
object HeartRateService {
    /** The Heart Rate service a sensor advertises, by its 16-bit id: 0x180D. */
    const val SERVICE: String = "0000180d-0000-1000-8000-00805f9b34fb"

    /** Heart Rate Measurement, 0x2A37: notified, a reading each, about a second apart. */
    const val MEASUREMENT: String = "00002a37-0000-1000-8000-00805f9b34fb"

    /** The Battery service, 0x180F, and its Battery Level, 0x2A19: one byte, a percentage. */
    const val BATTERY_SERVICE: String = "0000180f-0000-1000-8000-00805f9b34fb"
    const val BATTERY_LEVEL: String = "00002a19-0000-1000-8000-00805f9b34fb"

    /** The descriptor a characteristic's notifications are switched on by: 0x2902. */
    const val NOTIFICATIONS: String = "00002902-0000-1000-8000-00805f9b34fb"

    /**
     * The state a Heart Rate Measurement's [bytes] stand for, null when they are too short to say a heart rate: a
     * reading with contact, or, from a sensor that says it reads none (a strap off the chest) or a number no heart
     * beats at, a reading without, as 0.
     *
     * The first byte is its flags: bit 0 the heart rate as two bytes (little-endian) rather than one, bits 1 and 2
     * whether contact is detected and whether the sensor can tell at all. Energy expended and the beats' intervals
     * (bits 3 and 4) follow the heart rate, so they move nothing read here.
     */
    fun measurement(bytes: ByteArray): LiveHeartRateState.Reading? {
        val flags = bytes.firstOrNull()?.toInt() ?: return null
        val wide = flags and WIDE != 0
        if (bytes.size < if (wide) WIDE_SIZE else NARROW_SIZE) return null
        val bpm = if (wide) bytes.unsigned(1) or (bytes.unsigned(2) shl BITS) else bytes.unsigned(1)
        val told = flags and CONTACT_SUPPORTED != 0
        val touching = flags and CONTACT_DETECTED != 0
        val contact = bpm in HeartRate.BPM_RANGE && (touching || !told)
        return LiveHeartRateState.Reading(bpm = if (contact) bpm else 0, contact = contact)
    }

    /** A Battery Level's [bytes] as a percentage, null when there is none or it is past 100. */
    fun batteryLevel(bytes: ByteArray): Int? = bytes.firstOrNull()?.toInt()?.and(BYTE)?.takeIf { it <= FULL }

    private fun ByteArray.unsigned(index: Int): Int = this[index].toInt() and BYTE

    private const val WIDE = 0x01
    private const val CONTACT_DETECTED = 0x02
    private const val CONTACT_SUPPORTED = 0x04
    private const val NARROW_SIZE = 2
    private const val WIDE_SIZE = 3
    private const val BITS = 8
    private const val BYTE = 0xFF
    private const val FULL = 100
}
