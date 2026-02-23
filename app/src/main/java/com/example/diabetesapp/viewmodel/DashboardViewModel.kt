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
import com.example.diabetesapp.utils.AlgorithmEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

class DashboardViewModel(private val repository: BolusLogRepository) : ViewModel() {

    val allLogs: StateFlow<List<BolusLog>> = repository.allLogs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // State to trigger the Verification Modal
    val unverifiedWorkout = MutableStateFlow<BolusLog?>(null)

    // Simulated settings for the post-workout Algorithm Engine check
    private val dummySettings = BolusSettings(
        icrMorning = 10f, icrNoon = 10f, icrEvening = 10f, icrNight = 10f,
        isfMorning = 50f, isfNoon = 50f, isfEvening = 50f, isfNight = 50f,
        targetBG = 100f
    )

    fun checkForPendingWorkouts(logs: List<BolusLog>) {
        val now = System.currentTimeMillis()
        // Find the first PLANNED workout where (Start Time + Duration) has passed
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

    // Runs the updated workout through the CDSS to get post-workout warnings
    // Runs the updated workout through the CDSS to get post-workout warnings
    fun verifyAndCompleteWorkout(log: BolusLog, actualDuration: Float, actualIntensity: Float, sportType: String, actualStartTimeStr: String) {
        val context = PatientContext(
            therapyType = TherapyType.MDI_PENS,
            bolusSettings = dummySettings,
            currentBG = log.bloodGlucose,
            hasCGM = false,
            cgmTrend = CgmTrend.NONE,
            activeInsulinIOB = log.administeredDose,
            plannedCarbs = log.carbs,
            isDoingSport = true,
            sportType = sportType,
            sportIntensity = actualIntensity.toInt(),
            sportDurationMins = actualDuration.toInt(),
            minutesUntilSport = -actualDuration.toInt(), // Negative means it's in the past!
            timeOfDay = LocalTime.now()
        )

        val decision = AlgorithmEngine.calculateClinicalAdvice(context)

        val newInsight = "Post-Workout Insight: ${decision.clinicalRationale}"
        val combinedInsight = if (log.clinicalSuggestion.isNullOrBlank()) newInsight else "${log.clinicalSuggestion}\n\n$newInsight"

        // NEW: Parse the edited start time and update the log's timestamp
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = log.timestamp
        }
        try {
            val parts = actualStartTimeStr.split(":")
            if (parts.size == 2) {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
                calendar.set(java.util.Calendar.MINUTE, parts[1].toInt())
            }
        } catch (e: Exception) {
            // Failsafe: keep original timestamp if parsing fails
        }

        val updatedLog = log.copy(
            timestamp = calendar.timeInMillis, // Apply the new edited timestamp!
            status = "COMPLETED",
            sportDuration = actualDuration,
            sportIntensity = when (actualIntensity.toInt()) { 1 -> "Low"; 2 -> "Medium"; 3 -> "High"; else -> "Medium" },
            sportType = sportType,
            clinicalSuggestion = combinedInsight
        )

        viewModelScope.launch {
            repository.update(updatedLog)
            unverifiedWorkout.value = null
        }
    }
}

class DashboardViewModelFactory(private val repository: BolusLogRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}