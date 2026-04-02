package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.CgmTrend
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 4: CGM Trend Velocity Modifiers
 * Adjusts dose based on the direction and speed of glucose change.
 *
 * Current implementation uses percentage-based adjustments.
 * NOTE: Laffel/Aleppo (2017) consensus recommends ISF-unit-based adjustments.
 * This will be redesigned in a future phase with proper evidence grounding.
 *
 * DoubleUp ↑↑: +20%
 * SingleUp ↑: no change (to be addressed in CGM arrow phase)
 * Flat →: no change
 * SingleDown ↓: -20%
 * DoubleDown ↓↓: halve dose + suggest 15g rescue carbs if BG < 120
 */
class CgmTrendStep : AlgorithmStep {
    override val name = "CGM Trend"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        if (!context.hasCGM || context.cgmTrend == CgmTrend.NONE || state.currentDose <= 0) {
            return state
        }

        var result = state

        when (context.cgmTrend) {
            CgmTrend.DOUBLE_UP -> {
                val newDose = state.currentDose * 1.20
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "CGM Rising Fast",
                    emoji = "📈",
                    description = "📈 CGM ↑↑: Increased dose by 20%.",
                    effect = Effect.INCREASE,
                    percentChange = 0.20,
                    valueChange = state.currentDose * 0.20,
                    runningTotal = newDose
                )).copy(currentDose = newDose)
            }
            CgmTrend.SINGLE_DOWN -> {
                val newDose = state.currentDose * 0.80
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "CGM Falling",
                    emoji = "📉",
                    description = "📉 CGM ↓: Reduced dose by 20%.",
                    effect = Effect.DECREASE,
                    percentChange = -0.20,
                    valueChange = -(state.currentDose * 0.20),
                    runningTotal = newDose
                )).copy(currentDose = newDose)
            }
            CgmTrend.DOUBLE_DOWN -> {
                val newDose = state.currentDose * 0.50
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "CGM Dropping Fast",
                    emoji = "⚠️",
                    description = "⚠️ CGM ↓↓: Rapid drop. Halved dose.",
                    effect = Effect.DECREASE,
                    percentChange = -0.50,
                    valueChange = -(state.currentDose * 0.50),
                    runningTotal = newDose
                )).copy(currentDose = newDose)

                if (context.currentBG in 1.0..119.9) {
                    result = result.copy(rescueCarbs = result.rescueCarbs + 15)
                        .addEntry(BreakdownEntry(
                            stepName = name,
                            label = "Rescue Carbs (Rapid Drop)",
                            emoji = "🍬",
                            description = "⚠️ BG dropping fast below 120. Suggesting 15g rescue carbs.",
                            effect = Effect.WARNING,
                            runningTotal = newDose
                        ))
                }
            }
            else -> { /* Flat, SingleUp, FortyFiveUp/Down — no change yet */ }
        }

        return result
    }
}