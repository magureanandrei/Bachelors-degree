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

    // ICR values as strings for text field binding
    val icrMorning: String = "10",
    val icrNoon: String = "10",
    val icrEvening: String = "10",
    val icrNight: String = "10",

    // ISF values as strings for text field binding
    val isfMorning: String = "50",
    val isfNoon: String = "50",
    val isfEvening: String = "50",
    val isfNight: String = "50",

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
    val icrGlobalError: String? = null,
    val icrMorningError: String? = null,
    val icrNoonError: String? = null,
    val icrEveningError: String? = null,
    val icrNightError: String? = null,
    val isfGlobalError: String? = null,
    val isfMorningError: String? = null,
    val isfNoonError: String? = null,
    val isfEveningError: String? = null,
    val isfNightError: String? = null,

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
    ICR_MORNING,
    ICR_NOON,
    ICR_EVENING,
    ICR_NIGHT,
    ISF_GLOBAL,
    ISF_MORNING,
    ISF_NOON,
    ISF_EVENING,
    ISF_NIGHT,

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
                            icrMorning = persistedSettings.icrMorning.toInt().toString(),
                            icrNoon = persistedSettings.icrNoon.toInt().toString(),
                            icrEvening = persistedSettings.icrEvening.toInt().toString(),
                            icrNight = persistedSettings.icrNight.toInt().toString(),
                            isfMorning = persistedSettings.isfMorning.toInt().toString(),
                            isfNoon = persistedSettings.isfNoon.toInt().toString(),
                            isfEvening = persistedSettings.isfEvening.toInt().toString(),
                            isfNight = persistedSettings.isfNight.toInt().toString(),
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

    fun toggleIcrTimeDependent(enabled: Boolean, globalValue: String = "") {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(icrTimeDependent = enabled)

        // When enabling time-dependent mode, copy global value to all segments
        if (enabled && globalValue.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                draftSettings = currentDraft.copy(
                    icrMorning = globalValue,
                    icrNoon = globalValue,
                    icrEvening = globalValue,
                    icrNight = globalValue
                )
            )
            // Validate all segments
            validateFieldLive(FieldType.ICR_MORNING, globalValue)
            validateFieldLive(FieldType.ICR_NOON, globalValue)
            validateFieldLive(FieldType.ICR_EVENING, globalValue)
            validateFieldLive(FieldType.ICR_NIGHT, globalValue)
        }
    }

    fun toggleIsfTimeDependent(enabled: Boolean, globalValue: String = "") {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(isfTimeDependent = enabled)

        // When enabling time-dependent mode, copy global value to all segments
        if (enabled && globalValue.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                draftSettings = currentDraft.copy(
                    isfMorning = globalValue,
                    isfNoon = globalValue,
                    isfEvening = globalValue,
                    isfNight = globalValue
                )
            )
            // Validate all segments
            validateFieldLive(FieldType.ISF_MORNING, globalValue)
            validateFieldLive(FieldType.ISF_NOON, globalValue)
            validateFieldLive(FieldType.ISF_EVENING, globalValue)
            validateFieldLive(FieldType.ISF_NIGHT, globalValue)
        }
    }

    /**
     * Update draft state for global ICR (simple mode)
     * Updates all 4 segments with the same value
     */
    fun updateGlobalIcr(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(
                icrMorning = value,
                icrNoon = value,
                icrEvening = value,
                icrNight = value
            )
        )
        validateFieldLive(FieldType.ICR_GLOBAL, value)
    }

    /**
     * Update draft state for global ISF (simple mode)
     * Updates all 4 segments with the same value
     */
    fun updateGlobalIsf(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(
                isfMorning = value,
                isfNoon = value,
                isfEvening = value,
                isfNight = value
            )
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

    // ICR update methods for 4 time segments - UPDATE DRAFT ONLY
    fun updateIcrMorning(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(icrMorning = value)
        )
        validateFieldLive(FieldType.ICR_MORNING, value)
    }

    fun updateIcrNoon(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(icrNoon = value)
        )
        validateFieldLive(FieldType.ICR_NOON, value)
    }

    fun updateIcrEvening(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(icrEvening = value)
        )
        validateFieldLive(FieldType.ICR_EVENING, value)
    }

    fun updateIcrNight(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(icrNight = value)
        )
        validateFieldLive(FieldType.ICR_NIGHT, value)
    }

    // ISF update methods for 4 time segments - UPDATE DRAFT ONLY
    fun updateIsfMorning(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(isfMorning = value)
        )
        validateFieldLive(FieldType.ISF_MORNING, value)
    }

    fun updateIsfNoon(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(isfNoon = value)
        )
        validateFieldLive(FieldType.ISF_NOON, value)
    }

    fun updateIsfEvening(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(isfEvening = value)
        )
        validateFieldLive(FieldType.ISF_EVENING, value)
    }

    fun updateIsfNight(value: String) {
        val currentDraft = _uiState.value.draftSettings
        _uiState.value = _uiState.value.copy(
            draftSettings = currentDraft.copy(isfNight = value)
        )
        validateFieldLive(FieldType.ISF_NIGHT, value)
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
            FieldType.ICR_GLOBAL, FieldType.ICR_MORNING, FieldType.ICR_NOON,
            FieldType.ICR_EVENING, FieldType.ICR_NIGHT -> ValidationUtils.validateICR(value)
            FieldType.ISF_GLOBAL, FieldType.ISF_MORNING, FieldType.ISF_NOON,
            FieldType.ISF_EVENING, FieldType.ISF_NIGHT -> ValidationUtils.validateISF(value)
            FieldType.BASAL_DURATION -> validateBasalDuration(value)
        }

        // Update the appropriate error state
        _uiState.value = when (field) {
            FieldType.DURATION -> _uiState.value.copy(durationError = if (result.isValid) null else result.errorMessage)
            FieldType.TARGET_BG -> _uiState.value.copy(targetBGError = if (result.isValid) null else result.errorMessage)
            FieldType.ICR_GLOBAL -> _uiState.value.copy(icrGlobalError = if (result.isValid) null else result.errorMessage)
            FieldType.ICR_MORNING -> _uiState.value.copy(icrMorningError = if (result.isValid) null else result.errorMessage)
            FieldType.ICR_NOON -> _uiState.value.copy(icrNoonError = if (result.isValid) null else result.errorMessage)
            FieldType.ICR_EVENING -> _uiState.value.copy(icrEveningError = if (result.isValid) null else result.errorMessage)
            FieldType.ICR_NIGHT -> _uiState.value.copy(icrNightError = if (result.isValid) null else result.errorMessage)
            FieldType.ISF_GLOBAL -> _uiState.value.copy(isfGlobalError = if (result.isValid) null else result.errorMessage)
            FieldType.ISF_MORNING -> _uiState.value.copy(isfMorningError = if (result.isValid) null else result.errorMessage)
            FieldType.ISF_NOON -> _uiState.value.copy(isfNoonError = if (result.isValid) null else result.errorMessage)
            FieldType.ISF_EVENING -> _uiState.value.copy(isfEveningError = if (result.isValid) null else result.errorMessage)
            FieldType.ISF_NIGHT -> _uiState.value.copy(isfNightError = if (result.isValid) null else result.errorMessage)
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
            if (listOf(state.icrMorningError, state.icrNoonError,
                    state.icrEveningError, state.icrNightError).any { it != null }) return false
        }
        if (!state.isfTimeDependent) {
            if (state.isfGlobalError != null) return false
        } else {
            if (listOf(state.isfMorningError, state.isfNoonError,
                    state.isfEveningError, state.isfNightError).any { it != null }) return false
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
            "icrMorning" -> _uiState.value.copy(icrMorningError = null)
            "icrNoon" -> _uiState.value.copy(icrNoonError = null)
            "icrEvening" -> _uiState.value.copy(icrEveningError = null)
            "icrNight" -> _uiState.value.copy(icrNightError = null)
            "isfGlobal" -> _uiState.value.copy(isfGlobalError = null)
            "isfMorning" -> _uiState.value.copy(isfMorningError = null)
            "isfNoon" -> _uiState.value.copy(isfNoonError = null)
            "isfEvening" -> _uiState.value.copy(isfEveningError = null)
            "isfNight" -> _uiState.value.copy(isfNightError = null)
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
                draft.icrMorning.toFloatOrNull()?.let { repository.updateIcrMorning(it) }
                draft.icrNoon.toFloatOrNull()?.let { repository.updateIcrNoon(it) }
                draft.icrEvening.toFloatOrNull()?.let { repository.updateIcrEvening(it) }
                draft.icrNight.toFloatOrNull()?.let { repository.updateIcrNight(it) }

                // Save ISF values
                draft.isfMorning.toFloatOrNull()?.let { repository.updateIsfMorning(it) }
                draft.isfNoon.toFloatOrNull()?.let { repository.updateIsfNoon(it) }
                draft.isfEvening.toFloatOrNull()?.let { repository.updateIsfEvening(it) }
                draft.isfNight.toFloatOrNull()?.let { repository.updateIsfNight(it) }

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
                android.util.Log.d("BolusSettings", "ICR Morning: 1:${draft.icrMorning}")
                android.util.Log.d("BolusSettings", "ISF Morning: 1:${draft.isfMorning}")

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

    /**
     * Validate and save all settings (legacy method for compatibility)
     * Now delegates to saveSettings() after validation
     */
    @Suppress("UNUSED_PARAMETER")
    @Deprecated("Use saveSettings() instead", ReplaceWith("saveSettings()"))
    fun validateAndSave(
        durationText: String,
        targetBGText: String,
        icrGlobalText: String,
        icrMorningText: String,
        icrNoonText: String,
        icrEveningText: String,
        icrNightText: String,
        isfGlobalText: String,
        isfMorningText: String,
        isfNoonText: String,
        isfEveningText: String,
        isfNightText: String
    ): Boolean {
        // All validation is now done in real-time
        // Just check if there are any errors and save
        if (!areAllFieldsValid()) {
            _uiState.value = _uiState.value.copy(
                saveMessage = "Please fix invalid fields before saving"
            )
            return false
        }

        saveSettings()
        return true
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
