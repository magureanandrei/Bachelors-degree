package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType
import kotlin.math.abs
import kotlin.math.min

/**
 * Step 5: Sport Modifiers & Carb Suggestions
 * Handles insulin reduction during exercise and pre/post-sport safety advice.
 *
 * Sport reduction percentages: T1DEXIP study / ISPAD Exercise Guidelines (Moser et al. 2020)
 * Late-onset hypo window: McMahon et al. 2007, Maran et al. 2010
 * Rescue carb amounts: ADA Standards of Care ("rule of 15")
 */
class SportModifierStep : AlgorithmStep {
    override val name = "Sport Modifier"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        if (!context.isDoingSport) return state

        var result = state

        // A. No-CGM warning
        if (!context.hasCGM) {
            result = result.addWarning("⚠️ No CGM: Check BG manually 30 mins into activity.")
        }

        val isFuture = context.minutesUntilSport >= 0
        val absMinutes = abs(context.minutesUntilSport)

        // B. Insulin reduction (only if taking insulin)
        if (state.currentDose > 0) {
            var reductionPercent = 0.0
            var reductionLabel = ""

            when (context.sportType) {
                "Anaerobic" -> {
                    reductionPercent = 0.10
                    reductionLabel = "Anaerobic: 10% reduction."
                }
                "Mixed" -> {
                    reductionPercent = when (context.sportIntensity) {
                        1 -> 0.15; 2 -> 0.25; else -> 0.40
                    }
                    reductionLabel = "Mixed (Int ${context.sportIntensity}): ${String.format("%.0f", reductionPercent * 100)}% reduction."
                }
                else -> { // Aerobic
                    reductionPercent = when (context.sportIntensity) {
                        1 -> 0.25; 2 -> 0.50; else -> 0.75
                    }
                    reductionLabel = "Aerobic (Int ${context.sportIntensity}): ${String.format("%.0f", reductionPercent * 100)}% reduction."
                }
            }

            // Duration modifier (>45 mins)
            var durationExtra = 0.0
            if (context.sportDurationMins > 45) {
                val extraTime = context.sportDurationMins - 45
                durationExtra = min(0.20, (extraTime / 15.0) * 0.10)
                reductionPercent += durationExtra
                reductionLabel += " Duration >45m: +${String.format("%.0f", durationExtra * 100)}% extra."
            }

            // Cap at 90%
            reductionPercent = min(0.90, reductionPercent)

            val reductionAmount = state.currentDose * reductionPercent
            val newDose = state.currentDose * (1.0 - reductionPercent)

            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Sport Reduction",
                emoji = "🏃",
                description = "🏃 $reductionLabel",
                effect = Effect.DECREASE,
                percentChange = -reductionPercent,
                valueChange = -reductionAmount,
                runningTotal = newDose
            )).copy(currentDose = newDose)
        }

        // C. Pre-sport carb / pump therapy advice
        if (result.currentDose < 0.1 && context.currentBG > 0 && context.currentBG < 125
            && (isFuture || absMinutes <= 5)
        ) {
            if (context.currentBG < 90) {
                result = result.copy(rescueCarbs = 20)
                var carbAdvice = "⚠️ Low BG (${context.currentBG.toInt()}). Eat 20g fast-acting carbs."
                carbAdvice += if (absMinutes > 30) " Since sport is in ${absMinutes}m, add complex carbs."
                else " Wait 15m before starting."
                result = result.addEntry(BreakdownEntry(
                    stepName = name, label = "Pre-Sport Carbs (Low BG)", emoji = "⚠️",
                    description = carbAdvice, effect = Effect.WARNING,
                    runningTotal = result.currentDose
                ))
            } else { // BG 90-125
                if (context.sportType == "Aerobic") {
                    if (absMinutes > 30) {
                        when (context.therapyType) {
                            TherapyType.MDI -> {
                                result = result.copy(rescueCarbs = 15)
                                    .addEntry(BreakdownEntry(
                                        stepName = name, label = "Pre-Sport Carbs (MDI)",
                                        emoji = "💡",
                                        description = "💡 Pens: Aerobic will drop BG. Eat 15g complex carbs now.",
                                        effect = Effect.WARNING, runningTotal = result.currentDose
                                    ))
                            }
                            TherapyType.PUMP_STANDARD -> {
                                result = result.addEntry(BreakdownEntry(
                                    stepName = name, label = "Temp Basal Advice",
                                    emoji = "💡",
                                    description = "💡 Pump: Set 50% Temp Basal now. (Or eat 15g carbs).",
                                    effect = Effect.NEUTRAL, runningTotal = result.currentDose
                                ))
                            }
                            TherapyType.PUMP_AID -> {
                                result = result.addEntry(BreakdownEntry(
                                    stepName = name, label = "AID Exercise Target",
                                    emoji = "💡",
                                    description = "💡 AID Pump: Set 'Exercise Target' now to suspend micro-boluses.",
                                    effect = Effect.NEUTRAL, runningTotal = result.currentDose
                                ))
                            }
                        }
                    } else {
                        result = result.copy(rescueCarbs = 15)
                            .addEntry(BreakdownEntry(
                                stepName = name, label = "Pre-Sport Carbs (Aerobic)",
                                emoji = "⚠️",
                                description = "⚠️ Aerobic drops BG fast. Eat 15g fast carbs before starting.",
                                effect = Effect.WARNING, runningTotal = result.currentDose
                            ))
                    }
                } else if (context.sportType == "Mixed") {
                    result = result.copy(rescueCarbs = 10)
                        .addEntry(BreakdownEntry(
                            stepName = name, label = "Pre-Sport Carbs (Mixed)",
                            emoji = "💡",
                            description = "💡 Eat 10g carbs to stabilize BG for mixed activity.",
                            effect = Effect.WARNING, runningTotal = result.currentDose
                        ))
                }
            }
        }

        // D. Post-sport / late-onset warning
        if (!isFuture) {
            if (context.currentBG > 0 && context.currentBG < 80) {
                result = result.copy(rescueCarbs = 15)
                    .addEntry(BreakdownEntry(
                        stepName = name, label = "Post-Workout Low",
                        emoji = "⚠️",
                        description = "⚠️ Post-workout Low. Consume 15g fast carbs immediately.",
                        effect = Effect.WARNING, runningTotal = result.currentDose
                    ))
            } else if (context.sportType == "Aerobic" || context.sportType == "Mixed"
                || context.sportDurationMins > 45) {
                result = result.addWarning(
                    "⚠️ Insight: Risk of Late-Onset Hypoglycemia (7-11h window). Consider bedtime snack or reduced night basal."
                )
            }
        }

        return result
    }
}