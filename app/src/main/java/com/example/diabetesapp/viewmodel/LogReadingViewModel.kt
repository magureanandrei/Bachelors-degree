package com.example.diabetesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.models.CgmTrend
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.utils.AlgorithmEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

enum class InsightType { ON_TRACK, SUGGESTION, WARNING }

data class LogInsight(val type: InsightType, val title: String, val message: String)

data class LogReadingState(
    // REMOVED eventDate! Just keeping the time.
    val eventTime: String = "",
    val bloodGlucose: String = "",
    val carbs: String = "",
    val manualInsulin: String = "",
    val notes: String = "",

    // This now acts as the UI Toggle (False = Diet/Insulin, True = Sport)
    val isSportModeActive: Boolean = false,
    val sportType: String = "Aerobic",
    val sportIntensityValue: Float = 2f,
    val sportIntensity: String = "Medium",
    val sportDurationMinutes: Float = 45f,

    val currentInsight: LogInsight? = null,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val pendingClinicalSuggestion: String? = null
)

class LogReadingViewModel(
    private val repository: BolusLogRepository,
    private val settingsRepository: BolusSettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LogReadingState())
    val uiState: StateFlow<LogReadingState> = _uiState.asStateFlow()

    val settings: StateFlow<BolusSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BolusSettings()
    )

    init { resetState() }

    fun updateTime(value: String) {
        // Use your existing helper to figure out the exact millisecond timestamp
        val timestamp = getParsedTimestamp(value)

        if (timestamp > System.currentTimeMillis()) {
            // The time is in the future! Snap back to NOW.
            val now = LocalTime.now()
            _uiState.value = _uiState.value.copy(
                eventTime = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                errorMessage = "Cannot log events in the future. Reset to current time."
            )
        } else {
            // The time is valid (past or present), accept it.
            _uiState.value = _uiState.value.copy(eventTime = value, errorMessage = null)
        }
    }
    fun updateBloodGlucose(value: String) { _uiState.value = _uiState.value.copy(bloodGlucose = value, errorMessage = null) }
    fun updateCarbs(value: String) { _uiState.value = _uiState.value.copy(carbs = value, errorMessage = null) }
    fun updateManualInsulin(value: String) { _uiState.value = _uiState.value.copy(manualInsulin = value, errorMessage = null) }
    fun updateNotes(value: String) { _uiState.value = _uiState.value.copy(notes = value) }

    // Toggle between Diet and Sport Mode
    fun toggleSportMode(isActive: Boolean) { _uiState.value = _uiState.value.copy(isSportModeActive = isActive, errorMessage = null) }

    fun updateSportType(type: String) { _uiState.value = _uiState.value.copy(sportType = type) }
    fun updateSportDuration(value: Float) { _uiState.value = _uiState.value.copy(sportDurationMinutes = value) }
    fun updateSportIntensity(value: Float) {
        val intensityString = when (value.toInt()) { 1 -> "Low"; 3 -> "High"; else -> "Medium" }
        _uiState.value = _uiState.value.copy(sportIntensityValue = value, sportIntensity = intensityString)
    }

    fun dismissInsightDialog() { _uiState.value = _uiState.value.copy(currentInsight = null) }

    fun resetState() {
        val now = LocalTime.now()
        _uiState.value = LogReadingState(
            eventTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        )
    }

    // Helper to get exact timestamp from "HH:mm"
    private fun getParsedTimestamp(timeStr: String): Long {
        val calendar = Calendar.getInstance()
        try {
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toInt()
                val min = parts[1].toInt()
                // If entered time is > 2 hours in the future, assume it meant yesterday!
                if (hour > calendar.get(Calendar.HOUR_OF_DAY) + 2) {
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, min)
                calendar.set(Calendar.SECOND, 0)
            }
        } catch (e: Exception) { /* Fallback to now */ }
        return calendar.timeInMillis
    }

    fun analyzeLog() {
        val state = _uiState.value
        val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
        val carbs = state.carbs.toDoubleOrNull() ?: 0.0
        val insulin = state.manualInsulin.toDoubleOrNull() ?: 0.0

        if (bg == 0.0 && carbs == 0.0 && insulin == 0.0 && !state.isSportModeActive) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter data or switch to Sport Mode.")
            return
        }
        val eventTimestamp = getParsedTimestamp(state.eventTime)
        if (eventTimestamp > System.currentTimeMillis()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Cannot log manual events in the future. Please fix the time.")
            return
        }

        // We assume retrospective logs are always <= 0 minutes from now
        val minutesDiff = 0

        val context = PatientContext(
            therapyType = settings.value.therapyTypeEnum,
            bolusSettings = settings.value,
            currentBG = bg,
            hasCGM = false,
            cgmTrend = CgmTrend.NONE,
            activeInsulinIOB = insulin,
            plannedCarbs = carbs,
            isDoingSport = state.isSportModeActive,
            sportType = state.sportType,
            sportIntensity = state.sportIntensityValue.toInt(),
            sportDurationMins = state.sportDurationMinutes.toInt(),
            minutesUntilSport = minutesDiff,
            timeOfDay = LocalTime.now()
        )

        val decision = AlgorithmEngine.calculateClinicalAdvice(context)

        // Generating insights logic remains largely the same...
        val insight = when {
            decision.suggestedRescueCarbs > 0 && carbs < decision.suggestedRescueCarbs -> LogInsight(InsightType.WARNING, "Action Required", decision.clinicalRationale.ifBlank { "You need fast-acting carbs to prevent a low." })
            state.isSportModeActive && decision.clinicalRationale.contains("Late-Onset") -> LogInsight(InsightType.WARNING, "Post-Sport Alert", decision.clinicalRationale)
            decision.suggestedInsulinDose > 0.5 && insulin == 0.0 -> LogInsight(InsightType.SUGGESTION, "Insulin Recommended", "The algorithm suggests ${decision.suggestedInsulinDose}U. Consider adjusting your log if you took insulin.")
            decision.clinicalRationale.isNotBlank() -> LogInsight(InsightType.SUGGESTION, "Insight", decision.clinicalRationale)
            else -> LogInsight(InsightType.ON_TRACK, "Looking Good!", "Everything is perfectly on track.")
        }

        _uiState.value = _uiState.value.copy(
            currentInsight = insight,
            pendingClinicalSuggestion = decision.clinicalRationale.takeIf { it.isNotBlank() }
        )
    }

    fun executeSave() {
        val state = _uiState.value
        val timestamp = getParsedTimestamp(state.eventTime)

        viewModelScope.launch {
            if (state.isSportModeActive) {
                // SAVE SPORT ONLY
                val sportLog = BolusLog(
                    timestamp = timestamp,
                    eventType = "SPORT",
                    status = "COMPLETED", // Retrospective is always completed
                    bloodGlucose = 0.0, carbs = 0.0, standardDose = 0.0, suggestedDose = 0.0, administeredDose = 0.0,
                    isSportModeActive = true,
                    sportType = state.sportType,
                    sportIntensity = state.sportIntensity,
                    sportDuration = state.sportDurationMinutes,
                    notes = state.notes.ifBlank { "Retrospective workout logged" },
                    clinicalSuggestion = state.pendingClinicalSuggestion
                )
                repository.insert(sportLog)
            } else {
                // SAVE DIABETES LOG ONLY
                val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
                val carbs = state.carbs.toDoubleOrNull() ?: 0.0
                val insulin = state.manualInsulin.toDoubleOrNull() ?: 0.0

                val eventType = when {
                    bg > 0 && carbs == 0.0 && insulin == 0.0 -> "BG_CHECK"
                    carbs > 0 && insulin == 0.0 -> "MEAL"
                    else -> "MANUAL_INSULIN"
                }

                val log = BolusLog(
                    timestamp = timestamp,
                    eventType = eventType,
                    status = "COMPLETED",
                    bloodGlucose = bg, carbs = carbs, standardDose = insulin, suggestedDose = 0.0, administeredDose = insulin,
                    isSportModeActive = false, sportType = null, sportIntensity = null, sportDuration = null,
                    notes = state.notes.ifBlank { "Manual Log" },
                    clinicalSuggestion = state.pendingClinicalSuggestion
                )
                repository.insert(log)
            }

            resetState()
            _uiState.value = _uiState.value.copy(isSaved = true, currentInsight = null)
        }
    }
}
class LogReadingViewModelFactory(
    private val repository: BolusLogRepository,
    private val settingsRepository: BolusSettingsRepository
    ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogReadingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return LogReadingViewModel(repository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}