package com.example.diabetesapp.utils

import android.util.Log
import com.example.diabetesapp.data.models.BolusLog

object GraphDataBuilder {

    fun buildGraphEvents(
        localLogs: List<BolusLog>,
        xdripTreatments: List<BolusLog> = emptyList(),
        hcWorkouts: List<BolusLog> = emptyList()
    ): List<BolusLog> {
        val combined = (localLogs + xdripTreatments + hcWorkouts)
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
        Log.d("GraphDataBuilder", "Graph events: ${combined.size} total " +
                "(${localLogs.size} local + ${xdripTreatments.size} xDrip + ${hcWorkouts.size} HC workouts)")
        return combined
    }

    fun filterForDay(logs: List<BolusLog>, dayStart: Long): List<BolusLog> =
        logs.filter { it.timestamp >= dayStart }
}