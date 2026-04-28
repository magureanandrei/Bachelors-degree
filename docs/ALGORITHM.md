# Algorithm Engine Reference

Read this only when the task involves AlgorithmEngine, PatientContext, ClinicalDecision, or the dose calculation logic.

## Entry Point

`AlgorithmEngine.calculateClinicalAdvice(context: PatientContext): ClinicalDecision`

## Architecture

Modular pipeline: `AlgorithmPipeline` folds `List<AlgorithmStep>` over `CalculationState`. Each step returns a new state. `AlgorithmEngine` is a thin orchestrator.

## Pipeline Order (don't reorder without clinical review)

```
0. HypoGuardStep         — BG < hypoLimit → warn + rescue carbs. BG < 54 → severe warning
1. BaselineStep           — Meal (carbs/ICR) + Correction ((BG-target)/ISF)
                            AID: skip correction entirely (pump auto-corrects >120 mg/dL)
                            AID BG>250: warn about infusion set, still no correction
2. OutsideFactorsStep     — Illness +25%, Stress +15% (else-if), Heat -10% (independent)
3. IobDeductionStep       — Subtract IOB, floor at 0
4. CgmTrendStep           — MDI/Pump: DoubleUp +20%, SingleDown -20%, DoubleDown halve+15g carbs
                            AID: skip dose adjustments, keep carb suggestions for downward trends
5. SportModifierStep      — Reduction matrix (Aer 25/50/75%, Mix 15/25/40%, Ana 10%)
                            Duration >45min: up to +20% extra. Cap 90%
                            Section C: therapy-specific advice (MDI→carbs, Pump→temp basal, AID→exercise target)
                            Section D: post-sport late hypo warning (7-11h window)
6. BasalAwarenessStep     — MDI only. Warn if no basal logged. Sport+basal → remind can't suspend
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

- Phase 3: Nighttime safety (21:00-06:00 correction reduction)
- Phase 4: Sport remodel (walking category, meal/correction split, Zivkovic 2026)
- Phase 5: CGM trend redesign (ISF-unit-based per Aleppo/Laffel 2017)
