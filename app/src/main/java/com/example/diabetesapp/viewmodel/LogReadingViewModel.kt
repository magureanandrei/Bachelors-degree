package com.example.diabetesapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.models.BgFetchStatus
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.models.CgmTrend
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.utils.AlgorithmEngine
import com.example.diabetesapp.utils.CgmHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

enum class InsightType { ON_TRACK, SUGGESTION, WARNING }

data class LogInsight(val type: InsightType, val title: String, val message: String)

data class LogReadingState(
    val eventTime: String = "",
    val eventDate: String = "Today",
    val bloodGlucose: String = "",
    val carbs: String = "",
    val manualInsulin: String = "",
    val notes: String = "",
    val isSportModeActive: Boolean = false,
    val sportType: String = "Aerobic",
    val sportIntensityValue: Float = 2f,
    val sportIntensity: String = "Medium",
    val sportDurationMinutes: Float = 45f,
    val currentInsight: LogInsight? = null,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
    val pendingClinicalSuggestion: String? = null,
    val bgFetchStatus: BgFetchStatus = BgFetchStatus.IDLE,
    val showAidPenWarning: Boolean = false,
    val basalInsulin : String = ""
)

class LogReadingViewModel(
    private val repository: BolusLogRepository,
    private val settingsRepository: BolusSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogReadingState())
    val uiState: StateFlow<LogReadingState> = _uiState.asStateFlow()

    val settings: StateFlow<BolusSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BolusSettings()
    )

    init { resetState() }

    fun updateEventDate(date: String) {
        _uiState.value = _uiState.value.copy(eventDate = date, bgFetchStatus = BgFetchStatus.IDLE)
        fetchBgForTimestamp(getEventTimestamp())
    }

    fun updateTime(value: String) {
        val timestamp = getTimestampForTime(value, _uiState.value.eventDate)
        val now = System.currentTimeMillis()
        val yesterdayMidnight = getYesterdayMidnight()

        when {
            timestamp > now -> {
                val nowTime = LocalTime.now()
                _uiState.value = _uiState.value.copy(
                    eventTime = nowTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    errorMessage = "Cannot log events in the future. Reset to current time."
                )
            }
            timestamp < yesterdayMidnight -> {
                _uiState.value = _uiState.value.copy(
                    eventTime = "00:00",
                    eventDate = "Yesterday",
                    errorMessage = "Cannot log events older than yesterday."
                )
            }
            else -> {
                _uiState.value = _uiState.value.copy(
                    eventTime = value,
                    errorMessage = null,
                    bgFetchStatus = BgFetchStatus.IDLE
                )
                fetchBgForTimestamp(timestamp)
            }
        }
    }
    fun updateBasalInsulin(value: String) {
        _uiState.value = _uiState.value.copy(basalInsulin = value, errorMessage = null)
    }

    fun fetchBgForTimestamp(timestamp: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val windowMs = 20 * 60 * 1000L  // 20 min window for manual

                if (settings.value.isCgmEnabled) {
                    val history = CgmHelper.getBgHistoryFromXDrip()
                    val closest = history
                        .filter { Math.abs(it.timestamp - timestamp) <= 10 * 60 * 1000L }
                        .minByOrNull { Math.abs(it.timestamp - timestamp) }
                    if (closest != null) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                bloodGlucose = closest.bgValue.toString(),
                                bgFetchStatus = BgFetchStatus.FOUND
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(bgFetchStatus = BgFetchStatus.NOT_FOUND)
                        }
                    }
                } else {
                    // Manual path — check local DB
                    val closest = repository.getLogsNearTimestamp(timestamp, windowMs)
                        .filter { it.bloodGlucose > 0 }
                        .minByOrNull { Math.abs(it.timestamp - timestamp) }
                    if (closest != null) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                bloodGlucose = closest.bloodGlucose.toInt().toString(),
                                bgFetchStatus = BgFetchStatus.FOUND
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(bgFetchStatus = BgFetchStatus.NOT_FOUND)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LogReading", "BG fetch failed: ${e.message}")
            }
        }
    }

     fun getEventTimestamp(): Long =
        getTimestampForTime(_uiState.value.eventTime, _uiState.value.eventDate)

    private fun getTimestampForTime(timeStr: String, date: String): Long {
        val calendar = Calendar.getInstance()
        if (date == "Yesterday") calendar.add(Calendar.DAY_OF_YEAR, -1)
        try {
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                calendar.set(Calendar.MINUTE, parts[1].toInt())
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
        } catch (e: Exception) { }
        return calendar.timeInMillis
    }

    private fun getYesterdayMidnight(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun updateBloodGlucose(value: String) {
        _uiState.value = _uiState.value.copy(
            bloodGlucose = value,
            errorMessage = null,
            bgFetchStatus = BgFetchStatus.IDLE
        )
    }    fun updateCarbs(value: String) { _uiState.value = _uiState.value.copy(carbs = value, errorMessage = null) }
    fun updateManualInsulin(value: String) { _uiState.value = _uiState.value.copy(manualInsulin = value, errorMessage = null) }
    fun updateNotes(value: String) { _uiState.value = _uiState.value.copy(notes = value) }
    fun toggleSportMode(isActive: Boolean) { _uiState.value = _uiState.value.copy(isSportModeActive = isActive, errorMessage = null) }
    fun updateSportType(type: String) { _uiState.value = _uiState.value.copy(sportType = type) }
    fun updateSportDuration(value: Float) { _uiState.value = _uiState.value.copy(sportDurationMinutes = value) }
    fun updateSportIntensity(value: Float) {
        val intensityString = when (value.toInt()) { 1 -> "Low"; 3 -> "High"; else -> "Medium" }
        _uiState.value = _uiState.value.copy(sportIntensityValue = value, sportIntensity = intensityString)
    }
    fun dismissInsightDialog() { _uiState.value = _uiState.value.copy(currentInsight = null) }
    fun dismissAidWarning() { _uiState.value = _uiState.value.copy(showAidPenWarning = false) }
    fun confirmAidPenCorrection() {
        _uiState.value = _uiState.value.copy(showAidPenWarning = false)
        executeSave()
    }

    fun resetState() {
        val now = LocalTime.now()
        _uiState.value = LogReadingState(
            eventTime = now.format(DateTimeFormatter.ofPattern("HH:mm")),
            basalInsulin = ""  // ADD THIS
        )
    }

    fun analyzeLog() {
        val state = _uiState.value
        val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
        val carbs = state.carbs.toDoubleOrNull() ?: 0.0
        val insulin = state.manualInsulin.toDoubleOrNull() ?: 0.0

        if (bg == 0.0 && carbs == 0.0 && insulin == 0.0 && !state.isSportModeActive && (state.basalInsulin.toDoubleOrNull()
                ?: 0.0) == 0.0
        ) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter data or switch to Sport Mode.")
            return
        }

        val eventTimestamp = getEventTimestamp()
        if (eventTimestamp > System.currentTimeMillis()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Cannot log events in the future.")
            return
        }

        // AID pen correction warning
        if (settings.value.isAidPump && insulin > 0) {
            _uiState.value = _uiState.value.copy(showAidPenWarning = true)
            return
        }

        val currentSettings = settings.value
        val context = PatientContext(
            therapyType = currentSettings.therapyTypeEnum,
            bolusSettings = currentSettings,
            currentBG = bg,
            hasCGM = currentSettings.isCgmEnabled,
            cgmTrend = CgmTrend.NONE,
            activeInsulinIOB = insulin,
            plannedCarbs = carbs,
            isDoingSport = state.isSportModeActive,
            sportType = state.sportType,
            sportIntensity = state.sportIntensityValue.toInt(),
            sportDurationMins = state.sportDurationMinutes.toInt(),
            minutesUntilSport = 0,
            timeOfDay = LocalTime.now(),
            dailySteps = 0L
        )

        val decision = AlgorithmEngine.calculateClinicalAdvice(context)

        val insight = when {
            decision.suggestedRescueCarbs > 0 && carbs < decision.suggestedRescueCarbs ->
                LogInsight(InsightType.WARNING, "Action Required", decision.clinicalRationale.ifBlank { "You need fast-acting carbs to prevent a low." })
            state.isSportModeActive && decision.clinicalRationale.contains("Late-Onset") ->
                LogInsight(InsightType.WARNING, "Post-Sport Alert", decision.clinicalRationale)
            decision.suggestedInsulinDose > 0.5 && insulin == 0.0 ->
                LogInsight(InsightType.SUGGESTION, "Insulin Recommended", "The algorithm suggests ${decision.suggestedInsulinDose}U. Consider adjusting your log if you took insulin.")
            decision.clinicalRationale.isNotBlank() ->
                LogInsight(InsightType.SUGGESTION, "Insight", decision.clinicalRationale)
            else ->
                LogInsight(InsightType.ON_TRACK, "Looking Good!", "Everything is perfectly on track.")
        }

        _uiState.value = _uiState.value.copy(
            currentInsight = insight,
            pendingClinicalSuggestion = decision.clinicalRationale.takeIf { it.isNotBlank() }
        )
    }

    fun executeSave() {
        val state = _uiState.value
        val timestamp = getEventTimestamp()

        viewModelScope.launch {
            if (state.isSportModeActive) {
                repository.insert(
                    BolusLog(
                        timestamp = timestamp,
                        eventType = "SPORT",
                        status = "COMPLETED",
                        bloodGlucose = 0.0, carbs = 0.0, standardDose = 0.0,
                        suggestedDose = 0.0, administeredDose = 0.0,
                        isSportModeActive = true,
                        sportType = state.sportType,
                        sportIntensity = state.sportIntensity,
                        sportDuration = state.sportDurationMinutes,
                        notes = state.notes.ifBlank { "Retrospective workout logged" },
                        clinicalSuggestion = state.pendingClinicalSuggestion
                    )
                )
            } else {
                val bg = state.bloodGlucose.toDoubleOrNull() ?: 0.0
                val carbs = state.carbs.toDoubleOrNull() ?: 0.0
                val insulin = state.manualInsulin.toDoubleOrNull() ?: 0.0

                val eventType = when {
                    bg > 0 && carbs == 0.0 && insulin == 0.0 -> "BG_CHECK"
                    carbs > 0 && insulin == 0.0 -> "MEAL"
                    settings.value.isAidPump && insulin > 0 -> "MANUAL_PEN"
                    else -> "MANUAL_INSULIN"
                }

                repository.insert(
                    BolusLog(
                        timestamp = timestamp,
                        eventType = eventType,
                        status = "COMPLETED",
                        bloodGlucose = bg, carbs = carbs,
                        standardDose = insulin, suggestedDose = 0.0, administeredDose = insulin,
                        isSportModeActive = false,
                        sportType = null, sportIntensity = null, sportDuration = null,
                        notes = state.notes.ifBlank { "Manual Log" },
                        clinicalSuggestion = state.pendingClinicalSuggestion
                    )
                )

                // After the existing non-sport repository.insert() call:
                val basalDose = state.basalInsulin.toDoubleOrNull() ?: 0.0
                if (basalDose > 0) {
                    repository.insert(
                        BolusLog(
                            timestamp = timestamp,
                            eventType = "BASAL_INSULIN",
                            status = "COMPLETED",
                            bloodGlucose = 0.0,
                            carbs = 0.0,
                            standardDose = basalDose,
                            suggestedDose = 0.0,
                            administeredDose = basalDose,
                            isSportModeActive = false,
                            sportType = null,
                            sportIntensity = null,
                            sportDuration = null,
                            notes = state.notes.ifBlank { "Long-acting insulin" },
                            clinicalSuggestion = null
                        )
                    )
                }
            }

            resetState()
            _uiState.value = _uiState.value.copy(isSaved = true, currentInsight = null)
        }
    }
}

class LogReadingViewModelFactory(
    private val repository: BolusLogRepository,
    private val settingsRepository: BolusSettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LogReadingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LogReadingViewModel(repository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}