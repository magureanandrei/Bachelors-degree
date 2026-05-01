package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.CgmTrend
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 4: CGM Trend Velocity Modifiers
 * AID: pump reacts to trends automatically (EASD/ISPAD 2025 AID+PA position statement).
 * MDI/Standard Pump: ISF-unit-based adjustments (Aleppo et al. 2017, Laffel et al. 2017).
 * Formula: adjustment = anticipated_30min_change / ISF, rounded to nearest 0.5U.
 */
class CgmTrendStep : AlgorithmStep {
    override val name = "CGM Trend"

    // Anticipated 30-minute glucose change per trend arrow (mg/dL)
    // Source: Dexcom trend arrow specification, Aleppo et al. 2017
    private fun anticipatedChange(trend: CgmTrend): Double {
        return when (trend) {
            CgmTrend.DOUBLE_UP       ->  90.0
            CgmTrend.SINGLE_UP       ->  60.0
            CgmTrend.FORTY_FIVE_UP   ->  30.0
            CgmTrend.FLAT            ->   0.0
            CgmTrend.FORTY_FIVE_DOWN -> -30.0
            CgmTrend.SINGLE_DOWN     -> -60.0
            CgmTrend.DOUBLE_DOWN     -> -90.0
            CgmTrend.NONE            ->   0.0
        }
    }

    // Round to nearest 0.5U for practical dosing
    private fun roundToHalf(value: Double): Double = Math.round(value * 2.0) / 2.0

    private fun trendEmoji(trend: CgmTrend): String {
        return when (trend) {
            CgmTrend.DOUBLE_UP       -> "↑↑"
            CgmTrend.SINGLE_UP       -> "↑"
            CgmTrend.FORTY_FIVE_UP   -> "↗"
            CgmTrend.FLAT            -> "→"
            CgmTrend.FORTY_FIVE_DOWN -> "↘"
            CgmTrend.SINGLE_DOWN     -> "↓"
            CgmTrend.DOUBLE_DOWN     -> "↓↓"
            CgmTrend.NONE            -> ""
        }
    }

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        if (!context.hasCGM || context.cgmTrend == CgmTrend.NONE) return state
        if (context.cgmTrend == CgmTrend.FLAT) return state

        var result = state

        // ============================================================
        // AID: Skip dose adjustments, keep carb suggestions
        // Source: EASD/ISPAD 2025 AID+PA position statement (moser2025use)
        // ============================================================
        if (context.bolusSettings.isAidPump) {
            when (context.cgmTrend) {
                CgmTrend.DOUBLE_DOWN -> {
                    if (context.currentBG in 1.0..119.9) {
                        result = result.copy(rescueCarbs = result.rescueCarbs + 15)
                            .addEntry(BreakdownEntry(
                                stepName = name,
                                label = "Rapid Glucose Drop",
                                emoji = "⬇️⬇️",
                                description = "CGM shows rapidly falling glucose. Your pump will " +
                                    "reduce insulin delivery, but consider consuming 15g " +
                                    "fast-acting carbohydrates as your pump cannot provide carbs.",
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
                                description = "CGM shows falling glucose. Your pump is adjusting " +
                                    "insulin. Consider a small snack if symptoms develop.",
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
                else -> { /* Flat, NONE — no action needed */ }
            }
            return result
        }

        // ============================================================
        // MDI and Standard Pump: ISF-unit-based adjustments
        // Source: Aleppo et al. 2017, Laffel et al. 2017
        // Formula: adjustment = anticipated_30min_change / ISF
        // ============================================================

        // Safety: BG < 70 and trending up — treat hypo first, do not add insulin
        if (context.currentBG > 0 && context.currentBG < 70) {
            if (anticipatedChange(context.cgmTrend) > 0) {
                result = result.addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Low BG — Trend Ignored",
                    emoji = "⚠️",
                    description = "BG is below 70 mg/dL. Even though glucose is trending up " +
                        "(${trendEmoji(context.cgmTrend)}), no insulin is added. Treat the low first.",
                    effect = Effect.WARNING,
                    runningTotal = result.currentDose
                ))
                return result
            }
        }

        val currentHour = context.timeOfDay.hour
        val currentIsf = context.bolusSettings.getIsfForHour(currentHour).toDouble()

        if (currentIsf <= 0) return result

        val anticipated = anticipatedChange(context.cgmTrend)
        val rawAdjustment = anticipated / currentIsf
        val adjustment = roundToHalf(rawAdjustment)

        if (adjustment == 0.0) return result

        val newDose = maxOf(0.0, result.currentDose + adjustment)

        val directionWord = if (adjustment > 0) "rising" else "falling"
        val actionWord = if (adjustment > 0) "increased" else "decreased"
        val absAdjustment = Math.abs(adjustment)

        result = result.addEntry(BreakdownEntry(
            stepName = name,
            label = "CGM Trend: ${trendEmoji(context.cgmTrend)}",
            emoji = if (adjustment > 0) "📈" else "📉",
            description = "Glucose is $directionWord (${trendEmoji(context.cgmTrend)}), " +
                "anticipating a ${Math.abs(anticipated.toInt())} mg/dL change in 30 minutes. " +
                "Based on your ISF of ${currentIsf.toInt()} mg/dL/U, dose $actionWord by " +
                "${String.format("%.1f", absAdjustment)}U " +
                "(Aleppo et al. 2017).",
            effect = if (adjustment > 0) Effect.INCREASE else Effect.DECREASE,
            valueChange = adjustment,
            runningTotal = newDose
        )).copy(currentDose = newDose)

        // DoubleDown: rescue carbs scaled by BG level (Aleppo et al. 2017)
        if (context.cgmTrend == CgmTrend.DOUBLE_DOWN) {
            val carbAmount = when {
                context.currentBG < 90  -> 20
                context.currentBG < 120 -> 15
                else                    -> 10
            }
            result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, carbAmount))
                .addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Rescue Carbs (Rapid Drop)",
                    emoji = "🍬",
                    description = "Glucose is dropping rapidly. Consider ${carbAmount}g of " +
                        "fast-acting carbohydrates as a safety measure.",
                    effect = Effect.WARNING,
                    runningTotal = result.currentDose
                ))
        }

        // SingleDown + borderline BG: carb suggestion
        if (context.cgmTrend == CgmTrend.SINGLE_DOWN && context.currentBG in 70.0..99.9) {
            result = result.copy(rescueCarbs = maxOf(result.rescueCarbs, 10))
                .addEntry(BreakdownEntry(
                    stepName = name,
                    label = "Consider Snack",
                    emoji = "🍞",
                    description = "Glucose is falling with BG near lower range. " +
                        "Consider 10g carbohydrates if not eating soon.",
                    effect = Effect.NEUTRAL,
                    runningTotal = result.currentDose
                ))
        }

        return result
    }
}
