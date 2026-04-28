package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.CgmTrend
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 4: CGM Trend Velocity Modifiers
 * AID users: pump reacts to trends automatically (EASD/ISPAD 2024 AID+PA position statement,
 * moser2025use). Dose adjustments are skipped; carb suggestions for downward trends remain
 * because the pump cannot provide carbohydrates.
 * MDI/Standard Pump: percentage-based adjustments (future redesign to ISF-unit approach
 * per Aleppo/Laffel 2017 is deferred to a later phase).
 */
class CgmTrendStep : AlgorithmStep {
    override val name = "CGM Trend"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        if (!context.hasCGM || context.cgmTrend == CgmTrend.NONE) return state

        var result = state

        if (context.bolusSettings.isAidPump) {
            // AID: skip dose adjustments, pump manages trends automatically
            // Carb suggestions for downward trends are kept (pump cannot provide carbs)
            when (context.cgmTrend) {
                CgmTrend.DOUBLE_DOWN -> {
                    if (context.currentBG in 1.0..119.9) {
                        result = result.copy(rescueCarbs = result.rescueCarbs + 15)
                            .addEntry(BreakdownEntry(
                                stepName = name,
                                label = "Rapid Glucose Drop",
                                emoji = "⬇️",
                                description = "CGM shows rapidly falling glucose. Your pump will reduce insulin delivery, " +
                                    "but consider consuming 15g fast-acting carbohydrates as your pump cannot provide carbs.",
                                effect = Effect.WARNING,
                                runningTotal = result.currentDose
                            ))
                    }
                }
                CgmTrend.SINGLE_DOWN -> {
                    if (context.currentBG in 1.0..99.9) {
                        result = result.copy(rescueCarbs = result.rescueCarbs + 10)
                            .addEntry(BreakdownEntry(
                                stepName = name,
                                label = "Glucose Trending Down",
                                emoji = "⬇️",
                                description = "CGM shows falling glucose. Your pump is adjusting insulin. " +
                                    "Consider a small snack if symptoms develop.",
                                effect = Effect.NEUTRAL,
                                runningTotal = result.currentDose
                            ))
                    }
                }
                CgmTrend.DOUBLE_UP, CgmTrend.SINGLE_UP -> {
                    result = result.addEntry(BreakdownEntry(
                        stepName = name,
                        label = "CGM Trending Up",
                        emoji = "📈",
                        description = "Your pump is adjusting insulin delivery based on this trend.",
                        effect = Effect.NEUTRAL,
                        runningTotal = result.currentDose
                    ))
                }
                else -> { /* FLAT — no action needed */ }
            }
            return result
        }

        // MDI and Standard Pump — percentage-based adjustments (unchanged)
        if (state.currentDose <= 0) return state

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
