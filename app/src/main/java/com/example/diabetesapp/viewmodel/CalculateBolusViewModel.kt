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
    val calculatedDose: Double? = null,
    val standardDose: Double? = null,
    val showResult: Boolean = false,

    // --- THESIS: SPORT SIMULATOR VARIABLES ---
    val isSportModeActive: Boolean = false,
    val sportType: String = "Aerobic", // "Aerobic" or "Anaerobic"
    val sportIntensityValue: Float = 2f, // 1=Low, 2=Medium, 3=High
    val sportIntensity: String = "Medium",
    val sportDurationMinutes: Float = 45f,
    val sportReductionLog: String = "", // Explains WHY the dose was reduced
    // -----------------------------------------

    // Validation state
    val bloodGlucoseError: String? = null,
    val carbsError: String? = null,
    val manualInsulinError: String? = null,
    val warningMessage: String? = null,
    // Modal dialogs state
    val showAdvancedConfirmationDialog: Boolean = false,
    val showResultDialog: Boolean = false
)

class CalculateBolusViewModel(private val repository: BolusLogRepository) : ViewModel() {

    private val _inputState = MutableStateFlow(BolusInputState())
    val inputState: StateFlow<BolusInputState> = _inputState.asStateFlow()

    // For the MVP Playground, we hardcode a standard patient profile
    // rather than waiting for the DB to load.
    private val dummySettings = BolusSettings(
        icrMorning = 10f, icrNoon = 10f, icrEvening = 10f, icrNight = 10f,
        isfMorning = 50f, isfNoon = 50f, isfEvening = 50f, isfNight = 50f,
        targetBG = 100f
    )

    init {
        updateCurrentDateTime()
    }

    private fun updateCurrentDateTime() {
        val now = LocalDateTime.now()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val currentTime = now.format(timeFormatter)
        val currentDate = now.format(dateFormatter)
        _inputState.value = _inputState.value.copy(
            currentTime = currentTime,
            currentDate = currentDate
        )
    }

    // --- STANDARD UI UPDATES ---
    fun setInputMode(mode: InputMode) { _inputState.value = _inputState.value.copy(inputMode = mode) }
    fun updateDate(value: String) { _inputState.value = _inputState.value.copy(currentDate = value) }
    fun updateTime(value: String) { _inputState.value = _inputState.value.copy(currentTime = value) }
    fun updateCorrectionAmount(value: String) { _inputState.value = _inputState.value.copy(correctionAmount = value) }
    fun updateBasalRateExcess(value: String) { _inputState.value = _inputState.value.copy(basalRateExcess = value) }
    fun updateActiveInsulin(value: String) { _inputState.value = _inputState.value.copy(activeInsulin = value) }
    fun updateNotes(value: String) { _inputState.value = _inputState.value.copy(notes = value) }
    fun toggleAdvancedSection() { _inputState.value = _inputState.value.copy(isAdvancedExpanded = !_inputState.value.isAdvancedExpanded) }
    fun dismissAdvancedConfirmationDialog() { _inputState.value = _inputState.value.copy(showAdvancedConfirmationDialog = false) }
    fun dismissResultDialog() { _inputState.value = _inputState.value.copy(showResultDialog = false) }

    fun updateBloodGlucose(value: String) {
        _inputState.value = _inputState.value.copy(bloodGlucose = value, bloodGlucoseError = null, warningMessage = null)
    }

    fun updateCarbs(value: String) {
        _inputState.value = _inputState.value.copy(carbs = value, carbsError = null, warningMessage = null)
    }

    fun updateManualInsulin(value: String) {
        _inputState.value = _inputState.value.copy(manualInsulin = value, manualInsulinError = null, warningMessage = null)
    }

    // --- THESIS: SPORT UI UPDATES ---
    fun toggleSportMode(isActive: Boolean) {
        _inputState.value = _inputState.value.copy(isSportModeActive = isActive)
    }

    fun updateSportType(type: String) {
        _inputState.value = _inputState.value.copy(sportType = type)
    }

    fun updateSportIntensity(value: Float) {
        val intensityString = when (value.toInt()) {
            1 -> "Low (Walking)"
            2 -> "Medium (Jogging)"
            3 -> "High (Sprinting)"
            else -> "Medium"
        }
        _inputState.value = _inputState.value.copy(
            sportIntensityValue = value,
            sportIntensity = intensityString
        )
    }

    fun updateSportDuration(value: Float) {
        _inputState.value = _inputState.value.copy(sportDurationMinutes = value)
    }
    // ---------------------------------

    fun proceedWithCalculation() {
        _inputState.value = _inputState.value.copy(showAdvancedConfirmationDialog = false)
        performCalculation()
    }

    fun calculateBolus() {
        val state = _inputState.value

        // Safety Validation
        val bg = state.bloodGlucose.toDoubleOrNull()
        val carbs = state.carbs.toDoubleOrNull() ?: 0.0

        if (bg == null) {
            _inputState.value = _inputState.value.copy(bloodGlucoseError = "BG required")
            return
        }
        if (bg < 70 && state.isSportModeActive) {
            _inputState.value = _inputState.value.copy(warningMessage = "⚠️ Danger: BG too low for sports. Eat carbs and do not bolus.")
            return
        }

        // We skip the advanced confirmation dialog for the MVP to keep the playground fast
        performCalculation()
    }

    private fun performCalculation() {
        val state = _inputState.value

        val bg = state.bloodGlucose.toFloatOrNull() ?: return
        val carbs = state.carbs.toFloatOrNull() ?: 0f
        val activeInsulin = state.activeInsulin.toFloatOrNull() ?: 0f

        // 1. Calculate Standard Bolus using your Helper (Cast to Double)
        val standardBolus = BolusCalculatorHelper.calculateBolus(
            carbs = carbs,
            currentBG = bg,
            settings = dummySettings,
            activeInsulin = activeInsulin
        ).toDouble()

        // 2. Apply the Thesis Sport Algorithm (If Active)
        val finalDose = if (state.isSportModeActive) {
            applySportReduction(standardBolus, state)
        } else {
            standardBolus
        }

        // 3. Ensure dose is not negative
        val safeDose = maxOf(0.0, finalDose)

        _inputState.value = _inputState.value.copy(
            standardDose = standardBolus, // Save this for the database graph!
            calculatedDose = safeDose,
            showResult = true,
            showResultDialog = true
        )
    }

    /**
     * THESIS CORE ALGORITHM
     * This function implements the ISPAD Guidelines and T1DEXIP logic.
     */
    private fun applySportReduction(standardDose: Double, state: BolusInputState): Double {
        var reductionPercentage = 0.0
        var logMessage = "Standard Dose: ${String.format("%.1f", standardDose)}U\n"

        // Rule 1: Anaerobic Paradox (T1DEXIP Study)
        if (state.sportType == "Anaerobic") {
            logMessage += "Anaerobic activity detected. Minimizing insulin reduction to prevent hyperglycemia.\n"
            // Minor 10% reduction just for safety
            reductionPercentage = 0.10
        }
        // Rule 2: Aerobic Reduction Matrix (ISPAD 2022)
        else {
            logMessage += "Aerobic activity detected. Applying ISPAD reduction matrix.\n"

            // Base reduction based on Intensity
            when (state.sportIntensityValue.toInt()) {
                1 -> { // Low
                    reductionPercentage = 0.25
                    logMessage += "- Low Intensity: 25% base reduction.\n"
                }
                2 -> { // Medium
                    reductionPercentage = 0.50
                    logMessage += "- Medium Intensity: 50% base reduction.\n"
                }
                3 -> { // High
                    reductionPercentage = 0.75
                    logMessage += "- High Intensity: 75% base reduction.\n"
                }
            }

            // Modifier based on Duration (Linear Interpolation for > 45 mins)
            if (state.sportDurationMinutes > 45f) {
                // Add an extra 10% reduction for every 15 minutes over 45 mins (max 20% extra)
                val extraTime = state.sportDurationMinutes - 45f
                val extraReduction = minOf(0.20, (extraTime / 15f) * 0.10)
                reductionPercentage += extraReduction
                logMessage += "- Duration > 45m: Added ${String.format("%.0f", extraReduction * 100)}% extra safety reduction.\n"
            }
        }

        // Calculate final
        // Cap maximum reduction at 90% (Never reduce to absolute 0 if they are eating)
        val finalReduction = minOf(0.90, reductionPercentage)
        val newDose = standardDose * (1.0 - finalReduction)

        logMessage += "\nFinal Sport Dose: ${String.format("%.1f", newDose)}U (Reduced by ${String.format("%.0f", finalReduction * 100)}%)"

        // Save the log message to state so we can display it later
        _inputState.value = _inputState.value.copy(sportReductionLog = logMessage)

        return newDose
    }

    fun logEntry() {
        val state = _inputState.value

        // Ensure we actually have calculated numbers before trying to save
        if (state.calculatedDose == null || state.standardDose == null) return

        // 1. Create the Database Object
        val log = BolusLog(
            timestamp = System.currentTimeMillis(),
            bloodGlucose = state.bloodGlucose.toDoubleOrNull() ?: 0.0,
            carbs = state.carbs.toDoubleOrNull() ?: 0.0,
            standardDose = state.standardDose,
            finalDose = state.calculatedDose,
            isSportModeActive = state.isSportModeActive,
            sportType = if (state.isSportModeActive) state.sportType else null,
            sportIntensity = if (state.isSportModeActive) state.sportIntensity else null,
            sportDuration = if (state.isSportModeActive) state.sportDurationMinutes else null,
            notes = state.notes
        )

        // 2. Save it to Room in a Coroutine, then reset the UI
        viewModelScope.launch {
            repository.insert(log)
            resetForm()
        }
    }

    private fun resetForm() {
        updateCurrentDateTime()
        val currentSportMode = _inputState.value.isSportModeActive // Keep sport mode toggle state

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
            @Suppress("UNCHECKED_CAST")
            return CalculateBolusViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}