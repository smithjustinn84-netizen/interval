package dev.justinsmith.interval.nav

import androidx.navigation3.runtime.NavKey
import dev.justinsmith.interval.nav.About
import dev.justinsmith.interval.nav.Licenses
import dev.justinsmith.interval.navConfiguration
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Saving the back stack serializes each key as a [NavKey]. A route missing its `subclass(...)` in
 * `navConfiguration` fails here, on every platform, rather than only at runtime on iOS and desktop.
 * JSON with the same serializers module rather than `encodeToSavedState`: on Android a SavedState is a
 * Bundle, which local tests cannot create.
 */
class NavKeyRoundTripTest {
    private val serializer = PolymorphicSerializer(NavKey::class)
    private val json = Json { serializersModule = navConfiguration.serializersModule }

    @Test
    fun `every route survives saving and restoring`() {
        val routes = listOf<NavKey>(TimerTab, PresetsTab, HistoryTab, Settings, Workout, About, Licenses)
        for (route in routes) {
            assertEquals(route, json.decodeFromString(serializer, json.encodeToString(serializer, route)))
        }
    }
}
