package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 5: Nighttime Safety (Warning-Only)
 * During 21:00–06:00, adds advisory entries — no dose modification.
 * Dose adjustments for exercise and corrections are handled upstream by ContextualModifierStep.
 *
 * Evidence: McMahon 2007 (biphasic risk 7–11h post-exercise),
 * Zivkovic 2026 (nocturnal hypo band 00:00–06:00), ISPAD 2022 Exercise Chapter.
 */
class NighttimeSafetyStep : AlgorithmStep {
    override val name = "Nighttime Safety"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        val hour = context.timeOfDay.hour
        val isNighttime = hour >= 21 || hour < 6
        if (!isNighttime) return state

        var result = state

        // 1. Overnight exercise warning (McMahon 2007, Zivkovic 2026)
        if (context.exercisedToday) {
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Post-Exercise Overnight Risk",
                emoji = "🌙",
                description = "You exercised today. Exercise increases insulin sensitivity for " +
                    "up to 24 hours, raising the risk of overnight hypoglycemia. Consider a " +
                    "bedtime snack with protein and complex carbohydrates.",
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
        }

        // 2. Borderline BG bedtime snack suggestion
        if (context.currentBG > 0 && context.currentBG < 120 &&
            context.currentBG >= context.bolusSettings.hypoLimit) {
            val snackCarbs = if (context.currentBG < 90) 20 else 15
            result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, snackCarbs))
                .addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Bedtime Snack Suggestion",
                    emoji = "🍞",
                    description = "BG is ${context.currentBG.toInt()} mg/dL at bedtime. Consider " +
                        "a snack with ${snackCarbs}g of slow-acting carbohydrates.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))
        }

        return result
    }
}
