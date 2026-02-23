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
import com.example.diabetesapp.utils.WorkoutNotificationManager
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

    // NEW: Time parsing states
    val plannedSportTime: String = "",
    val minutesUntilSport: Float = 0f,

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

    private val dummySettings = BolusSettings(
        icrMorning = 10f, icrNoon = 10f, icrEvening = 10f, icrNight = 10f,
        isfMorning = 50f, isfNoon = 50f, isfEvening = 50f, isfNight = 50f,
        targetBG = 100f
    )

    init { updateCurrentDateTime() }

    private fun updateCurrentDateTime() {
        val now = LocalDateTime.now()
        val timeString = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        _inputState.value = _inputState.value.copy(
            currentTime = timeString,
            currentDate = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            plannedSportTime = timeString // Default planned time to right now
        )
    }

    // --- NEW: Time Parsing Logic ---
    fun updatePlannedSportTime(timeStr: String) {
        try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val planned = LocalTime.parse(timeStr, formatter)
            val now = LocalTime.now()

            // Calculate difference in minutes
            var diff = java.time.Duration.between(now, planned).toMinutes()

            // Handle edge case: It's 11 PM and they pick 6 AM (diff is -17 hours, but they mean tomorrow)
            // If the time is more than 12 hours in the past, assume they mean tomorrow.
            if (diff < -12 * 60) {
                diff += 24 * 60
            }

            // VALIDATION: Is the calculated time still in the past?
            if (diff < 0) {
                _inputState.value = _inputState.value.copy(
                    warningMessage = "Cannot plan a workout in the past. Time reset to now.",
                    plannedSportTime = now.format(formatter),
                    minutesUntilSport = 0f
                )
            } else {
                // Valid future time
                _inputState.value = _inputState.value.copy(
                    plannedSportTime = timeStr,
                    minutesUntilSport = diff.toFloat()
                )
            }
        } catch (e: Exception) {
            // Failsafe
        }
    }

    // Add this tiny helper to dismiss the snackbar so it doesn't get stuck
    fun clearWarningMessage() {
        _inputState.value = _inputState.value.copy(warningMessage = null)
    }

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

    // NEW
    fun updateMinutesUntilSport(value: Float) { _inputState.value = _inputState.value.copy(minutesUntilSport = value) }

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

    private fun performCalculation() {
        val state = _inputState.value
        val context = PatientContext(
            therapyType = TherapyType.MDI_PENS,
            bolusSettings = dummySettings,
            currentBG = state.bloodGlucose.toDoubleOrNull() ?: 0.0,
            hasCGM = false,
            cgmTrend = CgmTrend.NONE,
            activeInsulinIOB = state.activeInsulin.toDoubleOrNull() ?: 0.0,
            plannedCarbs = state.carbs.toDoubleOrNull() ?: 0.0,
            isDoingSport = state.isSportModeActive,
            sportType = state.sportType,
            sportIntensity = state.sportIntensityValue.toInt(),
            sportDurationMins = state.sportDurationMinutes.toInt(),
            minutesUntilSport = state.minutesUntilSport.toInt(), // NOW WIRED TO SLIDER
            timeOfDay = LocalTime.now()
        )

        val decision = AlgorithmEngine.calculateClinicalAdvice(context)

        _inputState.value = _inputState.value.copy(
            standardDose = (context.plannedCarbs / dummySettings.getCurrentIcr()) + maxOf(0.0, (context.currentBG - dummySettings.targetBG) / dummySettings.getCurrentIsf()),
            calculatedDose = decision.suggestedInsulinDose,
            userAdjustedDose = decision.suggestedInsulinDose,
            sportReductionLog = decision.clinicalRationale,
            warningMessage = if (decision.suggestedRescueCarbs > 0) "⚠️ Action Required: Algorithm suggests eating ${decision.suggestedRescueCarbs}g carbs instead of taking insulin." else null,
            showResult = true,
            showResultDialog = true
        )
    }

    fun logEntry(context: android.content.Context) {
        val state = _inputState.value
        if (state.calculatedDose == null || state.standardDose == null) return

        val now = System.currentTimeMillis()
        val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
        val carbs = state.carbs.toDoubleOrNull() ?: 0.0
        val dose = state.userAdjustedDose ?: state.calculatedDose

        viewModelScope.launch {
            if (state.isSportModeActive) {
                // 1. Save CURRENT state (Insulin/BG)
                if (bg > 0 || carbs > 0 || dose > 0) {
                    val currentLog = BolusLog(
                        timestamp = now,
                        eventType = "SMART_BOLUS",
                        status = "COMPLETED",
                        bloodGlucose = bg, carbs = carbs,
                        standardDose = state.standardDose, suggestedDose = state.calculatedDose, administeredDose = dose,
                        isSportModeActive = false, sportType = null, sportIntensity = null, sportDuration = null,
                        notes = "Pre-workout preparation.",
                        // FIX: The insight goes here, with the insulin!
                        clinicalSuggestion = state.sportReductionLog
                    )
                    repository.insert(currentLog)
                }

                // 2. Save SPORT state
                val sportStartTimestamp = now + (state.minutesUntilSport.toLong() * 60 * 1000L)
                val sportLog = BolusLog(
                    timestamp = sportStartTimestamp,
                    eventType = "SPORT",
                    status = "PLANNED",
                    bloodGlucose = 0.0, carbs = 0.0, standardDose = 0.0, suggestedDose = 0.0, administeredDose = 0.0,
                    isSportModeActive = true,
                    sportType = state.sportType, sportIntensity = state.sportIntensity, sportDuration = state.sportDurationMinutes,
                    // FIX: Only put the insight here if they didn't take any insulin/carbs
                    clinicalSuggestion = if (bg == 0.0 && carbs == 0.0 && dose == 0.0) state.sportReductionLog else null,
                    notes = state.notes
                )
                repository.insert(sportLog)

                val notificationTime = sportStartTimestamp + (state.sportDurationMinutes.toLong() * 60 * 1000L)
                WorkoutNotificationManager.scheduleNotification(context, notificationTime)

            } else {
                // Normal immediate log
                val log = BolusLog(
                    timestamp = now, eventType = "SMART_BOLUS", status = "COMPLETED",
                    bloodGlucose = bg, carbs = carbs, standardDose = state.standardDose, suggestedDose = state.calculatedDose, administeredDose = dose,
                    isSportModeActive = false, sportType = null, sportIntensity = null, sportDuration = null,
                    clinicalSuggestion = state.sportReductionLog, notes = state.notes
                )
                repository.insert(log)
            }
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