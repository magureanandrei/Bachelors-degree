package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 1: Baseline Calculations
 * Computes meal bolus (carbs / ICR) for all therapy types.
 * Correction bolus is therapy-aware: AID users receive no correction (pump auto-corrects
 * above 120 mg/dL every 5 minutes — MiniMed 780G SmartGuard; external corrections risk
 * insulin stacking). MDI and Standard Pump receive full correction.
 */
class BaselineStep : AlgorithmStep {
    override val name = "Baseline"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        var result = state
        val currentIcr = context.bolusSettings.getCurrentIcr().toDouble()
        val currentIsf = context.bolusSettings.getCurrentIsf().toDouble()
        val targetBg = context.bolusSettings.targetBG.toDouble()
        var dose = 0.0

        // Meal bolus — unchanged for all therapy types
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

        // Correction bolus — therapy-aware
        if (context.bolusSettings.isAidPump) {
            if (context.currentBG > 0 && context.currentBG > targetBg) {
                if (context.currentBG <= 250) {
                    result = result.addEntry(BreakdownEntry(
                        stepName = name,
                        label = "AID Auto-Correction Active",
                        emoji = "🤖",
                        description = "Your pump's SmartGuard is automatically managing your glucose with " +
                            "micro-corrections. No manual correction needed.",
                        effect = Effect.NEUTRAL,
                        runningTotal = dose
                    ))
                } else {
                    result = result.addEntry(BreakdownEntry(
                        stepName = name,
                        label = "Elevated BG Despite AID",
                        emoji = "⚠️",
                        description = "BG is very high (${context.currentBG.toInt()} mg/dL) despite active AID. " +
                            "Check your infusion set, sensor connection, and insulin reservoir. " +
                            "Consider a manual pen correction if the issue persists.",
                        effect = Effect.WARNING,
                        runningTotal = dose
                    ))
                }
                result = result.withMeta("correctionBolus", 0.0)
            }
        } else {
            // MDI and Standard Pump — full correction (unchanged)
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
        }

        return result.copy(currentDose = dose)
    }
}
