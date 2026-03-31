# CGM & IOB Reference

Read this only when the task involves CgmHelper, IobCalculator, xDrip integration, or CGM trend handling.

## CgmHelper.kt

Connects to **xDrip+** via local HTTP on `127.0.0.1:17580`.

| Function                   | Endpoint              | Returns                                    |
| -------------------------- | --------------------- | ------------------------------------------ |
| `getLatestBgFromXDrip()`   | `/pebble`             | Single `CgmReading` (BG, trend arrow, IOB) |
| `getBgHistoryFromXDrip()`  | `/sgv.json?count=320` | `List<CgmReading>` sorted oldest→newest    |
| `getTreatmentsFromXDrip()` | `/treatments.json`    | `List<BolusLog>` (CareLink sync)           |

### CgmReading

```kotlin
CgmReading(timestamp, bgValue, carbs, insulin, trendString, iob)
```

### Trend Arrow Mapping

DoubleUp→"↑↑", SingleUp→"↑", FortyFiveUp→"↗", Flat→"→", FortyFiveDown→"↘", SingleDown→"↓", DoubleDown→"↓↓"

### CareLink Treatment Filtering

- Keep: MEAL (carbs > 0), CALIBRATION, CORRECTION (dose ≥ 1.5U only)
- Drop: microboluses (< 1.5U auto-corrections from pump)

---

## IobCalculator.kt

Linear decay model: `fraction = 1 - (minutesAgo / durationMinutes)`

### Three Modes (based on xDrip data freshness)

| Mode     | Condition               | Calculation                                      |
| -------- | ----------------------- | ------------------------------------------------ |
| Fresh    | xDrip IOB ≤15 min old   | `xdripIob + manualIob`                           |
| Stale    | xDrip IOB 15–60 min old | Decay last known xDrip IOB forward + `manualIob` |
| No xDrip | No xDrip data available | Calculate from DB history only                   |

### IobResult

```kotlin
IobResult(
    totalIob: Double,
    fromManualDoses: Double,
    fromPump: Double?,
    hasManualOnTopOfPump: Boolean,  // warning flag
    isEstimated: Boolean,
    minutesSinceLastReading: Int
)
```

### Key Rules

- `BASAL_INSULIN` events are **excluded** from IOB calculation (long-acting insulin not tracked in decay model)
- Duration of action comes from `settings.durationOfAction` (in hours)
- Only events with `administeredDose > 0` are considered

---

## HypoPredictionCalculator.kt

Weighted quadratic regression on last 8 CGM readings (recency-weighted).

Prerequisites before predicting:

- ≥4 readings available
- Last reading ≤15 min old
- Last 3 readings actually trending down (slope ≤ -0.3 mg/dL/min)

Projects up to 90 minutes forward. Only returns prediction if hypo hit within 60 minutes.
