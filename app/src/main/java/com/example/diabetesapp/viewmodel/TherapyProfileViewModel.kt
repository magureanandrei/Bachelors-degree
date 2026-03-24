package com.example.diabetesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TherapyProfileState(
    val therapyType: String = "MDI",
    val glucoseSource: String = "MANUAL",
    val isSaved: Boolean = false,
    val hasChanges: Boolean = false
)

class TherapyProfileViewModel(private val repository: BolusSettingsRepository, private val logRepository: BolusLogRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(TherapyProfileState())
    val uiState: StateFlow<TherapyProfileState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                if (!_uiState.value.hasChanges) {
                    _uiState.value = _uiState.value.copy(
                        therapyType = settings.therapyType,
                        glucoseSource = settings.glucoseSource
                    )
                }
            }
        }
    }

    fun updateTherapyType(type: String) {
        _uiState.value = _uiState.value.copy(therapyType = type, hasChanges = true)
    }

    fun updateGlucoseSource(source: String) {
        _uiState.value = _uiState.value.copy(glucoseSource = source, hasChanges = true)
    }

    fun saveProfile() {
        val state = _uiState.value
        val current = repository.getSettingsImmediate()
        val changes = mutableListOf<String>()
        if (current.therapyType != state.therapyType)
            changes.add("Therapy → ${therapyDisplayName(state.therapyType)}")
        if (current.glucoseSource != state.glucoseSource)
            changes.add("Glucose → ${therapyDisplayName(state.glucoseSource)}")

        if (changes.isNotEmpty()) {
            viewModelScope.launch {
                logRepository.insert(
                    BolusLog(
                        timestamp = System.currentTimeMillis(),
                        eventType = "SETTINGS_CHANGE",
                        status = "COMPLETED",
                        notes = changes.joinToString(", "),
                        bloodGlucose = 0.0,
                        carbs = 0.0,
                        standardDose = 0.0,
                        suggestedDose = 0.0,
                        administeredDose = 0.0,
                        isSportModeActive = false,
                        sportType = null,
                        sportIntensity = null,
                        sportDuration = null,
                        clinicalSuggestion = null
                    )
                )
            }
        }

        repository.updateTherapyType(state.therapyType)
        repository.updateGlucoseSource(state.glucoseSource)
        _uiState.value = _uiState.value.copy(isSaved = true, hasChanges = false)
    }

    private fun therapyDisplayName(type: String) = when (type) {
        "PUMP_AID" -> "Smart Pump"
        "PUMP_STANDARD" -> "Standard Pump"
        "MDI" -> "Pens (MDI)"
        "CGM" -> "CGM"
        "MANUAL" -> "Fingerstick"
        else -> type
    }
}

class TherapyProfileViewModelFactory(
    private val repository: BolusSettingsRepository,
    private val logRepository: BolusLogRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TherapyProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TherapyProfileViewModel(repository, logRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}