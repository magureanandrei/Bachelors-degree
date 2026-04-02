package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 1: Baseline Calculations
 * Computes meal bolus (carbs / ICR) and correction bolus ((BG - target) / ISF).
 * Source: Standard bolus calculator formula (universal).
 */
class BaselineStep : AlgorithmStep {
    override val name = "Baseline"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        var result = state
        val currentIcr = context.bolusSettings.getCurrentIcr().toDouble()
        val currentIsf = context.bolusSettings.getCurrentIsf().toDouble()
        val targetBg = context.bolusSettings.targetBG.toDouble()
        var dose = 0.0

        // Meal bolus
        if (context.plannedCarbs > 0 && currentIcr > 0) {
            val mealBolus = context.plannedCarbs / currentIcr
            dose += mealBolus
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Meal Bolus",
                emoji = "🍽️",
                description = "Meal: ${String.format("%.1f", mealBolus)}U (${context.plannedCarbs.toInt()}g ÷ ${String.format("%.1f", currentIcr)} ICR).",
                effect = Effect.INCREASE,
                valueChange = mealBolus,
                runningTotal = dose
            ))
            result = result.withMeta("mealBolus", mealBolus)
        }

        // Correction bolus
        if (context.currentBG > targetBg && currentIsf > 0) {
            val correctionBolus = (context.currentBG - targetBg) / currentIsf
            dose += correctionBolus
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Correction Bolus",
                emoji = "🎯",
                description = "Correction: +${String.format("%.1f", correctionBolus)}U (BG ${context.currentBG.toInt()} → ${targetBg.toInt()} target, ISF ${String.format("%.1f", currentIsf)}).",
                effect = Effect.INCREASE,
                valueChange = correctionBolus,
                runningTotal = dose
            ))
            result = result.withMeta("correctionBolus", correctionBolus)
        }

        return result.copy(currentDose = dose)
    }
}