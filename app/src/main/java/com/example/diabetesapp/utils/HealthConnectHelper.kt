package com.example.diabetesapp.utils

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.diabetesapp.data.models.BolusLog
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectHelper(private val client: HealthConnectClient) {

    suspend fun getRecentWorkouts(): List<ExerciseSessionRecord> {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)
        Log.d("HC_Workouts", "--> Querying Health Connect (ExerciseSessionRecord) from $startTime to $endTime")

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            Log.d("HC_Workouts", "--> Found ${response.records.size} ExerciseSessionRecord(s)")
            response.records.forEach { record ->
                Log.d("HC_Workouts", "    - type=${record.exerciseType} start=${record.startTime} end=${record.endTime} origin=${record.metadata.dataOrigin}")
            }
            response.records
        } catch (e: Exception) {
            Log.e("HC_Workouts", "--> FAILED to read records: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getRawStepRecords(): List<StepsRecord> {
        val endTime = Instant.now()
        val startTime = endTime.minus(1, ChronoUnit.DAYS)
        return try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            ).records
        } catch (e: Exception) {
            Log.e("HC_Steps", "Failed to read step records: ${e.message}")
            emptyList()
        }
    }

    suspend fun getDailySteps(): Long {
        val endTime = Instant.now()
        val startTime = endTime.minus(1, ChronoUnit.DAYS)
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val total = response.records.sumOf { it.count }
            Log.d("HC_Steps", "Daily steps: $total")
            total
        } catch (e: Exception) {
            Log.e("HC_Steps", "Failed to read steps: ${e.message}")
            0L
        }
    }

    suspend fun getAverageHeartRateForSession(
        startTime: Instant,
        endTime: Instant
    ): Double? {
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val allSamples = response.records.flatMap { it.samples }
            if (allSamples.isEmpty()) null
            else allSamples.map { it.beatsPerMinute }.average()
        } catch (e: Exception) {
            Log.e("HC_HR", "Failed to read heart rate: ${e.message}")
            null
        }
    }

    suspend fun detectWalkingFromSteps(stepRecords: List<StepsRecord>): List<BolusLog> {
        return try {
            Log.d("HC_Steps", "Raw step buckets: ${stepRecords.size}")

            val walkingSessions = mutableListOf<BolusLog>()
            var sessionStart: Instant? = null
            var sessionSteps = 0L
            var lastEnd: Instant? = null

            val minSessionMinutes = 7L
            val walkingStepsPerMin = 55.0
            val maxGapMinutes = 10L

            stepRecords.sortedBy { it.startTime }.forEach { record ->
                val durationSeconds = ChronoUnit.SECONDS.between(
                    record.startTime, record.endTime
                )
                val durationMinutes = durationSeconds / 60.0
                val stepsPerMinute = if (durationMinutes > 0)
                    record.count / durationMinutes else 0.0

                val isWalking = stepsPerMinute >= walkingStepsPerMin

                if (isWalking) {
                    val gapFromLast = if (lastEnd != null)
                        ChronoUnit.MINUTES.between(lastEnd, record.startTime)
                    else Long.MAX_VALUE

                    if (sessionStart == null || gapFromLast > maxGapMinutes) {
                        // Save previous session if long enough
                        if (sessionStart != null && lastEnd != null) {
                            val sessionDurationMins = ChronoUnit.MINUTES.between(sessionStart, lastEnd).toDouble()
                            val avgStepsPerMin = if (sessionDurationMins > 0) sessionSteps / sessionDurationMins else 0.0

                            if (sessionDurationMins >= minSessionMinutes
                                && sessionSteps >= 400L
                                && avgStepsPerMin >= 40.0) {
                                walkingSessions.add(buildWalkLog(sessionStart!!, lastEnd!!, sessionSteps))
                            } else {
                                Log.d("HC_Steps", "Discarding: ${sessionDurationMins.toInt()}min, $sessionSteps steps, ${avgStepsPerMin.toInt()} avg spm")
                            }
                        }
                        // Start new session
                        sessionStart = record.startTime
                        sessionSteps = record.count
                    } else {
                        // Continue current session
                        sessionSteps += record.count
                    }
                    lastEnd = record.endTime
                } else {
                    // Not walking — end session if gap is too large
                    if (lastEnd != null && sessionStart != null) {
                        val gapFromLast = ChronoUnit.MINUTES.between(lastEnd, record.startTime)
                        if (gapFromLast > maxGapMinutes) {
                            val sessionDuration = ChronoUnit.MINUTES.between(
                                sessionStart, lastEnd
                            )
                            if (sessionDuration >= minSessionMinutes) {
                                walkingSessions.add(
                                    buildWalkLog(sessionStart!!, lastEnd!!, sessionSteps)
                                )
                            }
                            sessionStart = null
                            sessionSteps = 0L
                            lastEnd = null
                        }
                    }
                }
            }

            // Handle last open session
            if (sessionStart != null && lastEnd != null) {
                val sessionDuration = ChronoUnit.MINUTES.between(sessionStart, lastEnd)
                if (sessionDuration >= minSessionMinutes) {
                    walkingSessions.add(buildWalkLog(sessionStart!!, lastEnd!!, sessionSteps))
                }
            }

            Log.d("HC_Steps", "Detected ${walkingSessions.size} walking sessions")
            walkingSessions

        } catch (e: Exception) {
            Log.e("HC_Steps", "Walk detection failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun buildWalkLog(
        start: Instant,
        end: Instant,
        steps: Long
    ): BolusLog {
        val durationMins = ChronoUnit.MINUTES.between(start, end).toFloat()
        Log.d("HC_Steps", "  Walk session: ${durationMins}min, $steps steps, start=${start}")
        return BolusLog(
            id = 0,
            timestamp = start.toEpochMilli(),
            eventType = "SPORT",
            status = "COMPLETED",
            bloodGlucose = 0.0,
            carbs = 0.0,
            standardDose = 0.0,
            suggestedDose = 0.0,
            administeredDose = 0.0,
            isSportModeActive = true,
            sportType = "Walking",
            sportIntensity = "Low",
            sportDuration = durationMins,
            notes = "Auto-detected via steps (~$steps steps)",
            clinicalSuggestion = null
        )
    }
}