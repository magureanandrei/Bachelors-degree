package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 7: Nighttime Safety
 * During 21:00–06:00:
 *   - Reduces the correction component by 30% to lower overnight hypo risk.
 *   - Warns about post-exercise nocturnal hypoglycemia (insulin sensitivity elevated ≤24h).
 *   - Suggests a bedtime snack when BG < 120 mg/dL at bedtime.
 *
 * Evidence: McMahon 2007 (biphasic risk 7–11h post-exercise),
 * Zivkovic 2026 (nocturnal hypo band 00:00–06:00),
 * ISPAD 2022 Exercise Chapter, ADA Standards of Care.
 */
class NighttimeSafetyStep : AlgorithmStep {
    override val name = "Nighttime Safety"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        val hour = context.timeOfDay.hour
        val isNighttime = hour >= 21 || hour < 6
        if (!isNighttime) return state

        var result = state
        val hypoLimit = context.bolusSettings.hypoLimit.toDouble()

        // 1. Reduce correction component during nighttime
        val correctionBolus = result.metadata["correctionBolus"] as? Double ?: 0.0
        if (correctionBolus > 0 && result.currentDose > 0) {
            val correctionReduction = correctionBolus * 0.30
            val newDose = maxOf(0.0, result.currentDose - correctionReduction)
            result = result.copy(currentDose = newDose)
                .addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Nighttime Correction Reduction",
                    emoji = "🌙",
                    description = "Correction reduced by 30% (−${String.format("%.2f", correctionReduction)}U) during " +
                        "nighttime hours to lower the risk of overnight hypoglycemia. " +
                        "Monitor BG before sleep.",
                    effect = Effect.DECREASE,
                    valueChange = -correctionReduction,
                    runningTotal = newDose
                ))
        }

        // 2. Nocturnal hypo warning if exercised today
        if (context.exercisedToday) {
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Post-Exercise Overnight Risk",
                emoji = "⚠️",
                description = "You exercised today. Exercise increases insulin sensitivity " +
                    "for up to 24 hours, raising the risk of overnight hypoglycemia. " +
                    "Consider a bedtime snack with protein and complex carbohydrates.",
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
        }

        // 3. Borderline low BG at bedtime
        if (context.currentBG > 0 && context.currentBG < 120 && context.currentBG >= hypoLimit) {
            val snackCarbs = if (context.currentBG < 90) 20 else 15
            result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, snackCarbs))
                .addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Bedtime Snack Suggestion",
                    emoji = "🍞",
                    description = "BG is ${context.currentBG.toInt()} mg/dL at bedtime. Consider a snack with ${snackCarbs}g " +
                        "of slow-acting carbohydrates to maintain glucose overnight.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))
        }

        return result
    }
}
