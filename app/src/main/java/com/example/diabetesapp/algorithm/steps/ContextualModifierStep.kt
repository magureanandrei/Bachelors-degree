package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType

/**
 * Step 4: Contextual Modifier (Dominant Architecture)
 * Replaces OutsideFactorsStep + SportModifierStep + PostExerciseSensitivityStep.
 *
 * Priority resolution: Active Exercise > Exercise Recovery > Walk Recovery > Illness > Stress > Heat
 * Only ONE modifier applies to the dose. All other active conditions generate warnings.
 *
 * Exercise percentages: T1DEXIP / ISPAD 2022 (Moser et al.), AID flat: Moser 2025
 * Recovery reductions: Diabetologia 2023 (meal 50%), ISPAD 2022 (correction 50%)
 * Walking recovery: Zivkovic 2026 proportional tiers
 * Illness: ISPAD Sick Day Guidelines (+25%)
 * Stress: Lloyd 1999, Diep 2012 (+15%)
 * Heat: Koivisto 1981 (-10%)
 */
class ContextualModifierStep : AlgorithmStep {
    override val name = "Contextual Modifier"

    private enum class DominantModifier {
        ACTIVE_EXERCISE,     // Priority 1
        EXERCISE_RECOVERY,   // Priority 2: structured exercise 0-6h
        WALK_RECOVERY,       // Priority 3: walking recovery (shorter windows)
        ILLNESS,             // Priority 4
        STRESS,              // Priority 5
        HEAT,                // Priority 6
        NONE
    }

    private fun resolveDominant(context: PatientContext, status: PatientStatus): DominantModifier {
        return when (status.exercisePhase) {
            ExercisePhase.ACTIVE -> DominantModifier.ACTIVE_EXERCISE
            ExercisePhase.EXERCISE_RECOVERY -> DominantModifier.EXERCISE_RECOVERY
            ExercisePhase.WALK_RECOVERY -> DominantModifier.WALK_RECOVERY
            ExercisePhase.NONE -> when {
                context.isIllness -> DominantModifier.ILLNESS
                context.isHighStress -> DominantModifier.STRESS
                context.isExtremeHeat -> DominantModifier.HEAT
                else -> DominantModifier.NONE
            }
        }
    }

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        val status = StatusResolver.resolve(context)
        var result = state.copy(
            metadata = state.metadata + ("patientStatus" to status)
        )

        val dominant = resolveDominant(context, status)
        result = when (dominant) {
            DominantModifier.ACTIVE_EXERCISE -> applyActiveExercise(result, context, status)
            DominantModifier.EXERCISE_RECOVERY -> applyExerciseRecovery(result, context, status)
            DominantModifier.WALK_RECOVERY -> applyWalkRecovery(result, context, status)
            DominantModifier.ILLNESS -> applyIllness(result, context)
            DominantModifier.STRESS -> applyStress(result, context)
            DominantModifier.HEAT -> applyHeat(result, context)
            DominantModifier.NONE -> result
        }
        result = addNonDominantWarnings(result, context, dominant)
        return result
    }

    private fun applyActiveExercise(state: CalculationState, context: PatientContext, status: PatientStatus): CalculationState {
        var result = state

        // A. Ketone warning at very high BG (ISPAD 2022: ≥1.5 mmol/L = contraindicated)
        if (context.currentBG > 300) {
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Check Ketones Before Exercise",
                emoji = "🚨",
                description = "BG exceeds 300 mg/dL. Check blood ketones before exercising. " +
                    "If ketones are ≥1.5 mmol/L, exercise is contraindicated (ISPAD). " +
                    "If ketones are 0.6–1.4 mmol/L, postpone until corrective insulin is administered.",
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
        }

        val mealBolus = state.metadata["mealBolus"] as? Double ?: 0.0
        val correctionBolus = state.metadata["correctionBolus"] as? Double ?: 0.0

        // B. Meal reduction by sport type and intensity (T1DEXIP / Moser 2025 for AID)
        val mealReductionPercent = if (context.bolusSettings.isAidPump) {
            0.0  // AID: pump calculates dose from entered carbs. No manual dose modification.
        } else {
            when (context.sportType) {
                "Aerobic" -> when (context.sportIntensity) {
                    3 -> 0.75; 2 -> 0.50; else -> 0.25
                }
                "Mixed" -> when (context.sportIntensity) {
                    3 -> 0.40; 2 -> 0.25; else -> 0.15
                }
                "Walking" -> when {
                    context.sportDurationMins >= 45 -> 0.43  // Zivkovic ratio: 87% × aerobic medium 50%
                    context.sportDurationMins >= 30 -> 0.35  // Midpoint: 87% × aerobic low-medium 40%
                    else -> 0.25                              // ISPAD: moderate aerobic classification
                }
                "Anaerobic" -> 0.10
                else -> 0.25
            }
        }

        // Duration modifier: >45 min adds extra (ISPAD 45-min threshold).
        // Skip for Walking — it already accounts for duration in its own percentage tiers.
        val durationExtra = if (context.sportType != "Walking" && context.sportDurationMins > 45) {
            minOf(0.20, (context.sportDurationMins - 45) * 0.005)
        } else 0.0
        val totalMealReduction = minOf(0.90, mealReductionPercent + durationExtra)
        val reducedMeal = mealBolus * (1.0 - totalMealReduction)

        // C. Correction handling by sport type (Zaharieva 2017, Yardley 2013, ISPAD 2022)
        var reducedCorrection = correctionBolus
        when {
            (context.sportType == "Aerobic" || context.sportType == "Walking") &&
                context.currentBG in 140.0..250.0 -> {
                reducedCorrection = 0.0
                if (correctionBolus > 0) {
                    result = result.addEntry(BreakdownEntry(
                        stepName = name,
                        label = "Correction Withheld",
                        emoji = "🏃",
                        description = "Correction withheld. Aerobic exercise will serve as a natural correction.",
                        effect = Effect.NEUTRAL,
                        runningTotal = result.currentDose
                    ))
                }
            }
            (context.sportType == "Aerobic" || context.sportType == "Walking") &&
                context.currentBG > 250.0 -> {
                reducedCorrection = correctionBolus * 0.50
                if (correctionBolus > 0) {
                    result = result.addEntry(BreakdownEntry(
                        stepName = name,
                        label = "Reduced Correction (High BG + Aerobic)",
                        emoji = "⚠️",
                        description = "50% correction applied. BG is very high alongside exercise.",
                        effect = Effect.DECREASE,
                        runningTotal = result.currentDose
                    ))
                }
            }
            context.sportType == "Mixed" && correctionBolus > 0 -> {
                reducedCorrection = correctionBolus * 0.50
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Reduced Correction (Mixed Exercise)",
                    emoji = "🏋️",
                    description = "50% correction. Mixed exercise has variable BG effects.",
                    effect = Effect.DECREASE,
                    runningTotal = result.currentDose
                ))
            }
            context.sportType == "Anaerobic" && correctionBolus > 0 -> {
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Full Correction (Anaerobic)",
                    emoji = "🏋️",
                    description = "Full correction maintained. Anaerobic exercise does not reliably lower BG.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))
            }
        }

        // D. Apply new dose
        if (mealBolus > 0.0 || correctionBolus > 0.0) {
            val newDose = reducedMeal + reducedCorrection
            val mealReductionAmount = mealBolus * totalMealReduction
            val reductionPct = String.format("%.0f", totalMealReduction * 100)
            val description = if (mealBolus > 0) {
                "${context.sportType}: −$reductionPct% meal. " +
                    "Meal component: −${String.format("%.2f", mealReductionAmount)}U."
            } else {
                "${context.sportType}: sport adjustment applied."
            }
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Sport Reduction",
                emoji = "🏃",
                description = description,
                effect = Effect.DECREASE,
                percentChange = -totalMealReduction,
                valueChange = -mealReductionAmount,
                runningTotal = newDose
            )).copy(currentDose = newDose)
        } else if (state.currentDose > 0) {
            val newDose = state.currentDose * (1.0 - totalMealReduction)
            val reductionPct = String.format("%.0f", totalMealReduction * 100)
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Sport Reduction",
                emoji = "🏃",
                description = "${context.sportType}: −$reductionPct% reduction.",
                effect = Effect.DECREASE,
                percentChange = -totalMealReduction,
                valueChange = -(state.currentDose * totalMealReduction),
                runningTotal = newDose
            )).copy(currentDose = newDose)
        }

        // E. Therapy-specific advice
        when (context.therapyType) {
            TherapyType.MDI -> {
                val carbs = when (context.sportType) {
                    "Aerobic" -> 20; "Walking" -> 15; "Mixed" -> 15; else -> 10
                }
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Pre-Exercise Carbohydrates (MDI)",
                    emoji = "🍞",
                    description = "Your long-acting basal insulin cannot be suspended. " +
                        "Consume ${carbs}g of carbohydrates before exercise to prevent hypoglycemia.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))
                if (context.sportDurationMins >= 45) {
                    result = result.addEntry(BreakdownEntry(
                        stepName = name,
                        label = "Basal Dose Adjustment",
                        emoji = "💉",
                        description = "Consider reducing your next basal insulin dose by 20% " +
                            "to reduce the risk of delayed nocturnal hypoglycemia (ISPAD).",
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
                        "and maintain throughout the activity (Zaharieva 2017).",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))
                if (result.currentDose < 0.1 && context.currentBG > 0 && context.currentBG < 125) {
                    val carbs = when (context.sportType) {
                        "Aerobic", "Walking" -> 15; else -> 10
                    }
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
                    description = "Activate your pump's Exercise/Activity target 1–2 hours before " +
                        "planned activity. This raises your glucose target to 150 mg/dL and suspends " +
                        "automatic correction boluses (Moser 2025). Consider bolusing for only " +
                        "67–75% of your planned carbohydrates.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))
                if (context.currentBG > 0 && context.currentBG < 100) {
                    result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, 10))
                        .addEntry(BreakdownEntry(
                            stepName = name,
                            label = "Pre-Exercise Carbohydrates",
                            emoji = "🍞",
                            description = "BG is below 100 mg/dL. Consider 10g of fast-acting " +
                                "carbohydrates without additional insulin before starting exercise.",
                            effect = Effect.NEUTRAL,
                            runningTotal = result.currentDose
                        ))
                }
            }
        }

        // F. High IOB warning (IOB was already deducted; this flags residual sensitivity risk)
        if (context.activeInsulinIOB > 1.5) {
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "High Active Insulin",
                emoji = "⚠️",
                description = "High active insulin (${String.format("%.1f", context.activeInsulinIOB)}U). " +
                    "Exercise will increase insulin sensitivity. Consider extra fast-acting carbohydrates " +
                    "without additional insulin.",
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
        }

        // G. Late-hypo warnings (only when exercise is happening now or already finished)
        if (context.minutesUntilSport <= 0) {
            if (context.currentBG > 0 && context.currentBG < 80) {
                result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, 15))
                    .addEntry(BreakdownEntry(
                        stepName = name,
                        label = "Post-Workout Low",
                        emoji = "⚠️",
                        description = "Post-workout BG is low. Consume 15g fast-acting carbohydrates.",
                        effect = Effect.WARNING,
                        runningTotal = result.currentDose
                    ))
            }
            if (context.sportDurationMins >= 45 ||
                context.sportType in listOf("Aerobic", "Mixed", "Anaerobic")) {
                val warning = if (context.sportType == "Walking") {
                    "Moderate risk of delayed hypoglycemia. Walking has lower nocturnal risk. " +
                        "Monitor before bedtime."
                } else {
                    "Risk of late-onset hypoglycemia (7–11h window). Consider a bedtime snack " +
                        "with protein and complex carbohydrates."
                }
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Late-Onset Hypoglycemia Risk",
                    emoji = "🌙",
                    description = warning,
                    effect = Effect.WARNING,
                    runningTotal = result.currentDose
                ))
            }
        }

        return result
    }

    private fun applyExerciseRecovery(state: CalculationState, context: PatientContext, status: PatientStatus): CalculationState {
        var result = state
        val mealBolus = state.metadata["mealBolus"] as? Double ?: 0.0
        val correctionBolus = state.metadata["correctionBolus"] as? Double ?: 0.0

        if (mealBolus > 0.0 || correctionBolus > 0.0) {
            val reducedMeal = mealBolus * 0.50
            val reducedCorrection = correctionBolus * 0.50
            val newDose = reducedMeal + reducedCorrection

            if (mealBolus > 0) {
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Post-Exercise Meal Reduction",
                    emoji = "🏃",
                    description = "Post-exercise meal reduction: 50%. Insulin sensitivity is elevated " +
                        "after today's exercise (Diabetologia 2023).",
                    effect = Effect.DECREASE,
                    percentChange = -0.50,
                    valueChange = -(mealBolus * 0.50),
                    runningTotal = newDose
                ))
            }
            if (correctionBolus > 0) {
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Post-Exercise Correction Reduction",
                    emoji = "🏃",
                    description = "Post-exercise correction: 50%. ISPAD recommends conservative " +
                        "corrections following exercise.",
                    effect = Effect.DECREASE,
                    percentChange = -0.50,
                    valueChange = -(correctionBolus * 0.50),
                    runningTotal = newDose
                ))
            }
            result = result.copy(currentDose = newDose)
        } else if (state.currentDose > 0) {
            val newDose = state.currentDose * 0.50
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Post-Exercise Dose Reduction",
                emoji = "🏃",
                description = "Post-exercise 50% dose reduction. Insulin sensitivity is elevated " +
                    "after today's exercise.",
                effect = Effect.DECREASE,
                percentChange = -0.50,
                valueChange = -(state.currentDose * 0.50),
                runningTotal = newDose
            )).copy(currentDose = newDose)
        }

        when (context.therapyType) {
            TherapyType.MDI -> result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Post-Exercise Basal Advice",
                emoji = "💉",
                description = "Consider reducing your next basal dose by 20% (ISPAD). Monitor closely.",
                effect = Effect.NEUTRAL,
                runningTotal = result.currentDose
            ))
            TherapyType.PUMP_STANDARD -> result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Post-Exercise Temp Basal",
                emoji = "⚙️",
                description = "Consider a temp basal of 90% for 4–6 hours post-exercise (Zaharieva 2017).",
                effect = Effect.NEUTRAL,
                runningTotal = result.currentDose
            ))
            TherapyType.PUMP_AID -> {
                if (context.hoursSinceLastExercise <= 2.0f &&
                    context.lastExerciseSportType in listOf("Aerobic", "Mixed")) {
                    result = result.addEntry(BreakdownEntry(
                        stepName = name,
                        label = "Extend Exercise Target",
                        emoji = "🎯",
                        description = "Consider keeping your Exercise/Activity target active for " +
                            "2–3 hours after aerobic or mixed exercise to prevent rebound hypoglycemia.",
                        effect = Effect.NEUTRAL,
                        runningTotal = result.currentDose
                    ))
                }
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Post-Exercise Monitoring",
                    emoji = "👀",
                    description = "Insulin sensitivity is elevated after exercise. Monitor glucose closely. " +
                        "Consider carbohydrate intake without additional insulin if BG is borderline.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))
                if (context.currentBG > 0 && context.currentBG in 70.0..100.0) {
                    result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, 15))
                        .addEntry(BreakdownEntry(
                            stepName = name,
                            label = "Post-Exercise Carbohydrates",
                            emoji = "🍞",
                            description = "BG is ${context.currentBG.toInt()} mg/dL post-exercise. " +
                                "Consider 15g carbohydrates without additional insulin.",
                            effect = Effect.NEUTRAL,
                            runningTotal = result.currentDose
                        ))
                }
            }
        }

        return result
    }

    private fun applyWalkRecovery(state: CalculationState, context: PatientContext, status: PatientStatus): CalculationState {
        var result = state
        val mealBolus = state.metadata["mealBolus"] as? Double ?: 0.0
        val correctionBolus = state.metadata["correctionBolus"] as? Double ?: 0.0
        val duration = context.lastExerciseDurationMins

        // Walking recovery reduction: flat within window, scaled by walk duration.
        // 10-30 min → 15%, 1h window  (proportional from active tiers, Zivkovic ratio 0.87)
        // 30-45 min → 20%, 2h window
        // >45 min   → 25%, 3h window
        val reductionPercent = when {
            duration >= 45 -> 0.25
            duration >= 30 -> 0.20
            else -> 0.15
        }

        val hasMealOrCorrection = mealBolus > 0.0 || correctionBolus > 0.0

        if (hasMealOrCorrection) {
            val reducedMeal = mealBolus * (1.0 - reductionPercent)
            val reducedCorrection = correctionBolus * (1.0 - reductionPercent)
            val newDose = reducedMeal + reducedCorrection
            val totalReduction = (mealBolus + correctionBolus) - newDose

            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Post-Walk Recovery",
                emoji = "🚶",
                description = "Insulin sensitivity is elevated after your ${duration}-minute walk. " +
                    "Meal and correction reduced by ${(reductionPercent * 100).toInt()}%. " +
                    "Walking has the lowest nocturnal hypoglycemia risk of all exercise types.",
                effect = Effect.DECREASE,
                percentChange = -reductionPercent,
                valueChange = -totalReduction,
                runningTotal = newDose
            )).copy(currentDose = newDose)
        } else if (state.currentDose > 0) {
            val reduction = state.currentDose * reductionPercent
            val newDose = state.currentDose - reduction
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Post-Walk Recovery",
                emoji = "🚶",
                description = "Insulin sensitivity is elevated after your ${duration}-minute walk. " +
                    "Dose reduced by ${(reductionPercent * 100).toInt()}%.",
                effect = Effect.DECREASE,
                percentChange = -reductionPercent,
                valueChange = -reduction,
                runningTotal = newDose
            )).copy(currentDose = newDose)
        }

        when (context.therapyType) {
            TherapyType.MDI -> {
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Post-Walk Monitoring",
                    emoji = "👀",
                    description = "Monitor glucose closely. Your basal insulin remains active " +
                        "and cannot be adjusted. Consider a small snack if BG trends downward.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))
            }
            TherapyType.PUMP_STANDARD -> {
                if (duration >= 30) {
                    result = result.addEntry(BreakdownEntry(
                        stepName = name,
                        label = "Temp Basal Suggestion",
                        emoji = "⚙️",
                        description = "Consider a temporary basal rate of 90% for 1–2 hours " +
                            "post-walk to account for mildly elevated insulin sensitivity.",
                        effect = Effect.NEUTRAL,
                        runningTotal = result.currentDose
                    ))
                }
            }
            TherapyType.PUMP_AID -> {
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Post-Walk Monitoring",
                    emoji = "👀",
                    description = "Monitor glucose. Your pump will adjust insulin delivery " +
                        "automatically. Consider a small carb intake without insulin if BG is borderline.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))
                if (context.currentBG > 0 && context.currentBG in 70.0..100.0) {
                    result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, 10))
                        .addEntry(BreakdownEntry(
                            stepName = name,
                            label = "Post-Walk Carbohydrates",
                            emoji = "🍞",
                            description = "BG is ${context.currentBG.toInt()} mg/dL after walking. " +
                                "Consider 10g carbohydrates without additional insulin.",
                            effect = Effect.NEUTRAL,
                            runningTotal = result.currentDose
                        ))
                }
            }
        }

        return result
    }

    private fun applyIllness(state: CalculationState, context: PatientContext): CalculationState {
        if (state.currentDose <= 0) return state
        val increase = state.currentDose * 0.25
        val newDose = state.currentDose + increase
        return state.addEntry(BreakdownEntry(
            stepName = name,
            label = "Illness",
            emoji = "🩺",
            description = "Illness: +25% total dose. Illness increases insulin resistance (ISPAD Sick Day Guidelines).",
            effect = Effect.INCREASE,
            percentChange = 0.25,
            valueChange = increase,
            runningTotal = newDose
        )).copy(currentDose = newDose)
    }

    private fun applyStress(state: CalculationState, context: PatientContext): CalculationState {
        if (state.currentDose <= 0) return state
        val increase = state.currentDose * 0.15
        val newDose = state.currentDose + increase
        return state.addEntry(BreakdownEntry(
            stepName = name,
            label = "Stress",
            emoji = "😫",
            description = "Stress: +15% total dose. Stress elevates insulin resistance via cortisol.",
            effect = Effect.INCREASE,
            percentChange = 0.15,
            valueChange = increase,
            runningTotal = newDose
        )).copy(currentDose = newDose)
    }

    private fun applyHeat(state: CalculationState, context: PatientContext): CalculationState {
        if (state.currentDose <= 0) return state
        val decrease = state.currentDose * 0.10
        val newDose = state.currentDose - decrease
        return state.addEntry(BreakdownEntry(
            stepName = name,
            label = "Heat",
            emoji = "🔥",
            description = "Heat: −10% total dose. Elevated temperature accelerates insulin absorption.",
            effect = Effect.DECREASE,
            percentChange = -0.10,
            valueChange = -decrease,
            runningTotal = newDose
        )).copy(currentDose = newDose)
    }

    private fun addNonDominantWarnings(
        state: CalculationState,
        context: PatientContext,
        dominant: DominantModifier
    ): CalculationState {
        var result = state

        if (context.isIllness && dominant != DominantModifier.ILLNESS) {
            val dominantLabel = when (dominant) {
                DominantModifier.ACTIVE_EXERCISE -> "active exercise"
                DominantModifier.EXERCISE_RECOVERY -> "exercise recovery"
                DominantModifier.WALK_RECOVERY -> "recent walking activity"
                DominantModifier.STRESS -> "stress"
                DominantModifier.HEAT -> "elevated temperature"
                else -> "the current modifier"
            }
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Illness (Non-Dominant)",
                emoji = "🩺",
                description = "You are ill. Illness typically increases insulin needs by 20–50%. " +
                    "However, $dominantLabel is currently the primary factor affecting your dose. " +
                    "Monitor BG closely — if glucose remains elevated, illness may require additional correction.",
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
        }

        if (context.isHighStress && dominant != DominantModifier.STRESS) {
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Stress (Non-Dominant)",
                emoji = "😫",
                description = "Stress is active. This typically increases insulin needs. " +
                    "Monitor for unexpected BG elevation.",
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
        }

        if (context.isExtremeHeat && dominant != DominantModifier.HEAT) {
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Heat (Non-Dominant)",
                emoji = "🔥",
                description = "Elevated temperature detected. Insulin absorption may be faster than usual. " +
                    "Monitor for unexpected lows.",
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
        }

        return result
    }
}
