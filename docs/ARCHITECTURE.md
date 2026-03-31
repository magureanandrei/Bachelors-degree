# Architecture Reference

## File Tree

```
app/src/main/java/com/example/diabetesapp/
├── MainActivity.kt
├── data/
│   ├── dao/
│   │   └── BolusLogDao.kt
│   ├── database/
│   │   └── BolusDatabase.kt
│   ├── models/
│   │   ├── AlgorithmModels.kt        (PatientContext, ClinicalDecision, TherapyType, CgmTrend)
│   │   ├── BolusLog.kt               (Room entity)
│   │   ├── BolusSettings.kt          (data class + computed flags + TimeSegment)
│   │   └── InsulinType.kt / BasalInsulinType.kt
│   └── repository/
│       ├── BolusLogRepository.kt
│       └── BolusSettingsRepository.kt (SharedPreferences singleton)
├── ui/
│   ├── components/
│   │   ├── BottomNavBar.kt
│   │   ├── CompactLogEntryCard.kt     (Home screen rows)
│   │   ├── ContextFactorSection.kt    (2×2 grid: None/Stress/Illness/Heat)
│   │   ├── CurrentBgWidget.kt
│   │   ├── DoseBreakdownCard.kt       (green rationale card)
│   │   ├── DurationPickerSheet.kt     (ModalBottomSheet number wheels)
│   │   ├── ExpandableSettingsCard.kt  (accordion card)
│   │   ├── IobWidget.kt
│   │   ├── LogDetailsDialog.kt        (modal popup)
│   │   ├── LogEntryCard.kt            (History screen expandable card)
│   │   ├── PostWorkoutVerificationDialog.kt
│   │   ├── SettingsChangeDivider.kt
│   │   ├── SmartBolus.kt              (main bolus input form)
│   │   ├── SmartBolusResultDialog.kt
│   │   └── TimeScaledBgGraph.kt       (24h Canvas graph)
│   ├── screens/
│   │   ├── HomeScreen.kt
│   │   ├── HistoryScreen.kt
│   │   ├── CalculateBolusScreen.kt
│   │   ├── LogReadingScreen.kt
│   │   ├── BolusSettingsScreen.kt
│   │   ├── TherapyProfileScreen.kt
│   │   ├── InsightsScreen.kt          (placeholder)
│   │   ├── EducationScreen.kt         (placeholder)
│   │   └── MenuScreen.kt
│   └── theme/
│       └── DiabetesAppTheme.kt
├── utils/
│   ├── AlgorithmEngine.kt             ⚠️ don't touch unless asked
│   ├── BolusCalculatorHelper.kt
│   ├── CgmHelper.kt                   ⚠️ don't touch unless asked
│   ├── DateTimeUtils.kt
│   ├── FormatUtils.kt
│   ├── GraphDataBuilder.kt
│   ├── HealthConnectHelper.kt         ⚠️ don't touch unless asked
│   ├── HypoPredictionCalculator.kt    ⚠️ don't touch unless asked
│   ├── InsulinEventJoiner.kt
│   ├── IobCalculator.kt               ⚠️ don't touch unless asked
│   ├── WorkoutNotificationManager.kt
│   └── WorkoutProcessor.kt
└── viewmodel/
    ├── BolusSettingsViewModel.kt
    ├── CalculateBolusViewModel.kt
    ├── DashboardViewModel.kt
    ├── LogReadingViewModel.kt
    └── TherapyProfileViewModel.kt
```

---

## ViewModel Wiring Pattern

Every screen wires its ViewModel the same way:

```kotlin
val context = LocalContext.current
val database = remember { BolusDatabase.getDatabase(context) }
val repository = remember { BolusLogRepository(database.bolusLogDao()) }
val settingsRepository = remember { BolusSettingsRepository.getInstance(context) }

val viewModel: XxxViewModel = viewModel(
    factory = XxxViewModelFactory(repository, settingsRepository)
)

val uiState by viewModel.uiState.collectAsState()
val settings by viewModel.settings.collectAsState()
```

Not all ViewModels need both repositories — check the existing ViewModel to see which it takes.

---

## Core Data Model: BolusLog

Room entity (`bolus_log` table, DB version 2). Every event is one row.

```kotlin
BolusLog(
  id: Int (autoGenerate),
  timestamp: Long,
  eventType: String,
  status: String,            // "COMPLETED" | "PLANNED"
  bloodGlucose: Double,
  carbs: Double,
  standardDose: Double,      // raw math result
  suggestedDose: Double,     // after algorithm adjustments
  administeredDose: Double,
  isSportModeActive: Boolean,
  sportType: String?,        // "Aerobic" | "Mixed" | "Anaerobic" | "Walking" | "Running" | "Cycling" | "Swimming"
  sportIntensity: String?,   // "Low" | "Medium" | "High"
  sportDuration: Float?,     // minutes
  notes: String,
  activeAdjustment: String,  // "None" | "Illness" | "Stress" | "Heat"
  adjustmentScale: Float,
  isHighStress: Boolean,
  isIllness: Boolean,
  isExtremeHeat: Boolean,
  clinicalSuggestion: String?
)
```

### Event Types

| Value             | Meaning                               |
| ----------------- | ------------------------------------- |
| `SMART_BOLUS`     | Calculated via CalculateBolusScreen   |
| `MANUAL_INSULIN`  | Manual log via LogReadingScreen       |
| `MANUAL_PEN`      | Pen correction on AID pump            |
| `MEAL`            | Carbs only entry                      |
| `BG_CHECK`        | BG only, no insulin                   |
| `CORRECTION`      | Auto-imported from xDrip/CareLink     |
| `SPORT`           | Exercise event (planned or completed) |
| `BASAL_INSULIN`   | Long-acting insulin dose (MDI users)  |
| `SETTINGS_CHANGE` | Audit log of setting changes          |

### Adding a New Event Type

1. Add `eventType` string constant (document in table above)
2. Handle display in `CompactLogEntryCard`, `LogEntryCard`, `LogDetailsDialog`
3. Handle in `InsulinEventJoiner` if it involves insulin doses

---

## Settings Model: BolusSettings

Stored in SharedPreferences, accessed via `BolusSettingsRepository.getInstance(context)`.

### Key Fields

```kotlin
therapyType: String         // "MDI" | "PUMP_STANDARD" | "PUMP_AID"
glucoseSource: String       // "MANUAL" | "CGM"
insulinType: InsulinType
durationOfAction: Float     // hours (decimal, e.g. 4.5)
targetBG: Float             // mg/dL, typically 100
hypoLimit: Float            // default 70
hyperLimit: Float           // default 180
maxBolus: Float             // safety cap, default 15U

// ICR (Insulin-to-Carb Ratio) — grams per unit
icrMorning/Noon/Evening/Night: Float

// ISF (Insulin Sensitivity Factor) — mg/dL per unit
isfMorning/Noon/Evening/Night: Float

// MDI-only basal tracking
basalInsulinType: BasalInsulinType
basalDurationHours: Float
```

### Computed Flags (always use these instead of string comparison)

```kotlin
settings.isAidPump          // therapyType == "PUMP_AID"
settings.isPumpUser         // PUMP_STANDARD or PUMP_AID
settings.isCgmEnabled       // glucoseSource == "CGM"
settings.isMdi              // therapyType == "MDI"
settings.hasBasalConfigured // MDI + basal type set + duration > 0
settings.therapyTypeEnum    // → TherapyType enum
```

### Adding a New Settings Field

1. Add to `BolusSettings` data class with default value
2. Add SharedPreference key in `BolusSettingsRepository.getSettingsImmediate()` and `saveSettings()`
3. Add to `DraftSettings` and `BolusSettingsViewModel` if user-editable

---

## Time Profiles

The app uses a strict 24-hour array system (`icrProfile` and `isfProfile` in `BolusSettings`) to drive all time-based calculation lookups. Users can configure different insulin-to-carb ratios and insulin sensitivity factors for each specific hour of the day (00:00 to 23:00).

**Usage for calculations:**

- `settings.getIcrForHour(hour)`
- `settings.getIsfForHour(hour)`

_(Note: The legacy `TimeSegment` enum is kept strictly for generalized UI grouping, logging, and display labels (like in `BolusBreakdown`), but it no longer dictates active settings or mathematical values)._

---

## ViewModels Reference

| ViewModel                 | Screen         | Key StateFlows                                                                                                                            |
| ------------------------- | -------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `DashboardViewModel`      | Home, History  | `allLogs`, `settings`, `latestReading`, `graphEvents`, `cgmReadings`, `iobResult`, `hypoPrediction`, `unverifiedWorkout`, `historyEvents` |
| `CalculateBolusViewModel` | CalculateBolus | `inputState: BolusInputState`, `settings`                                                                                                 |
| `LogReadingViewModel`     | LogReading     | `uiState: LogReadingState`, `settings`                                                                                                    |
| `BolusSettingsViewModel`  | BolusSettings  | `uiState: BolusSettingsUiState` (draft/persisted split)                                                                                   |
| `TherapyProfileViewModel` | TherapyProfile | `uiState: TherapyProfileState`                                                                                                            |

### DashboardViewModel fetch modes

```
fetchDashboardData() branches:
  isAidPump + isCgmEnabled → fetchAidCgmMode()  (xDrip latest + history + treatments)
  isCgmEnabled → fetchCgmOnlyMode()              (xDrip latest + history)
  else → fetchManualMode()                        (local DB only)
```

Polling: Home screen polls `fetchDashboardData()` every 1 minute while active.

---

## Key UI Components

| Component                       | Purpose                                           |
| ------------------------------- | ------------------------------------------------- |
| `ExpandableSettingsCard`        | Accordion card with animated arrow                |
| `SmartBolus`                    | Main bolus input form (BG, carbs, sport, factors) |
| `SmartBolusResultDialog`        | Shows dose result with ±0.1U adjustment buttons   |
| `ContextFactorSection`          | 2×2 grid: None/Stress/Illness/Heat                |
| `DoseBreakdownCard`             | Shows algorithm rationale (green card)            |
| `CurrentBgWidget`               | BG display — different layout for CGM vs manual   |
| `IobWidget`                     | IOB display with therapy-aware status labels      |
| `TimeScaledBgGraph`             | 24h scrollable Canvas graph                       |
| `CompactLogEntryCard`           | Home screen history row (tap → modal)             |
| `LogEntryCard`                  | History screen expandable card with delete        |
| `LogDetailsDialog`              | Modal popup with full entry details               |
| `PostWorkoutVerificationDialog` | Workout completion confirmation                   |
| `DurationPickerSheet`           | ModalBottomSheet with NumberPicker wheels         |
| `BottomNavBar`                  | 5-tab navigation bar                              |
| `SettingsChangeDivider`         | Inline divider for SETTINGS_CHANGE events         |

---

## Screens: Current State

| Screen               | Status                                                         |
| -------------------- | -------------------------------------------------------------- |
| HomeScreen           | ✅ Full — graph, IOB, BG widget, 24h log, workout verification |
| HistoryScreen        | ✅ Full — grouped by day, expandable cards, delete             |
| CalculateBolusScreen | ✅ Full — SmartBolus form + result dialog                      |
| LogReadingScreen     | ✅ Full — Diet/Sport tabs, time picker, insights dialog        |
| BolusSettingsScreen  | ✅ Full — ICR/ISF time-dependent, duration picker              |
| TherapyProfileScreen | ✅ Full — MDI/Pump/AID + CGM/Manual                            |
| InsightsScreen       | ⏳ Placeholder — "Personalized trends coming soon"             |
| EducationScreen      | ⏳ Placeholder — "ISPAD & T1DEXIP rules will go here"          |
| MenuScreen           | ✅ Navigation hub + placeholder CGM/Wearables cards            |

---

## Common Task Checklists

### Adding a New Modal/Dialog to an Existing Screen

1. Create the dialog composable in `ui/components/XxxDialog.kt`
   - Signature: `@Composable fun XxxDialog(onDismiss: () -> Unit, ...relevant data params...)`
   - Use `AlertDialog` or `ModalBottomSheet` — match existing patterns
2. In the host screen (e.g., `HomeScreen.kt`):
   - Add state: `var showXxxDialog by remember { mutableStateOf(false) }`
   - Add trigger (button tap, card tap, condition met)
   - Add the composable at the bottom of the screen body:

```kotlin
     if (showXxxDialog) {
         XxxDialog(onDismiss = { showXxxDialog = false }, ...)
     }
```

3. If the dialog needs data from the ViewModel:
   - Pass it as params from the screen (don't wire a ViewModel inside the dialog)
   - If it needs to trigger an action (save, update), pass a callback lambda
4. If the dialog shows algorithm output, include `clinicalRationale` / `DoseBreakdownCard`

Reference patterns:

- Simple info modal: `LogDetailsDialog`
- Interactive modal with save: `SmartBolusResultDialog`
- Bottom sheet with pickers: `DurationPickerSheet`
- Confirmation with editable fields: `PostWorkoutVerificationDialog`

### Modifying Algorithm Behavior + Updating Rationale

1. In `AlgorithmEngine.kt`:
   - Locate the correct calculation stage (see `docs/ALGORITHM.md` for order)
   - Add new logic in the right position — order matters
   - Append to `logBuilder` with emoji + clear explanation text
2. If the new logic needs new input data:
   - Add field to `PatientContext` in `AlgorithmModels.kt` (with default value)
   - Wire the new field where `PatientContext` is constructed:
     - `CalculateBolusViewModel.kt` (for SmartBolus calculations)
     - `DashboardViewModel.verifyAndCompleteWorkout()` (for post-workout insights)
3. If the new logic affects dose display:
   - Verify `DoseBreakdownCard` renders the new rationale text correctly
   - Check `SmartBolusResultDialog` still shows accurate dose breakdown
4. If adding a new context factor (like illness/stress/heat):
   - Add UI toggle in `ContextFactorSection` or `SmartBolus`
   - Add field to `BolusInputState` in `CalculateBolusViewModel`
   - Add field to `BolusLog` entity (bump DB version, keep `fallbackToDestructiveMigration`)
   - Pass through to `PatientContext` construction
5. Always: update `clinicalRationale` string to explain the new behavior — transparency is non-negotiable

### Adding a New Screen (rare — keep for reference)

1. Create `ui/screens/XxxScreen.kt`
   - Signature: `@Composable fun XxxScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit = {})`
   - Wire ViewModel using the standard pattern (see ViewModel Wiring Pattern above)
2. Create `viewmodel/XxxViewModel.kt` + Factory if needed
3. In `MainActivity.kt`:
   - Add route string to the `when (currentScreen)` block
   - If tab screen: add to the `listOf("home", "history", ...)` checks (3 places in file)
   - If detail screen: pass `onNavigateBack = navigateBack`
4. Add navigation callback in the calling screen (e.g., `MenuScreen` gets `onNavigateToXxx`)
5. If tabbed: add `BottomNavItem` entry in `BottomNavBar.kt`

---

## Build Config

```kotlin
minSdk = 28
targetSdk = 36
jvmTarget = "11"
Room version = 2.6.1 (KSP compiler)
Health Connect = 1.2.0-alpha02
```

Permissions: `INTERNET` (xDrip HTTP), `SCHEDULE_EXACT_ALARM` + `USE_EXACT_ALARM` (workout notifications), `health.READ_EXERCISE`, `health.READ_STEPS`
