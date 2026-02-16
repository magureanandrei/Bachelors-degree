package com.example.diabetesapp.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bolus_log")
data class BolusLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val bloodGlucose: Double,
    val carbs: Double,
    val standardDose: Double,
    val finalDose: Double,
    val isSportModeActive: Boolean,
    val sportType: String?,
    val sportIntensity: String?,
    val sportDuration: Float?,
    val notes: String
)

