@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.settings

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.fakes.RecordingSharer
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.ShareableFile
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.fromKg
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest : MainDispatcherTest() {
    private val storage = FakeStorage()
    private val repository = storage.repository()
    private val player = RecordingCuePlayer()
    private val export = ShareableFile("interval-2026-10-15.json", "application/json", "{}")
    private val sharer = RecordingSharer()
    private val health = RecordingHealthStore()
    private val importer = FakeImporter()
    private val viewModel by lazy {
        SettingsViewModel(repository, player, { export }, sharer, health, importer, FixedTimestampFormatter)
    }

    @Test
    fun `export offers the exporter's file to share`() {
        viewModel.export()
        assertEquals(listOf(export), sharer.files)
    }

    @Test
    fun `an export that fails is dropped and the next one goes`() {
        var failing = true
        val exporter = DataExporter { if (failing) error("disk full") else export }
        val viewModel =
            SettingsViewModel(repository, player, exporter, sharer, health, importer, FixedTimestampFormatter)
        viewModel.export()
        assertTrue(sharer.files.isEmpty())
        failing = false
        viewModel.export()
        assertEquals(listOf(export), sharer.files)
    }

    @Test
    fun `a second tap while an export is under way exports nothing more`() {
        val built = CompletableDeferred<ShareableFile>()
        val viewModel =
            SettingsViewModel(repository, player, { built.await() }, sharer, health, importer, FixedTimestampFormatter)
        viewModel.export()
        viewModel.export()
        built.complete(export)
        assertEquals(listOf(export), sharer.files)
    }

    @Test
    fun `an import is reviewed first and imported once confirmed`() {
        val file = ShareableFile("interval.json", "application/json", "{}")
        importer.review = ImportReview.Ready(0, "0.2.0", ImportCounts(workouts = 2), alreadyHere = 5)
        viewModel.reviewImport(file)

        val step = viewModel.import.value as ImportStep.Review
        assertEquals(importer.review, step.review)
        assertEquals("day 0", step.exportedOn)
        assertTrue(importer.imported.isEmpty())

        viewModel.confirmImport(withSettings = true)
        assertEquals(listOf(file to true), importer.imported)
        assertEquals(ImportStep.Done(ImportCounts(workouts = 2)), viewModel.import.value)
        viewModel.dismissImport()
        assertNull(viewModel.import.value)
    }

    @Test
    fun `a file that can't be imported is never imported`() {
        importer.review = ImportReview.Unreadable
        viewModel.reviewImport(ShareableFile("x.json", "application/json", "nope"))
        viewModel.confirmImport(withSettings = false)
        assertTrue(importer.imported.isEmpty())
        assertEquals(ImportReview.Unreadable, (viewModel.import.value as ImportStep.Review).review)
    }

    @Test
    fun `an import that fails says so`() {
        importer.failing = true
        viewModel.reviewImport(ShareableFile("interval.json", "application/json", "{}"))
        viewModel.confirmImport(withSettings = false)
        assertEquals(ImportStep.Failed, viewModel.import.value)
    }

    @Test
    fun `toggles flip a single cue setting`() {
        viewModel.toggle(CueToggle.Halfway)
        val cues = viewModel.state.value.cues
        assertFalse(cues.halfway)
        assertTrue(cues.sound && cues.voice && cues.vibrate)
    }

    @Test
    fun `switching a cue on plays it once`() {
        viewModel.toggle(CueToggle.Sound)
        viewModel.toggle(CueToggle.Sound)
        viewModel.toggle(CueToggle.Vibrate)
        viewModel.toggle(CueToggle.Vibrate)
        assertEquals(listOf(Beep.Work), player.beeps)
        assertEquals(listOf(Haptic.Work), player.haptics)
        assertTrue(player.spoken.isEmpty())
    }

    @Test
    fun `switching voice on says a phase name`() {
        viewModel.toggle(CueToggle.Voice)
        viewModel.toggle(CueToggle.Voice)
        assertEquals(listOf<Speech>(Speech.Work(lastRound = false)), player.spoken)
    }

    @Test
    fun `the halfway sample is spoken when sound is off`() {
        viewModel.toggle(CueToggle.Sound)
        viewModel.toggle(CueToggle.Halfway)
        viewModel.toggle(CueToggle.Halfway)
        assertTrue(player.beeps.isEmpty())
        assertEquals(listOf<Speech>(Speech.Halfway), player.spoken)
    }

    @Test
    fun `the get-ready countdown steps and clamps`() {
        viewModel.stepPrep(1)
        assertEquals(15, viewModel.state.value.prepSeconds)
        viewModel.setPrep(-5)
        assertEquals(0, viewModel.state.value.prepSeconds)
    }

    @Test
    fun `theme and keep screen on survive a restart`() {
        viewModel.setTheme(ThemeMode.Dark)
        viewModel.toggleKeepScreenOn()
        val reloaded = storage.repository().appSettings.value
        assertEquals(ThemeMode.Dark, reloaded.theme)
        assertFalse(reloaded.keepScreenOn)
    }

    @Test
    fun `the weight starts at the default and can be turned off`() {
        assertEquals(WeightUnit.Kg.default, viewModel.state.value.weight)
        viewModel.setWeight(82)
        assertEquals(82, viewModel.state.value.weight)
        viewModel.clearWeight()
        assertNull(viewModel.state.value.weight)
    }

    @Test
    fun `a weight in pounds is kept in kilograms and shown in pounds`() {
        viewModel.setWeightUnit(WeightUnit.Lb)
        viewModel.setWeight(180)
        assertEquals(180, viewModel.state.value.weight)
        assertEquals(81.6, repository.appSettings.value.body.weightKg!!, absoluteTolerance = 0.1)
        viewModel.setWeightUnit(WeightUnit.Kg)
        assertEquals(82, viewModel.state.value.weight)
    }

    @Test
    fun `a weight is clamped to its unit's range`() {
        viewModel.setWeight(5)
        assertEquals(WeightUnit.Kg.range.first, viewModel.state.value.weight)
    }

    @Test
    fun `weight unit and effort survive a restart`() {
        viewModel.setWeightUnit(WeightUnit.Lb)
        viewModel.setWeight(150)
        viewModel.setEffort(Effort.Easy)
        val reloaded = storage.repository().appSettings.value.body
        assertEquals(WeightUnit.Lb, reloaded.unit)
        assertEquals(Effort.Easy, reloaded.effort)
        assertEquals(150, WeightUnit.Lb.fromKg(reloaded.weightKg!!))
    }

    @Test
    fun `the health store's standing is shown and can be given back`() {
        assertEquals(HealthStatus.Disconnected, viewModel.state.value.health)
        health.status.value = HealthStatus.Connected
        assertEquals(HealthStatus.Connected, viewModel.state.value.health)

        viewModel.refreshHealth()
        assertEquals(1, health.refreshes)

        viewModel.disconnectHealth()
        assertEquals(1, health.disconnects)
        assertEquals(HealthStatus.Disconnected, viewModel.state.value.health)
    }

    @Test
    fun `the health store's weight is offered in the unit and taken only when asked`() {
        health.weightKg = 82.4
        health.status.value = HealthStatus.Connected
        assertEquals(82, viewModel.state.value.healthWeight)
        assertEquals(WeightUnit.Kg.default, viewModel.state.value.weight)

        viewModel.setWeightUnit(WeightUnit.Lb)
        assertEquals(182, viewModel.state.value.healthWeight)
        viewModel.useHealthWeight()
        assertEquals(182, viewModel.state.value.weight)
        assertEquals(182, WeightUnit.Lb.fromKg(storage.repository().appSettings.value.body.weightKg!!))

        viewModel.setWeightUnit(WeightUnit.Kg)
        assertEquals(viewModel.state.value.healthWeight, viewModel.state.value.weight)
    }

    @Test
    fun `no weight is offered while disconnected or out of range`() {
        health.weightKg = 82.0
        assertNull(viewModel.state.value.healthWeight)
        viewModel.useHealthWeight()
        assertEquals(WeightUnit.Kg.default, viewModel.state.value.weight)

        health.status.value = HealthStatus.Connected
        assertEquals(82, viewModel.state.value.healthWeight)
        viewModel.disconnectHealth()
        assertNull(viewModel.state.value.healthWeight)

        health.weightKg = 400.0
        health.status.value = HealthStatus.Connected
        assertNull(viewModel.state.value.healthWeight)
    }
}

/** Reviews every file as [review] says, and records what it was asked to import. */
private class FakeImporter : DataImporter {
    var review: ImportReview = ImportReview.Ready(0, null, ImportCounts(workouts = 2), alreadyHere = 0)
    var failing = false
    val imported = mutableListOf<Pair<ShareableFile, Boolean>>()

    override suspend fun review(file: ShareableFile): ImportReview = review

    override suspend fun import(file: ShareableFile, withSettings: Boolean): ImportCounts {
        check(!failing) { "disk full" }
        imported += file to withSettings
        return (review as ImportReview.Ready).new
    }
}
