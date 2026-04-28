package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType
import kotlin.math.abs
import kotlin.math.min

/**
 * Step 5: Sport Modifiers & Carb Suggestions
 * Handles insulin reduction during exercise and pre/post-sport safety advice.
 * Section C is therapy-aware: MDI, Standard Pump, and AID receive distinct strategies.
 *
 * Sport reduction percentages: T1DEXIP study / ISPAD Exercise Guidelines (Moser et al. 2020)
 * MDI basal constraint: ISPAD 2022 Exercise Chapter (adolfsson2022ispad)
 * Standard Pump temp basal: Zaharieva & Riddell 2017 (zaharieva2017insulin)
 * AID exercise target: EASD/ISPAD 2024 AID+PA position statement (moser2025use)
 * Late-onset hypo window: McMahon et al. 2007, Maran et al. 2010
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

        // C. Therapy-specific exercise strategy advice
        when (context.therapyType) {
            TherapyType.MDI -> {
                if (result.currentDose < 0.1 && context.currentBG > 0 && context.currentBG < 125
                    && (isFuture || absMinutes <= 5)
                ) {
                    val carbs = when (context.sportType) {
                        "Aerobic" -> 20
                        "Mixed" -> 15
                        else -> 10
                    }
                    result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, carbs))
                        .addEntry(BreakdownEntry(
                            stepName = name,
                            label = "Pre-Exercise Carbohydrates (MDI)",
                            emoji = "🍞",
                            description = "Your long-acting basal insulin is still active and cannot be " +
                                "suspended during exercise. Consume ${carbs}g of carbohydrates before " +
                                "starting ${context.sportType} activity to prevent hypoglycemia.",
                            effect = Effect.WARNING,
                            runningTotal = result.currentDose
                        ))
                } else if (context.currentBG >= 90.0 && context.currentBG <= 125.0
                    && context.sportType == "Aerobic"
                    && (isFuture || absMinutes <= 5)
                ) {
                    result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, 15))
                        .addEntry(BreakdownEntry(
                            stepName = name,
                            label = "Pre-Exercise Carbohydrates (MDI)",
                            emoji = "🍞",
                            description = "BG is in a borderline range for aerobic exercise. With active " +
                                "basal insulin that cannot be reduced, consuming 15g carbohydrates is recommended.",
                            effect = Effect.NEUTRAL,
                            runningTotal = result.currentDose
                        ))
                }

                if (context.sportDurationMins >= 45) {
                    result = result.addEntry(BreakdownEntry(
                        stepName = name,
                        label = "Basal Dose Adjustment",
                        emoji = "💉",
                        description = "Consider reducing your next basal insulin dose by approximately 20% " +
                            "to reduce the risk of delayed nocturnal hypoglycemia.",
                        effect = Effect.NEUTRAL,
                        runningTotal = result.currentDose
                    ))
                }
            }

            TherapyType.PUMP_STANDARD -> {
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Temp Basal Recommendation",
                    emoji = "⚙️",
                    description = "Set a temporary basal rate of 50% starting 60–90 minutes before exercise " +
                        "and maintain throughout the activity. Post-exercise, consider a 20% basal reduction for 6 hours.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))

                if (result.currentDose < 0.1 && context.currentBG > 0 && context.currentBG < 125
                    && (isFuture || absMinutes <= 5)
                ) {
                    val carbs = if (context.sportType == "Aerobic") 15 else 10
                    result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, carbs))
                        .addEntry(BreakdownEntry(
                            stepName = name,
                            label = "Pre-Exercise Carbohydrates",
                            emoji = "🍞",
                            description = "Consider consuming ${carbs}g carbohydrates in addition to " +
                                "setting a reduced temp basal.",
                            effect = Effect.NEUTRAL,
                            runningTotal = result.currentDose
                        ))
                }
            }

            TherapyType.PUMP_AID -> {
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Activate Exercise Target",
                    emoji = "🎯",
                    description = "Activate your pump's Exercise/Activity target 1–2 hours before planned activity. " +
                        "This raises your glucose target to 150 mg/dL and suspends automatic correction boluses.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))

                if (context.currentBG > 0 && context.currentBG < 100) {
                    result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, 10))
                        .addEntry(BreakdownEntry(
                            stepName = name,
                            label = "Pre-Exercise Carbohydrates",
                            emoji = "🍞",
                            description = "Despite automated insulin adjustments, your pump cannot provide " +
                                "carbohydrates. Consider 10g of fast-acting carbs before starting exercise.",
                            effect = Effect.NEUTRAL,
                            runningTotal = result.currentDose
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
