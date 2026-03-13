package com.example.diabetesapp.utils

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
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

    suspend fun detectWalkingFromSteps(): List<BolusLog> {
        val endTime = Instant.now()
        val startTime = endTime.minus(1, ChronoUnit.DAYS)

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            Log.d("HC_Steps", "Raw step buckets: ${response.records.size}")

            val walkingSessions = mutableListOf<BolusLog>()
            var sessionStart: Instant? = null
            var sessionSteps = 0L
            var lastEnd: Instant? = null

            // Minimum 10 minutes to count as a session
            val minSessionMinutes = 10L
            // Walking threshold: 60+ steps per minute
            val walkingStepsPerMin = 60.0
            // Gap of 5+ minutes between buckets = new session
            val maxGapMinutes = 5L

            response.records.sortedBy { it.startTime }.forEach { record ->
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
                            val sessionDuration = ChronoUnit.MINUTES.between(
                                sessionStart, lastEnd
                            )
                            if (sessionDuration >= minSessionMinutes) {
                                walkingSessions.add(
                                    buildWalkLog(
                                        sessionStart!!,
                                        lastEnd!!,
                                        sessionSteps
                                    )
                                )
                            } else {
                                Log.d("HC_Steps", "Discarding short walk: ${sessionDuration}min < ${minSessionMinutes}min")
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
                    // Not walking — check if gap is too large to bridge
                    if (lastEnd != null && sessionStart != null) {
                        val gapFromLast = ChronoUnit.MINUTES.between(lastEnd, record.startTime)
                        if (gapFromLast > maxGapMinutes) {
                            // End current session
                            val sessionDuration = ChronoUnit.MINUTES.between(
                                sessionStart, lastEnd
                            )
                            if (sessionDuration >= minSessionMinutes) {
                                walkingSessions.add(
                                    buildWalkLog(
                                        sessionStart!!,
                                        lastEnd!!,
                                        sessionSteps
                                    )
                                )
                            }
                            sessionStart = null
                            sessionSteps = 0L
                            lastEnd = null
                        }
                    }
                }
            }

            // Handle last session
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