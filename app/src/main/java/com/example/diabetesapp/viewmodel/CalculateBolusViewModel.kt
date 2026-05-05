package com.example.diabetesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.algorithm.BreakdownEntry
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.models.CgmTrend
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.utils.AlgorithmEngine
import com.example.diabetesapp.utils.CgmHelper.getLatestBgFromXDrip
import com.example.diabetesapp.utils.IobCalculator
import com.example.diabetesapp.utils.WorkoutNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class InputMode { MANUAL, CALCULATE }

data class BolusInputState(
    val inputMode: InputMode = InputMode.CALCULATE,
    val currentDate: String = "",
    val currentTime: String = "",
    val bloodGlucose: String = "",
    val cgmTrendString: String = "",
    val minutesToLow: Int? = null,
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
    val breakdownSteps: List<BreakdownEntry> = emptyList(),
    val rescueCarbs: Int = 0,

    val bloodGlucoseError: String? = null,
    val carbsError: String? = null,
    val manualInsulinError: String? = null,
    val warningMessage: String? = null,
    val showAdvancedConfirmationDialog: Boolean = false,
    val showResultDialog: Boolean = false,

    // NEW: Context toggles
    val isContextExpanded: Boolean = false,
    val selectedFactor: String = "None"
)

class CalculateBolusViewModel(
    private val repository: BolusLogRepository,
    private val settingsRepository: BolusSettingsRepository // <-- 1. Inject the real settings here!
) : ViewModel() {

    private val _inputState = MutableStateFlow(BolusInputState())
    val inputState: StateFlow<BolusInputState> = _inputState.asStateFlow()

    // 2. Grab the live settings instead of dummy settings
    val settings: StateFlow<BolusSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BolusSettings()
    )

    init {
        updateCurrentDateTime()
        viewModelScope.launch {
            settings
                .filter { it.durationOfAction > 0 }
                .take(1)
                .collect { recalculateIob() }
        }
    }

    private fun recalculateIob() {
        viewModelScope.launch(Dispatchers.IO) {
            val logs = repository.getAllLogsImmediate()
            val currentSettings = settings.value
            val latestReading = if (currentSettings.isCgmEnabled) {
                try { getLatestBgFromXDrip() } catch (e: Exception) { null }
            } else null
            val result = IobCalculator.calculate(
                logs = logs,
                settings = currentSettings,
                xdripIob = latestReading?.iob,
                xdripTimestamp = latestReading?.timestamp
            )
            withContext(Dispatchers.Main) {
                _inputState.value = _inputState.value.copy(
                    activeInsulin = if (result.totalIob > 0.01)
                        String.format("%.1f", result.totalIob)
                    else ""
                )
            }
        }
    }

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

    fun toggleContextSection() {
        _inputState.value = _inputState.value.copy(isContextExpanded = !_inputState.value.isContextExpanded)
    }

    fun updateSelectedFactor(factor: String) {
        _inputState.value = _inputState.value.copy(selectedFactor = factor)
        // Auto-recalculate if values are already entered, but DO NOT open the dialog effectively
        // Actually, we should probably just store the factor. The user will hit calculate when they are ready.
        // If we want to update the background calculation, we can, but let's avoid popping the dialog if it's not open.
        if (_inputState.value.bloodGlucose.isNotEmpty() && _inputState.value.showResultDialog) {
            performCalculation(showDialog = true)
        }
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

    fun autoFetchLiveBgData() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                val twentyMinutesMs = 20 * 60 * 1000L
                val isCgmEnabled = settings.value.isCgmEnabled

                var successfulFetch = false

                if (isCgmEnabled) {
                    val cgmData = com.example.diabetesapp.utils.CgmHelper.getLatestBgFromXDrip()

                    if (cgmData != null && (currentTime - cgmData.timestamp) <= twentyMinutesMs) {
                        successfulFetch = true
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _inputState.update { it.copy(
                                bloodGlucose = cgmData.bgValue.toString(),
                                cgmTrendString = cgmData.trendString,
                                warningMessage = "Auto-filled from CGM!"
                            )}
                        }
                    }
                } else {
                    val lastManualLog = repository.getLatestManualBgLog()

                    if (lastManualLog != null && (currentTime - lastManualLog.timestamp) <= twentyMinutesMs) {
                        successfulFetch = true
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _inputState.update { it.copy(
                                bloodGlucose = lastManualLog.bloodGlucose.toInt().toString(),
                                cgmTrendString = "",
                                warningMessage = "Auto-filled from recent manual log."
                            )}
                        }
                    }
                }

                // --- NEW: The "No Reading" Fallback ---
                if (!successfulFetch) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _inputState.update { it.copy(
                            bloodGlucose = "", // Clear it so they don't use old data
                            cgmTrendString = "",
                            warningMessage = "No recent reading found (last 20m). Please check sensor or fingerstick."
                        )}
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("BG_Fetch", "Error fetching BG data", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _inputState.update { it.copy(warningMessage = "Error connecting to glucose source.") }
                }
            }
        }
    }

    fun adjustSuggestedDose(delta: Double) {
        val current = _inputState.value.userAdjustedDose ?: 0.0
        var newDose = maxOf(0.0, current + delta)
        newDose = Math.round(newDose * 10.0) / 10.0
        _inputState.value = _inputState.value.copy(userAdjustedDose = newDose)
    }

    fun proceedWithCalculation() {
        _inputState.value = _inputState.value.copy(showAdvancedConfirmationDialog = false)
        performCalculation(showDialog = false)
    }

    fun calculateBolus() {
        val state = _inputState.value
        val bg = state.bloodGlucose.toDoubleOrNull()
        if (bg == null) {
            _inputState.value = _inputState.value.copy(bloodGlucoseError = "BG required")
            return
        }
        performCalculation(showDialog = true)
    }

    private fun performCalculation(showDialog: Boolean) {
        viewModelScope.launch {
            val lastSport = withContext(Dispatchers.IO) {
                val todayStart = LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val recentSportLogs = repository.getCompletedSportLogsSince(todayStart)
                recentSportLogs
                    .sortedWith(compareByDescending<BolusLog> {
                        if (it.sportType == "Walking") 0 else 1
                    }.thenByDescending { it.sportDuration ?: 0f })
                    .firstOrNull()
            }

            val hoursSinceLastExercise = if (lastSport != null) {
                (System.currentTimeMillis() - lastSport.timestamp) / (1000f * 60f * 60f)
            } else -1f

            val lastExerciseSportType = lastSport?.sportType ?: ""
            val lastExerciseDurationMins = lastSport?.sportDuration?.toInt() ?: 0

            val state = _inputState.value
            val currentSettings = settings.value

            val logs = withContext(Dispatchers.IO) { repository.getAllLogsImmediate() }
            val latestReading = if (currentSettings.isCgmEnabled) {
                withContext(Dispatchers.IO) {
                    try { getLatestBgFromXDrip() } catch (e: Exception) { null }
                }
            } else null
            val iobResult = IobCalculator.calculate(
                logs = logs,
                settings = currentSettings,
                xdripIob = latestReading?.iob,
                xdripTimestamp = latestReading?.timestamp
            )
            val activeIob = iobResult.totalIob

            val cgmTrend = if (currentSettings.isCgmEnabled)
                CgmTrend.fromString(state.cgmTrendString)
            else
                CgmTrend.NONE

            val context = PatientContext(
                therapyType = currentSettings.therapyTypeEnum,
                bolusSettings = currentSettings,
                currentBG = state.bloodGlucose.toDoubleOrNull() ?: 0.0,
                hasCGM = currentSettings.isCgmEnabled,
                cgmTrend = cgmTrend,
                activeInsulinIOB = activeIob,
                plannedCarbs = state.carbs.toDoubleOrNull() ?: 0.0,
                isDoingSport = state.isSportModeActive,
                sportType = state.sportType,
                sportIntensity = state.sportIntensityValue.toInt(),
                sportDurationMins = state.sportDurationMinutes.toInt(),
                minutesUntilSport = state.minutesUntilSport.toInt(),
                isHighStress = state.selectedFactor == "Stress",
                isIllness = state.selectedFactor == "Illness",
                isExtremeHeat = state.selectedFactor == "Heat",
                timeOfDay = LocalTime.now(),
                dailySteps = 0L,
                basalDoseToday = 0.0,  // TODO: sum from DB in future iteration
                basalDurationHours = currentSettings.basalDurationHours,
                hasBasalConfigured = currentSettings.hasBasalConfigured,
                hoursSinceLastExercise = hoursSinceLastExercise,
                lastExerciseSportType = lastExerciseSportType,
                lastExerciseDurationMins = lastExerciseDurationMins
            )

            val recentPenDose = withContext(Dispatchers.IO) {
                val cutoff = System.currentTimeMillis() -
                    (currentSettings.durationOfAction * 60 * 60 * 1000).toLong()
                repository.getPenLogsAfter(cutoff).sumOf { it.administeredDose }
            }

            val decision = AlgorithmEngine.calculateClinicalAdvice(
                context,
                mapOf("recentManualPenDose" to recentPenDose)
            )

            val standardDose = if (currentSettings.isAidPump) {
                context.plannedCarbs / currentSettings.getCurrentIcr()
            } else {
                (context.plannedCarbs / currentSettings.getCurrentIcr()) +
                    maxOf(0.0, (context.currentBG - currentSettings.targetBG) / currentSettings.getCurrentIsf())
            }
            _inputState.value = _inputState.value.copy(
                activeInsulin = if (activeIob > 0.01) String.format("%.1f", activeIob) else "",
                standardDose = standardDose,
                calculatedDose = decision.suggestedInsulinDose,
                userAdjustedDose = decision.suggestedInsulinDose,
                sportReductionLog = decision.breakdownSteps
                    .filter { it.description.isNotBlank() }
                    .joinToString("\n\n") { "${it.label}\n${it.description}" },
                breakdownSteps = decision.breakdownSteps,
                rescueCarbs = decision.suggestedRescueCarbs,
                warningMessage = if (decision.suggestedRescueCarbs > 0) "Action Required: Algorithm suggests eating ${decision.suggestedRescueCarbs}g carbs instead of taking insulin." else null,
                showResult = true,
                showResultDialog = showDialog
            )
        }
    }

    fun logEntry(context: android.content.Context) {
        val state = _inputState.value
        if (state.calculatedDose == null || state.standardDose == null) return

        val now = System.currentTimeMillis()
        val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
        val carbs = state.carbs.toDoubleOrNull() ?: 0.0
        val dose = state.userAdjustedDose ?: state.calculatedDose
        val currentSettings = settings.value

        viewModelScope.launch {
            if (state.isSportModeActive) {
                // 1. Save CURRENT state (Insulin/BG)
                if (bg > 0 || carbs > 0 || dose > 0) {
                    val currentLog = if (currentSettings.isAidPump) {
                        BolusLog(
                            timestamp = now,
                            eventType = "SMART_BOLUS",
                            status = "COMPLETED",
                            bloodGlucose = bg, carbs = 0.0,
                            standardDose = 0.0, suggestedDose = 0.0, administeredDose = 0.0,
                            isSportModeActive = false, sportType = null, sportIntensity = null, sportDuration = null,
                            notes = buildAidNote(carbs, state.rescueCarbs, state.selectedFactor, state.notes),
                            clinicalSuggestion = state.sportReductionLog,
                            isHighStress = state.selectedFactor == "Stress",
                            isIllness = state.selectedFactor == "Illness",
                            isExtremeHeat = state.selectedFactor == "Heat"
                        )
                    } else {
                        BolusLog(
                            timestamp = now,
                            eventType = "SMART_BOLUS",
                            status = "COMPLETED",
                            bloodGlucose = bg, carbs = carbs,
                            standardDose = state.standardDose, suggestedDose = state.calculatedDose, administeredDose = dose,
                            isSportModeActive = false, sportType = null, sportIntensity = null, sportDuration = null,
                            notes = if (state.selectedFactor != "None") "${state.notes} [Factor: ${state.selectedFactor}]".trim() else state.notes,
                            clinicalSuggestion = state.sportReductionLog,
                            isHighStress = state.selectedFactor == "Stress",
                            isIllness = state.selectedFactor == "Illness",
                            isExtremeHeat = state.selectedFactor == "Heat"
                        )
                    }
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
                    notes = state.notes,
                    isHighStress = state.selectedFactor == "Stress",
                    isIllness = state.selectedFactor == "Illness",
                    isExtremeHeat = state.selectedFactor == "Heat"
                )
                repository.insert(sportLog)

                val notificationTime = sportStartTimestamp + (state.sportDurationMinutes.toLong() * 60 * 1000L)
                WorkoutNotificationManager.scheduleNotification(context, notificationTime)

                // Pre-sport reminders
                WorkoutNotificationManager.schedulePreSport30min(context, sportStartTimestamp)
                if (!currentSettings.isCgmEnabled) {
                    WorkoutNotificationManager.schedulePreSportBgCheck(context, sportStartTimestamp)
                }

            } else {
                // Normal immediate log
                val log = if (currentSettings.isAidPump) {
                    BolusLog(
                        timestamp = now,
                        eventType = "SMART_BOLUS",
                        status = "COMPLETED",
                        bloodGlucose = bg,
                        carbs = 0.0,
                        standardDose = 0.0,
                        suggestedDose = 0.0,
                        administeredDose = 0.0,
                        isSportModeActive = false,
                        sportType = null,
                        sportIntensity = null,
                        sportDuration = null,
                        clinicalSuggestion = state.sportReductionLog,
                        isHighStress = state.selectedFactor == "Stress",
                        isIllness = state.selectedFactor == "Illness",
                        isExtremeHeat = state.selectedFactor == "Heat",
                        notes = buildAidNote(carbs, state.rescueCarbs, state.selectedFactor, state.notes)
                    )
                } else {
                    BolusLog(
                        timestamp = now,
                        eventType = "SMART_BOLUS",
                        status = "COMPLETED",
                        bloodGlucose = bg,
                        carbs = carbs,
                        standardDose = state.standardDose,
                        suggestedDose = state.calculatedDose,
                        administeredDose = dose,
                        isSportModeActive = false,
                        sportType = null,
                        sportIntensity = null,
                        sportDuration = null,
                        clinicalSuggestion = state.sportReductionLog,
                        isHighStress = state.selectedFactor == "Stress",
                        isIllness = state.selectedFactor == "Illness",
                        isExtremeHeat = state.selectedFactor == "Heat",
                        notes = if (state.selectedFactor != "None") "${state.notes} [Factor: ${state.selectedFactor}]".trim() else state.notes
                    )
                }
                repository.insert(log)

                // Post-meal check (no CGM only)
                if (!currentSettings.isCgmEnabled) {
                    WorkoutNotificationManager.schedulePostMealCheck(context)
                }
                // Reschedule stale BG reminder when a BG reading is included
                if (bg > 0 && !currentSettings.isCgmEnabled) {
                    WorkoutNotificationManager.cancelStaleBgReminder(context)
                    WorkoutNotificationManager.scheduleStaleBgReminder(context, now)
                }
            }
            resetForm()
        }
    }

    private fun buildAidNote(carbs: Double, rescueCarbs: Int, selectedFactor: String, notes: String): String {
        val effectivePumpCarbs = maxOf(0, carbs.toInt() - rescueCarbs)
        val advisory = when {
            carbs > 0 && rescueCarbs == 0 -> "AID Advisory: Enter ${carbs.toInt()}g carbs into pump."
            carbs > 0 && rescueCarbs > 0 -> "AID Advisory: Treat low BG first — eat ${rescueCarbs}g fast-acting carbs before pump entry. Then enter ${effectivePumpCarbs}g into pump."
            rescueCarbs > 0 -> "AID Advisory: Low BG — eat ${rescueCarbs}g fast-acting carbs. No pump entry needed."
            else -> ""
        }
        return buildString {
            if (advisory.isNotEmpty()) append(advisory)
            if (selectedFactor != "None") {
                if (isNotEmpty()) append(" ")
                append("[Factor: $selectedFactor]")
            }
            if (notes.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(notes)
            }
        }.trim()
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
class CalculateBolusViewModelFactory(
    private val repository: BolusLogRepository,
    private val settingsRepository: BolusSettingsRepository // <-- 1. Add settings repo here
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalculateBolusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalculateBolusViewModel(repository, settingsRepository) as T // <-- 2. Pass it to the ViewModel
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}