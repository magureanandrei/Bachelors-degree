# Feature: Algorithm Refactor — Dominant Modifier Architecture

## Summary
Restructure the algorithm pipeline from sequential percentage-stacking into a layered architecture: Factual Adjustments (always apply) → Dominant Contextual Modifier (ONE wins) → Safety Layers (always apply). This eliminates stacking problems, makes every scenario deterministic, and ensures only one contextual modifier modifies the dose while all active conditions generate warnings.

## Why This Refactor
The current pipeline applies illness (+25%), sport (-X%), nighttime (-30%), and post-exercise (-50%) as sequential multipliers. In complex scenarios (ill + post-exercise + nighttime), these stack unpredictably. No clinical literature supports combined modifier percentages. This refactor ensures one dominant modifier applies, and others become informational warnings.

## New Pipeline

```
0. HypoGuardStep              — KEEP AS-IS. Immediate BG safety check.
1. BaselineStep                — KEEP AS-IS. Raw meal + correction (therapy-aware).
2. IobDeductionStep            — KEEP AS-IS. Subtract IOB, floor at 0.
3. CgmTrendStep                — KEEP AS-IS. Real-time BG trend adjustment.
4. ContextualModifierStep      — NEW. Replaces OutsideFactorsStep + SportModifierStep + PostExerciseSensitivityStep.
5. NighttimeSafetyStep         — MODIFY. Warning-only safety layer, no dose modification.
6. BasalAwarenessStep          — KEEP AS-IS. MDI basal warnings.
7. MaxBolusCapStep             — KEEP AS-IS. Final safety cap.
```

## Files to Delete
- `algorithm/steps/OutsideFactorsStep.kt` — absorbed into ContextualModifierStep
- `algorithm/steps/SportModifierStep.kt` — absorbed into ContextualModifierStep
- `algorithm/steps/PostExerciseSensitivityStep.kt` — absorbed into ContextualModifierStep (if it exists)

## Files to Create

### `algorithm/steps/ContextualModifierStep.kt`

This is the core of the refactor. It resolves the patient's current situation, picks the single most relevant modifier, applies it to the dose, and generates warnings for all other active conditions.

#### Priority Resolution (highest wins)

```kotlin
enum class DominantModifier {
    ACTIVE_EXERCISE,    // Priority 1: currently exercising or about to
    EXERCISE_RECOVERY,  // Priority 2: exercised recently, not currently active
    ILLNESS,            // Priority 3: illness flag active
    STRESS,             // Priority 4: stress flag active
    HEAT,               // Priority 5: heat flag active
    NONE                // No contextual modifier
}

private fun resolveDominant(context: PatientContext): DominantModifier {
    // Exercise phase resolution
    val exercisePhase = when {
        context.isDoingSport -> ExercisePhase.ACTIVE  // includes pre-exercise
        context.exercisedToday && !context.isDoingSport -> ExercisePhase.RECOVERY
        else -> ExercisePhase.NONE
    }

    return when {
        exercisePhase == ExercisePhase.ACTIVE -> DominantModifier.ACTIVE_EXERCISE
        exercisePhase == ExercisePhase.RECOVERY -> DominantModifier.EXERCISE_RECOVERY
        context.isIllness -> DominantModifier.ILLNESS
        context.isHighStress -> DominantModifier.STRESS
        context.isExtremeHeat -> DominantModifier.HEAT
        else -> DominantModifier.NONE
    }
}

private enum class ExercisePhase { NONE, ACTIVE, RECOVERY }
```

#### Modifier Application Logic

**ACTIVE_EXERCISE (Priority 1) — currently exercising or pre-exercise**

All citations: Riddell 2017, Adolfsson 2022 (ISPAD), Moser 2025, Zaharieva 2017, Yardley 2013, Zivkovic 2026.

```
// A. Ketone warning if BG > 300
if (currentBG > 300) {
    Add WARNING: "Check ketones before exercising. ≥1.5 mmol/L = contraindicated (ISPAD)."
}

// B. Read meal and correction from metadata
val mealBolus = metadata["mealBolus"] as? Double ?: 0.0
val correctionBolus = metadata["correctionBolus"] as? Double ?: 0.0

// C. Meal reduction by sport type and intensity (T1DEXIP percentages)
// These apply to MDI and Standard Pump. AID gets 25-33% flat (Moser 2025).
val mealReductionPercent = if (context.bolusSettings.isAidPump) {
    0.30  // 25-33%, use midpoint. Source: Moser 2025
} else {
    when (context.sportType) {
        "Aerobic" -> when (context.sportIntensity) {
            3 -> 0.75; 2 -> 0.50; else -> 0.25
        }
        "Mixed" -> when (context.sportIntensity) {
            3 -> 0.40; 2 -> 0.25; else -> 0.15
        }
        "Walking" -> 0.25  // ISPAD: moderate aerobic classification
        "Anaerobic" -> 0.10  // ISPAD Exercise Chapter
        else -> 0.25  // default to moderate
    }
}

// Duration modifier: >45 min adds extra (ISPAD 45-min threshold)
val durationExtra = if (context.sportDurationMins > 45) {
    minOf(0.20, (context.sportDurationMins - 45) * 0.005)
} else 0.0
val totalMealReduction = minOf(0.90, mealReductionPercent + durationExtra)

val reducedMeal = mealBolus * (1.0 - totalMealReduction)

// D. Correction handling by sport type (applied to MDI/Pump only — AID correction already 0)
val reducedCorrection = when {
    (context.sportType in listOf("Aerobic", "Walking")) && context.currentBG in 140.0..250.0 -> {
        // Skip correction — exercise is the correction. Source: Zaharieva 2017 zone-based approach
        Add NEUTRAL entry: "Correction withheld. Aerobic exercise will serve as a natural correction."
        0.0
    }
    (context.sportType in listOf("Aerobic", "Walking")) && context.currentBG > 250.0 -> {
        // 50% correction. Source: ISPAD — cautious correction at high BG
        Add DECREASE entry: "50% correction applied. BG is very high alongside exercise."
        correctionBolus * 0.50
    }
    context.sportType == "Mixed" && correctionBolus > 0 -> {
        // 50% correction. Source: ISPAD — cautious approach for mixed exercise
        Add DECREASE entry: "50% correction. Mixed exercise has variable BG effects."
        correctionBolus * 0.50
    }
    context.sportType == "Anaerobic" && correctionBolus > 0 -> {
        // Full correction. Source: Yardley 2013 — resistance doesn't reliably lower BG
        Add NEUTRAL entry: "Full correction maintained. Anaerobic exercise does not reliably lower BG."
        correctionBolus
    }
    else -> correctionBolus
}

// E. Combine and apply
val newDose = reducedMeal + reducedCorrection
// Add sport reduction BreakdownEntry showing the meal reduction percentage

// F. Therapy-specific advice
when (context.therapyType) {
    MDI -> {
        val carbs = when (context.sportType) {
            "Aerobic" -> 20; "Walking" -> 15; "Mixed" -> 15; else -> 10
        }
        Add NEUTRAL: "Basal can't be suspended. Consume ${carbs}g carbs before exercise."
        if (context.sportDurationMins >= 45) {
            Add NEUTRAL: "Consider reducing next basal dose by 20% (ISPAD)."
        }
    }
    PUMP_STANDARD -> {
        Add NEUTRAL: "Set temp basal to 50% starting 60-90 min before exercise (Zaharieva 2017)."
        if BG borderline: suggest carbs
    }
    PUMP_AID -> {
        Add NEUTRAL: "Activate Exercise/Activity target 1-2h before exercise (Moser 2025)."
        if (context.currentBG < 100): suggest 10g carbs
    }
}

// G. IOB warning — high IOB + imminent exercise
if (context.activeInsulinIOB > 1.5) {
    Add WARNING: "High active insulin ({IOB}U). Exercise will increase insulin sensitivity. 
                  Consider extra fast-acting carbohydrates without additional insulin."
}

// H. Late-hypo warning (only if exercise is happening now or just finished)
if (context.minutesUntilSport <= 0) {
    if (context.currentBG > 0 && context.currentBG < 80) {
        rescueCarbs += 15
        Add WARNING: "Post-workout BG is low. Consume 15g fast-acting carbohydrates."
    }
    if (context.sportDurationMins >= 45 || context.sportType in listOf("Aerobic", "Mixed", "Anaerobic")) {
        val warning = if (context.sportType == "Walking") {
            "Moderate risk of delayed hypoglycemia. Walking has lower nocturnal risk. Monitor before bedtime."
        } else {
            "Risk of late-onset hypoglycemia (7-11h window). Consider bedtime snack with protein and complex carbs."
        }
        Add WARNING: warning
    }
}

// Apply metadata fallback if meal/correction were both 0 in metadata
if (mealBolus == 0.0 && correctionBolus == 0.0 && state.currentDose > 0) {
    // Fallback: reduce total dose by the meal reduction percent
    newDose = state.currentDose * (1.0 - totalMealReduction)
}
```

**EXERCISE_RECOVERY (Priority 2) — exercised earlier today, not currently active**

Citations: ISPAD 2022 (50% correction), Diabetologia 2023 review (50% meal at first post-exercise meal).

```
val mealBolus = metadata["mealBolus"] as? Double ?: 0.0
val correctionBolus = metadata["correctionBolus"] as? Double ?: 0.0

// 50% meal reduction — Diabetologia 2023 review
val reducedMeal = mealBolus * 0.50
if (mealBolus > 0) {
    Add DECREASE entry: "Post-exercise meal reduction: 50%. Insulin sensitivity is elevated 
                         after today's exercise (Diabetologia 2023)."
}

// 50% correction reduction — ISPAD 2022 ("no more than 50%")
val reducedCorrection = correctionBolus * 0.50
if (correctionBolus > 0) {
    Add DECREASE entry: "Post-exercise correction: 50%. ISPAD recommends conservative 
                         corrections following exercise."
}

val newDose = reducedMeal + reducedCorrection

// Therapy-specific post-exercise advice
when (context.therapyType) {
    MDI -> Add NEUTRAL: "Consider reducing next basal dose by 20% (ISPAD). Monitor closely."
    PUMP_STANDARD -> Add NEUTRAL: "Consider a temp basal of 90% for 4-6 hours (Zaharieva 2017)."
    PUMP_AID -> Add NEUTRAL: "Monitor glucose post-exercise. Consider keeping Exercise Target 
                               active if within 2 hours of finishing exercise."
}

// Metadata fallback
if (mealBolus == 0.0 && correctionBolus == 0.0 && state.currentDose > 0) {
    newDose = state.currentDose * 0.50
}
```

**ILLNESS (Priority 3)**

Citation: ISPAD Sick Day Guidelines — insulin needs increase 20-50%. Using 25% as conservative midpoint.

```
val increase = state.currentDose * 0.25
val newDose = state.currentDose + increase
Add INCREASE entry: "Illness: +25% total dose. Illness increases insulin resistance (ISPAD)."
```

**STRESS (Priority 4)**

Citation: Lloyd et al. 1999, Diep et al. 2012 — stress/depression impair glycemic control. Using 15%.

```
val increase = state.currentDose * 0.15
val newDose = state.currentDose + increase
Add INCREASE entry: "Stress: +15% total dose. Stress elevates insulin resistance via cortisol."
```

**HEAT (Priority 5)**

Citation: Koivisto 1981 — heat accelerates subcutaneous insulin absorption.

```
val decrease = state.currentDose * 0.10
val newDose = state.currentDose - decrease
Add DECREASE entry: "Heat: -10% total dose. Elevated temperature accelerates insulin absorption."
```

**NONE — no contextual modifier. Return state unchanged.**

#### Non-Dominant Warnings

After applying the dominant modifier, generate warnings for ALL other active conditions:

```
// If exercise is active/recovery but wasn't dominant (shouldn't happen given priorities, but safe)
// If illness is active but exercise won dominance:
if (context.isIllness && dominant != ILLNESS) {
    Add WARNING: "You are ill. Illness typically increases insulin needs by 20-50%. 
                  However, ${dominantLabel} is currently the primary factor affecting your dose. 
                  Monitor BG closely — if glucose remains elevated, illness may require 
                  additional correction."
}

// If stress is active but something else won:
if (context.isHighStress && dominant != STRESS) {
    Add WARNING: "Stress is active. This typically increases insulin needs. 
                  Monitor for unexpected BG elevation."
}

// If heat is active but something else won:
if (context.isExtremeHeat && dominant != HEAT) {
    Add WARNING: "Elevated temperature detected. Insulin absorption may be faster than usual. 
                  Monitor for unexpected lows."
}
```

## Files to Modify

### `algorithm/steps/NighttimeSafetyStep.kt` — Simplify to Warning-Only

Remove ALL dose modification logic (the 30% correction reduction). Keep only:

```kotlin
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
```

### `utils/AlgorithmEngine.kt` — Updated Pipeline

```kotlin
private val pipeline = AlgorithmPipeline(
    listOf(
        HypoGuardStep(),            // 0. Immediate BG safety
        BaselineStep(),              // 1. Raw meal + correction (therapy-aware)
        IobDeductionStep(),          // 2. Subtract IOB
        CgmTrendStep(),              // 3. Real-time BG trend
        ContextualModifierStep(),    // 4. ONE dominant modifier
        NighttimeSafetyStep(),       // 5. Nighttime warnings
        BasalAwarenessStep(),        // 6. MDI basal warnings
        MaxBolusCapStep()            // 7. Safety cap
    )
)
```

## PatientContext — No New Fields Required

Existing fields used:
- `isDoingSport`, `sportType`, `sportIntensity`, `sportDurationMins`, `minutesUntilSport` — exercise phase resolution
- `exercisedToday` — recovery phase detection
- `isIllness`, `isHighStress`, `isExtremeHeat` — outside factors
- `activeInsulinIOB` — high IOB + exercise warning
- `therapyType` — therapy-specific advice
- `currentBG` — correction handling, ketone warning, bedtime snack
- `timeOfDay` — used by NighttimeSafetyStep

The `hoursSinceLastExercise`, `lastExerciseSportType`, `lastExerciseIntensity`, `lastExerciseDurationMins` fields from Phase 5 spec are NOT needed. The simplified recovery phase uses flat 50% (cited) regardless of exercise type.

## Edge Cases

1. **AID user + active exercise:** BaselineStep already set correction to 0. ContextualModifierStep reads correctionBolus=0 from metadata. Only meal reduction and AID-specific advice apply.
2. **AID user + recovery:** Same — correction is 0, only meal gets 50% reduction.
3. **Illness + active exercise:** Exercise wins dominance. Illness becomes a warning. No stacking.
4. **Illness + stress:** Illness wins (higher priority). Stress becomes a warning. They don't stack (same as current else-if behavior).
5. **Recovery + nighttime:** Recovery is dominant (dose modifier). NighttimeSafetyStep adds bedtime warnings on top. No stacking on dose — recovery handles the reduction, nighttime adds advisory.
6. **No modifiers active:** ContextualModifierStep returns state unchanged. Pure bolus math.
7. **Metadata fallback:** If mealBolus and correctionBolus are both 0 in metadata but currentDose > 0, apply reduction to total dose to prevent regression.
8. **High IOB + pre-exercise:** IOB was already subtracted by IobDeductionStep. ContextualModifierStep adds a WARNING about high IOB risk — doesn't modify dose further, suggests carbs.

## Acceptance Criteria

- [ ] ContextualModifierStep replaces OutsideFactorsStep, SportModifierStep, and PostExerciseSensitivityStep
- [ ] OutsideFactorsStep.kt and SportModifierStep.kt deleted
- [ ] Priority resolution: Active Exercise > Recovery > Illness > Stress > Heat
- [ ] Only ONE modifier applies to the dose per calculation
- [ ] Non-dominant active conditions generate informational warnings
- [ ] Active exercise: meal reduction uses T1DEXIP percentages (MDI/Pump) or 30% flat (AID/Moser 2025)
- [ ] Active exercise: correction handled by sport type (aerobic skip 140-250, 50% >250, mixed 50%, anaerobic full)
- [ ] Active exercise: ketone warning at BG > 300
- [ ] Active exercise: therapy-specific advice (MDI carbs, Pump temp basal, AID exercise target)
- [ ] Active exercise: high IOB warning when IOB > 1.5U
- [ ] Active exercise: late-hypo warning for sessions ≥45min or aerobic/mixed/anaerobic
- [ ] Active exercise: walking gets softer late-hypo warning
- [ ] Recovery: 50% meal reduction (Diabetologia 2023)
- [ ] Recovery: 50% correction reduction (ISPAD 2022)
- [ ] Recovery: therapy-specific post-exercise advice
- [ ] Illness: +25% total dose when dominant
- [ ] Stress: +15% total dose when dominant
- [ ] Heat: -10% total dose when dominant
- [ ] NighttimeSafetyStep: NO dose modification, warnings only
- [ ] NighttimeSafetyStep: exercise overnight warning when exercisedToday
- [ ] NighttimeSafetyStep: bedtime snack when BG < 120
- [ ] BasalAwarenessStep: unchanged
- [ ] MaxBolusCapStep: unchanged
- [ ] Pipeline order: HypoGuard → Baseline → IOB → CgmTrend → ContextualModifier → NighttimeSafety → BasalAwareness → MaxBolusCap
- [ ] All existing BreakdownEntry output preserved or improved
- [ ] Metadata fallback works when mealBolus/correctionBolus not in metadata

## Files to Reference

- `@algorithm/AlgorithmStep.kt` — interface, CalculationState, BreakdownEntry
- `@algorithm/steps/BaselineStep.kt` — stores mealBolus/correctionBolus in metadata
- `@algorithm/steps/SportModifierStep.kt` — current implementation to absorb and delete
- `@algorithm/steps/OutsideFactorsStep.kt` — current implementation to absorb and delete
- `@algorithm/steps/NighttimeSafetyStep.kt` — to simplify
- `@algorithm/steps/CgmTrendStep.kt` — unchanged, for reference
- `@utils/AlgorithmEngine.kt` — pipeline wiring
- `@data/models/AlgorithmModels.kt` — PatientContext, TherapyType