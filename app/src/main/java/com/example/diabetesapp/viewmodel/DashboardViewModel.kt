package com.example.diabetesapp.viewmodel

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.models.CgmTrend
import com.example.diabetesapp.data.models.HypoPrediction
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.utils.AlgorithmEngine
import com.example.diabetesapp.utils.CgmHelper
import com.example.diabetesapp.utils.CgmReading
import com.example.diabetesapp.utils.DateTimeUtils
import com.example.diabetesapp.utils.GraphDataBuilder
import com.example.diabetesapp.utils.HealthConnectHelper
import com.example.diabetesapp.utils.HypoPredictionCalculator
import com.example.diabetesapp.utils.IobCalculator
import com.example.diabetesapp.utils.IobResult
import com.example.diabetesapp.utils.WorkoutProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

class DashboardViewModel(
    private val repository: BolusLogRepository,
    private val settingsRepository: BolusSettingsRepository,
    private val healthConnectHelper: HealthConnectHelper? = null
) : ViewModel() {

    val allLogs: StateFlow<List<BolusLog>> = repository.allLogs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val settings: StateFlow<BolusSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BolusSettings()
    )

    private val _latestReading = MutableStateFlow<CgmReading?>(null)
    val latestReading: StateFlow<CgmReading?> = _latestReading.asStateFlow()

    private val _graphEvents = MutableStateFlow<List<BolusLog>>(emptyList())
    val graphEvents: StateFlow<List<BolusLog>> = _graphEvents.asStateFlow()

    private val _cgmReadings = MutableStateFlow<List<CgmReading>>(emptyList())
    val cgmReadings: StateFlow<List<CgmReading>> = _cgmReadings.asStateFlow()

    val unverifiedWorkout = MutableStateFlow<BolusLog?>(null)

    private val _historyEvents = MutableStateFlow<List<BolusLog>>(emptyList())
    val historyEvents: StateFlow<List<BolusLog>> = _historyEvents.asStateFlow()

    private val _recentWorkouts = MutableStateFlow<List<ExerciseSessionRecord>>(emptyList())
    val recentWorkouts: StateFlow<List<ExerciseSessionRecord>> = _recentWorkouts.asStateFlow()

    private val _iobResult = MutableStateFlow<IobResult?>(null)
    val iobResult: StateFlow<IobResult?> = _iobResult.asStateFlow()

    private val _hypoPrediction = MutableStateFlow<HypoPrediction?>(null)
    val hypoPrediction: StateFlow<HypoPrediction?> = _hypoPrediction.asStateFlow()

    private val _hcWorkoutLogs = MutableStateFlow<List<BolusLog>>(emptyList())

    private val _dailySteps = MutableStateFlow(0L)
    val dailySteps: StateFlow<Long> = _dailySteps.asStateFlow()

    private val _xdripTreatmentsCache = MutableStateFlow<List<BolusLog>>(emptyList())

    // todaysTreatments now computes 24h start fresh each time instead of
    // depending on a changing StateFlow which caused recomposition cascades
    val todaysTreatments: StateFlow<List<BolusLog>> = _graphEvents
        .map { events ->
            val dayStart = DateTimeUtils.get24hStartTimestamp()
            events.filter { it.timestamp >= dayStart && (it.carbs > 0 || it.administeredDose >= 1.5f || it.eventType == "BASAL_INSULIN") }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            allLogs.collect { logs ->
                val dayStart = DateTimeUtils.get24hStartTimestamp()
                val todayLocal = GraphDataBuilder.filterForDay(allLogs.value, dayStart)
                _graphEvents.value = GraphDataBuilder.buildGraphEvents(
                    localLogs = todayLocal,
                    xdripTreatments = _xdripTreatmentsCache.value,
                    hcWorkouts = _hcWorkoutLogs.value
                )
                recalculateIob()
            }
        }
    }

    private var iobJob: Job? = null

    fun recalculateIob() {
        iobJob?.cancel()
        iobJob = viewModelScope.launch(Dispatchers.IO) {
            val logs = allLogs.value
            val currentSettings = settings.value
            val latestReading = _latestReading.value
            val xdripIob = latestReading?.iob
            val xdripTimestamp = latestReading?.timestamp
            val result = IobCalculator.calculate(logs, currentSettings, xdripIob, xdripTimestamp)
            withContext(Dispatchers.Main) {
                _iobResult.value = result
            }
        }
    }

    private fun calculateHypoPrediction(readings: List<CgmReading>, hypoLimit: Float) {
        if (!settings.value.isCgmEnabled) return
        _hypoPrediction.value = HypoPredictionCalculator.calculate(readings, hypoLimit)
    }

    fun fetchHistoryData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when {
                    settings.value.isAidPump && settings.value.isCgmEnabled -> fetchAidCgmHistory()
                    else -> fetchStandardHistory()
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "History fetch failed", e)
            }
        }
    }

    private suspend fun fetchAidCgmHistory() {
        val xdripTreatments = CgmHelper.getTreatmentsFromXDrip()
        val history = CgmHelper.getBgHistoryFromXDrip()
        val xdripWithBg = xdripTreatments.map {
            it.copy(bloodGlucose = CgmHelper.findClosestBg(it.timestamp, history))
        }
        withContext(Dispatchers.Main) {
            _historyEvents.value = (allLogs.value + xdripWithBg + _hcWorkoutLogs.value)
                .distinctBy { "${it.timestamp}_${it.eventType}" }
                .sortedByDescending { it.timestamp }
        }
    }

    private suspend fun fetchStandardHistory() {
        withContext(Dispatchers.Main) {
            _historyEvents.value = (allLogs.value + _hcWorkoutLogs.value)
                .distinctBy { "${it.timestamp}_${it.eventType}" }
                .sortedByDescending { it.timestamp }
        }
    }

    fun fetchDashboardData() {
        fetchRecentWorkouts()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when {
                    settings.value.isAidPump && settings.value.isCgmEnabled -> fetchAidCgmMode()
                    settings.value.isCgmEnabled -> fetchCgmOnlyMode()
                    else -> fetchManualMode()
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Fetch failed", e)
            }
        }
    }

    private suspend fun fetchAidCgmMode() {
        val dayStart = DateTimeUtils.get24hStartTimestamp()
        val latest = CgmHelper.getLatestBgFromXDrip()
        val history = CgmHelper.getBgHistoryFromXDrip()
        val todayXDrip = CgmHelper.getTreatmentsFromXDrip()
            .filter { it.timestamp >= dayStart }
            .map { it.copy(bloodGlucose = CgmHelper.findClosestBg(it.timestamp, history)) }

        _xdripTreatmentsCache.value = todayXDrip
        persistCareLinkTreatments(todayXDrip)

        withContext(Dispatchers.Main) {
            if (latest != null) _latestReading.value = latest  // only update if we got a fresh reading
            _cgmReadings.value = history
            calculateHypoPrediction(history, settings.value.hypoLimit)
            recalculateIob()  // always call — uses stale timestamp from last known reading
            _graphEvents.value = GraphDataBuilder.buildGraphEvents(
                localLogs = GraphDataBuilder.filterForDay(allLogs.value, dayStart),
                xdripTreatments = todayXDrip,
                hcWorkouts = _hcWorkoutLogs.value
            )
        }
    }

    private suspend fun fetchCgmOnlyMode() {
        val dayStart = DateTimeUtils.get24hStartTimestamp()
        val latest = CgmHelper.getLatestBgFromXDrip()
        val history = CgmHelper.getBgHistoryFromXDrip()

        withContext(Dispatchers.Main) {
            if (latest != null) _latestReading.value = latest  // same fix here
            _cgmReadings.value = history
            calculateHypoPrediction(history, settings.value.hypoLimit)
            recalculateIob()
            _graphEvents.value = GraphDataBuilder.buildGraphEvents(
                localLogs = GraphDataBuilder.filterForDay(allLogs.value, dayStart),
                hcWorkouts = _hcWorkoutLogs.value
            )
        }
    }

    private fun fetchManualMode() {
        val dayStart = DateTimeUtils.get24hStartTimestamp()
        val localLogs = GraphDataBuilder.filterForDay(allLogs.value, dayStart)
        _graphEvents.value = GraphDataBuilder.buildGraphEvents(
            localLogs = localLogs,
            hcWorkouts = _hcWorkoutLogs.value
        )
    }

    private suspend fun persistCareLinkTreatments(treatments: List<BolusLog>) {
        val existingLogs = allLogs.value
        treatments.forEach { treatment ->
            val alreadySaved = existingLogs.any { existing ->
                Math.abs(existing.timestamp - treatment.timestamp) <= 2 * 60 * 1000L
                        && existing.notes == "Auto-entry via CareLink"
            }
            if (!alreadySaved && treatment.bloodGlucose > 0) {
                repository.insert(treatment)
            }
        }
    }

    fun fetchRecentWorkouts() {
        val helper = healthConnectHelper ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val stepRecords = helper.getRawStepRecords()
            val steps = stepRecords.sumOf { it.count }
            val detectedWalks = helper.detectWalkingFromSteps(stepRecords)
            val records = helper.getRecentWorkouts()
            val dayStart = DateTimeUtils.get24hStartTimestamp()

            val allWorkouts = WorkoutProcessor.process(records, detectedWalks, helper, dayStart)
            WorkoutProcessor.persistNew(
                allWorkouts = allWorkouts,
                existingLogs = allLogs.value,
                repository = repository,
                stepRecords = stepRecords
            )

            withContext(Dispatchers.Main) {
                _recentWorkouts.value = records
                _dailySteps.value = steps
                _hcWorkoutLogs.value = allWorkouts
                val localLogs = GraphDataBuilder.filterForDay(allLogs.value, dayStart)
                _graphEvents.value = GraphDataBuilder.buildGraphEvents(
                    localLogs = localLogs,
                    xdripTreatments = _xdripTreatmentsCache.value,
                    hcWorkouts = allWorkouts
                )
            }
        }
    }

    fun checkForPendingWorkouts(logs: List<BolusLog>) {
        unverifiedWorkout.value = WorkoutProcessor.checkForPendingWorkout(logs)
    }

    fun dismissVerification() {
        unverifiedWorkout.value = null
    }

    fun deleteLog(log: BolusLog) {
        viewModelScope.launch {
            repository.delete(log)
        }
    }

    fun verifyAndCompleteWorkout(
        log: BolusLog,
        actualDuration: Float,
        actualIntensity: Float,
        sportType: String,
        actualStartTimeStr: String
    ) {
        val currentSettings = settings.value
        val context = PatientContext(
            therapyType = currentSettings.therapyTypeEnum,
            bolusSettings = currentSettings,
            currentBG = log.bloodGlucose,
            hasCGM = currentSettings.isCgmEnabled,
            cgmTrend = CgmTrend.NONE,
            activeInsulinIOB = log.administeredDose,
            plannedCarbs = log.carbs,
            isDoingSport = true,
            sportType = sportType,
            sportIntensity = actualIntensity.toInt(),
            sportDurationMins = actualDuration.toInt(),
            minutesUntilSport = -actualDuration.toInt(),
            timeOfDay = LocalTime.now(),
            dailySteps = 0L
        )
        val decision = AlgorithmEngine.calculateClinicalAdvice(context)
        val updatedLog = WorkoutProcessor.buildVerifiedLog(
            log = log,
            actualDuration = actualDuration,
            actualIntensity = actualIntensity,
            sportType = sportType,
            actualStartTimeStr = actualStartTimeStr,
            clinicalRationale = "Post-Workout Insight: ${decision.clinicalRationale}"
        )
        viewModelScope.launch {
            repository.update(updatedLog)
            unverifiedWorkout.value = null
        }
    }
}

class DashboardViewModelFactory(
    private val repository: BolusLogRepository,
    private val settingsRepository: BolusSettingsRepository,
    private val context: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            val healthConnectHelper = context?.let {
                try {
                    HealthConnectHelper(HealthConnectClient.getOrCreate(it))
                } catch (e: Exception) {
                    Log.w("DashboardVM", "Health Connect init failed: ${e.message}")
                    null
                }
            }
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(repository, settingsRepository, healthConnectHelper) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}