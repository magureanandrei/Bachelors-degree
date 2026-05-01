# Feature: CGM Trend Redesign — ISF-Unit-Based Adjustments

## Summary

Replace the percentage-based CGM trend arrow adjustments (DoubleUp +20%, SingleDown -20%, DoubleDown halve) with the ISF-unit-based approach from the Aleppo et al. 2017 / Laffel et al. 2017 consensus. This uses the patient's individual Insulin Sensitivity Factor to calculate a fixed unit adjustment based on the anticipated 30-minute glucose change, rather than scaling by a percentage of the current dose. All seven arrow directions are now handled (FortyFiveUp, FortyFiveDown, SingleUp were previously ignored). AID handling is unchanged.

## Clinical Evidence Base

- **Aleppo et al. 2017:** "A practical approach to using trend arrows on the Dexcom G5 CGM system for the management of adults with diabetes." J Endocr Soc 1(12):1445–1460. Defines ISF-based unit adjustments for adults across four ISF categories.
- **Laffel et al. 2017:** "A practical approach to using trend arrows on the Dexcom G5 CGM system to manage children and adolescents with diabetes." J Endocr Soc 1(12):1461–1476. Extends to pediatric ISF categories.
- **Ziegler et al. 2019:** "Therapy adjustments based on trend arrows using continuous glucose monitoring systems." J Diabetes Sci Technol 13(4):763–773. Further stratifies by baseline glucose level.
- **EASD/ISPAD 2025 (Moser et al.):** AID users — pump reacts to trends automatically. Skip dose adjustments, keep carb suggestions.

## The Formula

Instead of a hardcoded lookup table, use the patient's actual ISF directly:

```
anticipated_change = rate_per_arrow × 30 minutes

adjustment_units = anticipated_change / current_ISF

Round to nearest 0.5U for practical dosing
```

Anticipated 30-minute glucose change per trend arrow (from Dexcom specification):

| Trend Arrow     | Direction        | Rate (mg/dL/min) | 30-min Change (mg/dL) |
| --------------- | ---------------- | ---------------- | --------------------- |
| ↑↑ DoubleUp     | Rising fast      | >3               | +90                   |
| ↑ SingleUp      | Rising           | 2–3              | +60                   |
| ↗ FortyFiveUp   | Rising slightly  | 1–2              | +30                   |
| → Flat          | Stable           | <1               | 0                     |
| ↘ FortyFiveDown | Falling slightly | -1 to -2         | -30                   |
| ↓ SingleDown    | Falling          | -2 to -3         | -60                   |
| ↓↓ DoubleDown   | Falling fast     | <-3              | -90                   |

Example: Patient ISF = 50 mg/dL/U, trend is SingleUp (↑):

- Anticipated change: +60 mg/dL in 30 min
- Adjustment: 60 / 50 = +1.2U → rounded to +1.0U
- Add 1.0U to the calculated dose

Example: Patient ISF = 30 mg/dL/U, trend is DoubleDown (↓↓):

- Anticipated change: -90 mg/dL in 30 min
- Adjustment: -90 / 30 = -3.0U
- Subtract 3.0U from the calculated dose

This produces results that match the Aleppo/Laffel lookup tables but uses the patient's precise ISF rather than category buckets, which is more accurate for a CDSS.

## Modified File

### `algorithm/steps/CgmTrendStep.kt` — Full Rewrite of MDI/Pump Section

**AID section stays exactly as-is.** Only the MDI/Standard Pump section changes.

```kotlin
class CgmTrendStep : AlgorithmStep {
    override val name = "CGM Trend"

    // Anticipated 30-minute glucose change per trend arrow (mg/dL)
    // Source: Dexcom trend arrow specification, Aleppo et al. 2017
    private fun anticipatedChange(trend: CgmTrend): Double {
        return when (trend) {
            CgmTrend.DOUBLE_UP -> 90.0
            CgmTrend.SINGLE_UP -> 60.0
            CgmTrend.FORTY_FIVE_UP -> 30.0
            CgmTrend.FLAT -> 0.0
            CgmTrend.FORTY_FIVE_DOWN -> -30.0
            CgmTrend.SINGLE_DOWN -> -60.0
            CgmTrend.DOUBLE_DOWN -> -90.0
            CgmTrend.NONE -> 0.0
        }
    }

    // Round to nearest 0.5U for practical dosing
    private fun roundToHalf(value: Double): Double {
        return Math.round(value * 2.0) / 2.0
    }

    // Get the trend arrow emoji for display
    private fun trendEmoji(trend: CgmTrend): String {
        return when (trend) {
            CgmTrend.DOUBLE_UP -> "↑↑"
            CgmTrend.SINGLE_UP -> "↑"
            CgmTrend.FORTY_FIVE_UP -> "↗"
            CgmTrend.FLAT -> "→"
            CgmTrend.FORTY_FIVE_DOWN -> "↘"
            CgmTrend.SINGLE_DOWN -> "↓"
            CgmTrend.DOUBLE_DOWN -> "↓↓"
            CgmTrend.NONE -> ""
        }
    }

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        if (!context.hasCGM || context.cgmTrend == CgmTrend.NONE) return state
        if (context.cgmTrend == CgmTrend.FLAT) return state  // Flat = no adjustment needed

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
                CgmTrend.FORTY_FIVE_DOWN -> {
                    if (context.currentBG in 1.0..89.9) {
                        result = result.copy(rescueCarbs = result.rescueCarbs + 10)
                            .addEntry(BreakdownEntry(
                                stepName = name,
                                label = "Glucose Drifting Down",
                                emoji = "↘️",
                                description = "CGM shows a gradual glucose decline with BG below 90 mg/dL. " +
                                    "Consider a small snack. Your pump is adjusting insulin.",
                                effect = Effect.NEUTRAL,
                                runningTotal = result.currentDose
                            ))
                    }
                }
                else -> { /* FortyFiveUp, Flat handled by pump */ }
            }
            return result
        }

        // ============================================================
        // MDI and Standard Pump: ISF-unit-based adjustments
        // Source: Aleppo et al. 2017, Laffel et al. 2017
        // Formula: adjustment = anticipated_30min_change / ISF
        // ============================================================

        // Safety check: do not add insulin if BG is low (Aleppo safety recommendation)
        if (context.currentBG > 0 && context.currentBG < 70) {
            if (anticipatedChange(context.cgmTrend) > 0) {
                // BG is low but trending up — do NOT add insulin, treat hypo first
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

        // Get the patient's current ISF for this time of day
        val currentHour = context.timeOfDay.hour
        val currentIsf = context.bolusSettings.getIsfForHour(currentHour).toDouble()

        if (currentIsf <= 0) return result  // Safety: can't divide by 0

        // Calculate adjustment
        val anticipated = anticipatedChange(context.cgmTrend)
        val rawAdjustment = anticipated / currentIsf
        val adjustment = roundToHalf(rawAdjustment)

        if (adjustment == 0.0) return result  // Rounded to zero, no meaningful change

        val newDose = maxOf(0.0, result.currentDose + adjustment)

        // Build descriptive rationale
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

        // DoubleDown additional safety: rescue carbs regardless of adjustment
        // Source: Aleppo et al. 2017 — DoubleDown warrants extra caution
        if (context.cgmTrend == CgmTrend.DOUBLE_DOWN) {
            val carbAmount = when {
                context.currentBG < 90 -> 20
                context.currentBG < 120 -> 15
                else -> 10
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

        // SingleDown additional safety: carb suggestion if BG is borderline
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
```

## Key Differences from Current Implementation

| Aspect            | Old (percentage)    | New (ISF-unit)                         |
| ----------------- | ------------------- | -------------------------------------- |
| DoubleUp          | +20% of dose        | +90/ISF units (e.g., +1.8U for ISF 50) |
| SingleUp          | ignored             | +60/ISF units (e.g., +1.2U for ISF 50) |
| FortyFiveUp       | ignored             | +30/ISF units (e.g., +0.6U for ISF 50) |
| Flat              | no change           | no change                              |
| FortyFiveDown     | ignored             | -30/ISF units (e.g., -0.6U for ISF 50) |
| SingleDown        | -20% of dose        | -60/ISF units (e.g., -1.2U for ISF 50) |
| DoubleDown        | halve dose          | -90/ISF units (e.g., -1.8U for ISF 50) |
| Low BG + up trend | still added insulin | NO insulin added (Aleppo safety)       |
| AID handling      | unchanged           | unchanged                              |

## Safety Features Added

1. **Low BG + upward trend:** If BG < 70 and trend is up, do NOT add insulin. The patient needs to treat the hypo first, regardless of the upward trend. Source: Aleppo et al. 2017 safety recommendation.

2. **DoubleDown rescue carbs scaled by BG:** Instead of flat 15g, now scales: 20g if BG < 90, 15g if BG < 120, 10g otherwise. More conservative at lower BG levels.

3. **SingleDown + borderline BG:** New carb suggestion for BG 70-99 with SingleDown trend.

4. **FortyFiveDown for AID:** New carb suggestion for AID users if BG < 90 and trend is FortyFiveDown (gradually drifting lower).

5. **Dose floor at 0:** `maxOf(0.0, currentDose + adjustment)` ensures dose never goes negative from large downward adjustments.

## No Other Files Modified

This change is entirely self-contained in CgmTrendStep.kt. No changes needed to:

- AlgorithmEngine.kt (pipeline order unchanged)
- PatientContext / AlgorithmModels.kt (already has `cgmTrend` and `bolusSettings` with ISF profiles)
- ContextualModifierStep (reads dose after CgmTrendStep, doesn't interact with trend logic)
- Any other step or ViewModel

## ISF Access

The step uses `context.bolusSettings.getIsfForHour(hour)` which already exists and returns the time-segmented ISF for the current hour. This ensures the trend adjustment is based on the patient's ISF for the current time of day (e.g., morning ISF may differ from evening ISF due to dawn phenomenon).

## Edge Cases

1. **ISF = 0 or negative:** Guard clause returns state unchanged. Should never happen in practice (settings validation prevents it).
2. **Very small ISF (e.g., 15):** DoubleUp adjustment = 90/15 = 6.0U. This is a large adjustment but correct for a very insulin-resistant patient. MaxBolusCapStep downstream will catch if total exceeds maxBolus.
3. **Very large ISF (e.g., 100):** DoubleUp adjustment = 90/100 = 0.9U → rounded to 1.0U. Appropriate for an insulin-sensitive patient.
4. **Dose already at 0 from previous steps:** Upward adjustments can add insulin back (e.g., IOB subtracted everything, but BG is rising fast). Downward adjustments stay at 0 floor.
5. **AID user with all trends:** Completely unchanged from current behavior. Only carb suggestions for downward trends.
6. **BG not entered (0):** The low-BG safety check (`currentBG > 0 && currentBG < 70`) skips when BG is 0. Adjustment still applies based on trend alone.

## Acceptance Criteria

- [ ] All seven trend arrow directions handled for MDI/Standard Pump
- [ ] Adjustment calculated as `anticipated_30min_change / ISF` (not percentage of dose)
- [ ] ISF retrieved from time-segmented profile via `getIsfForHour(hour)`
- [ ] Adjustment rounded to nearest 0.5U
- [ ] Low BG (<70) + upward trend: no insulin added, warning shown
- [ ] DoubleDown: rescue carbs scaled by BG level (20g/15g/10g)
- [ ] SingleDown + BG 70-99: carb suggestion
- [ ] Dose floor at 0 (never negative)
- [ ] AID: completely unchanged (skip dose, keep carb suggestions)
- [ ] AID FortyFiveDown: new carb suggestion if BG < 90
- [ ] Rationale text includes anticipated mg/dL change, ISF value, and unit adjustment
- [ ] Rationale cites "Aleppo et al. 2017"
- [ ] No other files modified

## Files to Reference

- `@algorithm/steps/CgmTrendStep.kt` — file to rewrite
- `@algorithm/AlgorithmStep.kt` — CalculationState, BreakdownEntry
- `@data/models/AlgorithmModels.kt` — CgmTrend enum, PatientContext
- `@data/models/BolusSettings.kt` — getIsfForHour() method
