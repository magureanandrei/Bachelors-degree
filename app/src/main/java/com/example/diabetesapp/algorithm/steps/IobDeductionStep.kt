package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 3: Active Insulin (IOB) Deduction
 * Subtracts insulin-on-board from the running dose, floors at 0.
 * Source: Standard IOB deduction (universal in bolus calculators).
 */
class IobDeductionStep : AlgorithmStep {
    override val name = "IOB Deduction"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        if (context.activeInsulinIOB <= 0) return state

        val mealBolus = (state.metadata["mealBolus"] as? Double) ?: 0.0
        val correctionBolus = (state.metadata["correctionBolus"] as? Double) ?: 0.0

        val adjustedCorrection = maxOf(0.0, correctionBolus - context.activeInsulinIOB)
        val deduction = correctionBolus - adjustedCorrection
        val newDose = mealBolus + adjustedCorrection

        if (deduction == 0.0) return state

        val iobStr = String.format("%.1f", context.activeInsulinIOB)
        val corrStr = String.format("%.1f", correctionBolus)
        val adjCorrStr = String.format("%.1f", adjustedCorrection)
        val mealStr = String.format("%.1f", mealBolus)

        val description = if (adjustedCorrection == 0.0) {
            "IOB of ${iobStr}U fully covers the correction component (${corrStr}U). Meal bolus of ${mealStr}U unchanged."
        } else {
            "IOB of ${iobStr}U partially offsets correction. Correction reduced from ${corrStr}U to ${adjCorrStr}U. Meal bolus of ${mealStr}U unchanged."
        }

        var result = state
            .addEntry(BreakdownEntry(
                stepName = name,
                label = "Active Insulin (IOB)",
                emoji = "💉",
                description = description,
                effect = Effect.DECREASE,
                valueChange = -deduction,
                runningTotal = newDose
            ))
            .copy(currentDose = newDose)

        val recentPenDose = (state.metadata["recentManualPenDose"] as? Double) ?: 0.0
        if (recentPenDose > 0.0 && context.bolusSettings.isAidPump) {
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Manual Pen Correction Detected",
                emoji = "⚠️",
                description = "A manual pen correction of ${String.format("%.1f", recentPenDose)}U " +
                    "was detected alongside AID. Your pump's SmartGuard may not account for " +
                    "this insulin — risk of insulin stacking. Monitor closely and consider " +
                    "informing your pump of the manual dose if your device supports it.",
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
        }
        return result
    }
}