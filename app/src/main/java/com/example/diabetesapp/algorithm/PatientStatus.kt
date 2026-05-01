package com.example.diabetesapp.algorithm

import com.example.diabetesapp.data.models.PatientContext

/**
 * Resolved once per calculation. Every step reads zones instead of raw comparisons.
 * Stored in CalculationState.metadata["patientStatus"].
 */
data class PatientStatus(
    val bgZone: BgZone,
    val exercisePhase: ExercisePhase,
    val timeZone: TimeZone,
    val iobLevel: IobLevel
)

enum class BgZone {
    HYPO,           // < 54 mg/dL (clinically significant)
    LOW,            // 54 to < hypoLimit (default 70)
    BORDERLINE,     // hypoLimit to 90
    IN_RANGE,       // 90 to 140
    ABOVE_RANGE,    // 140 to 250
    HIGH,           // 250 to 300
    CRITICAL,       // > 300
    UNKNOWN         // BG not entered (0 or negative)
}

enum class ExercisePhase {
    NONE,               // no exercise context
    ACTIVE,             // isDoingSport == true (pre-exercise or currently exercising)
    WALK_RECOVERY,      // walked recently, in recovery window
    EXERCISE_RECOVERY   // structured exercise, in 0-6h recovery window
}

enum class TimeZone {
    NIGHTTIME,  // 21:00 - 06:00
    MORNING,    // 06:00 - 10:00
    DAYTIME,    // 10:00 - 17:00
    EVENING     // 17:00 - 21:00
}

enum class IobLevel {
    NONE,       // IOB ≈ 0 (< 0.1)
    LOW,        // < 1.0U
    MODERATE,   // 1.0 - 3.0U
    HIGH        // > 3.0U
}

object StatusResolver {

    fun resolve(context: PatientContext): PatientStatus {
        return PatientStatus(
            bgZone = resolveBgZone(context),
            exercisePhase = resolveExercisePhase(context),
            timeZone = resolveTimeZone(context),
            iobLevel = resolveIobLevel(context)
        )
    }

    private fun resolveBgZone(context: PatientContext): BgZone {
        val bg = context.currentBG
        val hypoLimit = context.bolusSettings.hypoLimit
        return when {
            bg <= 0 -> BgZone.UNKNOWN
            bg < 54 -> BgZone.HYPO
            bg < hypoLimit -> BgZone.LOW
            bg < 90 -> BgZone.BORDERLINE
            bg <= 140 -> BgZone.IN_RANGE
            bg <= 250 -> BgZone.ABOVE_RANGE
            bg <= 300 -> BgZone.HIGH
            else -> BgZone.CRITICAL
        }
    }

    private fun resolveExercisePhase(context: PatientContext): ExercisePhase {
        if (context.isDoingSport) return ExercisePhase.ACTIVE

        val hours = context.hoursSinceLastExercise
        if (hours < 0) return ExercisePhase.NONE

        val sportType = context.lastExerciseSportType
        val duration = context.lastExerciseDurationMins

        if (sportType == "Walking") {
            return when {
                duration < 10 -> ExercisePhase.NONE
                duration < 30 && hours <= 1.0f -> ExercisePhase.WALK_RECOVERY
                duration < 45 && hours <= 2.0f -> ExercisePhase.WALK_RECOVERY
                duration >= 45 && hours <= 3.0f -> ExercisePhase.WALK_RECOVERY
                else -> ExercisePhase.NONE
            }
        }

        if (sportType in listOf("Aerobic", "Mixed", "Anaerobic")) {
            return if (hours <= 6.0f) ExercisePhase.EXERCISE_RECOVERY
            else ExercisePhase.NONE
        }

        return ExercisePhase.NONE
    }

    private fun resolveTimeZone(context: PatientContext): TimeZone {
        val hour = context.timeOfDay.hour
        return when {
            hour >= 21 || hour < 6 -> TimeZone.NIGHTTIME
            hour < 10 -> TimeZone.MORNING
            hour < 17 -> TimeZone.DAYTIME
            else -> TimeZone.EVENING
        }
    }

    private fun resolveIobLevel(context: PatientContext): IobLevel {
        val iob = context.activeInsulinIOB
        return when {
            iob < 0.1 -> IobLevel.NONE
            iob < 1.0 -> IobLevel.LOW
            iob <= 3.0 -> IobLevel.MODERATE
            else -> IobLevel.HIGH
        }
    }
}
