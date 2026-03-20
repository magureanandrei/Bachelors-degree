package com.example.diabetesapp.utils

import android.util.Log
import com.example.diabetesapp.data.models.BolusLog

enum class GraphMode {
    AID_CGM,    // CGM line + CareLink treatments + hypo prediction
    CGM_ONLY,   // CGM line + no treatments + hypo prediction
    MANUAL      // BG dots only, no CGM line, no hypo prediction
}

object GraphDataBuilder {

    fun resolveGraphMode(isCgmEnabled: Boolean, isAidPump: Boolean): GraphMode = when {
        isAidPump && isCgmEnabled -> GraphMode.AID_CGM
        isCgmEnabled -> GraphMode.CGM_ONLY
        else -> GraphMode.MANUAL
    }

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