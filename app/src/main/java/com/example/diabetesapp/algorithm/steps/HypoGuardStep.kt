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
            val rescueAmount = 20
            val hypoReservedCarbs = minOf(rescueAmount, context.plannedCarbs.toInt())
            val description = when {
                context.plannedCarbs >= rescueAmount ->
                    "Your BG is critically low at ${context.currentBG.toInt()} mg/dL. " +
                        "Your entered carbs will serve as the rescue treatment — " +
                        "eat them first WITHOUT bolusing. Wait for BG to recover above ${hypoLimit.toInt()} " +
                        "mg/dL before entering anything into your pump/taking insulin."
                context.plannedCarbs > 0 ->
                    "Your BG is critically low at ${context.currentBG.toInt()} mg/dL. " +
                        "Eat your ${context.plannedCarbs.toInt()}g plus an additional " +
                        "${rescueAmount - context.plannedCarbs.toInt()}g of fast-acting carbs. " +
                        "Do not bolus until BG recovers above ${hypoLimit.toInt()} mg/dL."
                else ->
                    "Your BG is critically low at ${context.currentBG.toInt()} mg/dL. " +
                        "Consume 15-20g of fast-acting carbohydrates immediately. " +
                        "Do not administer insulin until BG recovers above ${hypoLimit.toInt()} mg/dL."
            }
            result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, rescueAmount))
            result = result.withMeta("hypoReservedCarbs", hypoReservedCarbs)
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Severe Hypoglycemia",
                emoji = "🚨",
                description = description,
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
            result
        } else {
            val rescueAmount = 15
            val hypoReservedCarbs = minOf(rescueAmount, context.plannedCarbs.toInt())
            val description = when {
                context.plannedCarbs >= rescueAmount ->
                    "Your BG is low. Your entered carbs will serve as the rescue treatment — " +
                        "eat them first WITHOUT bolusing. Wait for BG to recover above ${hypoLimit.toInt()} " +
                        "mg/dL before entering anything into your pump/taking insulin."
                context.plannedCarbs > 0 ->
                    "Your BG is low. Eat your ${context.plannedCarbs.toInt()}g plus an additional " +
                        "${rescueAmount - context.plannedCarbs.toInt()}g of fast-acting carbs. " +
                        "Do not bolus until BG recovers above ${hypoLimit.toInt()} mg/dL."
                else ->
                    "Your BG is below your hypo threshold (${hypoLimit.toInt()} mg/dL). " +
                        "Consider treating with 15g fast-acting carbohydrates before bolusing."
            }
            result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, rescueAmount))
            result = result.withMeta("hypoReservedCarbs", hypoReservedCarbs)
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Low Blood Glucose",
                emoji = "⚠️",
                description = description,
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
            result
        }
    }
}
