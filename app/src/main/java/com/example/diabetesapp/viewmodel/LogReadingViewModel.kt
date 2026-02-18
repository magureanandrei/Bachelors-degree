package com.example.diabetesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.repository.BolusLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import kotlin.math.abs

data class LogReadingState(
    val eventDate: String = "",
    val eventTime: String = "",
    val bloodGlucose: String = "",
    val carbs: String = "",
    val manualInsulin: String = "",
    val notes: String = "",

    // Sport Mode Additions
    val isSportModeActive: Boolean = false,
    val sportType: String = "Aerobic",
    val sportIntensityValue: Float = 2f,
    val sportIntensity: String = "Medium",
    val sportDurationMinutes: Float = 45f,

    // Dialogs
    val showCarbSuggestionDialog: Boolean = false,
    val suggestedCarbs: Int = 0,
    val carbSuggestionMessage: String = "",

    val showPostSportAlert: Boolean = false,
    val postSportAlertMessage: String = "",

    val isSaved: Boolean = false,
    val errorMessage: String? = null,

    // Temporarily holds the clinical advice to save to the database
    val pendingClinicalSuggestion: String? = null
)

class LogReadingViewModel(private val repository: BolusLogRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LogReadingState())
    val uiState: StateFlow<LogReadingState> = _uiState.asStateFlow()

    init {
        resetState()
    }

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
        val intensityString = when (value.toInt()) {
            1 -> "Low (Walking)"
            2 -> "Medium (Jogging)"
            3 -> "High (Sprinting)"
            else -> "Medium"
        }
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

        // --- SMART TIMESTAMP LOGIC ---
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val eventDateTimeString = "${state.eventDate} ${state.eventTime}"
        val eventDateTime = try { LocalDateTime.parse(eventDateTimeString, formatter) } catch (e: Exception) { LocalDateTime.now() }
        val now = LocalDateTime.now()

        // Positive = Future, Negative = Past
        val minutesDiff = Duration.between(now, eventDateTime).toMinutes()
        val isFuture = minutesDiff > 0
        val absMinutes = abs(minutesDiff).toInt()

        // --- THE CARB SUGGESTION ALGORITHM ---
        if (state.isSportModeActive && bg > 0.0 && insulin == 0.0) {
            var suggestedCarbs = 0
            var message = ""
            var clinicalLog = ""

            // FUTURE EVENT
            if (isFuture || absMinutes <= 5) { // Treat anything within 5 mins as "About to start"
                if (bg < 90) {
                    suggestedCarbs = 20
                    message = if (absMinutes > 30) {
                        "Your BG is low (${bg} mg/dL). Consume fast-acting carbs now. Since your exercise is in ${absMinutes} mins, also eat a complex carb to sustain you."
                    } else {
                        "Your BG is low (${bg} mg/dL). Consume fast-acting carbs and wait 15 minutes before starting."
                    }
                } else if (bg in 90.0..125.0) {
                    if (state.sportType == "Aerobic") {
                        suggestedCarbs = 15
                        message = if (absMinutes > 30) {
                            "Aerobic exercise will drop your BG. Since you start in ${absMinutes} mins, consider eating 15g of complex carbs now, or reduce your pump's basal rate."
                        } else {
                            "Aerobic exercise drops BG rapidly. Consume 15g of fast-acting carbs before starting."
                        }
                    } else if (state.sportType == "Mixed") {
                        suggestedCarbs = 10
                        message = "Consume 10g of carbs to stabilize BG before your mixed activity."
                    }
                } else if (bg > 250) {
                    message = "Warning: BG is high. Check for ketones. If moderate/high, do not exercise."
                }

                if (suggestedCarbs > 0 && carbs < suggestedCarbs) {
                    _uiState.value = _uiState.value.copy(
                        showCarbSuggestionDialog = true,
                        suggestedCarbs = suggestedCarbs,
                        carbSuggestionMessage = message,
                        pendingClinicalSuggestion = "Pre-Sport Suggestion: $message" // Save for history!
                    )
                    return
                }
            }

            // PAST EVENT
            else {
                if (bg < 80) {
                    _uiState.value = _uiState.value.copy(
                        showCarbSuggestionDialog = true,
                        suggestedCarbs = 15,
                        carbSuggestionMessage = "You logged a low post-workout BG (${bg} mg/dL). Consume 15g fast-acting carbs immediately.",
                        pendingClinicalSuggestion = "Post-Sport Low Rescue: 15g carbs recommended."
                    )
                    return
                } else if (state.sportType == "Aerobic" || state.sportType == "Mixed" || state.sportDurationMinutes > 45f) {
                    val alertMessage = "Because you exercised ${absMinutes} mins ago, your muscles will rebuild glycogen over the next 7-11 hours.\n\n⚠️ Be cautious of late-onset overnight hypoglycemia. Consider a bedtime snack or a temporary basal reduction."

                    _uiState.value = _uiState.value.copy(
                        showPostSportAlert = true,
                        postSportAlertMessage = alertMessage,
                        pendingClinicalSuggestion = "Post-Sport Alert: Late-Onset Hypoglycemia risk flagged."
                    )
                    return
                }
            }
        }

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

        // Parse the custom timestamp for the database
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
            clinicalSuggestion = state.pendingClinicalSuggestion // Save the advice to the DB!
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