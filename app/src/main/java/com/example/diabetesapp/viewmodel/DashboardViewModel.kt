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
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.utils.AlgorithmEngine
import com.example.diabetesapp.utils.CgmHelper
import com.example.diabetesapp.utils.CgmReading
import com.example.diabetesapp.utils.HealthConnectHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Calendar

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

    private val _hcWorkoutLogs = MutableStateFlow<List<BolusLog>>(emptyList())

    private val _dailySteps = MutableStateFlow(0L)
    val dailySteps: StateFlow<Long> = _dailySteps.asStateFlow()

    private val _xdripTreatmentsCache = MutableStateFlow<List<BolusLog>>(emptyList())

    // todaysTreatments now computes 24h start fresh each time instead of
    // depending on a changing StateFlow which caused recomposition cascades
    val todaysTreatments: StateFlow<List<BolusLog>> = _graphEvents
        .map { events ->
            val dayStart = get24hStartTimestamp()
            events.filter { it.timestamp >= dayStart && (it.carbs > 0 || it.administeredDose >= 1.5f) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            allLogs.collect { logs ->
                val dayStart = get24hStartTimestamp()
                val todayLocal = logs.filter { it.timestamp >= dayStart }
                rebuildGraphEvents(
                    localLogs = todayLocal,
                    xdripTreatments = _xdripTreatmentsCache.value,
                    hcWorkouts = _hcWorkoutLogs.value
                )
            }
        }
    }

    private fun rebuildGraphEvents(
        localLogs: List<BolusLog>,
        xdripTreatments: List<BolusLog>,
        hcWorkouts: List<BolusLog>
    ) {
        val combined = (localLogs + xdripTreatments + hcWorkouts)
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
        Log.d("DashboardVM", "Graph events: ${combined.size} total (${localLogs.size} local + ${xdripTreatments.size} xDrip + ${hcWorkouts.size} HC workouts)")
        _graphEvents.value = combined
    }

    fun fetchHistoryData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isCgmEnabled = settings.value.isCgmEnabled
                val isPumpUser = settings.value.isPumpUser
                val hcWorkouts = _hcWorkoutLogs.value

                if (isCgmEnabled && isPumpUser) {
                    val xdripTreatments = CgmHelper.getTreatmentsFromXDrip()
                    val history = CgmHelper.getBgHistoryFromXDrip()
                    val xdripWithBg = xdripTreatments.map { treatment ->
                        treatment.copy(bloodGlucose = findClosestBg(treatment.timestamp, history))
                    }
                    withContext(Dispatchers.Main) {
                        _historyEvents.value = (allLogs.value + xdripWithBg + hcWorkouts)
                            .distinctBy { it.timestamp }
                            .sortedByDescending { it.timestamp }
                    }
                } else if (isCgmEnabled) {
                    withContext(Dispatchers.Main) {
                        _historyEvents.value = (allLogs.value + hcWorkouts)
                            .distinctBy { it.timestamp }
                            .sortedByDescending { it.timestamp }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _historyEvents.value = (allLogs.value + hcWorkouts)
                            .distinctBy { it.timestamp }
                            .sortedByDescending { it.timestamp }
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "History fetch failed", e)
            }
        }
    }

    fun fetchDashboardData() {
        fetchRecentWorkouts()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isCgmEnabled = settings.value.isCgmEnabled
                val isPumpUser = settings.value.isPumpUser
                val dayStart = get24hStartTimestamp()

                if (isCgmEnabled) {
                    val latest = CgmHelper.getLatestBgFromXDrip()
                    val history = CgmHelper.getBgHistoryFromXDrip()

                    val todayXDrip = if (isPumpUser) {
                        val xdripTreatments = CgmHelper.getTreatmentsFromXDrip()
                        xdripTreatments
                            .filter { it.timestamp >= dayStart }
                            .map { treatment ->
                                treatment.copy(
                                    bloodGlucose = findClosestBg(treatment.timestamp, history)
                                )
                            }
                    } else {
                        emptyList()
                    }

                    _xdripTreatmentsCache.value = todayXDrip

                    withContext(Dispatchers.Main) {
                        _latestReading.value = latest
                        _cgmReadings.value = history
                        val localLogs = allLogs.value.filter { it.timestamp >= dayStart }
                        rebuildGraphEvents(
                            localLogs = localLogs,
                            xdripTreatments = todayXDrip,
                            hcWorkouts = _hcWorkoutLogs.value
                        )
                    }
                } else {
                    val localLogs = allLogs.value.filter { it.timestamp >= dayStart }
                    withContext(Dispatchers.Main) {
                        rebuildGraphEvents(
                            localLogs = localLogs,
                            xdripTreatments = emptyList(),
                            hcWorkouts = _hcWorkoutLogs.value
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Fetch failed", e)
            }
        }
    }

    private fun findClosestBg(timestamp: Long, cgmReadings: List<CgmReading>): Double {
        val windowMs = 10 * 60 * 1000L
        return cgmReadings
            .filter { Math.abs(it.timestamp - timestamp) <= windowMs }
            .minByOrNull { Math.abs(it.timestamp - timestamp) }
            ?.bgValue?.toDouble() ?: 0.0
    }

    fun getLogicalDayStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun get24hStartTimestamp(): Long =
        System.currentTimeMillis() - 24 * 60 * 60 * 1000L

    fun fetchRecentWorkouts() {
        val helper = healthConnectHelper ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val stepRecords = helper.getRawStepRecords()
            val steps = stepRecords.sumOf { it.count }
            val detectedWalks = helper.detectWalkingFromSteps(stepRecords)
            val records = helper.getRecentWorkouts()

            Log.d("HC_Steps", "Today's steps: $steps")

            val dayStart = get24hStartTimestamp()

            val confirmedWorkoutLogs = records.mapNotNull { record ->
                val startMs = record.startTime.toEpochMilli()
                if (startMs < dayStart) return@mapNotNull null
                val durationMinutes = ChronoUnit.MINUTES.between(
                    record.startTime, record.endTime
                ).toFloat()
                val sportType = when (record.exerciseType) {
                    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
                    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
                    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
                    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
                    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
                    else -> "Workout"
                }
                BolusLog(
                    id = 0,
                    timestamp = startMs,
                    eventType = "SPORT",
                    status = "COMPLETED",
                    bloodGlucose = 0.0,
                    carbs = 0.0,
                    standardDose = 0.0,
                    suggestedDose = 0.0,
                    administeredDose = 0.0,
                    isSportModeActive = true,
                    sportType = sportType,
                    sportIntensity = "Medium",
                    sportDuration = durationMinutes,
                    notes = "Auto-imported from Health Connect",
                    clinicalSuggestion = null
                )
            }

            val todayWalks = detectedWalks.filter { it.timestamp >= dayStart }

            val bucketMs = 10 * 60 * 1000L
            val allWorkouts = (confirmedWorkoutLogs + todayWalks)
                .groupBy { it.timestamp / bucketMs }
                .map { (_, entries) ->
                    entries.firstOrNull { it.notes == "Auto-imported from Health Connect" }
                        ?: entries.first()
                }
                .sortedBy { it.timestamp }

            withContext(Dispatchers.Main) {
                _recentWorkouts.value = records
                _dailySteps.value = steps
                _hcWorkoutLogs.value = allWorkouts
                val localLogs = allLogs.value.filter { it.timestamp >= dayStart }
                rebuildGraphEvents(
                    localLogs = localLogs,
                    xdripTreatments = _xdripTreatmentsCache.value,
                    hcWorkouts = allWorkouts
                )
            }
        }
    }

    fun checkForPendingWorkouts(logs: List<BolusLog>) {
        val now = System.currentTimeMillis()
        val pending = logs.firstOrNull { log ->
            log.status == "PLANNED" && log.isSportModeActive &&
                    (log.timestamp + ((log.sportDuration ?: 0f) * 60 * 1000L)) < now
        }
        unverifiedWorkout.value = pending
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
            cgmTrend = CgmTrend.NONE, // post-workout, no live trend needed
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
        val newInsight = "Post-Workout Insight: ${decision.clinicalRationale}"
        val combinedInsight = if (log.clinicalSuggestion.isNullOrBlank()) newInsight
        else "${log.clinicalSuggestion}\n\n$newInsight"

        val calendar = Calendar.getInstance().apply { timeInMillis = log.timestamp }
        try {
            val parts = actualStartTimeStr.split(":")
            if (parts.size == 2) {
                calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                calendar.set(Calendar.MINUTE, parts[1].toInt())
            }
        } catch (_: Exception) { }

        val updatedLog = log.copy(
            timestamp = calendar.timeInMillis,
            status = "COMPLETED",
            sportDuration = actualDuration,
            sportIntensity = when (actualIntensity.toInt()) {
                1 -> "Low"; 2 -> "Medium"; 3 -> "High"; else -> "Medium"
            },
            sportType = sportType,
            clinicalSuggestion = combinedInsight
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