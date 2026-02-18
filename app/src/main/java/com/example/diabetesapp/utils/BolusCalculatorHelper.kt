package com.example.diabetesapp.utils

import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.models.TimeSegment

/**
 * Bolus Calculator Utility Functions
 * Uses the 4-Block Time Segment system for medically accurate calculations
 */
object BolusCalculatorHelper {

    /**
     * Calculate total bolus dose based on current time segment
     *
     * @param carbs Grams of carbohydrates to cover
     * @param currentBG Current blood glucose in mg/dL
     * @param settings User's bolus settings with time-segmented values
     * @param activeInsulin Active Insulin On Board (IOB) in units (optional, default 0)
     * @return Total bolus in units (never negative)
     */
    fun calculateBolus(
        carbs: Float,
        currentBG: Float,
        settings: BolusSettings,
        activeInsulin: Float = 0f
    ): Float {
        // Get current time segment (MORNING/NOON/EVENING/NIGHT)
        val currentSegment = TimeSegment.getCurrentSegment()

        // Get appropriate ICR and ISF for this time
        val icr = settings.getIcrForSegment(currentSegment)
        val isf = settings.getIsfForSegment(currentSegment)

        // Calculate carb coverage
        val carbBolus = if (carbs > 0) carbs / icr else 0f

        // Calculate correction dose
        val bgDelta = currentBG - settings.targetBG
        val correctionBolus = bgDelta / isf

        // Total dose minus IOB
        val totalBolus = carbBolus + correctionBolus - activeInsulin

        // Never return negative (user shouldn't remove insulin!)
        return maxOf(0f, totalBolus)
    }

    /**
     * Calculate only the carb coverage portion
     */
    fun calculateCarbBolus(carbs: Float, settings: BolusSettings): Float {
        val segment = TimeSegment.getCurrentSegment()
        val icr = settings.getIcrForSegment(segment)
        return if (carbs > 0) carbs / icr else 0f
    }

    /**
     * Calculate only the correction portion
     */
    fun calculateCorrectionBolus(currentBG: Float, settings: BolusSettings): Float {
        val segment = TimeSegment.getCurrentSegment()
        val isf = settings.getIsfForSegment(segment)
        val bgDelta = currentBG - settings.targetBG
        return bgDelta / isf
    }

    /**
     * Get current ICR with time segment info
     * @return Pair of (segment, icr value)
     */
    fun getCurrentIcrWithSegment(settings: BolusSettings): Pair<TimeSegment, Float> {
        val segment = TimeSegment.getCurrentSegment()
        return segment to settings.getIcrForSegment(segment)
    }

    /**
     * Get current ISF with time segment info
     * @return Pair of (segment, isf value)
     */
    fun getCurrentIsfWithSegment(settings: BolusSettings): Pair<TimeSegment, Float> {
        val segment = TimeSegment.getCurrentSegment()
        return segment to settings.getIsfForSegment(segment)
    }

    /**
     * Calculate bolus for a FUTURE time (e.g., pre-bolusing)
     * Useful for planning ahead
     */
    fun calculateBolusForTime(
        hour: Int, // 0-23
        carbs: Float,
        currentBG: Float,
        settings: BolusSettings,
        activeInsulin: Float = 0f
    ): Float {
        val segment = TimeSegment.getSegmentForHour(hour)
        val icr = settings.getIcrForSegment(segment)
        val isf = settings.getIsfForSegment(segment)

        val carbBolus = if (carbs > 0) carbs / icr else 0f
        val correctionBolus = (currentBG - settings.targetBG) / isf
        val totalBolus = carbBolus + correctionBolus - activeInsulin

        return maxOf(0f, totalBolus)
    }

    /**
     * Format bolus result for display
     */
    fun formatBolus(units: Float): String {
        return String.format("%.2f", units)
    }

    /**
     * Get user-friendly explanation of calculation
     */
    fun getCalculationBreakdown(
        carbs: Float,
        currentBG: Float,
        settings: BolusSettings,
        activeInsulin: Float = 0f
    ): BolusBreakdown {
        val segment = TimeSegment.getCurrentSegment()
        val icr = settings.getIcrForSegment(segment)
        val isf = settings.getIsfForSegment(segment)

        val carbBolus = if (carbs > 0) carbs / icr else 0f
        val correctionBolus = (currentBG - settings.targetBG) / isf
        val totalBeforeIOB = carbBolus + correctionBolus
        val finalBolus = maxOf(0f, totalBeforeIOB - activeInsulin)

        return BolusBreakdown(
            timeSegment = segment,
            icr = icr,
            isf = isf,
            carbBolus = carbBolus,
            correctionBolus = correctionBolus,
            activeInsulin = activeInsulin,
            finalBolus = finalBolus
        )
    }
}

/**
 * Detailed breakdown of bolus calculation for transparency
 */
data class BolusBreakdown(
    val timeSegment: TimeSegment,
    val icr: Float,
    val isf: Float,
    val carbBolus: Float,
    val correctionBolus: Float,
    val activeInsulin: Float,
    val finalBolus: Float
) {
    fun toDisplayString(): String {
        return buildString {
            appendLine("Time: ${timeSegment.displayName} ${timeSegment.icon}")
            appendLine("ICR: 1:${icr.toInt()} g/unit")
            appendLine("ISF: 1:${isf.toInt()} mg/dL/unit")
            appendLine()
            appendLine("Carb Bolus: ${String.format("%.2f", carbBolus)} units")
            appendLine("Correction: ${String.format("%.2f", correctionBolus)} units")
            if (activeInsulin > 0) {
                appendLine("Active IOB: -${String.format("%.2f", activeInsulin)} units")
            }
            appendLine("─────────────────")
            appendLine("Total: ${String.format("%.2f", finalBolus)} units")
        }
    }
}

