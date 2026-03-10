package com.example.diabetesapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.models.CgmTrend
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.utils.AlgorithmEngine
import com.example.diabetesapp.utils.CgmHelper
import com.example.diabetesapp.utils.CgmReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Calendar
import java.util.Date

class DashboardViewModel(
    private val repository: BolusLogRepository,
    private val settingsRepository: BolusSettingsRepository // Injecting the settings repo
) : ViewModel() {

    // Observe all logs for the graph/list
    val allLogs: StateFlow<List<BolusLog>> = repository.allLogs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Observe live settings from the database.
    val settings: StateFlow<BolusSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BolusSettings()
    )

    // --- NEW: Single Rich Reading for the Big Widget ---
    private val _latestReading = MutableStateFlow<CgmReading?>(null)
    val latestReading: StateFlow<CgmReading?> = _latestReading.asStateFlow()

    private val _graphEvents = MutableStateFlow<List<BolusLog>>(emptyList())
    val graphEvents: StateFlow<List<BolusLog>> = _graphEvents.asStateFlow()

    // --- NEW: CGM Graph Data State ---
    private val _cgmReadings = MutableStateFlow<List<CgmReading>>(emptyList())
    val cgmReadings: StateFlow<List<CgmReading>> = _cgmReadings.asStateFlow()

    // State to trigger the Verification Modal
    val unverifiedWorkout = MutableStateFlow<BolusLog?>(null)

    // StateFlow that holds the logical day start timestamp (3 AM cutoff)
    private val _logicalDayStart = MutableStateFlow(getLogicalDayStartTimestamp())
    val logicalDayStartFlow: StateFlow<Long> = _logicalDayStart.asStateFlow()

    init {
        // Keep _graphEvents in sync with local logs automatically
        viewModelScope.launch {
            allLogs.collect { logs ->
                val dayStart = getLogicalDayStartTimestamp()
                val todayLocal = logs.filter { it.timestamp >= dayStart }.sortedBy { it.timestamp }
                // Merge with any existing xDrip treatments already in _graphEvents
                val existing = _graphEvents.value.filter { it.id == 0 } // xDrip logs have id=0
                _graphEvents.value = (todayLocal + existing)
                    .distinctBy { it.timestamp }
                    .sortedBy { it.timestamp }
            }
        }
    }


    val todaysTreatments: StateFlow<List<BolusLog>> = _graphEvents.asStateFlow()
        .combine(logicalDayStartFlow) { events, dayStart ->
            events.filter { it.timestamp >= dayStart && (it.carbs > 0 || it.administeredDose >= 1.5f) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- UPDATED: Background Fetcher for Dashboard Data ---
    fun fetchDashboardData(isCgmEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isCgmEnabled) {
                    val latest = CgmHelper.getLatestBgFromXDrip()
                    val history = CgmHelper.getBgHistoryFromXDrip()

                    val dayStart = getLogicalDayStartTimestamp()

                    val xdripTreatments = CgmHelper.getTreatmentsFromXDrip()
                    Log.d("DashboardVM", "dayStart=$dayStart (${Date(dayStart)})")
                    xdripTreatments.forEach {
                        Log.d("DashboardVM", "  raw xDrip: ts=${it.timestamp} (${Date(it.timestamp)}) carbs=${it.carbs} insulin=${it.administeredDose}")
                    }
                    val todayXDrip = xdripTreatments
                        .filter { it.timestamp >= dayStart }
                        .map { treatment ->
                            treatment.copy(
                                bloodGlucose = findClosestBg(treatment.timestamp, history)
                            )
                        }


                    withContext(Dispatchers.Main) {
                        _latestReading.value = latest
                        _cgmReadings.value = history

                        val localLogs = allLogs.value.filter { it.timestamp >= dayStart }
                        val combined = (localLogs + todayXDrip)
                            .distinctBy { it.timestamp }
                            .sortedBy { it.timestamp }

                        Log.d("DashboardVM", "Graph events: ${combined.size} total (${localLogs.size} local + ${todayXDrip.size} xDrip)")
                        _graphEvents.value = combined
                    }
                } else {
                    val dayStart = getLogicalDayStartTimestamp()
                    val localLogs = allLogs.value.filter { it.timestamp >= dayStart }
                    withContext(Dispatchers.Main) {
                        _graphEvents.value = localLogs.sortedBy { it.timestamp }
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Fetch failed", e)
            }
        }
    }

    private fun findClosestBg(timestamp: Long, cgmReadings: List<CgmReading>): Double {
        val windowMs = 10 * 60 * 1000L
        return cgmReadings
            .filter { Math.abs(it.timestamp - timestamp) <= windowMs }
            .minByOrNull { Math.abs(it.timestamp - timestamp) }
            ?.bgValue?.toDouble() ?: 0.0
    }

    fun getLogicalDayStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun checkForPendingWorkouts(logs: List<BolusLog>) {
        val now = System.currentTimeMillis()
        val pending = logs.firstOrNull { log ->
            log.status == "PLANNED" && log.isSportModeActive &&
                    (log.timestamp + ((log.sportDuration ?: 0f) * 60 * 1000L)) < now
        }
        unverifiedWorkout.value = pending
    }

    fun dismissVerification() {
        unverifiedWorkout.value = null
    }

    fun deleteLog(log: BolusLog) {
        viewModelScope.launch {
            repository.delete(log)
        }
    }

    fun verifyAndCompleteWorkout(
        log: BolusLog,
        actualDuration: Float,
        actualIntensity: Float,
        sportType: String,
        actualStartTimeStr: String
    ) {
        // Use the current value from our live settings flow
        val currentSettings = settings.value

        val context = PatientContext(
            therapyType = TherapyType.MDI_PENS,
            bolusSettings = currentSettings, // Link to real user settings
            currentBG = log.bloodGlucose,
            hasCGM = false,
            cgmTrend = CgmTrend.NONE,
            activeInsulinIOB = log.administeredDose,
            plannedCarbs = log.carbs,
            isDoingSport = true,
            sportType = sportType,
            sportIntensity = actualIntensity.toInt(),
            sportDurationMins = actualDuration.toInt(),
            minutesUntilSport = -actualDuration.toInt(),
            timeOfDay = LocalTime.now()
        )

        val decision = AlgorithmEngine.calculateClinicalAdvice(context)
        val newInsight = "Post-Workout Insight: ${decision.clinicalRationale}"
        val combinedInsight = if (log.clinicalSuggestion.isNullOrBlank()) newInsight else "${log.clinicalSuggestion}\n\n$newInsight"

        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = log.timestamp
        }
        try {
            val parts = actualStartTimeStr.split(":")
            if (parts.size == 2) {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
                calendar.set(java.util.Calendar.MINUTE, parts[1].toInt())
            }
        } catch (e: Exception) { /* keep original */ }

        val updatedLog = log.copy(
            timestamp = calendar.timeInMillis,
            status = "COMPLETED",
            sportDuration = actualDuration,
            sportIntensity = when (actualIntensity.toInt()) {
                1 -> "Low"; 2 -> "Medium"; 3 -> "High"; else -> "Medium"
            },
            sportType = sportType,
            clinicalSuggestion = combinedInsight
        )

        viewModelScope.launch {
            repository.update(updatedLog)
            unverifiedWorkout.value = null
        }
    }
}

class DashboardViewModelFactory(
    private val repository: BolusLogRepository,
    private val settingsRepository: BolusSettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}