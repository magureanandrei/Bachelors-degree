package com.example.diabetesapp.data.models

data class BolusSettings(
    // General Configuration
    val insulinType: InsulinType = InsulinType.NOVORAPID,
    val durationOfAction: Float = 4.0f, // Duration in hours (decimal)

    // Insulin-to-Carb Ratio (ICR) - 4 Time Segments
    // How many grams of carbs 1 unit covers
    val icrMorning: Float = 10f,   // 06-11
    val icrNoon: Float = 10f,      // 11-16
    val icrEvening: Float = 10f,   // 16-23
    val icrNight: Float = 10f,     // 23-06

    // Insulin Sensitivity Factor (ISF) / Correction Factor - 4 Time Segments
    // How much 1 unit lowers BG in mg/dL
    val isfMorning: Float = 50f,   // 06-11
    val isfNoon: Float = 50f,      // 11-16
    val isfEvening: Float = 50f,   // 16-23
    val isfNight: Float = 50f,     // 23-06

    // Blood Glucose Target (Global for V1)
    val targetBG: Float = 100f // mg/dL
) {
    // Computed properties for display
    val durationDisplay: String
        get() = "${durationOfAction}h"

    val targetBGDisplay: String
        get() = "${targetBG.toInt()} mg/dL"

    /**
     * Get ICR value for a specific time segment
     */
    fun getIcrForSegment(segment: TimeSegment): Float {
        return when (segment) {
            TimeSegment.MORNING -> icrMorning
            TimeSegment.NOON -> icrNoon
            TimeSegment.EVENING -> icrEvening
            TimeSegment.NIGHT -> icrNight
        }
    }

    /**
     * Get ISF value for a specific time segment
     */
    fun getIsfForSegment(segment: TimeSegment): Float {
        return when (segment) {
            TimeSegment.MORNING -> isfMorning
            TimeSegment.NOON -> isfNoon
            TimeSegment.EVENING -> isfEvening
            TimeSegment.NIGHT -> isfNight
        }
    }

    /**
     * Get ICR for current time
     */
    fun getCurrentIcr(): Float = getIcrForSegment(TimeSegment.getCurrentSegment())

    /**
     * Get ISF for current time
     */
    fun getCurrentIsf(): Float = getIsfForSegment(TimeSegment.getCurrentSegment())

    /**
     * Check if all ICR values are the same (Simple mode)
     */
    val hasUniformIcr: Boolean
        get() = icrMorning == icrNoon && icrNoon == icrEvening && icrEvening == icrNight

    /**
     * Check if all ISF values are the same (Simple mode)
     */
    val hasUniformIsf: Boolean
        get() = isfMorning == isfNoon && isfNoon == isfEvening && isfEvening == isfNight

    /**
     * Get display summary for ICR (shows range if values differ)
     */
    val icrSummary: String
        get() {
            val values = listOf(icrMorning, icrNoon, icrEvening, icrNight)
            return if (values.distinct().size == 1) {
                "1:${icrMorning.toInt()}"
            } else {
                val min = values.minOrNull()?.toInt() ?: 0
                val max = values.maxOrNull()?.toInt() ?: 0
                "1:$min-$max"
            }
        }

    /**
     * Get display summary for ISF (shows range if values differ)
     */
    val isfSummary: String
        get() {
            val values = listOf(isfMorning, isfNoon, isfEvening, isfNight)
            return if (values.distinct().size == 1) {
                "1:${isfMorning.toInt()}"
            } else {
                val min = values.minOrNull()?.toInt() ?: 0
                val max = values.maxOrNull()?.toInt() ?: 0
                "1:$min-$max"
            }
        }
}

