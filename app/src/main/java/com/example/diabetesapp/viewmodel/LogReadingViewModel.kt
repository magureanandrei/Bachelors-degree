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

data class LogReadingState(
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

    // Carb Suggestion Dialog
    val showCarbSuggestionDialog: Boolean = false,
    val suggestedCarbs: Int = 0,
    val carbSuggestionMessage: String = "",

    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class LogReadingViewModel(private val repository: BolusLogRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LogReadingState())
    val uiState: StateFlow<LogReadingState> = _uiState.asStateFlow()

    fun updateBloodGlucose(value: String) { _uiState.value = _uiState.value.copy(bloodGlucose = value, errorMessage = null) }
    fun updateCarbs(value: String) { _uiState.value = _uiState.value.copy(carbs = value, errorMessage = null) }
    fun updateManualInsulin(value: String) { _uiState.value = _uiState.value.copy(manualInsulin = value, errorMessage = null) }
    fun updateNotes(value: String) { _uiState.value = _uiState.value.copy(notes = value) }

    fun resetState() {
        _uiState.value = LogReadingState()
    }

    fun toggleSportMode(isActive: Boolean) { _uiState.value = _uiState.value.copy(isSportModeActive = isActive) }
    fun updateSportType(type: String) { _uiState.value = _uiState.value.copy(sportType = type) }
    fun updateSportDuration(value: Float) { _uiState.value = _uiState.value.copy(sportDurationMinutes = value) }
    fun updateSportIntensity(value: Float) {
        val intensityString = when (value.toInt()) {
            1 -> "Low"
            2 -> "Medium"
            3 -> "High"
            else -> "Medium"
        }
        _uiState.value = _uiState.value.copy(sportIntensityValue = value, sportIntensity = intensityString)
    }

    fun dismissCarbDialog() {
        _uiState.value = _uiState.value.copy(showCarbSuggestionDialog = false)
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

        // --- THE CARB SUGGESTION ALGORITHM ---
        // If they are doing sport, typed in their BG, but aren't taking insulin
        if (state.isSportModeActive && bg > 0.0 && insulin == 0.0) {
            var suggestedCarbs = 0
            var message = ""

            if (bg < 90) {
                suggestedCarbs = 20
                message = "Your BG is low (${bg} mg/dL). Consume fast-acting carbs and wait 15 minutes before starting exercise."
            } else if (bg in 90.0..125.0) {
                if (state.sportType == "Aerobic") {
                    suggestedCarbs = 15
                    message = "Your BG is dropping into the safe zone, but Aerobic exercise will lower it further. Consume 15g of carbs before starting."
                } else if (state.sportType == "Mixed") {
                    suggestedCarbs = 10
                    message = "Consume a small amount of carbs to stabilize BG for mixed activity."
                }
            } else if (bg > 250) {
                message = "Warning: BG is high. Check for ketones before exercising. Hydrate well."
            }

            // If the algorithm suggests carbs, and the user hasn't already typed carbs into the box, show the warning!
            if (suggestedCarbs > 0 && carbs < suggestedCarbs) {
                _uiState.value = _uiState.value.copy(
                    showCarbSuggestionDialog = true,
                    suggestedCarbs = suggestedCarbs,
                    carbSuggestionMessage = message
                )
                return // Stop the save process so they can read the dialog
            }
        }

        // If no warning needed, or they already bypassed it, save to DB
        executeSave()
    }

    fun executeSave() {
        val state = _uiState.value
        val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
        val carbs = state.carbs.toDoubleOrNull() ?: 0.0
        val insulin = state.manualInsulin.toDoubleOrNull() ?: 0.0

        // Determine Event Type for the unified timeline
        val eventType = when {
            state.isSportModeActive -> "SPORT"
            insulin > 0.0 && carbs == 0.0 -> "MANUAL_INSULIN"
            carbs > 0.0 && insulin == 0.0 -> "MEAL"
            bg > 0.0 && carbs == 0.0 && insulin == 0.0 -> "BG_CHECK"
            else -> "MIXED_LOG"
        }

        val log = BolusLog(
            timestamp = System.currentTimeMillis(),
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
            notes = state.notes.ifBlank { if (state.isSportModeActive) "Workout logged" else "Manual Log" }
        )

        viewModelScope.launch {
            repository.insert(log)
            _uiState.value = LogReadingState(isSaved = true)
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