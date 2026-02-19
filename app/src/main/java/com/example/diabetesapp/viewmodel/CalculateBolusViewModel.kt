package com.example.diabetesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.models.CgmTrend
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.utils.AlgorithmEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class InputMode { MANUAL, CALCULATE }

data class BolusInputState(
    val inputMode: InputMode = InputMode.CALCULATE,
    val currentDate: String = "",
    val currentTime: String = "",
    val bloodGlucose: String = "",
    val carbs: String = "",
    val manualInsulin: String = "",
    val correctionAmount: String = "",
    val basalRateExcess: String = "",
    val activeInsulin: String = "",
    val notes: String = "",
    val isAdvancedExpanded: Boolean = false,

    // Dose Tracking
    val standardDose: Double? = null,
    val calculatedDose: Double? = null,
    val userAdjustedDose: Double? = null,
    val showResult: Boolean = false,

    // Sport Variables
    val isSportModeActive: Boolean = false,
    val sportType: String = "Aerobic",
    val sportIntensityValue: Float = 2f,
    val sportIntensity: String = "Medium",
    val sportDurationMinutes: Float = 45f,
    val sportReductionLog: String = "",

    val bloodGlucoseError: String? = null,
    val carbsError: String? = null,
    val manualInsulinError: String? = null,
    val warningMessage: String? = null,
    val showAdvancedConfirmationDialog: Boolean = false,
    val showResultDialog: Boolean = false
)

class CalculateBolusViewModel(private val repository: BolusLogRepository) : ViewModel() {
    private val _inputState = MutableStateFlow(BolusInputState())
    val inputState: StateFlow<BolusInputState> = _inputState.asStateFlow()

    // Temporary dummy settings until we build the Profile Tab
    private val dummySettings = BolusSettings(
        icrMorning = 10f, icrNoon = 10f, icrEvening = 10f, icrNight = 10f,
        isfMorning = 50f, isfNoon = 50f, isfEvening = 50f, isfNight = 50f,
        targetBG = 100f
    )

    init { updateCurrentDateTime() }

    private fun updateCurrentDateTime() {
        val now = LocalDateTime.now()
        _inputState.value = _inputState.value.copy(
            currentTime = now.format(DateTimeFormatter.ofPattern("HH:mm")),
            currentDate = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        )
    }

    // Standard UI Updaters
    fun setInputMode(mode: InputMode) { _inputState.value = _inputState.value.copy(inputMode = mode) }
    fun updateDate(value: String) { _inputState.value = _inputState.value.copy(currentDate = value) }
    fun updateTime(value: String) { _inputState.value = _inputState.value.copy(currentTime = value) }
    fun updateCorrectionAmount(value: String) { _inputState.value = _inputState.value.copy(correctionAmount = value) }
    fun updateBasalRateExcess(value: String) { _inputState.value = _inputState.value.copy(basalRateExcess = value) }
    fun updateActiveInsulin(value: String) { _inputState.value = _inputState.value.copy(activeInsulin = value) }
    fun updateNotes(value: String) { _inputState.value = _inputState.value.copy(notes = value) }
    fun resetState() { _inputState.value = BolusInputState(); updateCurrentDateTime() }
    fun toggleAdvancedSection() { _inputState.value = _inputState.value.copy(isAdvancedExpanded = !_inputState.value.isAdvancedExpanded) }
    fun dismissAdvancedConfirmationDialog() { _inputState.value = _inputState.value.copy(showAdvancedConfirmationDialog = false) }
    fun dismissResultDialog() { _inputState.value = _inputState.value.copy(showResultDialog = false) }
    fun updateBloodGlucose(value: String) { _inputState.value = _inputState.value.copy(bloodGlucose = value, bloodGlucoseError = null, warningMessage = null) }
    fun updateCarbs(value: String) { _inputState.value = _inputState.value.copy(carbs = value, carbsError = null, warningMessage = null) }
    fun updateManualInsulin(value: String) { _inputState.value = _inputState.value.copy(manualInsulin = value, manualInsulinError = null, warningMessage = null) }
    fun toggleSportMode(isActive: Boolean) { _inputState.value = _inputState.value.copy(isSportModeActive = isActive) }
    fun updateSportType(type: String) { _inputState.value = _inputState.value.copy(sportType = type) }
    fun updateSportDuration(value: Float) { _inputState.value = _inputState.value.copy(sportDurationMinutes = value) }

    fun updateSportIntensity(value: Float) {
        val intensityString = when (value.toInt()) { 1 -> "Low"; 2 -> "Medium"; 3 -> "High"; else -> "Medium" }
        _inputState.value = _inputState.value.copy(sportIntensityValue = value, sportIntensity = intensityString)
    }

    fun adjustSuggestedDose(delta: Double) {
        val current = _inputState.value.userAdjustedDose ?: 0.0
        var newDose = maxOf(0.0, current + delta)
        newDose = Math.round(newDose * 10.0) / 10.0
        _inputState.value = _inputState.value.copy(userAdjustedDose = newDose)
    }

    fun proceedWithCalculation() {
        _inputState.value = _inputState.value.copy(showAdvancedConfirmationDialog = false)
        performCalculation()
    }

    fun calculateBolus() {
        val state = _inputState.value
        val bg = state.bloodGlucose.toDoubleOrNull()
        if (bg == null) {
            _inputState.value = _inputState.value.copy(bloodGlucoseError = "BG required")
            return
        }
        performCalculation()
    }

    // --- THE NEW CENTRALIZED ENGINE CALL ---
    private fun performCalculation() {
        val state = _inputState.value

        // 1. Build the Patient Context
        val context = PatientContext(
            therapyType = TherapyType.MDI_PENS, // Hardcoded until we build Settings
            bolusSettings = dummySettings,
            currentBG = state.bloodGlucose.toDoubleOrNull() ?: 0.0,
            hasCGM = false, // Hardcoded for MVP until we add UI toggle
            cgmTrend = CgmTrend.NONE,
            activeInsulinIOB = state.activeInsulin.toDoubleOrNull() ?: 0.0,
            plannedCarbs = state.carbs.toDoubleOrNull() ?: 0.0,
            isDoingSport = state.isSportModeActive,
            sportType = state.sportType,
            sportIntensity = state.sportIntensityValue.toInt(),
            sportDurationMins = state.sportDurationMinutes.toInt(),
            minutesUntilSport = 0, // "Right Now"
            timeOfDay = LocalTime.now()
        )

        // 2. Feed it to the Engine
        val decision = AlgorithmEngine.calculateClinicalAdvice(context)

        // 3. Update the UI
        _inputState.value = _inputState.value.copy(
            standardDose = (context.plannedCarbs / dummySettings.getCurrentIcr()) + maxOf(0.0, (context.currentBG - dummySettings.targetBG) / dummySettings.getCurrentIsf()), // Just for visual comparison
            calculatedDose = decision.suggestedInsulinDose,
            userAdjustedDose = decision.suggestedInsulinDose,
            sportReductionLog = decision.clinicalRationale,
            warningMessage = if (decision.suggestedRescueCarbs > 0) "⚠️ Low BG Warning: Algorithm suggests eating ${decision.suggestedRescueCarbs}g carbs instead of taking insulin." else null,
            showResult = true,
            showResultDialog = true
        )
    }

    fun logEntry() {
        val state = _inputState.value
        if (state.calculatedDose == null || state.standardDose == null) return

        val log = BolusLog(
            timestamp = System.currentTimeMillis(),
            eventType = "SMART_BOLUS",
            bloodGlucose = state.bloodGlucose.toDoubleOrNull() ?: 0.0,
            carbs = state.carbs.toDoubleOrNull() ?: 0.0,
            standardDose = state.standardDose,
            suggestedDose = state.calculatedDose,
            administeredDose = state.userAdjustedDose ?: state.calculatedDose,
            isSportModeActive = state.isSportModeActive,
            clinicalSuggestion = if (state.isSportModeActive) state.sportReductionLog else null,
            sportType = if (state.isSportModeActive) state.sportType else null,
            sportIntensity = if (state.isSportModeActive) state.sportIntensity else null,
            sportDuration = if (state.isSportModeActive) state.sportDurationMinutes else null,
            notes = state.notes
        )

        viewModelScope.launch {
            repository.insert(log)
            resetForm()
        }
    }

    private fun resetForm() {
        updateCurrentDateTime()
        val currentSportMode = _inputState.value.isSportModeActive
        _inputState.value = BolusInputState(
            currentTime = _inputState.value.currentTime,
            currentDate = _inputState.value.currentDate,
            isSportModeActive = currentSportMode
        )
    }
}

class CalculateBolusViewModelFactory(private val repository: BolusLogRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalculateBolusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return CalculateBolusViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}