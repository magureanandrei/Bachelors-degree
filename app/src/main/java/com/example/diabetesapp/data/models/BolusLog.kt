package com.example.diabetesapp.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bolus_log")
data class BolusLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val eventType: String,

    // NEW: Track if this is a planned future event or a completed past event
    val status: String = "COMPLETED",

    val bloodGlucose: Double,
    val carbs: Double,
    val standardDose: Double,
    val suggestedDose: Double,
    val administeredDose: Double,

    val isSportModeActive: Boolean,
    val sportType: String?,
    val sportIntensity: String?,
    val sportDuration: Float?,
    val notes: String,
    val activeAdjustment: String = "None", // "None", "Illness", "Stress", "Heat"
    val adjustmentScale: Float = 1.0f,

    // NEW FIELDS
    val isHighStress: Boolean = false,
    val isIllness: Boolean = false,
    val isExtremeHeat: Boolean = false,

    val clinicalSuggestion: String?
) {
    val isAutoEntry: Boolean
        get() = notes.startsWith("Auto-")
}