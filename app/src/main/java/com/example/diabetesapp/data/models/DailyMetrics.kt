package com.example.diabetesapp.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_metrics")
data class DailyMetrics(
    @PrimaryKey
    val dateEpochDay: Long,
    val tbr: Float,
    val tir: Float,
    val tar: Float,
    val cv: Float,
    val steps: Long,
    val insulinUnits: Float,
    val carbs: Float,
    val readingCount: Int,
    val isCgmData: Boolean,
    val avgBg: Float = 0f
)
