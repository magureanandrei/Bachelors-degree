# Algorithm Engine Reference

Read this only when the task involves AlgorithmEngine, PatientContext, ClinicalDecision, or the dose calculation logic.

## Entry Point

`AlgorithmEngine.calculateClinicalAdvice(context: PatientContext): ClinicalDecision`

## Calculation Order (don't reorder)

1. **Baseline** = Carb bolus (`carbs / ICR`) + Correction (`(BG - target) / ISF`)
2. **Outside Factors** — multiply baseline:
   - Illness: +25%
   - Stress: +15%
   - Heat: -10%
   - Illness and Stress are `else-if` — no double stacking
   - Heat is independent (affects absorption, not resistance)
3. **IOB deduction** — subtract active insulin, floor at 0
4. **CGM Trend modifiers** (only if `hasCGM` and trend ≠ NONE):
   - DoubleUp ↑↑: +20%
   - SingleDown ↓: -20%
   - DoubleDown ↓↓: halve dose + suggest 15g rescue carbs if BG < 120
5. **Sport reductions** (if `isDoingSport`):
   - Anaerobic: -10%
   - Mixed Low/Med/High: -15/-25/-40%
   - Aerobic Low/Med/High: -25/-50/-75%
   - Duration >45min adds extra reduction (up to +20% more)
   - Cap: max 90% total reduction
6. **Rescue carb suggestions** if BG low + sport imminent
7. **Post-sport late hypoglycemia warning** (7–11h window after exercise)

## Input: PatientContext

```kotlin
PatientContext(
    therapyType: TherapyType,
    bolusSettings: BolusSettings,
    currentBG: Double,
    hasCGM: Boolean,
    cgmTrend: CgmTrend,          // DOUBLE_UP, SINGLE_UP, FLAT, SINGLE_DOWN, DOUBLE_DOWN, NONE
    activeInsulinIOB: Double,
    plannedCarbs: Double,
    isDoingSport: Boolean,
    sportType: String,            // "Aerobic", "Anaerobic", "Mixed"
    sportIntensity: Int,          // 1=Low, 2=Medium, 3=High
    sportDurationMins: Int,
    minutesUntilSport: Int,       // positive=future, negative=past
    isCompetitiveEvent: Boolean,
    timeOfDay: LocalTime,
    isHighStress: Boolean,
    isIllness: Boolean,
    isExtremeHeat: Boolean,
    dailySteps: Long,
    basalDoseToday: Double,
    basalDurationHours: Float,
    hasBasalConfigured: Boolean
)
```

## Output: ClinicalDecision

```kotlin
ClinicalDecision(
    suggestedInsulinDose: Double,
    suggestedRescueCarbs: Int,
    clinicalRationale: String      // explanation text for UI + history
)
```

## Key Rules
- When adding new logic, always update `clinicalRationale` text to explain it
- Safety-first: when uncertain, suggest lower insulin, more rescue carbs
- Therapy-aware: MDI users can't suspend basal → need more proactive carb suggestions
- AID users get reduced corrections (pump auto-adjusts)