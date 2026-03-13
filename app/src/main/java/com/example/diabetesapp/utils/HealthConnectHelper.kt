package com.example.diabetesapp.utils

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectHelper(private val client: HealthConnectClient) {

    suspend fun getRecentWorkouts(): List<ExerciseSessionRecord> {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)
        Log.d(
            "HC_Workouts",
            "--> Querying Health Connect (ExerciseSessionRecord) from $startTime to $endTime"
        )

        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            Log.d("HC_Workouts", "--> Found ${response.records.size} ExerciseSessionRecord(s)")
            if (response.records.isEmpty()) {
                Log.w(
                    "HC_Workouts",
                    "--> No ExerciseSessionRecord data found. The source app may not be writing workout sessions to Health Connect yet."
                )
            }
            response.records.forEach { record ->
                Log.d(
                    "HC_Workouts",
                    "    - type=${record.exerciseType} start=${record.startTime} end=${record.endTime} origin=${record.metadata.dataOrigin}"
                )
            }
            response.records
        } catch (e: Exception) {
            Log.e("HC_Workouts", "--> FAILED to read records: ${e.message}", e)
            emptyList()
        }
    }

    // In HealthConnectHelper, add alongside getRecentWorkouts:
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
            response.records.sumOf { it.count }
        } catch (e: Exception) {
            Log.e("HC_Steps", "Failed: ${e.message}")
            0L
        }
    }
}