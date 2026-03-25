package com.example.diabetesapp.utils

import com.example.diabetesapp.data.models.BolusLog

/**
 * Joins short-acting bolus events and long-acting basal events
 * for display in history and graph swimlanes.
 *
 * Rule: a BASAL_INSULIN event is NOT merged into any bolus event.
 * It always remains its own distinct row, but we annotate nearby
 * bolus events so the UI can show "X U bolus + Y U basal" together.
 */
object InsulinEventJoiner {

    data class JoinedInsulinEvent(
        val primaryLog: BolusLog,           // The bolus or basal event
        val associatedBasal: BolusLog? = null  // Basal dose taken "same session" (within 2h window)
    ) {
        val totalInsulin: Double
            get() = primaryLog.administeredDose + (associatedBasal?.administeredDose ?: 0.0)

        val isBasalOnly: Boolean
            get() = primaryLog.eventType == "BASAL_INSULIN"

        val hasBolusAndBasal: Boolean
            get() = associatedBasal != null && !isBasalOnly
    }

    /**
     * Takes a flat list of all logs (bolus + basal mixed) and returns
     * JoinedInsulinEvents. Basal logs are always their own entry.
     * Bolus logs get an associatedBasal if a basal was taken within
     * [windowHours] hours before or after (default: 2h).
     *
     * This is used for the History screen grouped view.
     */
    fun joinForHistory(
        logs: List<BolusLog>,
        windowHours: Int = 2
    ): List<JoinedInsulinEvent> {
        val windowMs = windowHours * 60 * 60 * 1000L
        val basalLogs = logs.filter { it.eventType == "BASAL_INSULIN" }
        val bolusLogs = logs.filter { it.eventType != "BASAL_INSULIN" }

        val usedBasalIds = mutableSetOf<Int>()

        // For each bolus, find the nearest unused basal within window
        val joined = bolusLogs.map { bolus ->
            val nearestBasal = basalLogs
                .filter { it.id !in usedBasalIds }
                .filter { kotlin.math.abs(it.timestamp - bolus.timestamp) <= windowMs }
                .minByOrNull { kotlin.math.abs(it.timestamp - bolus.timestamp) }

            if (nearestBasal != null) {
                usedBasalIds.add(nearestBasal.id)
                JoinedInsulinEvent(primaryLog = bolus, associatedBasal = nearestBasal)
            } else {
                JoinedInsulinEvent(primaryLog = bolus)
            }
        }.toMutableList()

        // Any basal not consumed by a bolus becomes its own entry
        val unconsumedBasals = basalLogs
            .filter { it.id !in usedBasalIds }
            .map { JoinedInsulinEvent(primaryLog = it) }

        return (joined + unconsumedBasals).sortedByDescending { it.primaryLog.timestamp }
    }

    /**
     * Simpler version for the graph swimlane — just returns
     * basal logs separately so the graph can draw them at a
     * different Y position / color.
     */
    fun splitForGraph(logs: List<BolusLog>): Pair<List<BolusLog>, List<BolusLog>> {
        val bolus = logs.filter { it.eventType != "BASAL_INSULIN" }
        val basal = logs.filter { it.eventType == "BASAL_INSULIN" }
        return bolus to basal
    }
}