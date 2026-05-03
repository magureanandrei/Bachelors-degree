# Algorithm Engine Reference

Read this only when the task involves AlgorithmEngine, PatientContext, ClinicalDecision, or the dose calculation logic.

## Entry Point

`AlgorithmEngine.calculateClinicalAdvice(context: PatientContext): ClinicalDecision`

## Architecture

Modular pipeline: `AlgorithmPipeline` folds `List<AlgorithmStep>` over `CalculationState`. Each step returns a new state. `AlgorithmEngine` is a thin orchestrator.

## Pipeline Order (don't reorder without clinical review)

```
0. HypoGuardStep         — BG < hypoLimit → warn + rescue carbs. BG < 54 → severe.
                            Sets metadata["hypoReservedCarbs"] to absorb entered carbs into rescue.
1. BaselineStep           — Meal (effectiveCarbs/ICR) + Correction ((BG-target)/ISF)
                            AID: no correction (pump auto-corrects). Reads hypoReservedCarbs to reduce effective carbs.
2. CgmTrendStep           — AID: carb suggestions only. MDI/Pump: ISF-unit adjustments (Aleppo 2017)
3. ContextualModifier     — ONE dominant modifier: Exercise > Recovery > Illness > Stress > Heat
                            AID: illness/stress/heat generate warnings only, no dose change
4. NighttimeSafetyStep   — Bedtime warnings. AID: higher threshold (90 vs 120), no rescueCarbs injection
5. IobDeductionStep       — Subtract IOB after all modifiers, floor at 0
6. BasalAwarenessStep     — MDI only: warn if no basal logged
7. MaxBolusCapStep        — Clamp to settings.maxBolus
```

## CalculationState

```kotlin
CalculationState(
    currentDose: Double, rescueCarbs: Int,
    breakdown: List<BreakdownEntry>, warnings: List<String>,
    metadata: Map<String, Any>  // inter-step data: "mealBolus", "correctionBolus"
)
```

## BreakdownEntry

```kotlin
BreakdownEntry(stepName, label, emoji, description, effect: Effect,
    valueChange: Double?, percentChange: Double?, runningTotal: Double)
// Effect: INCREASE, DECREASE, NEUTRAL, WARNING
```

## PatientContext

```kotlin
PatientContext(
    therapyType: TherapyType, bolusSettings: BolusSettings,
    currentBG: Double, hasCGM: Boolean, cgmTrend: CgmTrend,
    activeInsulinIOB: Double, plannedCarbs: Double,
    isDoingSport: Boolean, sportType: String, sportIntensity: Int,
    sportDurationMins: Int, minutesUntilSport: Int,
    timeOfDay: LocalTime,
    isHighStress: Boolean, isIllness: Boolean, isExtremeHeat: Boolean,
    dailySteps: Long, basalDoseToday: Double,
    basalDurationHours: Float, hasBasalConfigured: Boolean
)
```

## ClinicalDecision

```kotlin
ClinicalDecision(
    suggestedInsulinDose: Double, suggestedRescueCarbs: Int,
    clinicalRationale: String,  // backward-compat flat string
    breakdownSteps: List<BreakdownEntry>  // structured data
)
```

## Key Rules

- Use `settings.isAidPump`, `settings.isMdi`, `settings.isPumpUser` — never compare therapyType strings
- Safety-first: less insulin > more insulin when uncertain
- Every step adds BreakdownEntry explaining what it did
- `clinicalRationale` is auto-built from breakdown entries by AlgorithmPipeline

## Planned Future Phases

- Phase 6: basalDoseToday sum from DB (currently hardcoded 0.0 in CalculateBolusViewModel)
