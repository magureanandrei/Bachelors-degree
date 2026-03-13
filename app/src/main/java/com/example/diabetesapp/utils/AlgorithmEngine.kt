package com.example.diabetesapp.utils

import com.example.diabetesapp.data.models.CgmTrend
import com.example.diabetesapp.data.models.ClinicalDecision
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType
import kotlin.math.abs

object AlgorithmEngine {

    /**
     * The single source of truth for the Clinical Decision Support System (CDSS).
     * Takes in the total patient context and outputs a calculated decision and rationale.
     */
    fun calculateClinicalAdvice(context: PatientContext): ClinicalDecision {
        val logBuilder = StringBuilder()
        var rescueCarbs = 0

        // ---------------------------------------------------------
        // 1. BASELINE CALCULATIONS (Carbs + Correction)
        // ---------------------------------------------------------
        var baseInsulin = 0.0
        val currentIcr = context.bolusSettings.getCurrentIcr().toDouble()
        val currentIsf = context.bolusSettings.getCurrentIsf().toDouble()
        val targetBg = context.bolusSettings.targetBG.toDouble()

        if (context.plannedCarbs > 0 && currentIcr > 0) {
            val mealBolus = context.plannedCarbs / currentIcr
            baseInsulin += mealBolus
            logBuilder.append("Meal: ${String.format("%.1f", mealBolus)}U. ")
        }

        if (context.currentBG > targetBg && currentIsf > 0) {
            val correctionBolus = (context.currentBG - targetBg) / currentIsf
            baseInsulin += correctionBolus
            logBuilder.append("Correction: +${String.format("%.1f", correctionBolus)}U. ")
        }

        // ---------------------------------------------------------
        // 2. OUTSIDE FACTORS (Resistance/Absorption Modifiers)
        // Applied BEFORE IOB deduction because they affect total systemic need.
        // ---------------------------------------------------------
        if (baseInsulin > 0) {
            var factorMultiplier = 1.0

            // Use else-if to prevent double-stacking massive resistance increases
            if (context.isIllness) {
                factorMultiplier += 0.25
                logBuilder.append("\n🩺 Illness: +25% total dose (Resistance). ")
            } else if (context.isHighStress) {
                factorMultiplier += 0.15
                logBuilder.append("\n😫 Stress: +15% total dose (Cortisol). ")
            }

            // Heat is handled independently as it affects absorption speed, not resistance
            if (context.isExtremeHeat) {
                factorMultiplier -= 0.10
                logBuilder.append("\n🔥 Heat: -10% total dose (Rapid Absorption). ")
            }

            baseInsulin *= factorMultiplier
        }

        // ---------------------------------------------------------
        // 3. ACTIVE INSULIN (IOB) DEDUCTION
        // ---------------------------------------------------------
        if (context.activeInsulinIOB > 0 && baseInsulin > 0) {
            baseInsulin -= context.activeInsulinIOB
            baseInsulin = maxOf(0.0, baseInsulin)
            logBuilder.append("\n💉 Active IOB Deducted. ")
        }

        // ---------------------------------------------------------
        // 4. CGM TREND MODIFIERS (The Velocity Check)
        // ---------------------------------------------------------
        if (context.hasCGM && context.cgmTrend != CgmTrend.NONE && baseInsulin > 0) {
            when (context.cgmTrend) {
                CgmTrend.DOUBLE_UP -> {
                    baseInsulin *= 1.20
                    logBuilder.append("\n📈 CGM ↑↑: Increased dose by 20%. ")
                }
                CgmTrend.SINGLE_DOWN -> {
                    baseInsulin *= 0.80
                    logBuilder.append("\n📉 CGM ↓: Reduced dose by 20%. ")
                }
                CgmTrend.DOUBLE_DOWN -> {
                    baseInsulin *= 0.50
                    logBuilder.append("\n⚠️ CGM ↓↓: Rapid drop. Halved dose. ")
                    if (context.currentBG in 1.0..119.9) rescueCarbs += 15
                }
                else -> { /* Flat or Single Up - standard math applies */ }
            }
        }

        // ---------------------------------------------------------
        // 5. SPORT MODIFIERS & CARB SUGGESTIONS
        // ---------------------------------------------------------
        if (context.isDoingSport) {
            logBuilder.append("\n\n--- Sport CDSS Analysis ---\n")

            // A. Check for Fingerstick users (No CGM)
            if (!context.hasCGM) {
                logBuilder.append("⚠️ No CGM: Check BG manually 30 mins into activity.\n")
            }

            val isFuture = context.minutesUntilSport >= 0
            val absMinutes = abs(context.minutesUntilSport)

            // B. Insulin Reduction Math (Only applies if they are taking insulin)
            if (baseInsulin > 0) {
                var reductionPercent = 0.0

                when (context.sportType) {
                    "Anaerobic" -> {
                        reductionPercent = 0.10
                        logBuilder.append("Anaerobic: 10% reduction. ")
                    }
                    "Mixed" -> {
                        reductionPercent = when (context.sportIntensity) {
                            1 -> 0.15; 2 -> 0.25; else -> 0.40
                        }
                        logBuilder.append("Mixed (Int ${context.sportIntensity}): ${String.format("%.0f", reductionPercent * 100)}% reduction. ")
                    }
                    else -> { // Aerobic
                        reductionPercent = when (context.sportIntensity) {
                            1 -> 0.25; 2 -> 0.50; else -> 0.75
                        }
                        logBuilder.append("Aerobic (Int ${context.sportIntensity}): ${String.format("%.0f", reductionPercent * 100)}% reduction. ")
                    }
                }

                // Duration Modifier (>45 mins adds extra reduction)
                if (context.sportDurationMins > 45) {
                    val extraTime = context.sportDurationMins - 45
                    val extraReduction = minOf(0.20, (extraTime / 15.0) * 0.10)
                    reductionPercent += extraReduction
                    logBuilder.append("Duration >45m: +${String.format("%.0f", extraReduction * 100)}% reduction. ")
                }

                // Apply Cap and Calculate Final Dose
                reductionPercent = minOf(0.90, reductionPercent) // Max 90% reduction
                baseInsulin *= (1.0 - reductionPercent)
            }

            // C. Pre-Sport Carb / Pump Therapy Advice (If insulin is 0 or very low)
            if (baseInsulin < 0.1 && context.currentBG > 0 && context.currentBG < 125 && (isFuture || absMinutes <= 5)) {
                if (context.currentBG < 90) {
                    rescueCarbs = 20
                    logBuilder.append("⚠️ Low BG (${context.currentBG}). Eat 20g fast-acting carbs. ")
                    if (absMinutes > 30) logBuilder.append("Since sport is in ${absMinutes}m, add complex carbs. ")
                    else logBuilder.append("Wait 15m before starting. ")
                } else { // BG is 90-125
                    if (context.sportType == "Aerobic") {
                        if (absMinutes > 30) {
                            when (context.therapyType) {
                                TherapyType.MDI_PENS -> {
                                    rescueCarbs = 15
                                    logBuilder.append("💡 Pens: Aerobic will drop BG. Eat 15g complex carbs now. ")
                                }
                                TherapyType.STANDARD_PUMP -> {
                                    logBuilder.append("💡 Pump: Set 50% Temp Basal now. (Or eat 15g carbs). ")
                                }
                                TherapyType.AID_PUMP -> {
                                    logBuilder.append("💡 AID Pump: Set 'Exercise Target' now to suspend micro-boluses. ")
                                }
                            }
                        } else {
                            rescueCarbs = 15
                            logBuilder.append("⚠️ Aerobic drops BG fast. Eat 15g fast carbs before starting. ")
                        }
                    } else if (context.sportType == "Mixed") {
                        rescueCarbs = 10
                        logBuilder.append("💡 Eat 10g carbs to stabilize BG for mixed activity. ")
                    }
                }
            }

            // D. Post-Sport / Late-Onset Warning
            if (!isFuture) {
                if (context.currentBG > 0 && context.currentBG < 80) {
                    rescueCarbs = 15
                    logBuilder.append("⚠️ Post-workout Low. Consume 15g fast carbs immediately. ")
                } else if (context.sportType == "Aerobic" || context.sportType == "Mixed" || context.sportDurationMins > 45) {
                    logBuilder.append("\n⚠️ Insight: Risk of Late-Onset Hypoglycemia (7-11h window). Consider bedtime snack or reduced night basal. ")
                }
            }
        }

        // Clean up floating point math for final dose (e.g., 1.90000001 -> 1.9)
        val finalInsulin = Math.round(maxOf(0.0, baseInsulin) * 10.0) / 10.0

        return ClinicalDecision(
            suggestedInsulinDose = finalInsulin,
            suggestedRescueCarbs = rescueCarbs,
            clinicalRationale = logBuilder.toString().trim()
        )
    }
}