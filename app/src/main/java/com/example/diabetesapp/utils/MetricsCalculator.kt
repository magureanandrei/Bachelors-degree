package com.example.diabetesapp.utils

import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.DailyMetrics
import kotlin.math.sqrt

object MetricsCalculator {

    // Overload 1: pre-fetched BG readings (source-agnostic) + separate insulin/carb logs
    fun computeDayMetrics(
        dateEpochDay: Long,
        bgReadings: List<Float>,
        insulinLogs: List<BolusLog>,
        hypoLimit: Float,
        hyperLimit: Float,
        steps: Long = 0L,
        isCgmData: Boolean = false
    ): DailyMetrics {
        val (tbr, tir, tar, cv) = tirCv(bgReadings, hypoLimit, hyperLimit)

        val insulin = insulinLogs
            .filter { it.administeredDose > 0 && it.eventType != "BASAL_INSULIN" }
            .sumOf { it.administeredDose }
            .toFloat()

        val carbs = insulinLogs
            .filter { it.carbs > 0 }
            .sumOf { it.carbs }
            .toFloat()

        return DailyMetrics(
            dateEpochDay = dateEpochDay,
            tbr = tbr, tir = tir, tar = tar, cv = cv,
            steps = steps,
            insulinUnits = insulin,
            carbs = carbs,
            readingCount = bgReadings.size,
            isCgmData = isCgmData
        )
    }

    // Overload 2: BolusLog list only — extracts BG readings internally (manual-mode backfill)
    fun computeDayMetrics(
        dateEpochDay: Long,
        logs: List<BolusLog>,
        hypoLimit: Float,
        hyperLimit: Float,
        steps: Long = 0L,
        isCgmData: Boolean = false
    ): DailyMetrics {
        val bgReadings = logs
            .filter { it.bloodGlucose > 0 }
            .map { it.bloodGlucose.toFloat() }
        return computeDayMetrics(
            dateEpochDay = dateEpochDay,
            bgReadings = bgReadings,
            insulinLogs = logs,
            hypoLimit = hypoLimit,
            hyperLimit = hyperLimit,
            steps = steps,
            isCgmData = isCgmData
        )
    }

    private fun tirCv(
        bgReadings: List<Float>,
        hypoLimit: Float,
        hyperLimit: Float
    ): Quad {
        if (bgReadings.size < 3) return Quad(0f, 0f, 0f, 0f)

        val below = bgReadings.count { it < hypoLimit }.toFloat()
        val above = bgReadings.count { it > hyperLimit }.toFloat()
        val inRange = bgReadings.size - below - above
        val total = bgReadings.size.toFloat()

        val tbrPct = (below / total) * 100f
        val tirPct = (inRange / total) * 100f
        val tarPct = (above / total) * 100f

        val mean = bgReadings.average().toFloat()
        val variance = bgReadings.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance)
        val cvPct = if (mean > 0f) (stdDev / mean) * 100f else 0f

        return Quad(tbrPct, tirPct, tarPct, cvPct)
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
}
