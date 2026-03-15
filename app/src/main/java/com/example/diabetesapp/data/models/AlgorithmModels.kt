package com.example.diabetesapp.data.models

import java.time.LocalTime

// --- ENUMS: Defining the specific medical states ---

/**
 * Defines how the patient receives their baseline insulin.
 */
enum class TherapyType {
        MDI,
        PUMP_STANDARD,
        PUMP_AID;

    companion object {
        fun fromString(value: String): TherapyType = when (value) {
            "PUMP_STANDARD" -> PUMP_STANDARD
            "PUMP_AID" -> PUMP_AID
            else -> MDI
        }
    }
}

/**
 * Defines the velocity of blood glucose changes from a Continuous Glucose Monitor.
 */
enum class CgmTrend {
    DOUBLE_UP, SINGLE_UP, FLAT, SINGLE_DOWN, DOUBLE_DOWN, NONE;

    companion object {
        fun fromString(value: String): CgmTrend = when (value) {
            "↑↑" -> DOUBLE_UP
            "↑" -> SINGLE_UP
            "→" -> FLAT
            "↘" -> SINGLE_DOWN
            "↓" -> SINGLE_DOWN
            "↓↓" -> DOUBLE_DOWN
            else -> NONE
        }
    }
}

// --- THE INPUT: Everything the algorithm needs to make a safe decision ---

data class PatientContext(
    // 1. Core Profile
    val therapyType: TherapyType,
    val bolusSettings: BolusSettings,       // <-- Integrates your existing time-segmented settings!

    // 2. Current State
    val currentBG: Double,
    val hasCGM: Boolean = false,
    val cgmTrend: CgmTrend = CgmTrend.NONE,
    val activeInsulinIOB: Double = 0.0,
    val plannedCarbs: Double = 0.0,

    // 3. Sport State
    val isDoingSport: Boolean = false,
    val sportType: String = "Aerobic",      // "Aerobic", "Anaerobic", "Mixed"
    val sportIntensity: Int = 2,            // 1 (Low), 2 (Medium), 3 (High)
    val sportDurationMins: Int = 45,
    val minutesUntilSport: Int = 0,         // Positive = Future, Negative = Past

    // 4. Advanced Clinical Dimensions
    val isCompetitiveEvent: Boolean = false,
    val timeOfDay: LocalTime = LocalTime.now(),
    val isHighStress: Boolean = false,
    val isIllness: Boolean = false,
    val isExtremeHeat: Boolean = false,

    // 5. Passive Activity Context (from Health Connect)
    val dailySteps: Long = 0L
)

// --- THE OUTPUT: What the algorithm gives back to the UI ---

data class ClinicalDecision(
    val suggestedInsulinDose: Double,
    val suggestedRescueCarbs: Int,
    val clinicalRationale: String // The explanation text to display & save to History
)