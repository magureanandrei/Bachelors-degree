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

    fun saveEntry() {
        val state = _uiState.value

        val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
        val carbs = state.carbs.toDoubleOrNull() ?: 0.0
        val insulin = state.manualInsulin.toDoubleOrNull() ?: 0.0

        if (bg == 0.0 && carbs == 0.0 && insulin == 0.0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter at least one value to log.")
            return
        }

        // Smart categorization for the timeline
        val eventType = when {
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
            isSportModeActive = false,
            sportType = null,
            sportIntensity = null,
            sportDuration = null,
            notes = state.notes.ifBlank { "Manual Log" }
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