package dev.justinsmith.interval.ui

import androidx.compose.runtime.compositionLocalOf
import dev.justinsmith.interval.timer.WeightUnit

/**
 * The weight unit set in Settings (kg or lb): every bell, bar, plate, tonnage, PR and benchmark reads this.
 * The app provides it once, over everything; a preview or test with none defaults to kilograms.
 */
val LocalWeightUnit = compositionLocalOf { WeightUnit.Kg }
