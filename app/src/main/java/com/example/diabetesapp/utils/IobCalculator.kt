package com.example.diabetesapp.utils

import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.BolusSettings

data class IobResult(
    val totalIob: Double,
    val fromManualDoses: Double,  // doses logged manually in app
    val fromPump: Double?,        // xDrip IOB (null if not available)
    val hasManualOnTopOfPump: Boolean,  // warning flag
    val isEstimated: Boolean = false,
    val minutesSinceLastReading: Int = 0
)

object IobCalculator {

    /**
     * Insulin Activity Curve — bilinear model (OpenAPS oref0)
     * Returns fraction of a dose still active at [minutesAgo] minutes after injection.
     *
     * The bilinear model assumes insulin activity rises linearly to a peak,
     * then falls linearly to zero. Peak time scales with DIA at the ratio 75/180
     * (default: 75 minutes for 3-hour DIA, 100 minutes for 4-hour DIA).
     *
     * Source: OpenAPS oref0 (Lewis 2016), based on Walsh insulin action curves.
     * Formula: https://openaps.readthedocs.io/en/latest/docs/While%20You%20Wait%20For%20Gear/understanding-insulin-on-board-calculations.html
     *
     * @param minutesAgo minutes since insulin was administered
     * @param durationMinutes total duration of insulin action in minutes
     * @return fraction of insulin still on board (0.0 to 1.0)
     */
    private fun insulinActivityFraction(minutesAgo: Float, durationMinutes: Float): Float {
        if (minutesAgo <= 0f) return 1f
        if (minutesAgo >= durationMinutes) return 0f

        val peak = durationMinutes * (75f / 180f)
        val t = minutesAgo

        return if (t < peak) {
            // Before peak: IOB decreases slowly (most insulin still active)
            1f - (t * t) / (peak * durationMinutes)
        } else {
            // After peak: IOB decreases more rapidly
            (durationMinutes - t) * (durationMinutes - t) / ((durationMinutes - peak) * durationMinutes)
        }
    }

    /**
     * Calculate IOB purely from logged bolus history in the DB
     * Works for all therapy types
     */
    fun calculateFromHistory(
        logs: List<BolusLog>,
        settings: BolusSettings
    ): Double {
        val now = System.currentTimeMillis()
        val durationMs = settings.durationOfAction * 60 * 60 * 1000f
        val durationMinutes = settings.durationOfAction * 60f

        return logs
            .filter { log ->
                val dose = log.administeredDose
                val ageMs = now - log.timestamp
                dose > 0
                        && ageMs >= 0
                        && ageMs <= durationMs
                        && log.eventType != "BASAL_INSULIN"  // NEW
            }
            .sumOf { log ->
                val minutesAgo = ((now - log.timestamp) / 60000f)
                val fraction = insulinActivityFraction(minutesAgo, durationMinutes)
                log.administeredDose * fraction
            }
    }

    private fun calculateManualIob(
        logs: List<BolusLog>,
        settings: BolusSettings
    ): Double {
        val now = System.currentTimeMillis()
        val durationMs = settings.durationOfAction * 60 * 60 * 1000f
        val durationMinutes = settings.durationOfAction * 60f

        return logs.filter { log ->
            val ageMs = now - log.timestamp
            val isManual = log.notes != "Auto-entry via CareLink"
                    && log.notes?.startsWith("Auto-imported") != true
                    && log.notes?.startsWith("Auto-detected") != true
            log.administeredDose > 0
                    && ageMs >= 0
                    && ageMs <= durationMs
                    && isManual
                    && log.eventType != "BASAL_INSULIN"  // NEW
        }.sumOf { log ->
            val minutesAgo = (now - log.timestamp) / 60000f
            val fraction = insulinActivityFraction(minutesAgo, durationMinutes)
            log.administeredDose * fraction
        }
    }

    /**
     * Full IOB calculation with CGM fallback and warning logic
     */
    fun calculate(
        logs: List<BolusLog>,
        settings: BolusSettings,
        xdripIob: Double?,
        xdripTimestamp: Long?  // timestamp of last xDrip reading
    ): IobResult {
        val now = System.currentTimeMillis()
        val minutesSinceLast = xdripTimestamp?.let {
            ((now - it) / 60000).toInt()
        } ?: Int.MAX_VALUE

        val durationMinutes = settings.durationOfAction * 60f

        // Manual doses from our DB
        val manualIob = calculateManualIob(logs, settings)

        return when {
            xdripIob != null && minutesSinceLast <= 15 -> {
                // Fresh xDrip IOB — fully trust it
                IobResult(
                    totalIob = xdripIob + manualIob,
                    fromManualDoses = manualIob,
                    fromPump = xdripIob,
                    hasManualOnTopOfPump = manualIob > 0 && settings.isAidPump,
                    isEstimated = false,
                    minutesSinceLastReading = minutesSinceLast
                )
            }
            xdripIob != null && minutesSinceLast <= 60 -> {
                // Stale xDrip — decay the last known value forward
                val minutesStale = minutesSinceLast.toFloat()
                val decayFraction = insulinActivityFraction(minutesStale, durationMinutes)
                val decayedPumpIob = xdripIob * decayFraction
                IobResult(
                    totalIob = decayedPumpIob + manualIob,
                    fromManualDoses = manualIob,
                    fromPump = decayedPumpIob,
                    hasManualOnTopOfPump = manualIob > 0 && settings.isAidPump,
                    isEstimated = true,
                    minutesSinceLastReading = minutesSinceLast
                )
            }
            else -> {
                val totalIob = calculateFromHistory(logs, settings)
                val hasStaleTimestamp = xdripTimestamp != null && minutesSinceLast > 20
                IobResult(
                    totalIob = totalIob,
                    fromManualDoses = totalIob,
                    fromPump = null,
                    hasManualOnTopOfPump = false,
                    isEstimated = hasStaleTimestamp,
                    minutesSinceLastReading = minutesSinceLast
                )
            }
        }
    }
}