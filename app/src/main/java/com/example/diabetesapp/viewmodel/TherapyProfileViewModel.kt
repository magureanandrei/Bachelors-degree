package com.example.diabetesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

class TherapyProfileViewModel(private val repository: BolusSettingsRepository) : ViewModel() {
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
        repository.updateTherapyType(state.therapyType)
        repository.updateGlucoseSource(state.glucoseSource)
        _uiState.value = _uiState.value.copy(isSaved = true, hasChanges = false)
    }
}

class TherapyProfileViewModelFactory(private val repository: BolusSettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TherapyProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return TherapyProfileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}