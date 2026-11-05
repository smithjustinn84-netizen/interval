package dev.justinsmith.interval.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.orEmptyOnError
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.storedName
import dev.justinsmith.interval.timer.withPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The presets, the built-ins and the user's own, which follow the database: the [PresetStore]. Applying one puts
 * its shape on the Timer through [settings]; saving one names it against what is stored. Written through
 * [writer], the queue every interval repository shares, so a preset applied and saved lands in the order it was
 * made.
 */
class PresetRepository(
    private val presetDao: PresetDao,
    private val settings: SettingsRepository,
    private val writer: SerialWriter,
    scope: CoroutineScope,
) : PresetStore {
    private val log = Logger.withTag("PresetRepository")

    private val _ready = MutableStateFlow(false)

    /** True once the user's presets have been read. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _presets = MutableStateFlow(builtInPresets)

    /** Every preset the lists show: the built-ins, then the user's. */
    override val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    /**
     * The user's presets, in the order they were saved: [presets] without the built-ins, worked out from it, so
     * the two never disagree. It can trail [presets] for a moment after a change, never the other way.
     */
    override val customPresets: StateFlow<List<Preset>> =
        presets.map { all -> all.filter { it.custom } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // Mapped upstream of its fallback, so a row that fails to map counts as a failed read.
        scope.launch {
            presetDao.observeAll()
                .map { rows -> rows.map { it.toDomain() } }
                .orEmptyOnError("presets", log)
                .collect { custom ->
                    _presets.value = builtInPresets + custom
                    _ready.value = true
                }
        }
    }

    /** Puts [preset] on the Timer, keeping the user's get-ready countdown and, mostly, activity: see [withPreset]. */
    override fun applyPreset(preset: Preset) {
        settings.updateConfig { it.withPreset(preset) }
    }

    /**
     * Saves [preset] as a new one of the user's own, under its [storedName]: a blank name becomes its
     * shape's ("40/20 ×6"), and one another preset has gets a number ("Tabata 2").
     */
    override fun savePreset(preset: Preset) {
        writer.persist { presetDao.insert(preset.copy(id = null).named().toEntity()) }
    }

    /** Saves [preset] as a new one of the user's own and puts its shape on the Timer. */
    override fun saveAndApplyPreset(preset: Preset) {
        applyPreset(preset)
        savePreset(preset)
    }

    /**
     * Rewrites one of the user's own presets, found by its id: its name (kept unique among the others) and
     * shape. When it is the preset on the Timer the new shape goes there too, so the two stay in step
     * rather than leaving the old shape on the Timer, unsaved.
     */
    override fun updatePreset(preset: Preset) {
        if (preset.id == null) return
        // Asked now: once the write lands, the list already holds the new shape.
        val onTimer = _presets.value.matching(settings.config.value)?.id == preset.id
        writer.persist { presetDao.update(preset.named().toEntity()) }
        if (onTimer) applyPreset(preset)
    }

    /** Removes one of the user's own presets. Built-ins have no id and are left alone. */
    override fun deletePreset(preset: Preset) {
        val id = preset.id ?: return
        writer.persist { presetDao.delete(id) }
    }

    /**
     * Puts a preset [deletePreset] removed back under its own id, so it returns to its place in the
     * list and any plan made from it finds it again: Undo. Its name is kept unique as on a save.
     */
    override fun restorePreset(preset: Preset) {
        if (preset.id == null) return
        writer.persist { presetDao.insert(preset.named().toEntity()) }
    }

    // Named inside the write against what is stored, not against [presets]: that follows the database, so
    // two saves in a row would both miss the other and take the same name.
    private suspend fun Preset.named(): Preset =
        copy(name = storedName(builtInPresets + presetDao.all().map { it.toDomain() }))
}
