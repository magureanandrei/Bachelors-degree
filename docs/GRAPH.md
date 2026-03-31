# Graph Reference

Read this only when the task involves TimeScaledBgGraph, graph rendering, or swimlane display.

## TimeScaledBgGraph.kt

Scrollable Canvas-based 24h blood glucose graph.

### Three Modes (GraphMode enum)

| Mode       | Source                         | Features                                             |
| ---------- | ------------------------------ | ---------------------------------------------------- |
| `AID_CGM`  | CGM line + CareLink treatments | Full: CGM line, treatments, hypo prediction          |
| `CGM_ONLY` | CGM line only                  | CGM line + hypo prediction, no treatments            |
| `MANUAL`   | Manual BG dots                 | Square markers only, no CGM line, no hypo prediction |

Mode is resolved by `GraphDataBuilder.resolveGraphMode(isCgmEnabled, isAidPump)`.

### Axes

- X-axis: 24h window + 2.5h future buffer
- Y-axis: 40–350 mg/dL fixed range
- Auto-scrolls to "now minus 1h" on load

### Safety Lines

- Hypo limit (red dashed): `settings.hypoLimit` (default 70)
- Target BG (green solid): `settings.targetBG` (default 100)
- Hyper limit (amber dashed): `settings.hyperLimit` (default 180)
- Target range shaded green between hypo and hyper

### Swimlanes (bottom of graph)

- Row 1 (`carbsY`): Meal events — pink 🍽️ icon
- Row 2 (`insulinY`): Bolus (blue circle), SmartBolus (orange circle), Basal (green square)

### Sport Blocks

- Completed sport: teal fill (`#4DB6AC`)
- Planned sport: orange dashed border (`#FF9800`)
- Auto-detected walks: grey fill

### Hypo Prediction

- Dashed red line fading out into the future
- Only shown for CGM modes (`AID_CGM`, `CGM_ONLY`)
- Data comes from `HypoPredictionCalculator`

### Graph Data Pipeline

```
DashboardViewModel.fetchDashboardData()
  → GraphDataBuilder.buildGraphEvents(localLogs, xdripTreatments, hcWorkouts)
  → GraphDataBuilder.filterForDay(events, dayStart)
  → passed to TimeScaledBgGraph composable
```

### Key Parameters

```kotlin
TimeScaledBgGraph(
    logs: List<BolusLog>,
    cgmReadings: List<CgmReading>,
    dayStartTimestamp: Long,
    endTimestamp: Long,
    targetBg: Float,
    hypoLimit: Float,
    hyperLimit: Float,
    graphMode: GraphMode,
    settings: BolusSettings,
    hypoPrediction: HypoPrediction?,
    graphHeightDp: Dp,
    modifier: Modifier
)
```
