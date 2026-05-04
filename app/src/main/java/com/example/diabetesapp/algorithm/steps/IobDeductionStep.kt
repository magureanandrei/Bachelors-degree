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

        // Pen correction warning — must run BEFORE early return
        // so it fires even when currentDose == 0 (AID therapy)
        val recentPenDose = (state.metadata["recentManualPenDose"] as? Double) ?: 0.0
        var result = state
        if (recentPenDose > 0.0 && context.bolusSettings.isAidPump) {
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Manual Pen Correction Detected",
                emoji = "",
                description = "A manual pen correction of ${String.format("%.1f", recentPenDose)}U " +
                        "was detected alongside AID. Your pump's SmartGuard may not account for " +
                        "this insulin, risk of insulin stacking. Monitor closely",
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
        }

        // Early return after pen check — IOB deduction not needed if no dose
        if (context.activeInsulinIOB <= 0 || state.currentDose <= 0) return result

        // IOB deduction — correction component only (Walsh 2012)
        val mealBolus = (state.metadata["mealBolus"] as? Double) ?: 0.0
        val correctionBolus = (state.metadata["correctionBolus"] as? Double) ?: 0.0

        val adjustedCorrection = maxOf(0.0, correctionBolus - context.activeInsulinIOB)
        val deduction = correctionBolus - adjustedCorrection
        val newDose = mealBolus + adjustedCorrection

        if (deduction <= 0.0) return result

        return result
            .addEntry(BreakdownEntry(
                stepName = name,
                label = "Active Insulin (IOB)",
                emoji = "",
                description = when {
                    correctionBolus == 0.0 ->
                        "No correction component to offset. IOB does not reduce meal bolus."
                    adjustedCorrection == 0.0 ->
                        "IOB of ${String.format("%.1f", context.activeInsulinIOB)}U fully covers " +
                                "the correction component (${String.format("%.1f", correctionBolus)}U). " +
                                "Meal bolus of ${String.format("%.1f", mealBolus)}U unchanged."
                    else ->
                        "IOB of ${String.format("%.1f", context.activeInsulinIOB)}U partially " +
                                "offsets correction. Correction reduced from " +
                                "${String.format("%.1f", correctionBolus)}U to " +
                                "${String.format("%.1f", adjustedCorrection)}U. " +
                                "Meal bolus of ${String.format("%.1f", mealBolus)}U unchanged."
                },
                effect = Effect.DECREASE,
                valueChange = -deduction,
                runningTotal = newDose
            ))
            .copy(currentDose = newDose)
    }
}