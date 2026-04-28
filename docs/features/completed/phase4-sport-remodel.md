# Feature: Phase 4 — Sport Remodel

## Summary

Restructure the SportModifierStep to apply sport reduction percentages to the meal and correction components separately (not the total dose), add sport-type-aware correction handling, introduce walking as a distinct sport category with its own reduction, add a ketone warning at very high BG, and update the late-hypo warning to be type-dependent rather than time-dependent.

## Clinical Evidence Base

- **Meal/correction split:** T1DEXIP percentages (Moser et al. 2020, Adolfsson et al. 2022) measured prandial bolus reductions, not total dose reductions. Applying them to the full dose (meal + correction) is a simplification that over-reduces corrections.
- **Aerobic skips correction:** Zivkovic et al. 2026 shows aerobic exercise drops BG by −1.43 mmol/l on average. Exercise itself serves as a correction mechanism. Zaharieva & Riddell 2017 zone-based approach supports this.
- **Anaerobic keeps correction:** Yardley et al. 2013 — resistance exercise produces smaller acute glucose decline, can even cause transient BG rise via catecholamines. Correction is justified.
- **Walking as moderate aerobic:** ISPAD 2022 Exercise Chapter classifies walking as "moderate intensity aerobic activity." Zivkovic 2026 shows walking has intermediate acute drop (−1.24 mmol/l), lowest nocturnal hypo risk.
- **Ketone contraindication:** ISPAD 2022 — blood ketones ≥1.5 mmol/L = exercise contraindicated. BG > 300 without ketone data → warn to check ketones.
- **Nocturnal hypo not time-dependent:** Zivkovic 2026 — exercise timing (before vs after 15:30) did NOT significantly affect nocturnal hypo probability.
- **Post-exercise correction caution:** ISPAD 2022 recommends max 50% correction dose post-exercise. Zaharieva & Riddell 2015 case study links aggressive post-exercise correction to fatal hypo.

## Changes to SportModifierStep

### Section B: Meal/Correction Split

**Current behavior:** Applies sport reduction percentage to `state.currentDose` (total = meal + correction combined).

**New behavior:** Read `mealBolus` and `correctionBolus` from metadata (stored by BaselineStep). Apply T1DEXIP reduction to the meal component. Handle correction separately based on sport type.

```
val mealBolus = state.metadata["mealBolus"] as? Double ?: 0.0
val correctionBolus = state.metadata["correctionBolus"] as? Double ?: 0.0

// Step 1: Apply T1DEXIP reduction to MEAL component only
val mealReduction = mealBolus * reductionPercent
val reducedMeal = mealBolus - mealReduction

// Step 2: Handle CORRECTION based on sport type and BG
val reducedCorrection: Double
when {
    // Aerobic or Walking + BG 140-250: skip correction (exercise is the correction)
    (sportType == "Aerobic" || sportType == "Walking") && currentBG in 140.0..250.0 -> {
        reducedCorrection = 0.0
        Add NEUTRAL BreakdownEntry:
            label = "Correction Skipped (Aerobic)"
            emoji = "🏃"
            description = "Correction withheld during aerobic exercise. The expected
                          glucose-lowering effect of the activity will serve as a
                          natural correction."
    }

    // Aerobic or Walking + BG > 250: 50% correction + ketone check
    (sportType == "Aerobic" || sportType == "Walking") && currentBG > 250 -> {
        reducedCorrection = correctionBolus * 0.50
        Add DECREASE BreakdownEntry:
            label = "Reduced Correction (High BG + Aerobic)"
            emoji = "⚠️"
            description = "BG is very high. A 50% correction ({reducedCorrection}U) is
                          applied alongside the exercise. Check for ketones if BG
                          exceeds 300 mg/dL."
    }

    // Mixed + any high BG: 50% correction
    sportType == "Mixed" && correctionBolus > 0 -> {
        reducedCorrection = correctionBolus * 0.50
        Add DECREASE BreakdownEntry:
            label = "Reduced Correction (Mixed Exercise)"
            emoji = "🏋️"
            description = "Correction reduced by 50% during mixed exercise. Mixed
                          activity has variable effects on blood glucose."
    }

    // Anaerobic + any high BG: keep full correction
    sportType == "Anaerobic" && correctionBolus > 0 -> {
        reducedCorrection = correctionBolus
        Add NEUTRAL BreakdownEntry:
            label = "Full Correction (Anaerobic)"
            emoji = "🏋️"
            description = "Full correction maintained during anaerobic exercise.
                          Resistance training does not reliably lower blood glucose
                          and may cause transient increases."
    }

    // Default: keep correction as-is (shouldn't normally reach here)
    else -> {
        reducedCorrection = correctionBolus
    }
}

// Step 3: Combine
val newDose = reducedMeal + reducedCorrection
val totalReduction = state.currentDose - newDose

// Add the sport reduction BreakdownEntry showing meal reduction
Add DECREASE BreakdownEntry:
    label = "Sport Reduction"
    emoji = "🏃"
    description = "{sportType} ({intensity}): Meal bolus reduced by
                  {reductionPercent}% (-{mealReduction}U)."
    + if duration modifier: " Duration >45m: +{extra}% extra."
    valueChange = -totalReduction
    runningTotal = newDose

state.currentDose = newDose
```

**Important:** If `mealBolus` and `correctionBolus` are both 0 in metadata (e.g., metadata wasn't populated, or the user entered no carbs and BG is at target), fall back to the current behavior of applying the reduction to `state.currentDose` directly. This prevents a regression where sport mode does nothing when metadata is missing.

### Walking Sport Category

**Add "Walking" to the sport type recognition in Section B:**

```
when (context.sportType) {
    "Walking" -> {
        reductionPercent = 0.25  // flat 25%, ISPAD moderate aerobic classification
        reductionLabel = "Walking: 25% reduction."
    }
    "Anaerobic" -> { ... }
    "Mixed" -> { ... }
    else -> { /* Aerobic — existing logic */ }
}
```

Walking gets a flat 25% regardless of intensity (walking intensity is inherently moderate — there's no "high intensity walking" that isn't just running). No duration modifier for walking unless duration exceeds 45 min, in which case the existing duration logic applies.

### Ketone Warning at Very High BG

**Add to the beginning of SportModifierStep, before Section B:**

```
// Ketone check warning at very high BG
if (context.currentBG > 300 && context.isDoingSport) {
    Add WARNING BreakdownEntry:
        label = "Check Ketones Before Exercise"
        emoji = "🚨"
        description = "BG exceeds 300 mg/dL. Check blood ketones before exercising.
                      If ketones are ≥1.5 mmol/L, exercise is contraindicated due
                      to the risk of diabetic ketoacidosis (DKA). If ketones are
                      0.6–1.4 mmol/L, postpone exercise until corrective insulin
                      is administered."
        effect = WARNING
}
```

This is a visible warning that appears prominently in the rationale. It does NOT block the calculation — the user may not have ketone data and might still choose to proceed. But the warning is clear and clinically grounded (ISPAD thresholds).

### Section D: Updated Late-Hypo Warning

**Current behavior:** Shows late-hypo warning for aerobic/mixed or >45min sessions, without considering exercise type for nocturnal risk severity.

**New behavior:** Key the warning off exercise type (per Zivkovic: aerobic ≈ anaerobic nocturnal risk > walking nocturnal risk). Remove any time-of-day dependency.

```
// D. Post-sport late-onset warning
if (!isFuture) {
    if (context.currentBG > 0 && context.currentBG < 80) {
        // Immediate post-workout low — unchanged
        result = result.copy(rescueCarbs = 15)
            .addEntry(...)
    } else if (context.sportDurationMins >= 45 ||
               context.sportType == "Aerobic" ||
               context.sportType == "Anaerobic" ||
               context.sportType == "Mixed") {

        // Severity based on sport type (Zivkovic 2026: walking has lowest nocturnal risk)
        val warningText = if (context.sportType == "Walking") {
            "⚠️ Moderate risk of delayed hypoglycemia. Walking has a lower nocturnal " +
            "hypo risk than structured exercise, but monitor glucose before bedtime."
        } else {
            "⚠️ Risk of Late-Onset Hypoglycemia (7-11h window). " +
            "Consider a bedtime snack with protein and complex carbohydrates, " +
            "or a 20% reduction in overnight basal insulin."
        }
        result = result.addWarning(warningText)
    }
}
```

### Section C: Therapy-Specific Advice — Minor Updates

Update the MDI carb advice to account for walking:

```
TherapyType.MDI -> {
    // Walking gets slightly lower carb suggestion than aerobic
    val carbs = when (context.sportType) {
        "Aerobic" -> 20
        "Walking" -> 15
        "Mixed" -> 15
        else -> 10  // Anaerobic
    }
    ...
}
```

Similarly for AID and Standard Pump sections, include "Walking" alongside "Aerobic" in the conditions that trigger carb suggestions.

## Files Modified

| File                                   | Change            |
| -------------------------------------- | ----------------- |
| `algorithm/steps/SportModifierStep.kt` | All changes above |

## Files NOT Modified

No new files. No data model changes. No ViewModel changes. Walking is already detected by Health Connect and stored as `sportType = "Walking"` in BolusLog. The PatientContext already receives `sportType` as a String, so "Walking" flows through naturally.

## Edge Cases

1. **Metadata missing:** If `mealBolus` / `correctionBolus` not in metadata (old code path, test scenario), fall back to applying reduction to total `state.currentDose`.
2. **Meal-only bolus (no correction):** `correctionBolus = 0.0` → correction handling logic is skipped, only meal reduction applies. Correct behavior.
3. **Correction-only (no carbs):** `mealBolus = 0.0` → meal reduction produces 0. Correction handled by sport-type logic. If aerobic: correction skipped (exercise is correction). If anaerobic: full correction kept.
4. **Walking + BG < 140:** No correction to handle. Just the 25% meal reduction. Standard behavior.
5. **Walking + BG > 300:** Ketone warning fires (from new ketone check). Then 50% correction applied (walking treated like aerobic for correction logic).
6. **AID user + sport:** BaselineStep already skipped the correction (Phase 2). So `correctionBolus` in metadata is 0.0. The correction handling in SportModifierStep sees 0 and skips. Only meal reduction applies. Correct.
7. **Sport type not recognized:** Falls to `else` branch (treated as Aerobic). Existing behavior preserved.

## Acceptance Criteria

- [ ] Sport reduction applied to MEAL component only (not total dose)
- [ ] Correction handled separately based on sport type
- [ ] Aerobic + BG 140-250: correction skipped with explanatory entry
- [ ] Aerobic + BG > 250: 50% correction + ketone mention
- [ ] Mixed: 50% correction
- [ ] Anaerobic: full correction maintained
- [ ] Walking recognized as sport type with flat 25% reduction
- [ ] Walking uses same correction logic as Aerobic (skip 140-250, 50% if >250)
- [ ] Ketone warning appears when BG > 300 during any sport
- [ ] Late-hypo warning uses sport type for severity (walking = softer warning)
- [ ] Late-hypo warning does NOT depend on time of day
- [ ] Metadata fallback: if mealBolus/correctionBolus not in metadata, reduce total dose (no regression)
- [ ] AID users: correctionBolus already 0 from BaselineStep, sport step handles gracefully
- [ ] All Phase 1/2/3 behavior unchanged for non-sport scenarios

## Files to Reference

- `@algorithm/steps/SportModifierStep.kt` — the file being modified
- `@algorithm/steps/BaselineStep.kt` — stores mealBolus/correctionBolus in metadata
- `@algorithm/AlgorithmStep.kt` — CalculationState, BreakdownEntry, metadata pattern
- `@data/models/AlgorithmModels.kt` — PatientContext fields
