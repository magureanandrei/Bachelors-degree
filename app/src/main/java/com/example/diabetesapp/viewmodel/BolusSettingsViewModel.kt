package com.example.diabetesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.models.BasalInsulinType
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.models.InsulinType
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.utils.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Draft State - holds temporary user edits before saving
 * This is the "working copy" that changes as the user types
 */
data class DraftSettings(
    val insulinType: InsulinType = InsulinType.NOVORAPID,
    val durationOfAction: String = "4.0",
    val targetBG: String = "100",

    val icrValues: List<String> = List(24) { "10" },
    val isfValues: List<String> = List(24) { "50" },

    val maxBolus: String = "15.0",
    val hypoLimit: String = "70",
    val hyperLimit: String = "180",

    val basalInsulinType: BasalInsulinType = BasalInsulinType.NONE,
    val basalDurationHours: String = ""   // Empty = not set; user must type
)

data class BolusSettingsUiState(
    val persistedSettings: BolusSettings = BolusSettings(), // Read-only: loaded from DB
    val draftSettings: DraftSettings = DraftSettings(), // Editable: working copy
    val expandedCard: ExpandableCard? = ExpandableCard.GENERAL, // Only one card expanded at a time
    val icrTimeDependent: Boolean = false, // Toggle for ICR time segments
    val isfTimeDependent: Boolean = false,  // Toggle for ISF time segments

    // Validation error states (real-time)
    val durationError: String? = null,
    val targetBGError: String? = null,
    val icrErrors: List<String?> = List(24) { null },
    val isfErrors: List<String?> = List(24) { null },
    val icrGlobalError: String? = null,
    val isfGlobalError: String? = null,

    val basalDurationError: String? = null,

    // Save feedback
    val saveMessage: String? = null,
    val isSaving: Boolean = false
)

enum class ExpandableCard {
    GENERAL,
    ICR,
    ISF,
    TARGET_BG,

    SAFETY_LIMITS,

    BASAL_INSULIN
}

enum class FieldType {
    DURATION,
    TARGET_BG,
    ICR_GLOBAL,
    ISF_GLOBAL,
    BASAL_DURATION
}

class BolusSettingsViewModel(
    private val repository: BolusSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BolusSettingsUiState())
    val uiState: StateFlow<BolusSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { persistedSettings ->
                // Only update draft state if it hasn't been modified yet
                // On first load, populate the draft from persisted data
                if (_uiState.value.draftSettings == DraftSettings()) {
                    _uiState.value = _uiState.value.copy(
                        persistedSettings = persistedSettings,
                        draftSettings = DraftSettings(
                            insulinType = persistedSettings.insulinType,
                            durationOfAction = persistedSettings.durationOfAction.toString(),
                            targetBG = persistedSettings.targetBG.toInt().toString(),
                            icrValues = persistedSettings.icrProfile.map { it.toInt().toString() },
                            isfValues = persistedSettings.isfProfile.map { it.toInt().toString() },
                            maxBolus = persistedSettings.maxBolus.toString(),
                            hypoLimit = persistedSettings.hypoLimit.toInt().toString(),
                            hyperLimit = persistedSettings.hyperLimit.toInt().toString(),
                            basalInsulinType = persistedSettings.basalInsulinType,
                            basalDurationHours = if (persistedSettings.basalDurationHours > 0f)
                                persistedSettings.basalDurationHours.toInt().toString() else ""
                        ),
                        icrTimeDependent = !persistedSettings.hasUniformIcr,
                        isfTimeDependent = !persistedSettings.hasUniformIsf
                    )
                } else {
                    // Just update the persisted settings reference
                    _uiState.value = _uiState.value.copy(persistedSettings = persistedSettings)
                }
            }
        }
    }

    fun toggleCardExpansion(card: ExpandableCard) {
        // Exclusive accordion: if clicking the same card, collapse it; otherwise expand the new one
        _uiState.value = _uiState.value.copy(
            expandedCard = if (_uiState.value.expandedCard == card) null else card
        )
    }

    private fun validateIcrAtHour(hour: Int, value: String) {
        val result = ValidationUtils.validateICR(value)
        val newErrors = _uiState.value.icrErrors.toMutableList().apply {
            this[hour] = if (result.isValid) null else result.errorMessage
        }
        _uiState.value = _uiState.value.copy(icrErrors = newErrors)
    }

    private fun validateIsfAtHour(hour: Int, value: String) {
        val result = ValidationUtils.validateISF(value)
        val newErrors = _uiState.value.isfErrors.toMutableList().apply {
            this[hour] = if (result.isValid) null else result.errorMessage
        }
        _uiState.value = _uiState.value.copy(isfErrors = newErrors)
    }

    fun toggleIcrTimeDependent(enabled: Boolean) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(icrTimeDependent = enabled)

        if (enabled) {
            val globalValue = currentDraft.icrValues.firstOrNull() ?: "10"
            _uiState.value = _uiState.value.copy(
                draftSettings = currentDraft.copy(icrValues = List(24) { globalValue })
            )
            for (i in 0..23) {
                validateIcrAtHour(i, globalValue)
            }
        }
    }

    fun toggleIsfTimeDependent(enabled: Boolean) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(isfTimeDependent = enabled)

        if (enabled) {
            val globalValue = currentDraft.isfValues.firstOrNull() ?: "50"
            _uiState.value = _uiState.value.copy(
                draftSettings = currentDraft.copy(isfValues = List(24) { globalValue })
            )
            for (i in 0..23) {
                validateIsfAtHour(i, globalValue)
            }
        }
    }

    fun updateGlobalIcr(value: String) {
        val current = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = current.copy(icrValues = List(24) { value })
        )
        validateFieldLive(FieldType.ICR_GLOBAL, value)
    }

    fun updateGlobalIsf(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(isfValues = List(24) { value })
        )
        validateFieldLive(FieldType.ISF_GLOBAL, value)
    }

    /**
     * Update draft state for insulin type
     * This can be saved immediately as it's a simple dropdown selection
     */
    fun updateInsulinType(type: InsulinType) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(insulinType = type)
        )
    }

    /**
     * Update draft state for duration of action
     */
    fun updateDurationOfAction(duration: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(durationOfAction = duration)
        )
        validateFieldLive(FieldType.DURATION, duration)
    }

    fun updateIcrAtHour(hour: Int, value: String) {
        val current = _uiState.value.draftSettings
        val newList = current.icrValues.toMutableList().apply { this[hour] = value }
        _uiState.value = _uiState.value.copy(
            draftSettings = current.copy(icrValues = newList)
        )
        validateIcrAtHour(hour, value)
    }

    fun updateIsfAtHour(hour: Int, value: String) {
        val current = _uiState.value.draftSettings
        val newList = current.isfValues.toMutableList().apply { this[hour] = value }
        _uiState.value = _uiState.value.copy(
            draftSettings = current.copy(isfValues = newList)
        )
        validateIsfAtHour(hour, value)
    }
    /**
     * Update draft state for target BG
     */
    fun updateTargetBG(target: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(targetBG = target)
        )
        validateFieldLive(FieldType.TARGET_BG, target)
    }

    fun isCardExpanded(card: ExpandableCard): Boolean {
        return _uiState.value.expandedCard == card
    }
    fun updateMaxBolus(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(draftSettings = currentDraft.copy(maxBolus = value))
    }
    fun updateHypoLimit(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(draftSettings = currentDraft.copy(hypoLimit = value))
    }
    fun updateHyperLimit(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(draftSettings = currentDraft.copy(hyperLimit = value))
    }

    fun updateBasalInsulinType(type: BasalInsulinType) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value =
            _uiState.value.copy(draftSettings = currentDraft.copy(basalInsulinType = type))
    }

    fun updateBasalDurationHours(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value =
            _uiState.value.copy(draftSettings = currentDraft.copy(basalDurationHours = value))
    }

    /**
     * Live validation - validates a single field as user types
     */
    fun validateFieldLive(field: FieldType, value: String) {
        val result = when (field) {
            FieldType.DURATION -> ValidationUtils.validateDuration(value)
            FieldType.TARGET_BG -> ValidationUtils.validateTargetBG(value)
            FieldType.ICR_GLOBAL -> ValidationUtils.validateICR(value)
            FieldType.ISF_GLOBAL -> ValidationUtils.validateISF(value)
            FieldType.BASAL_DURATION -> validateBasalDuration(value)
        }

        // Update the appropriate error state
        _uiState.value = when (field) {
            FieldType.DURATION -> _uiState.value.copy(durationError = if (result.isValid) null else result.errorMessage)
            FieldType.TARGET_BG -> _uiState.value.copy(targetBGError = if (result.isValid) null else result.errorMessage)
            FieldType.ICR_GLOBAL -> _uiState.value.copy(icrGlobalError = if (result.isValid) null else result.errorMessage)
            FieldType.ISF_GLOBAL -> _uiState.value.copy(isfGlobalError = if (result.isValid) null else result.errorMessage)
            FieldType.BASAL_DURATION -> _uiState.value.copy(basalDurationError = if (result.isValid) null else result.errorMessage)
        }
    }

    /**
     * Basal duration validation:
     * - Empty is OK (field is optional — user may not have filled it yet)
     * - If filled, must be a number between 8 and 72 hours
     */
    private fun validateBasalDuration(value: String): ValidationUtils.ValidationResult {
        if (value.isBlank()) return ValidationUtils.ValidationResult.success()
        val num = value.toFloatOrNull()
            ?: return ValidationUtils.ValidationResult.error("Enter a valid number")
        if (num < 8f) return ValidationUtils.ValidationResult.error("Must be at least 8 hours")
        if (num > 72f) return ValidationUtils.ValidationResult.error("Must be 72 hours or less")
        return ValidationUtils.ValidationResult.success()
    }

    fun areAllFieldsValid(): Boolean {
        val state = _uiState.value
        if (state.durationError != null || state.targetBGError != null) return false
        if (!state.icrTimeDependent) {
            if (state.icrGlobalError != null) return false
        } else {
            if (state.icrErrors.any { it != null }) return false
        }
        if (!state.isfTimeDependent) {
            if (state.isfGlobalError != null) return false
        } else {
            if (state.isfErrors.any { it != null }) return false
        }
        if (state.basalDurationError != null) return false
        return true
    }

    /**
     * Clear validation error for a specific field
     */
    fun clearError(fieldName: String) {
        _uiState.value = when (fieldName) {
            "duration" -> _uiState.value.copy(durationError = null)
            "targetBG" -> _uiState.value.copy(targetBGError = null)
            "icrGlobal" -> _uiState.value.copy(icrGlobalError = null)
            "isfGlobal" -> _uiState.value.copy(isfGlobalError = null)
            "basalDuration" -> _uiState.value.copy(basalDurationError = null)
            else -> _uiState.value
        }
    }

    /**
     * Clear save message
     */
    fun clearSaveMessage() {
        _uiState.value = _uiState.value.copy(saveMessage = null)
    }

    /**
     * Commit the draft state to the repository (database/SharedPreferences)
     * This is the ONLY method that writes to persistent storage
     */
    fun saveSettings() {
        val draft = _uiState.value.draftSettings

        // Re-validate all fields before saving
        if (!areAllFieldsValid()) {
            _uiState.value = _uiState.value.copy(
                saveMessage = "Please fix all validation errors before saving"
            )
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSaving = true)

                // Convert draft strings to floats and save to repository
                val durationValue = draft.durationOfAction.toFloatOrNull() ?: 4.0f
                val targetBGValue = draft.targetBG.toFloatOrNull() ?: 100f

                // Save all values to repository in one batch
                repository.updateInsulinType(draft.insulinType)
                repository.updateDurationOfAction(durationValue)
                repository.updateTargetBG(targetBGValue)

                // Save ICR values
                val icrFloats = draft.icrValues.map { it.toFloatOrNull() ?: 10f }
                repository.updateIcrProfile(icrFloats)

                val isfFloats = draft.isfValues.map { it.toFloatOrNull() ?: 50f }
                repository.updateIsfProfile(isfFloats)

                draft.maxBolus.toFloatOrNull()?.let { repository.updateMaxBolus(it) }
                draft.hypoLimit.toFloatOrNull()?.let { repository.updateHypoLimit(it) }
                draft.hyperLimit.toFloatOrNull()?.let { repository.updateHyperLimit(it) }

                repository.updateBasalInsulinType(draft.basalInsulinType)
                draft.basalDurationHours.toFloatOrNull()?.let {
                    repository.updateBasalDurationHours(it)
                }

                android.util.Log.d("BolusSettings", "=== Settings Saved Successfully ===")
                android.util.Log.d("BolusSettings", "Duration: ${draft.durationOfAction} hours")
                android.util.Log.d("BolusSettings", "Target BG: ${draft.targetBG} mg/dL")
                

                _uiState.value = _uiState.value.copy(
                    saveMessage = "Settings Saved Successfully! ✓",
                    isSaving = false
                )
            } catch (e: Exception) {
                android.util.Log.e("BolusSettings", "Error saving settings", e)
                _uiState.value = _uiState.value.copy(
                    saveMessage = "Error saving settings: ${e.message}",
                    isSaving = false
                )
            }
        }
    }
}

class BolusSettingsViewModelFactory(
    private val repository: BolusSettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BolusSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BolusSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
