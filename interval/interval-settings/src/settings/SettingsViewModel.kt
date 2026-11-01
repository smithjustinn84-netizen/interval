package dev.justinsmith.interval.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HealthStore
import dev.justinsmith.interval.platform.ShareableFile
import dev.justinsmith.interval.platform.Sharer
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.playCue
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.fromKg
import dev.justinsmith.interval.timer.stepped
import dev.justinsmith.interval.timer.toKg
import dev.justinsmith.interval.timer.withValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val cues: CueSettings,
    val prepSeconds: Int,
    val app: AppSettings,
    /** How the platform's health store stands; [HealthStatus.Unavailable] where there is none, and no row for it. */
    val health: HealthStatus = HealthStatus.Unavailable,
) {
    val body: BodySettings get() = app.body

    /** The body weight in whole units of [BodySettings.unit], or null when calorie estimates are off. */
    val weight: Int? get() = body.weightKg?.let { body.unit.fromKg(it) }
}

/** Where an import stands, from the file picked to what came in; null while there is none. */
sealed interface ImportStep {
    /** The file picked is being read and weighed against what is stored. */
    data object Reading : ImportStep

    /** What [file] comes to, for the review sheet; [exportedOn] the day it was written, in words. */
    data class Review(val file: ShareableFile, val review: ImportReview, val exportedOn: String?) : ImportStep

    data object Importing : ImportStep

    /** It came in: [added], kind by kind. */
    data class Done(val added: ImportCounts) : ImportStep

    /** A write failed part way: importing the file again adds what is missing. */
    data object Failed : ImportStep
}

/** Toggles a single [CueSettings] flag. */
enum class CueToggle { Sound, Voice, Halfway, Vibrate }

/**
 * The set-once preferences. A cue switched on plays a sample through [player], so its volume can be checked.
 * Export builds the file with [exporter] and hands it to [sharer]; Import reads a file back with [importer], one
 * [ImportStep] after another ([import]). The [health] store is shown as it stands
 * and disconnected from here; connecting takes the platform's sheet, which the screen opens.
 */
@Suppress("detekt.TooManyFunctions") // One function per setting, and export and import beside them.
class SettingsViewModel(
    private val repository: IntervalRepository,
    private val player: CuePlayer,
    private val exporter: DataExporter,
    private val sharer: Sharer,
    private val health: HealthStore,
    private val importer: DataImporter,
    private val timestamps: TimestampFormatter,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(repository.cueSettings, repository.config, repository.appSettings, health.status, ::buildState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = buildState(
                    repository.cueSettings.value,
                    repository.config.value,
                    repository.appSettings.value,
                    health.status.value,
                ),
            )

    /** Reads the health store's state again: as the screen opens, and as its permission sheet closes. */
    fun refreshHealth() = health.refresh()

    /** Stops writing workouts to the health store, giving its permissions back. */
    fun disconnectHealth() = health.disconnect()

    fun toggle(toggle: CueToggle) {
        repository.updateCueSettings { cues ->
            when (toggle) {
                CueToggle.Sound -> cues.copy(sound = !cues.sound)
                CueToggle.Voice -> cues.copy(voice = !cues.voice)
                CueToggle.Halfway -> cues.copy(halfway = !cues.halfway)
                CueToggle.Vibrate -> cues.copy(vibrate = !cues.vibrate)
            }
        }
        preview(toggle, repository.cueSettings.value)
    }

    /** Plays the cue [toggle] has just switched on, as a workout would; switching one off is silent. */
    private fun preview(toggle: CueToggle, cues: CueSettings) {
        when (toggle) {
            CueToggle.Sound -> if (cues.sound) player.beep(Beep.Work)
            CueToggle.Voice -> if (cues.voice) player.speak(Speech.Work(lastRound = false))
            CueToggle.Halfway -> playCue(Cue.Halfway, cues, player)
            CueToggle.Vibrate -> if (cues.vibrate) player.vibrate(Haptic.Work)
        }
    }

    fun stepPrep(direction: Int) {
        repository.updateConfig { it.stepped(ConfigField.Prep, direction) }
    }

    fun setPrep(seconds: Int) {
        repository.updateConfig { it.withValue(ConfigField.Prep, seconds) }
    }

    fun toggleKeepScreenOn() {
        repository.updateAppSettings { it.copy(keepScreenOn = !it.keepScreenOn) }
    }

    fun setTheme(theme: ThemeMode) {
        repository.updateAppSettings { it.copy(theme = theme) }
    }

    /** Sets the body weight to [value] whole units of the current unit, clamped to its range. */
    fun setWeight(value: Int) {
        updateBody { it.copy(weightKg = it.unit.toKg(value.coerceIn(it.unit.range))) }
    }

    /** Turns the calorie estimates off by forgetting the body weight; setting one turns them on again. */
    fun clearWeight() {
        updateBody { it.copy(weightKg = null) }
    }

    fun setWeightUnit(unit: WeightUnit) {
        updateBody { it.copy(unit = unit) }
    }

    fun setEffort(effort: Effort) {
        updateBody { it.copy(effort = effort) }
    }

    private val log = Logger.withTag("SettingsViewModel")
    private var exporting: Job? = null

    /**
     * Builds the export of everything stored and offers it to share or save; a second tap while one is under
     * way does nothing. A file that can't be written is logged, and the share sheet just doesn't open.
     */
    @Suppress("detekt.TooGenericExceptionCaught")
    fun export() {
        if (exporting?.isActive == true) return
        exporting = viewModelScope.launch {
            try {
                sharer.shareFile(exporter.export())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Couldn't export" }
            }
        }
    }

    private val importStep = MutableStateFlow<ImportStep?>(null)

    /** Where an import stands; null while none is under way. */
    val import: StateFlow<ImportStep?> = importStep.asStateFlow()

    /** Reads [file], picked to import, and has the review sheet say what it comes to. Nothing is written yet. */
    fun reviewImport(file: ShareableFile) {
        if (importStep.value == ImportStep.Reading || importStep.value == ImportStep.Importing) return
        importStep.value = ImportStep.Reading
        viewModelScope.launch {
            val review = importer.review(file)
            val day = (review as? ImportReview.Ready)?.let { timestamps.date(it.exportedAtEpochMillis) }
            importStep.value = ImportStep.Review(file, review, day)
        }
    }

    /** Imports the file under review, its settings too when [withSettings]. */
    @Suppress("detekt.TooGenericExceptionCaught")
    fun confirmImport(withSettings: Boolean) {
        val review = importStep.value as? ImportStep.Review ?: return
        if (review.review !is ImportReview.Ready) return
        importStep.value = ImportStep.Importing
        viewModelScope.launch {
            importStep.value = try {
                ImportStep.Done(importer.import(review.file, withSettings))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Couldn't import" }
                ImportStep.Failed
            }
        }
    }

    /** Closes the review sheet, or lets go of what the import came to once it is said. */
    fun dismissImport() {
        if (importStep.value != ImportStep.Importing) importStep.value = null
    }

    private fun updateBody(transform: (BodySettings) -> BodySettings) {
        repository.updateAppSettings { it.copy(body = transform(it.body)) }
    }

    private fun buildState(cues: CueSettings, config: IntervalConfig, app: AppSettings, health: HealthStatus) =
        SettingsUiState(cues, config.prepSeconds, app, health)
}
