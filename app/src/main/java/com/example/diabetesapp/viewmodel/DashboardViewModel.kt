package com.example.diabetesapp.viewmodel

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Calendar

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

    // --- NEW: CGM Graph Data State ---
    private val _cgmReadings = MutableStateFlow<List<CgmReading>>(emptyList())
    val cgmReadings: StateFlow<List<CgmReading>> = _cgmReadings.asStateFlow()

    // State to trigger the Verification Modal
    val unverifiedWorkout = MutableStateFlow<BolusLog?>(null)

    // --- UPDATED: Background Fetcher for Dashboard Data ---
    fun fetchDashboardData(isCgmEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isCgmEnabled) {
                    // PATH A: Live Sensor Mode
                    val latest = CgmHelper.getLatestBgFromXDrip()
                    val history = CgmHelper.getBgHistoryFromXDrip()

                    withContext(Dispatchers.Main) {
                        _latestReading.value = latest
                        _cgmReadings.value = history
                    }
                } else {
                    // PATH B: Manual Fingerstick Mode
                    val lastManualLog = repository.getLatestManualBgLog()

                    withContext(Dispatchers.Main) {
                        if (lastManualLog != null) {
                            _latestReading.value = CgmReading(
                                timestamp = lastManualLog.timestamp,
                                bgValue = lastManualLog.bloodGlucose.toInt(),
                                trendString = "", // No trend arrows for fingersticks!
                                iob = null        // No pump IOB in manual mode
                            )
                        } else {
                            _latestReading.value = null
                        }
                        // Clear the continuous line from the graph in manual mode
                        _cgmReadings.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getLogicalDayStartTimestamp(): Long {
        // Uses the phone's LOCAL timezone settings
        val calendar = Calendar.getInstance()

        // If current time is between Midnight and 3 AM,
        // we want the graph to start at 3 AM of the PREVIOUS day.
        if (calendar.get(Calendar.HOUR_OF_DAY) < 3) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        calendar.set(Calendar.HOUR_OF_DAY, 3)
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