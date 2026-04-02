package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 2: Outside Factors (Resistance/Absorption Modifiers)
 * Applied BEFORE IOB deduction because they affect total systemic insulin need.
 *
 * - Illness: +25% (insulin resistance). Source: ISPAD Sick Day Guidelines.
 * - Stress: +15% (cortisol-mediated). Source: physiological mechanism.
 * - Heat: -10% (faster absorption). Source: insulin pharmacokinetics literature.
 *
 * Illness and Stress are mutually exclusive (else-if) to prevent stacking.
 * Heat is independent (different mechanism: absorption vs resistance).
 */
class OutsideFactorsStep : AlgorithmStep {
    override val name = "Outside Factors"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        if (state.currentDose <= 0) return state

        var result = state
        var factorMultiplier = 1.0

        // Illness and Stress are else-if: no double stacking
        if (context.isIllness) {
            factorMultiplier += 0.25
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Illness",
                emoji = "🩺",
                description = "🩺 Illness: +25% total dose (insulin resistance).",
                effect = Effect.INCREASE,
                percentChange = 0.25,
                valueChange = state.currentDose * 0.25,
                runningTotal = state.currentDose * factorMultiplier
            ))
        } else if (context.isHighStress) {
            factorMultiplier += 0.15
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Stress",
                emoji = "😫",
                description = "😫 Stress: +15% total dose (cortisol response).",
                effect = Effect.INCREASE,
                percentChange = 0.15,
                valueChange = state.currentDose * 0.15,
                runningTotal = state.currentDose * factorMultiplier
            ))
        }

        // Heat is independent (affects absorption speed, not resistance)
        if (context.isExtremeHeat) {
            factorMultiplier -= 0.10
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Heat",
                emoji = "🔥",
                description = "🔥 Heat: -10% total dose (rapid absorption).",
                effect = Effect.DECREASE,
                percentChange = -0.10,
                valueChange = -(state.currentDose * 0.10),
                runningTotal = state.currentDose * factorMultiplier
            ))
        }

        return result.copy(currentDose = state.currentDose * factorMultiplier)
    }
}