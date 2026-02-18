package com.example.diabetesapp.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bolus_log")
data class BolusLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long, // This will now represent the actual event time
    val eventType: String,

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

    // NEW: Save the exact suggestion the app gave the user!
    val clinicalSuggestion: String?
)