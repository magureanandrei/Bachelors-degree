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

    val showCarbSuggestionDialog: Boolean = false,
    val suggestedCarbs: Int = 0,
    val carbSuggestionMessage: String = "",

    val showPostSportAlert: Boolean = false,
    val postSportAlertMessage: String = "",

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

    fun dismissCarbDialog() { _uiState.value = _uiState.value.copy(showCarbSuggestionDialog = false) }
    fun dismissPostSportAlert() { _uiState.value = _uiState.value.copy(showPostSportAlert = false) }

    fun resetState() {
        val now = LocalDateTime.now()
        _uiState.value = LogReadingState(
            eventDate = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            eventTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        )
    }

    // --- THE NEW CENTRALIZED ENGINE CALL ---
    fun attemptSave() {
        val state = _uiState.value
        val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
        val carbs = state.carbs.toDoubleOrNull() ?: 0.0
        val insulin = state.manualInsulin.toDoubleOrNull() ?: 0.0

        if (bg == 0.0 && carbs == 0.0 && insulin == 0.0 && !state.isSportModeActive) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter data or enable Sport Mode.")
            return
        }

        if (state.isSportModeActive && bg == 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Blood Glucose is required to assess sport safety.")
            return
        }

        // Calculate time offset based on the user's selected Time/Date
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val eventDateTimeString = "${state.eventDate} ${state.eventTime}"
        val eventDateTime = try { LocalDateTime.parse(eventDateTimeString, formatter) } catch (e: Exception) { LocalDateTime.now() }
        val minutesDiff = Duration.between(LocalDateTime.now(), eventDateTime).toMinutes().toInt()

        // 1. Build the Patient Context
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

        // 2. Feed it to the Engine
        val decision = AlgorithmEngine.calculateClinicalAdvice(context)

        // 3. Let the Engine dictate the Dialogs!
        if (state.isSportModeActive && insulin == 0.0) {

            // If the engine suggests carbs, and we haven't logged enough yet...
            if (decision.suggestedRescueCarbs > 0 && carbs < decision.suggestedRescueCarbs) {
                _uiState.value = _uiState.value.copy(
                    showCarbSuggestionDialog = true,
                    suggestedCarbs = decision.suggestedRescueCarbs,
                    carbSuggestionMessage = decision.clinicalRationale,
                    pendingClinicalSuggestion = "Pre-Sport Rescue: ${decision.clinicalRationale}"
                )
                return
            }

            // If the engine gave a late-onset warning (it does this for PAST events)
            if (minutesDiff < 0 && decision.clinicalRationale.contains("Late-Onset")) {
                _uiState.value = _uiState.value.copy(
                    showPostSportAlert = true,
                    postSportAlertMessage = decision.clinicalRationale,
                    pendingClinicalSuggestion = "Post-Sport Alert: Late-Onset Hypoglycemia risk flagged."
                )
                return
            }
        }

        // If no warnings were triggered, proceed to save
        executeSave()
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
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}

class LogReadingViewModelFactory(private val repository: BolusLogRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogReadingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return LogReadingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}