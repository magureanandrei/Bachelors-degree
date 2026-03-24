package com.example.diabetesapp.utils

import android.util.Log
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.repository.BolusLogRepository
import java.time.temporal.ChronoUnit
import java.util.Calendar

object WorkoutProcessor {

    suspend fun process(
        records: List<ExerciseSessionRecord>,
        detectedWalks: List<BolusLog>,
        helper: HealthConnectHelper,
        dayStart: Long
    ): List<BolusLog> {

        // 1. Map Strava ExerciseSessionRecords → BolusLog, drop walking + old entries
        val stravaActivities = records.mapNotNull { record ->
            val startMs = record.startTime.toEpochMilli()
            if (startMs < dayStart) return@mapNotNull null

            val durationMinutes = ChronoUnit.MINUTES.between(
                record.startTime, record.endTime
            ).toFloat()

            val sportType = when (record.exerciseType) {
                ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> return@mapNotNull null
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
                ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
                else -> "Workout"
            }

            val avgHr = helper.getAverageHeartRateForSession(record.startTime, record.endTime)
            val intensity = when {
                avgHr == null -> "Medium"
                avgHr >= 160  -> "High"
                avgHr >= 130  -> "Medium"
                else          -> "Low"
            }

            Log.d("WorkoutProcessor", "$sportType: avgHR=${avgHr?.toInt()}, intensity=$intensity")

            BolusLog(
                id = 0,
                timestamp = startMs,
                eventType = "SPORT",
                status = "COMPLETED",
                bloodGlucose = 0.0,
                carbs = 0.0,
                standardDose = 0.0,
                suggestedDose = 0.0,
                administeredDose = 0.0,
                isSportModeActive = true,
                sportType = sportType,
                sportIntensity = intensity,
                sportDuration = durationMinutes,
                notes = "Auto-imported from Strava",
                clinicalSuggestion = null
            )
        }

        // 2. Filter walks to today, suppress those overlapped by Strava
        val todayWalks = detectedWalks.filter { it.timestamp >= dayStart }
        val filteredWalks = suppressOverlappedWalks(todayWalks, stravaActivities)

        // 3. Merge walks within 15-min gap
        val mergedWalks = mergeWalkSessions(filteredWalks)

        return (stravaActivities + mergedWalks).sortedBy { it.timestamp }
    }

    suspend fun persistNew(
        allWorkouts: List<BolusLog>,
        existingLogs: List<BolusLog>,
        repository: BolusLogRepository,
        stepRecords: List<StepsRecord> = emptyList()
    ) {
        allWorkouts.forEach { workout ->
            if (workout.sportType == "Walking" && workout.notes.startsWith("Auto-detected")) {
                val workoutEnd = workout.timestamp + (workout.sportDuration!! * 60 * 1000L).toLong()

                // Re-sum steps for this walk's full time window
                val freshSteps = stepRecords
                    .filter { it.startTime.toEpochMilli() >= workout.timestamp - 60 * 1000L
                            && it.endTime.toEpochMilli() <= workoutEnd + 60 * 1000L }
                    .sumOf { it.count }

                val updatedNotes = if (freshSteps > 0)
                    "Auto-detected via steps (~$freshSteps steps)"
                else workout.notes

                // Skip if steps are suspiciously low
                if (freshSteps in 1..399) {
                    Log.d("WorkoutProcessor", "Skipping low-step walk: $freshSteps steps")
                    // Also clean up if already persisted
                    existingLogs.filter { existing ->
                        existing.isSportModeActive &&
                                existing.sportType == "Walking" &&
                                existing.timestamp >= workout.timestamp - 60 * 1000L &&
                                existing.timestamp <= workoutEnd
                    }.forEach { repository.delete(it) }
                    return@forEach
                }

                // Remove stale duplicates and re-insert with fresh steps
                existingLogs.filter { existing ->
                    existing.isSportModeActive &&
                            existing.sportType == "Walking" &&
                            existing.timestamp >= workout.timestamp - 60 * 1000L &&
                            existing.timestamp <= workoutEnd
                }.forEach { repository.delete(it) }

                repository.insert(workout.copy(notes = updatedNotes))

            } else {
                val alreadySaved = existingLogs.any { existing ->
                    existing.isSportModeActive &&
                            Math.abs(existing.timestamp - workout.timestamp) <= 2 * 60 * 1000L &&
                            existing.sportType == workout.sportType
                }
                if (!alreadySaved) {
                    repository.insert(workout)
                }
            }
        }
    }

    private fun suppressOverlappedWalks(
        walks: List<BolusLog>,
        stravaActivities: List<BolusLog>
    ): List<BolusLog> {
        return walks.filter { walk ->
            val walkStart = walk.timestamp
            val walkEnd = walkStart + (walk.sportDuration!! * 60 * 1000L).toLong()
            val walkDuration = walkEnd - walkStart
            stravaActivities.none { activity ->
                val actStart = activity.timestamp
                val actEnd = actStart + (activity.sportDuration!! * 60 * 1000L).toLong()
                val overlapStart = maxOf(walkStart, actStart)
                val overlapEnd = minOf(walkEnd, actEnd)
                val overlapMs = (overlapEnd - overlapStart).coerceAtLeast(0L)
                overlapMs > walkDuration * 0.5
            }
        }
    }

    private fun mergeWalkSessions(walks: List<BolusLog>): List<BolusLog> {
        val merged = mutableListOf<BolusLog>()
        val sorted = walks.sortedBy { it.timestamp }

        sorted.forEach { walk ->
            val last = merged.lastOrNull()
            if (last != null) {
                val lastEnd = last.timestamp + (last.sportDuration!! * 60 * 1000L).toLong()
                val gapMs = walk.timestamp - lastEnd
                if (gapMs <= 15 * 60 * 1000L && last.sportType == walk.sportType) {
                    val newDuration = ((walk.timestamp + (walk.sportDuration!! * 60 * 1000L).toLong()
                            - last.timestamp) / 60000f)
                    merged[merged.lastIndex] = last.copy(sportDuration = newDuration)
                } else {
                    merged.add(walk)
                }
            } else {
                merged.add(walk)
            }
        }
        return merged
    }

    fun checkForPendingWorkout(logs: List<BolusLog>): BolusLog? {
        val now = System.currentTimeMillis()
        return logs.firstOrNull { log ->
            log.status == "PLANNED" && log.isSportModeActive &&
                    (log.timestamp + ((log.sportDuration ?: 0f) * 60 * 1000L)) < now
        }
    }

    fun buildVerifiedLog(
        log: BolusLog,
        actualDuration: Float,
        actualIntensity: Float,
        sportType: String,
        actualStartTimeStr: String,
        clinicalRationale: String
    ): BolusLog {
        val calendar = Calendar.getInstance().apply { timeInMillis = log.timestamp }
        try {
            val parts = actualStartTimeStr.split(":")
            if (parts.size == 2) {
                calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                calendar.set(Calendar.MINUTE, parts[1].toInt())
            }
        } catch (_: Exception) { }

        val combinedInsight = if (log.clinicalSuggestion.isNullOrBlank()) clinicalRationale
        else "${log.clinicalSuggestion}\n\n$clinicalRationale"

        return log.copy(
            timestamp = calendar.timeInMillis,
            status = "COMPLETED",
            sportDuration = actualDuration,
            sportIntensity = when (actualIntensity.toInt()) {
                1 -> "Low"; 2 -> "Medium"; 3 -> "High"; else -> "Medium"
            },
            sportType = sportType,
            clinicalSuggestion = combinedInsight
        )
    }
}