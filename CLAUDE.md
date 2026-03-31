# Diabetes Manager App

Android (Kotlin/Compose) thesis prototype for Type 1 Diabetes management.
Package: `com.example.diabetesapp` | Min SDK 28 | Target SDK 36
MVVM + Repository | Jetpack Compose (no XML) | Room DB v2 | Health Connect

## Task Behavior

- Read files before modifying. Never guess at code structure.
- Don't touch files marked ⚠️ unless explicitly asked.
- Use computed flags (`settings.isAidPump`, `settings.isMdi`, `settings.isCgmEnabled`) — never compare therapyType strings directly.
- `fallbackToDestructiveMigration()` is used — DB schema changes wipe data.

## Architecture

```
UI Screens → ViewModels → Repositories → (Room DAO | SharedPreferences | CgmHelper)
```

- Navigation: manual back-stack in `MainActivity.kt` (no Jetpack Navigation)
- Settings: `BolusSettingsRepository.getInstance(context)` (SharedPreferences singleton)
- Database: `BolusDatabase.getDatabase(context)` → `BolusLogRepository(db.bolusLogDao())`

## Routes (MainActivity.kt)

Tab screens (bottom bar): `home`, `history`, `stats`, `education`, `menu`
Detail screens (no bottom bar): `calculate_bolus`, `log_reading`, `bolus_settings`, `therapy_profile`

## ⚠️ Don't Touch Unless Asked

`AlgorithmEngine.kt`, `CgmHelper.kt`, `HealthConnectHelper.kt`, `HypoPredictionCalculator.kt`, `IobCalculator.kt`

## Brand Colors

Primary: `#00897B` | Dark: `#00695C` | Deepest: `#004D40` | Background: `#F5F5F5` | Cards: White
Sport completed: `#4DB6AC` | Sport planned: `#FF9800` | Hypo: `#E53935` | Hyper: `#FFB74D` | Basal: `#2E7D32`

## Reference Docs (read only when the task involves that area)

- `@docs/ARCHITECTURE.md` — file tree, data models, settings, event types, UI components, screen checklists
- `@docs/ALGORITHM.md` — AlgorithmEngine calculation order, PatientContext, ClinicalDecision
- `@docs/CGM_AND_IOB.md` — CgmHelper endpoints, IobCalculator modes, trend mapping
- `@docs/HEALTH_CONNECT.md` — HealthConnectHelper, WorkoutProcessor, walk detection, verification flow
- `@docs/GRAPH.md` — TimeScaledBgGraph modes, swimlanes, hypo prediction rendering
- `@docs/VISION.md` — product goals, design principles, future direction

## Feature Specs

When a `docs/features/xxx.md` file exists, read it for the full spec before implementing.
Delete or move to `docs/features/completed/` after implementation is done.
