# Feature: Algorithm Patch — StatusResolver + Walking Recovery + AID Fix

## Summary

Add a StatusResolver that computes PatientStatus once at pipeline start. Fix walking recovery to use duration-based windows and proportional percentages instead of flat 50%. Remove AID meal dose modification (AID users don't manually dose — pump calculates from entered carbs). Add required PatientContext fields for recovery resolution.

## Changes Overview

1. Create `PatientStatus` data class and `StatusResolver` (resolves zones once, stored in metadata)
2. Add `hoursSinceLastExercise`, `lastExerciseSportType`, `lastExerciseDurationMins` to PatientContext
3. Fix `applyExerciseRecovery()` in ContextualModifierStep — walking-specific recovery
4. Fix AID active exercise — remove 30% meal reduction, replace with advice-only
5. Fix AID recovery exercise target advice — only within 2h of structured aerobic/mixed
6. Update CalculateBolusViewModel to populate new fields
7. NighttimeSafetyStep uses `exercisedToday` computed property (already works)

---

## New Files

### `algorithm/PatientStatus.kt`

```kotlin
package com.example.diabetesapp.algorithm

import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType

/**
 * Resolved once per calculation. Every step reads zones instead of raw comparisons.
 * Stored in CalculationState.metadata["patientStatus"].
 */
data class PatientStatus(
    val bgZone: BgZone,
    val exercisePhase: ExercisePhase,
    val timeZone: TimeZone,
    val iobLevel: IobLevel
)

enum class BgZone {
    HYPO,           // < 54 mg/dL (clinically significant)
    LOW,            // 54 to < hypoLimit (default 70)
    BORDERLINE,     // hypoLimit to 90
    IN_RANGE,       // 90 to 140
    ABOVE_RANGE,    // 140 to 250
    HIGH,           // 250 to 300
    CRITICAL,       // > 300
    UNKNOWN         // BG not entered (0 or negative)
}

enum class ExercisePhase {
    NONE,               // no exercise context
    ACTIVE,             // isDoingSport == true (pre-exercise or currently exercising)
    WALK_RECOVERY,      // walked recently, in recovery window
    EXERCISE_RECOVERY   // structured exercise, in 0-6h recovery window
}

enum class TimeZone {
    NIGHTTIME,  // 21:00 - 06:00
    MORNING,    // 06:00 - 10:00
    DAYTIME,    // 10:00 - 17:00
    EVENING     // 17:00 - 21:00
}

enum class IobLevel {
    NONE,       // IOB ≈ 0 (< 0.1)
    LOW,        // < 1.0U
    MODERATE,   // 1.0 - 3.0U
    HIGH        // > 3.0U
}

object StatusResolver {

    fun resolve(context: PatientContext): PatientStatus {
        return PatientStatus(
            bgZone = resolveBgZone(context),
            exercisePhase = resolveExercisePhase(context),
            timeZone = resolveTimeZone(context),
            iobLevel = resolveIobLevel(context)
        )
    }

    private fun resolveBgZone(context: PatientContext): BgZone {
        val bg = context.currentBG
        val hypoLimit = context.bolusSettings.hypoLimit
        return when {
            bg <= 0 -> BgZone.UNKNOWN
            bg < 54 -> BgZone.HYPO
            bg < hypoLimit -> BgZone.LOW
            bg < 90 -> BgZone.BORDERLINE
            bg <= 140 -> BgZone.IN_RANGE
            bg <= 250 -> BgZone.ABOVE_RANGE
            bg <= 300 -> BgZone.HIGH
            else -> BgZone.CRITICAL
        }
    }

    private fun resolveExercisePhase(context: PatientContext): ExercisePhase {
        // Currently exercising or about to
        if (context.isDoingSport) return ExercisePhase.ACTIVE

        // Not currently exercising — check recovery
        val hours = context.hoursSinceLastExercise
        if (hours < 0) return ExercisePhase.NONE

        val sportType = context.lastExerciseSportType
        val duration = context.lastExerciseDurationMins

        // Walking recovery — shorter windows based on duration
        if (sportType == "Walking") {
            return when {
                duration < 10 -> ExercisePhase.NONE  // too short, no recovery
                duration < 30 && hours <= 1.0f -> ExercisePhase.WALK_RECOVERY
                duration < 45 && hours <= 2.0f -> ExercisePhase.WALK_RECOVERY
                duration >= 45 && hours <= 3.0f -> ExercisePhase.WALK_RECOVERY
                else -> ExercisePhase.NONE  // outside recovery window
            }
        }

        // Structured exercise recovery — 0-6 hours (ISPAD basal reduction timeframe)
        if (sportType in listOf("Aerobic", "Mixed", "Anaerobic")) {
            return if (hours <= 6.0f) ExercisePhase.EXERCISE_RECOVERY
            else ExercisePhase.NONE
        }

        return ExercisePhase.NONE
    }

    private fun resolveTimeZone(context: PatientContext): TimeZone {
        val hour = context.timeOfDay.hour
        return when {
            hour >= 21 || hour < 6 -> TimeZone.NIGHTTIME
            hour < 10 -> TimeZone.MORNING
            hour < 17 -> TimeZone.DAYTIME
            else -> TimeZone.EVENING
        }
    }

    private fun resolveIobLevel(context: PatientContext): IobLevel {
        val iob = context.activeInsulinIOB
        return when {
            iob < 0.1 -> IobLevel.NONE
            iob < 1.0 -> IobLevel.LOW
            iob <= 3.0 -> IobLevel.MODERATE
            else -> IobLevel.HIGH
        }
    }
}
```

---

## Data Model Changes

### PatientContext (AlgorithmModels.kt)

**Add these fields** (plain data class, no DB impact):

```kotlin
val hoursSinceLastExercise: Float = -1f,    // -1 = no exercise today
val lastExerciseSportType: String = "",      // "Walking", "Aerobic", "Mixed", "Anaerobic"
val lastExerciseDurationMins: Int = 0
```

**Change `exercisedToday`** from a stored field to a computed property:

```kotlin
val exercisedToday: Boolean get() = hoursSinceLastExercise in 0f..24f
```

If `exercisedToday` was a constructor parameter before, remove it from the constructor and make it a computed `get()` property. All existing code that reads `exercisedToday` will continue to work.

---

## ViewModel Wiring

### CalculateBolusViewModel

When constructing PatientContext, query the most recent completed SPORT event:

```kotlin
val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
val recentSportLogs = repository.getCompletedSportLogsSince(todayStart)
val lastSport = recentSportLogs.maxByOrNull { it.timestamp }

val hoursSinceLastExercise = if (lastSport != null) {
    (System.currentTimeMillis() - lastSport.timestamp) / (1000f * 60f * 60f)
} else -1f

val lastExerciseSportType = lastSport?.sportType ?: ""
val lastExerciseDurationMins = lastSport?.sportDuration?.toInt() ?: 0
```

Pass all three fields when constructing PatientContext. Remove any existing `exercisedToday = ...` parameter from the constructor call (it's now computed).

If `getCompletedSportLogsSince` doesn't exist in the repository/DAO, add it:

```kotlin
// In BolusLogDao
@Query("SELECT * FROM bolus_log WHERE eventType = 'SPORT' AND status = 'COMPLETED' AND timestamp >= :since ORDER BY timestamp DESC")
suspend fun getCompletedSportLogsSince(since: Long): List<BolusLog>

// In BolusLogRepository
suspend fun getCompletedSportLogsSince(since: Long) = bolusLogDao.getCompletedSportLogsSince(since)
```

---

## Modified Files

### `algorithm/steps/ContextualModifierStep.kt`

#### 1. Resolve and store PatientStatus at the start of apply()

```kotlin
override fun apply(state: CalculationState, context: PatientContext): CalculationState {
    // Resolve status once
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
```

#### 2. Update DominantModifier enum and resolution

```kotlin
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
```

#### 3. Fix AID in applyActiveExercise()

Replace the AID meal reduction block. Currently it applies 30% flat. Change to advice-only:

**Find and replace** the block where `mealReductionPercent` is set for AID:

```kotlin
// OLD:
val mealReductionPercent = if (context.bolusSettings.isAidPump) {
    0.30
} else { ... }

// NEW:
val mealReductionPercent = if (context.bolusSettings.isAidPump) {
    0.0  // AID: pump calculates dose from entered carbs. No manual dose modification.
} else { ... }
```

Then in the therapy advice section (section E), update AID advice:

```kotlin
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
```

#### 4. Update walking active reduction to be duration-aware

In the `mealReductionPercent` calculation for non-AID, change the Walking branch:

```kotlin
// OLD:
"Walking" -> 0.25

// NEW:
"Walking" -> when {
    context.sportDurationMins >= 45 -> 0.43  // Zivkovic ratio: 87% × aerobic medium 50%
    context.sportDurationMins >= 30 -> 0.35  // Midpoint: 87% × aerobic low-medium 40%
    else -> 0.25                              // ISPAD: moderate aerobic classification
}
```

Note: The duration modifier (>45 min extra) should NOT apply on top of walking's already duration-scaled percentages. Add a check:

```kotlin
val durationExtra = if (context.sportType != "Walking" && context.sportDurationMins > 45) {
    minOf(0.20, (context.sportDurationMins - 45) * 0.005)
} else 0.0
```

Walking already accounts for duration in its own percentage tiers, so the generic duration modifier is skipped for walking.

#### 5. New function: applyWalkRecovery()

```kotlin
private fun applyWalkRecovery(state: CalculationState, context: PatientContext, status: PatientStatus): CalculationState {
    var result = state
    val mealBolus = state.metadata["mealBolus"] as? Double ?: 0.0
    val correctionBolus = state.metadata["correctionBolus"] as? Double ?: 0.0
    val duration = context.lastExerciseDurationMins

    // Walking recovery reduction: flat within window, based on walk duration
    // 10-30 min walk → 15% reduction, 1h window
    // 30-45 min walk → 20% reduction, 2h window
    // >45 min walk  → 25% reduction, 3h window
    // Derivation: proportional from active walking reductions,
    // scaled by Zivkovic walking/aerobic ratio (0.87)
    val reductionPercent = when {
        duration >= 45 -> 0.25
        duration >= 30 -> 0.20
        else -> 0.15  // 10-30 min
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
        // Metadata fallback
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

    // Therapy-specific walking recovery advice
    when (context.bolusSettings.therapyTypeEnum) {
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
        else -> {}
    }

    return result
}

```

#### 6. Fix applyExerciseRecovery() — unchanged percentages but fix AID advice

The 50% meal + 50% correction for structured exercise stays. Only fix the AID advice timing:

```kotlin
// In applyExerciseRecovery(), replace the AID therapy advice block:
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
```

#### 7. Update addNonDominantWarnings() for WALK_RECOVERY

Add WALK_RECOVERY to the dominant label mapping:

```kotlin
DominantModifier.WALK_RECOVERY -> "recent walking activity"
```

---

## Other Steps — Optional StatusResolver Usage

Other steps CAN read PatientStatus from metadata for cleaner code, but this is optional and not required for this patch. Example:

```kotlin
// In any step:
val status = state.metadata["patientStatus"] as? PatientStatus
if (status?.bgZone == BgZone.CRITICAL) { ... }
```

Steps that could benefit from this in a future cleanup:

- HypoGuardStep: read bgZone instead of raw BG comparison
- NighttimeSafetyStep: read timeZone instead of hour comparison
- CgmTrendStep: read iobLevel for IOB-aware trend adjustments (future)

For now, these steps continue working with raw comparisons. No changes required.

---

## Edge Cases

1. **Walking 8 min detected:** Below 10-min threshold. `resolveExercisePhase` returns NONE. No recovery. Correct.
2. **Walking 15 min, bolusing 2 hours later:** `hoursSinceLastExercise = 2.0`, `lastExerciseDurationMins = 15`, duration < 30 so window is 1h. 2h > 1h window → NONE. No recovery. Correct.
3. **Walking 40 min, bolusing 1 hour later:** `duration = 40` (30-45 range), window = 2h, hours = 1.0 ≤ 2.0 → WALK_RECOVERY. 20% reduction. Correct.
4. **Walking 60 min, bolusing 2.5 hours later:** `duration = 60` (≥45 range), window = 3h, hours = 2.5 ≤ 3.0 → WALK_RECOVERY. 25% reduction. Correct.
5. **Gym (aerobic) 60 min, bolusing 4 hours later:** Structured exercise, hours = 4 ≤ 6 → EXERCISE_RECOVERY. 50% reduction. Correct.
6. **Gym then walk home, bolusing at dinner:** `lastSport` = walk (most recent). Walk was 20 min, 3 hours ago. Window for 20-min walk = 1h. 3h > 1h → walk recovery expired. BUT gym session also exists from today. The ViewModel takes `maxByOrNull { it.timestamp }` which is the walk, not the gym. **PROBLEM: gym recovery gets lost because walk is more recent.**

**Fix for edge case 6:** The ViewModel should find the most impactful exercise today, not just the most recent. Change the query logic:

```kotlin
// Instead of most recent, find the exercise with longest duration today
// (structured exercise always takes priority over walks)
val lastSport = recentSportLogs
    .sortedWith(compareByDescending<BolusLog> {
        if (it.sportType == "Walking") 0 else 1  // structured exercise first
    }.thenByDescending { it.sportDuration ?: 0f })
    .firstOrNull()
```

This prioritizes structured exercise over walking. If you did both gym and walked home, the gym session is used for recovery calculation, not the walk.

7. **AID user, active exercise:** `mealReductionPercent = 0.0` (no dose modification). Only exercise target advice and carb suggestions. Correct.
8. **AID user, 1h after aerobic:** EXERCISE_RECOVERY, `hoursSinceLastExercise = 1.0 ≤ 2.0` AND sportType = "Aerobic" → shows "extend exercise target" advice. Correct.
9. **AID user, 5h after aerobic:** EXERCISE_RECOVERY, `hoursSinceLastExercise = 5.0 > 2.0` → no exercise target advice, just monitoring warning. Correct.
10. **Multiple walks in a day (10 min + 15 min + 20 min):** ViewModel picks the longest-duration walk. Recovery based on that one. Acceptable simplification.

---

## Acceptance Criteria

- [ ] PatientStatus data class created with BgZone, ExercisePhase, TimeZone, IobLevel
- [ ] StatusResolver resolves all zones correctly from PatientContext
- [ ] PatientStatus stored in metadata at start of ContextualModifierStep
- [ ] `hoursSinceLastExercise`, `lastExerciseSportType`, `lastExerciseDurationMins` added to PatientContext
- [ ] `exercisedToday` changed to computed property (`hoursSinceLastExercise in 0f..24f`)
- [ ] CalculateBolusViewModel populates new fields from most impactful SPORT event today
- [ ] Walking <10 min: no recovery phase
- [ ] Walking 10-30 min: 1h window, 15% flat reduction
- [ ] Walking 30-45 min: 2h window, 20% flat reduction
- [ ] Walking >45 min: 3h window, 25% flat reduction
- [ ] Structured exercise: 6h window, 50% meal + 50% correction (unchanged)
- [ ] AID active exercise: 0% meal reduction (was 30%), advice-only
- [ ] AID active exercise: "Activate Exercise Target" + "bolus for 67-75% of carbs" advice
- [ ] AID recovery: exercise target advice only within 2h of aerobic/mixed
- [ ] AID recovery: carb suggestion if BG 70-100
- [ ] Walking active reduction: 25% (<30min), 35% (30-45min), 43% (>45min)
- [ ] Walking duration modifier (>45min extra) skipped for walking (already in walking tiers)
- [ ] DominantModifier enum includes WALK_RECOVERY
- [ ] Priority: Active > Exercise Recovery > Walk Recovery > Illness > Stress > Heat
- [ ] Gym-then-walk-home edge case: ViewModel picks gym (structured exercise) over walk
- [ ] Non-dominant warnings include WALK_RECOVERY label
- [ ] All existing Phase 1-4 behavior for non-walking scenarios unchanged

## Files to Reference

- `@algorithm/steps/ContextualModifierStep.kt` — main file to modify
- `@algorithm/AlgorithmStep.kt` — CalculationState, BreakdownEntry, metadata
- `@data/models/AlgorithmModels.kt` — PatientContext, TherapyType
- `@viewmodel/CalculateBolusViewModel.kt` — wire new fields
- `@data/dao/BolusLogDao.kt` — add query if needed
- `@data/repository/BolusLogRepository.kt` — add repository method if needed
- `@utils/AlgorithmEngine.kt` — pipeline unchanged, no modifications needed
