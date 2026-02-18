package com.example.diabetesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.utils.BolusCalculatorHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class InputMode {
    MANUAL,
    CALCULATE
}

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
    val calculatedDose: Double? = null,    // The Suggested Dose
    val userAdjustedDose: Double? = null,  // The Administered Dose
    val showResult: Boolean = false,

    // --- THESIS: SPORT SIMULATOR VARIABLES ---
    val isSportModeActive: Boolean = false,
    val sportType: String = "Aerobic",
    val sportIntensityValue: Float = 2f,
    val sportIntensity: String = "Medium",
    val sportDurationMinutes: Float = 45f,
    val sportReductionLog: String = "",
    // -----------------------------------------

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

    fun setInputMode(mode: InputMode) { _inputState.value = _inputState.value.copy(inputMode = mode) }
    fun updateDate(value: String) { _inputState.value = _inputState.value.copy(currentDate = value) }
    fun updateTime(value: String) { _inputState.value = _inputState.value.copy(currentTime = value) }
    fun updateCorrectionAmount(value: String) { _inputState.value = _inputState.value.copy(correctionAmount = value) }
    fun updateBasalRateExcess(value: String) { _inputState.value = _inputState.value.copy(basalRateExcess = value) }
    fun updateActiveInsulin(value: String) { _inputState.value = _inputState.value.copy(activeInsulin = value) }
    fun updateNotes(value: String) { _inputState.value = _inputState.value.copy(notes = value) }

    fun resetState() {
        _inputState.value = BolusInputState()
        updateCurrentDateTime()
    }

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
        val intensityString = when (value.toInt()) {
            1 -> "Low"
            2 -> "Medium"
            3 -> "High"
            else -> "Medium"
        }
        _inputState.value = _inputState.value.copy(sportIntensityValue = value, sportIntensity = intensityString)
    }

    // User Override Stepper
    fun adjustSuggestedDose(delta: Double) {
        val current = _inputState.value.userAdjustedDose ?: 0.0
        var newDose = maxOf(0.0, current + delta)

        // Fix floating point precision (so 2.0 - 0.1 doesn't become 1.9000000001)
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
        if (bg < 70 && state.isSportModeActive) {
            _inputState.value = _inputState.value.copy(warningMessage = "⚠️ Danger: BG too low for sports. Eat carbs and do not bolus.")
            return
        }
        performCalculation()
    }

    private fun performCalculation() {
        val state = _inputState.value
        val bg = state.bloodGlucose.toFloatOrNull() ?: return
        val carbs = state.carbs.toFloatOrNull() ?: 0f
        val activeInsulin = state.activeInsulin.toFloatOrNull() ?: 0f

        val standardBolus = BolusCalculatorHelper.calculateBolus(
            carbs = carbs, currentBG = bg, settings = dummySettings, activeInsulin = activeInsulin
        ).toDouble()

        val finalDose = if (state.isSportModeActive) applySportReduction(standardBolus, state) else standardBolus
        val safeDose = maxOf(0.0, finalDose)

        _inputState.value = _inputState.value.copy(
            standardDose = standardBolus,
            calculatedDose = safeDose,
            userAdjustedDose = safeDose, // Initialize manual adjustment to the suggestion
            showResult = true,
            showResultDialog = true
        )
    }

    private fun applySportReduction(standardDose: Double, state: BolusInputState): Double {
        var reductionPercentage = 0.0
        var logMessage = "Standard Dose: ${String.format("%.1f", standardDose)}U\n"

        if (state.sportType == "Anaerobic") {
            logMessage += "Anaerobic activity detected. Minimizing insulin reduction to prevent hyperglycemia.\n"
            reductionPercentage = 0.10
        } else {
            logMessage += "Aerobic activity detected. Applying ISPAD reduction matrix.\n"
            when (state.sportIntensityValue.toInt()) {
                1 -> { reductionPercentage = 0.25; logMessage += "- Low Intensity: 25% base reduction.\n" }
                2 -> { reductionPercentage = 0.50; logMessage += "- Medium Intensity: 50% base reduction.\n" }
                3 -> { reductionPercentage = 0.75; logMessage += "- High Intensity: 75% base reduction.\n" }
            }
            if (state.sportDurationMinutes > 45f) {
                val extraTime = state.sportDurationMinutes - 45f
                val extraReduction = minOf(0.20, (extraTime / 15f) * 0.10)
                reductionPercentage += extraReduction
                logMessage += "- Duration > 45m: Added ${String.format("%.0f", extraReduction * 100)}% extra safety reduction.\n"
            }
        }
        val finalReduction = minOf(0.90, reductionPercentage)
        val newDose = standardDose * (1.0 - finalReduction)
        logMessage += "\nFinal Sport Dose: ${String.format("%.1f", newDose)}U (Reduced by ${String.format("%.0f", finalReduction * 100)}%)"

        _inputState.value = _inputState.value.copy(sportReductionLog = logMessage)
        return newDose
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