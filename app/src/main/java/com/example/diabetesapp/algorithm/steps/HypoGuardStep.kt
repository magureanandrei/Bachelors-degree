package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 0: Hypo Guard
 * Warns before any calculation if BG is below the hypoglycemia threshold.
 * Pipeline continues — user may still have carbs to bolus for.
 *
 * Thresholds: International consensus (<70 mg/dL = hypo, <54 mg/dL = clinically significant)
 */
class HypoGuardStep : AlgorithmStep {
    override val name = "Hypo Guard"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        if (context.currentBG <= 0) return state

        val hypoLimit = context.bolusSettings.hypoLimit.toDouble()
        if (context.currentBG >= hypoLimit) return state

        var result = state

        return if (context.currentBG < 54.0) {
            result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, 20))
                .addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Severe Hypoglycemia",
                    emoji = "🚨",
                    description = "Your BG is critically low at ${context.currentBG.toInt()} mg/dL. " +
                        "Consume 15-20g of fast-acting carbohydrates immediately. " +
                        "Do not administer insulin until BG recovers above ${hypoLimit.toInt()} mg/dL.",
                    effect = Effect.WARNING,
                    runningTotal = result.currentDose
                ))
            result
        } else {
            result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, 15))
                .addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Low Blood Glucose",
                    emoji = "⚠️",
                    description = "Your BG is below your hypo threshold (${hypoLimit.toInt()} mg/dL). " +
                        "Consider treating with 15g fast-acting carbohydrates before bolusing.",
                    effect = Effect.WARNING,
                    runningTotal = result.currentDose
                ))
            result
        }
    }
}
