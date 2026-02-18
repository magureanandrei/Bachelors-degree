package com.example.diabetesapp.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bolus_log")
data class BolusLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,

    // Types: "SMART_BOLUS", "MANUAL_INSULIN", "MEAL", "BG_CHECK", "MIXED_LOG"
    val eventType: String,

    val bloodGlucose: Double,
    val carbs: Double,
    val standardDose: Double,      // Normal math
    val suggestedDose: Double,     // Sport Algo math
    val administeredDose: Double,  // What the user ACTUALLY took

    val isSportModeActive: Boolean,
    val sportType: String?,
    val sportIntensity: String?,
    val sportDuration: Float?,
    val notes: String
)