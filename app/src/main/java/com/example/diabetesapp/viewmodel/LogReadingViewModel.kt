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
import com.example.diabetesapp.utils.AlgorithmEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration

enum class InsightType { ON_TRACK, SUGGESTION, WARNING }

data class LogInsight(
    val type: InsightType,
    val title: String,
    val message: String
)

data class LogReadingState(
    val eventDate: String = "",
    val eventTime: String = "",
    val bloodGlucose: String = "",
    val carbs: String = "",
    val manualInsulin: String = "",
    val notes: String = "",

    val isSportModeActive: Boolean = false,
    val sportType: String = "Aerobic",
    val sportIntensityValue: Float = 2f,
    val sportIntensity: String = "Medium",
    val sportDurationMinutes: Float = 45f,

    // The Unified Insight State
    val currentInsight: LogInsight? = null,

    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val pendingClinicalSuggestion: String? = null
)

class LogReadingViewModel(private val repository: BolusLogRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LogReadingState())
    val uiState: StateFlow<LogReadingState> = _uiState.asStateFlow()

    private val dummySettings = BolusSettings(
        icrMorning = 10f, icrNoon = 10f, icrEvening = 10f, icrNight = 10f,
        isfMorning = 50f, isfNoon = 50f, isfEvening = 50f, isfNight = 50f,
        targetBG = 100f
    )

    init { resetState() }

    fun updateDate(value: String) { _uiState.value = _uiState.value.copy(eventDate = value) }
    fun updateTime(value: String) { _uiState.value = _uiState.value.copy(eventTime = value) }
    fun updateBloodGlucose(value: String) { _uiState.value = _uiState.value.copy(bloodGlucose = value, errorMessage = null) }
    fun updateCarbs(value: String) { _uiState.value = _uiState.value.copy(carbs = value, errorMessage = null) }
    fun updateManualInsulin(value: String) { _uiState.value = _uiState.value.copy(manualInsulin = value, errorMessage = null) }
    fun updateNotes(value: String) { _uiState.value = _uiState.value.copy(notes = value) }
    fun toggleSportMode(isActive: Boolean) { _uiState.value = _uiState.value.copy(isSportModeActive = isActive, errorMessage = null) }
    fun updateSportType(type: String) { _uiState.value = _uiState.value.copy(sportType = type) }
    fun updateSportDuration(value: Float) { _uiState.value = _uiState.value.copy(sportDurationMinutes = value) }

    fun updateSportIntensity(value: Float) {
        val intensityString = when (value.toInt()) { 1 -> "Low"; 2 -> "Medium"; 3 -> "High"; else -> "Medium" }
        _uiState.value = _uiState.value.copy(sportIntensityValue = value, sportIntensity = intensityString)
    }

    fun dismissInsightDialog() { _uiState.value = _uiState.value.copy(currentInsight = null) }

    fun resetState() {
        val now = LocalDateTime.now()
        _uiState.value = LogReadingState(
            eventDate = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            eventTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        )
    }

    // --- THE NEW UNIFIED ANALYZE FLOW ---
    fun analyzeLog() {
        val state = _uiState.value
        val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
        val carbs = state.carbs.toDoubleOrNull() ?: 0.0
        val insulin = state.manualInsulin.toDoubleOrNull() ?: 0.0

        if (bg == 0.0 && carbs == 0.0 && insulin == 0.0 && !state.isSportModeActive) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter data or enable Sport Mode.")
            return
        }

        // Calculate time offset based on the user's selected Time/Date
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val eventDateTimeString = "${state.eventDate} ${state.eventTime}"
        val eventDateTime = try { LocalDateTime.parse(eventDateTimeString, formatter) } catch (e: Exception) { LocalDateTime.now() }

        // Positive = Future, Negative = Past
        val minutesDiff = Duration.between(LocalDateTime.now(), eventDateTime).toMinutes().toInt()

        val context = PatientContext(
            therapyType = TherapyType.MDI_PENS,
            bolusSettings = dummySettings,
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

        // Generate the appropriate Insight based on the Algorithm's output
        val insight = when {
            // 1. Critical Carb Warning
            decision.suggestedRescueCarbs > 0 && carbs < decision.suggestedRescueCarbs -> {
                LogInsight(
                    type = InsightType.WARNING,
                    title = "Action Required",
                    message = decision.clinicalRationale.ifBlank { "You need ${decision.suggestedRescueCarbs}g of fast-acting carbs to prevent a low." }
                )
            }
            // 2. Late-Onset Warning for past sports
            state.isSportModeActive && minutesDiff <= 0 && decision.clinicalRationale.contains("Late-Onset") -> {
                LogInsight(
                    type = InsightType.WARNING,
                    title = "Post-Sport Alert",
                    message = decision.clinicalRationale
                )
            }
            // 3. Pre-Sport Strategy (Future event)
            state.isSportModeActive && minutesDiff > 0 -> {
                LogInsight(
                    type = InsightType.SUGGESTION,
                    title = "Pre-Sport Strategy",
                    message = decision.clinicalRationale.ifBlank { "You are clear to start your ${state.sportType} workout in $minutesDiff minutes. Monitor BG closely." }
                )
            }
            // 4. Missed Insulin Suggestion
            decision.suggestedInsulinDose > 0.5 && insulin == 0.0 -> {
                LogInsight(
                    type = InsightType.SUGGESTION,
                    title = "Insulin Recommended",
                    message = "The algorithm suggests ${decision.suggestedInsulinDose}U to cover this entry. Consider adjusting your log if you haven't taken insulin."
                )
            }
            // 5. General Rationale present
            decision.clinicalRationale.isNotBlank() && state.isSportModeActive -> {
                LogInsight(
                    type = InsightType.SUGGESTION,
                    title = "Sport Insight",
                    message = decision.clinicalRationale
                )
            }
            // 6. ALL CLEAR / ON TRACK
            else -> {
                val statusMessage = if (bg in 70.0..180.0) {
                    "Your blood glucose is in range. Great job keeping it steady!"
                } else if (bg > 180.0 && insulin > 0.0) {
                    "Correction dose noted. Drink water and monitor for the next 2 hours."
                } else {
                    "Log looks good. Everything is on track."
                }

                LogInsight(
                    type = InsightType.ON_TRACK,
                    title = "Looking Good!",
                    message = statusMessage
                )
            }
        }

        _uiState.value = _uiState.value.copy(
            currentInsight = insight,
            pendingClinicalSuggestion = decision.clinicalRationale.takeIf { it.isNotBlank() }
        )
    }

    fun executeSave() {
        val state = _uiState.value
        val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
        val carbs = state.carbs.toDoubleOrNull() ?: 0.0
        val insulin = state.manualInsulin.toDoubleOrNull() ?: 0.0

        val eventType = when {
            state.isSportModeActive -> "SPORT"
            insulin > 0.0 && carbs == 0.0 -> "MANUAL_INSULIN"
            carbs > 0.0 && insulin == 0.0 -> "MEAL"
            bg > 0.0 && carbs == 0.0 && insulin == 0.0 -> "BG_CHECK"
            else -> "MIXED_LOG"
        }

        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val timestamp = try {
            val dateTime = LocalDateTime.parse("${state.eventDate} ${state.eventTime}", formatter)
            dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) { System.currentTimeMillis() }

        val log = BolusLog(
            timestamp = timestamp,
            eventType = eventType,
            bloodGlucose = bg,
            carbs = carbs,
            standardDose = 0.0,
            suggestedDose = 0.0,
            administeredDose = insulin,
            isSportModeActive = state.isSportModeActive,
            sportType = if (state.isSportModeActive) state.sportType else null,
            sportIntensity = if (state.isSportModeActive) state.sportIntensity else null,
            sportDuration = if (state.isSportModeActive) state.sportDurationMinutes else null,
            notes = state.notes.ifBlank { if (state.isSportModeActive) "Workout logged" else "Manual Log" },
            clinicalSuggestion = state.pendingClinicalSuggestion
        )

        viewModelScope.launch {
            repository.insert(log)
            resetState()
            _uiState.value = _uiState.value.copy(isSaved = true, currentInsight = null)
        }
    }
}
// Keep Factory class as is...
class LogReadingViewModelFactory(private val repository: BolusLogRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogReadingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return LogReadingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}