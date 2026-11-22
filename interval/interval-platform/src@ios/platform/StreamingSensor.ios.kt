package dev.justinsmith.interval.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSUserDefaults

/**
 * iOS's heart-rate sensor: any strap or armband, streamed over CoreBluetooth ([IosHeartRateLink]), which records no
 * class. On the main thread, as CoreBluetooth answers there.
 */
internal fun iosStreamingSensor(): StreamingSensor {
    val link = IosHeartRateLink()
    return StreamingSensor(
        radio = link,
        paired = DefaultsPairedSensor(NSUserDefaults.standardUserDefaults),
        access = object : SensorAccess {
            override val supported: Boolean get() = link.supported
            override val allowed: Boolean get() = link.allowed

            override suspend fun ready(): Boolean = link.ready()
        },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    )
}

/** Which sensor is the app's, in the user defaults under keys of its own. */
private class DefaultsPairedSensor(private val defaults: NSUserDefaults) : PairedSensorStore {
    override val sensor: NearbySensor?
        get() {
            val id = defaults.stringForKey(KEY_ID) ?: return null
            val name = defaults.stringForKey(KEY_NAME) ?: return null
            return NearbySensor(id, name)
        }

    override var battery: Int?
        get() = defaults.objectForKey(KEY_BATTERY)?.let { defaults.integerForKey(KEY_BATTERY).toInt() }
            ?.takeIf { it in BATTERY_RANGE }
        set(value) {
            if (value == null) {
                defaults.removeObjectForKey(KEY_BATTERY)
            } else {
                defaults.setInteger(value.coerceIn(BATTERY_RANGE).toLong(), forKey = KEY_BATTERY)
            }
        }

    override fun pair(id: String, name: String) {
        defaults.setObject(id, forKey = KEY_ID)
        defaults.setObject(name, forKey = KEY_NAME)
        defaults.removeObjectForKey(KEY_BATTERY)
    }

    override fun clear() {
        listOf(KEY_ID, KEY_NAME, KEY_BATTERY).forEach(defaults::removeObjectForKey)
    }

    private companion object {
        const val KEY_ID = "sensor.id"
        const val KEY_NAME = "sensor.name"
        const val KEY_BATTERY = "sensor.battery"
        val BATTERY_RANGE = 0..100
    }
}
