# Health Connect & Workout Reference

Read this only when the task involves HealthConnectHelper, WorkoutProcessor, walk detection, or workout verification.

## Permissions

`ExerciseSessionRecord`, `StepsRecord`, `HeartRateRecord`

## Pipeline (DashboardViewModel.fetchRecentWorkouts)

1. `HealthConnectHelper.getRawStepRecords()` → detect walking via step cadence
2. `HealthConnectHelper.getRecentWorkouts()` → Strava/HC exercise sessions
3. `WorkoutProcessor.process()` → merge, dedupe, suppress overlapping walks
4. `WorkoutProcessor.persistNew()` → save to Room DB as `SPORT` events

## Walk Detection Thresholds

- Minimum 7 minutes duration
- Minimum 400 steps
- Minimum 40 avg steps/min
- ≥55 steps/min cadence to qualify as walking
- Maximum 10-min gap between step records to merge into one session

## WorkoutProcessor.process()

- Maps Strava `ExerciseSessionRecord` → `BolusLog` with `eventType = "SPORT"`
- Sport type mapping: RUNNING→"Running", BIKING→"Cycling", SWIMMING→"Swimming", WALKING→skipped (handled separately)
- Intensity from avg heart rate: ≥160 High, ≥130 Medium, else Low
- Suppresses walks that overlap with Strava activities
- Merges walk sessions within 15-min gap

## WorkoutProcessor.persistNew()

- Deduplicates against existing DB entries (2-min timestamp window + same sport type)
- Walks with <400 steps are cleaned up (deleted if already persisted)
- Re-sums steps from raw records for accurate step counts

## Workout Verification Flow

When a `PLANNED` sport event's end time passes:

1. `WorkoutProcessor.checkForPendingWorkout()` detects it
2. `DashboardViewModel.unverifiedWorkout` emits the log
3. `PostWorkoutVerificationDialog` shows — user confirms or edits duration/intensity/type/start time
4. `WorkoutProcessor.buildVerifiedLog()` creates updated log
5. Post-workout `AlgorithmEngine` call generates late-hypo insights
6. `repository.update()` saves the verified log

## Basal Insulin Tracking (MDI Only)

- Shown in `LogReadingScreen` when `settings.isMdi == true`
- Saved as `BASAL_INSULIN` event type
- `InsulinEventJoiner.joinForHistory()` pairs basal events with nearby bolus events (1h window)
- Unconsumed basals shown as standalone cards
- **Excluded from IOB** — long-acting insulin not tracked in decay model
