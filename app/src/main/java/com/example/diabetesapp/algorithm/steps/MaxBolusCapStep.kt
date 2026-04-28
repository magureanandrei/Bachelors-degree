package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 7: Max Bolus Safety Cap
 * Clamps the final recommended dose to the user's configured maximum.
 * Standard safety feature present in all commercial bolus calculators.
 */
class MaxBolusCapStep : AlgorithmStep {
    override val name = "Max Bolus Cap"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        val maxBolus = context.bolusSettings.maxBolus.toDouble()
        if (maxBolus <= 0 || state.currentDose <= maxBolus) return state

        val originalDose = state.currentDose
        return state.addEntry(BreakdownEntry(
            stepName = name,
            label = "Safety Cap Applied",
            emoji = "🔒",
            description = "Calculated dose of ${String.format("%.1f", originalDose)}U exceeds your maximum " +
                "bolus limit of ${String.format("%.1f", maxBolus)}U. Dose has been capped at ${String.format("%.1f", maxBolus)}U.",
            effect = Effect.DECREASE,
            valueChange = maxBolus - originalDose,
            runningTotal = maxBolus
        )).copy(currentDose = maxBolus)
    }
}
