package dev.justinsmith.interval.fakes

import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.storedName
import dev.justinsmith.interval.timer.withPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The settings in memory alone, for a ViewModel test that needs no storage: a change is there at once, as the
 * repository's is.
 */
class InMemorySettingsStore(
    config: IntervalConfig = IntervalConfig(),
    app: AppSettings = AppSettings(),
) : SettingsStore {
    override val config = MutableStateFlow(config)
    override val cueSettings = MutableStateFlow(CueSettings())
    override val appSettings = MutableStateFlow(app)

    override fun updateConfig(transform: (IntervalConfig) -> IntervalConfig) = config.update(transform)

    override fun updateCueSettings(transform: (CueSettings) -> CueSettings) = cueSettings.update(transform)

    override fun updateAppSettings(transform: (AppSettings) -> AppSettings) = appSettings.update(transform)
}

/**
 * The presets in memory, the built-ins then the user's, applied to [settings] as the repository applies them
 * ([withPreset]) and named as it names them ([storedName]); the user's are numbered from 1 as they are saved.
 */
class InMemoryPresetStore(private val settings: SettingsStore) : PresetStore {
    private val all = MutableStateFlow(builtInPresets)
    private val custom = MutableStateFlow(emptyList<Preset>())
    private var nextId = 1L

    override val presets: StateFlow<List<Preset>> get() = all
    override val customPresets: StateFlow<List<Preset>> get() = custom

    override fun applyPreset(preset: Preset) = settings.updateConfig { it.withPreset(preset) }

    override fun savePreset(preset: Preset) = keep(custom.value + preset.copy(id = null).named().copy(id = nextId++))

    override fun saveAndApplyPreset(preset: Preset) {
        applyPreset(preset)
        savePreset(preset)
    }

    override fun updatePreset(preset: Preset) {
        if (preset.id == null) return
        val onTimer = all.value.matching(settings.config.value)?.id == preset.id
        keep(custom.value.map { if (it.id == preset.id) preset.named() else it })
        if (onTimer) applyPreset(preset)
    }

    override fun deletePreset(preset: Preset) = keep(custom.value.filterNot { it.id == preset.id })

    override fun restorePreset(preset: Preset) = keep((custom.value + preset.named()).sortedBy { it.id })

    private fun Preset.named() = copy(name = storedName(all.value))

    private fun keep(presets: List<Preset>) {
        custom.value = presets
        all.value = builtInPresets + presets
    }
}
