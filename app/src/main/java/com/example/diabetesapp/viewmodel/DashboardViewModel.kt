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
import com.example.diabetesapp.data.models.TherapyType
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.utils.AlgorithmEngine
import com.example.diabetesapp.utils.CgmHelper
import com.example.diabetesapp.utils.CgmReading
import com.example.diabetesapp.utils.HealthConnectHelper
import com.example.diabetesapp.utils.IobCalculator
import com.example.diabetesapp.utils.IobResult
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
                recalculateIob()
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

    fun recalculateIob() {
        viewModelScope.launch(Dispatchers.IO) {
            val logs = allLogs.value
            val currentSettings = settings.value
            val latestReading = _latestReading.value  // capture current value
            val xdripIob = latestReading?.iob
            val xdripTimestamp = latestReading?.timestamp  // pass timestamp too
            val result = IobCalculator.calculate(logs, currentSettings, xdripIob, xdripTimestamp)
            withContext(Dispatchers.Main) {
                _iobResult.value = result
            }
        }
    }
    private fun calculateHypoPrediction(
        readings: List<CgmReading>,
        hypoLimit: Float
    ) {
        val recent = readings
            .filter { it.bgValue > 0 }
            .sortedByDescending { it.timestamp }
            .take(8)
            .reversed()

        if (recent.size < 4) {
            _hypoPrediction.value = null
            return
        }

        val tBase = recent.first().timestamp
        val xs = recent.map { (it.timestamp - tBase).toFloat() / 60000f }
        val ys = recent.map { it.bgValue.toFloat() }

        // Recency weights — most recent reading gets weight 1.0, oldest gets ~0.2
        val weights = xs.indices.map { i ->
            0.2f + 0.8f * (i.toFloat() / (xs.size - 1))
        }

        // Weighted quadratic regression — solve for a, b, c in y = ax² + bx + c
        // Build weighted sums
        var sw   = 0.0; var swx  = 0.0; var swx2 = 0.0
        var swx3 = 0.0; var swx4 = 0.0
        var swy  = 0.0; var swxy = 0.0; var swx2y = 0.0

        for (i in xs.indices) {
            val w = weights[i].toDouble()
            val x = xs[i].toDouble()
            val y = ys[i].toDouble()
            val x2 = x * x
            sw    += w
            swx   += w * x
            swx2  += w * x2
            swx3  += w * x2 * x
            swx4  += w * x2 * x2
            swy   += w * y
            swxy  += w * x * y
            swx2y += w * x2 * y
        }

        // Solve 3x3 system using Gaussian elimination
        // [swx4 swx3 swx2] [a]   [swx2y]
        // [swx3 swx2 swx ] [b] = [swxy ]
        // [swx2 swx  sw  ] [c]   [swy  ]
        val mat = Array(3) { DoubleArray(4) }
        mat[0][0] = swx4; mat[0][1] = swx3; mat[0][2] = swx2; mat[0][3] = swx2y
        mat[1][0] = swx3; mat[1][1] = swx2; mat[1][2] = swx;  mat[1][3] = swxy
        mat[2][0] = swx2; mat[2][1] = swx;  mat[2][2] = sw;   mat[2][3] = swy

        // Forward elimination
        for (col in 0..1) {
            for (row in col + 1..2) {
                val factor = mat[row][col] / mat[col][col]
                for (j in col..3) mat[row][j] -= factor * mat[col][j]
            }
        }

        // Back substitution
        val c = mat[2][3] / mat[2][2]
        val b = (mat[1][3] - mat[1][2] * c) / mat[1][1]
        val a = (mat[0][3] - mat[0][2] * c - mat[0][1] * b) / mat[0][0]

        // Current BG estimate from curve
        val latestX = xs.last().toDouble()
        val currentBg = a * latestX * latestX + b * latestX + c

        // Only predict if currently trending down
        val slopeAtLatest = 2 * a * latestX + b  // derivative of quadratic at latest point
        if (slopeAtLatest >= -0.1) {
            _hypoPrediction.value = null
            return
        }

        // Project forward minute by minute
        val now = System.currentTimeMillis()
        val latestTimestamp = recent.last().timestamp
        val projectionPoints = mutableListOf<Pair<Long, Float>>()
        var minutesFromNow = -1

        // Start from last real reading
        projectionPoints.add(Pair(latestTimestamp, recent.last().bgValue.toFloat()))

        for (m in 1..90) {
            val t = latestTimestamp + m * 60 * 1000L
            val minutesFromBase = (t - tBase).toFloat() / 60000.0
            val projectedBg = (a * minutesFromBase * minutesFromBase + b * minutesFromBase + c).toFloat()

            projectionPoints.add(Pair(t, projectedBg))

            // Find when it crosses hypo limit
            if (projectedBg <= hypoLimit && minutesFromNow == -1) {
                val msFromNow = t - now
                minutesFromNow = (msFromNow / 60000).toInt()
            }

            // Stop well below hypo line so it visually crosses it
            if (projectedBg <= hypoLimit - 15f) break
        }

        if (minutesFromNow == -1 || minutesFromNow > 60 || currentBg <= hypoLimit) {
            _hypoPrediction.value = null
            return
        }

        _hypoPrediction.value = HypoPrediction(
            minutesUntilHypo = minutesFromNow,
            predictedBgAtHypo = hypoLimit,
            projectionPoints = projectionPoints
        )
    }

    fun fetchHistoryData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isCgmEnabled = settings.value.isCgmEnabled
                val isAidPump = settings.value.isAidPump
                val hcWorkouts = _hcWorkoutLogs.value

                if (isCgmEnabled && isAidPump) {
                    // AID pump + CGM: include CareLink treatments
                    val xdripTreatments = CgmHelper.getTreatmentsFromXDrip()
                    val history = CgmHelper.getBgHistoryFromXDrip()
                    val xdripWithBg = xdripTreatments.map {
                        it.copy(bloodGlucose = findClosestBg(it.timestamp, history))
                    }
                    withContext(Dispatchers.Main) {
                        _historyEvents.value = (allLogs.value + xdripWithBg + hcWorkouts)
                            .distinctBy { it.timestamp }
                            .sortedByDescending { it.timestamp }
                    }
                } else {
                    // Everyone else: local logs + HC workouts only
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
                val isAidPump = settings.value.isAidPump
                val dayStart = get24hStartTimestamp()

                if (isCgmEnabled) {
                    val latest = CgmHelper.getLatestBgFromXDrip()
                    val history = CgmHelper.getBgHistoryFromXDrip()

                    // CareLink only for AID pump
                    val todayXDrip = if (isAidPump) {
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

                    // Persist xDrip treatments that have a matched BG and aren't already in DB
                    val existingLogs = allLogs.value
                    todayXDrip.forEach { treatment ->
                        val alreadySaved = existingLogs.any { existing ->
                            Math.abs(existing.timestamp - treatment.timestamp) <= 2 * 60 * 1000L
                                    && existing.notes == "Auto-entry via CareLink"
                        }
                        if (!alreadySaved && treatment.bloodGlucose > 0) {
                            repository.insert(treatment)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _latestReading.value = latest
                        recalculateIob()
                        _cgmReadings.value = history
                        calculateHypoPrediction(history, settings.value.hypoLimit)
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
        System.currentTimeMillis() - 25 * 60 * 60 * 1000L

    fun fetchRecentWorkouts() {
        val helper = healthConnectHelper ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val stepRecords = helper.getRawStepRecords()
            val steps = stepRecords.sumOf { it.count }
            val detectedWalks = helper.detectWalkingFromSteps(stepRecords)
            val records = helper.getRecentWorkouts()

            Log.d("HC_Steps", "Today's steps: $steps")

            val dayStart = get24hStartTimestamp()

            // All ExerciseSessionRecords come from Strava — drop Walking types
            val stravaActivities = records.mapNotNull { record ->
                val startMs = record.startTime.toEpochMilli()
                if (startMs < dayStart) return@mapNotNull null

                val durationMinutes = ChronoUnit.MINUTES.between(
                    record.startTime, record.endTime
                ).toFloat()

                val sportType = when (record.exerciseType) {
                    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> return@mapNotNull null
                    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
                    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
                    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
                    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
                    else -> "Workout"
                }

                val avgHr = helper.getAverageHeartRateForSession(record.startTime, record.endTime)
                val intensity = when {
                    avgHr == null -> "Medium"
                    avgHr >= 160  -> "High"
                    avgHr >= 130  -> "Medium"
                    else          -> "Low"
                }

                Log.d("HC_Workouts", "$sportType: avgHR=${avgHr?.toInt()}, intensity=$intensity")

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
                    sportIntensity = intensity,
                    sportDuration = durationMinutes,
                    notes = "Auto-imported from Strava",
                    clinicalSuggestion = null
                )
            }

            val todayWalks = detectedWalks.filter { it.timestamp >= dayStart }

            // Suppress step-detected walks that are >50% overlapped by a Strava activity
            val filteredWalks = todayWalks.filter { walk ->
                val walkStart = walk.timestamp
                val walkEnd = walkStart + (walk.sportDuration!! * 60 * 1000L).toLong()
                val walkDuration = walkEnd - walkStart
                stravaActivities.none { activity ->
                    val actStart = activity.timestamp
                    val actEnd = actStart + (activity.sportDuration!! * 60 * 1000L).toLong()
                    val overlapStart = maxOf(walkStart, actStart)
                    val overlapEnd = minOf(walkEnd, actEnd)
                    val overlapMs = (overlapEnd - overlapStart).coerceAtLeast(0L)
                    overlapMs > walkDuration * 0.5
                }
            }

            // Merge walks that are within 15 minutes of each other into one session
            val mergedWalks = mutableListOf<BolusLog>()
            val sortedWalks = filteredWalks.sortedBy { it.timestamp }

            sortedWalks.forEach { walk ->
                val last = mergedWalks.lastOrNull()
                if (last != null) {
                    val lastEnd = last.timestamp + (last.sportDuration!! * 60 * 1000L).toLong()
                    val gapMs = walk.timestamp - lastEnd
                    if (gapMs <= 15 * 60 * 1000L && last.sportType == walk.sportType) {
                        // Merge into the last walk — extend its duration
                        val newDuration = ((walk.timestamp + (walk.sportDuration!! * 60 * 1000L).toLong()
                                - last.timestamp) / 60000f)
                        mergedWalks[mergedWalks.lastIndex] = last.copy(sportDuration = newDuration)
                    } else {
                        mergedWalks.add(walk)
                    }
                } else {
                    mergedWalks.add(walk)
                }
            }

            val allWorkouts = (stravaActivities + mergedWalks).sortedBy { it.timestamp }

            // Persist any new activities that aren't already in the DB
            val existingLogs = allLogs.value
            allWorkouts.forEach { workout ->
                if (workout.sportType == "Walking" && workout.notes.startsWith("Auto-detected")) {
                    // Remove any existing walk entries that fall within this merged walk's time window
                    val workoutEnd = workout.timestamp + (workout.sportDuration!! * 60 * 1000L).toLong()
                    existingLogs.filter { existing ->
                        existing.isSportModeActive &&
                                existing.sportType == "Walking" &&
                                existing.timestamp >= workout.timestamp - 60 * 1000L &&
                                existing.timestamp <= workoutEnd
                    }.forEach { duplicate ->
                        repository.delete(duplicate)
                    }
                    repository.insert(workout)
                } else {
                    val alreadySaved = existingLogs.any { existing ->
                        existing.isSportModeActive &&
                                Math.abs(existing.timestamp - workout.timestamp) <= 2 * 60 * 1000L &&
                                existing.sportType == workout.sportType
                    }
                    if (!alreadySaved) {
                        repository.insert(workout)
                    }
                }
            }

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