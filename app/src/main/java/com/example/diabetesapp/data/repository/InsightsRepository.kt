package com.example.diabetesapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.diabetesapp.data.dao.DailyMetricsDao
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.models.DailyMetrics
import com.example.diabetesapp.utils.CgmHelper
import com.example.diabetesapp.utils.CgmReading
import com.example.diabetesapp.utils.HealthConnectHelper
import com.example.diabetesapp.utils.MetricsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SpikeSummary(
    val hour: Int,
    val peakBg: Int,
    val bgAtMeal: Int,
    val minutesAfterMeal: Int
)

class InsightsRepository(
    private val bolusRepo: BolusLogRepository,
    private val metricsDao: DailyMetricsDao,
    private val hcHelper: HealthConnectHelper?,
    private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("insights_prefs", Context.MODE_PRIVATE)

    suspend fun backfillIfNeeded(settings: BolusSettings) {
        if (prefs.getBoolean("backfill_done_v2", false)) return

        prefs.edit().remove("backfill_done").apply()

        val today = LocalDate.now()

        if (settings.isCgmEnabled) {
            val cgmHistory = try {
                CgmHelper.getBgHistoryExtended(days = 30)
            } catch (e: Exception) {
                emptyList()
            }

            if (cgmHistory.isEmpty()) {
                backfillFromDb(settings, today)
            } else {
                val zone = ZoneId.systemDefault()
                val earliestTimestamp = cgmHistory.minOf { it.timestamp }
                val earliestDay = Instant.ofEpochMilli(earliestTimestamp)
                    .atZone(zone)
                    .toLocalDate()

                val allDbLogs = bolusRepo.getAllLogsImmediate()

                var current = earliestDay
                while (!current.isAfter(today.minusDays(1))) {
                    val dayStart = current.atStartOfDay(zone).toInstant().toEpochMilli()
                    val dayEnd = current.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                    val bgReadings = cgmHistory
                        .filter { it.timestamp in dayStart..dayEnd }
                        .map { it.bgValue.toFloat() }

                    val dbLogs = allDbLogs.filter { it.timestamp in dayStart..dayEnd }

                    val steps = try {
                        hcHelper?.getStepsForDay(dayStart, dayEnd) ?: 0L
                    } catch (e: Exception) { 0L }

                    val metrics = MetricsCalculator.computeDayMetrics(
                        dateEpochDay = current.toEpochDay(),
                        bgReadings = bgReadings,
                        insulinLogs = dbLogs,
                        hypoLimit = settings.hypoLimit,
                        hyperLimit = settings.hyperLimit,
                        steps = steps,
                        isCgmData = true
                    )
                    metricsDao.upsert(metrics)
                    current = current.plusDays(1)
                }
            }
        } else {
            backfillFromDb(settings, today)
        }

        prefs.edit().putBoolean("backfill_done_v2", true).apply()
    }

    private suspend fun backfillFromDb(settings: BolusSettings, today: LocalDate) {
        val allLogs = bolusRepo.getAllLogsImmediate()
        if (allLogs.isEmpty()) return

        val zone = ZoneId.systemDefault()
        val logsByDay = allLogs.groupBy { log ->
            Instant.ofEpochMilli(log.timestamp).atZone(zone).toLocalDate().toEpochDay()
        }

        logsByDay.forEach { (epochDay, dayLogs) ->
            val date = LocalDate.ofEpochDay(epochDay)
            if (!date.isBefore(today)) return@forEach
            val metrics = MetricsCalculator.computeDayMetrics(
                dateEpochDay = epochDay,
                logs = dayLogs,
                hypoLimit = settings.hypoLimit,
                hyperLimit = settings.hyperLimit,
                steps = 0L,
                isCgmData = false
            )
            metricsDao.upsert(metrics)
        }
    }

    suspend fun refreshToday(settings: BolusSettings) {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = System.currentTimeMillis()

        val bgReadings = if (settings.isCgmEnabled) {
            val cgmHistory = withContext(Dispatchers.IO) {
                try {
                    CgmHelper.getBgHistoryExtended(days = 2)
                } catch (e: Exception) {
                    Log.e("InsightsRepo", "CGM fetch failed: ${e.message}")
                    emptyList()
                }
            }
            Log.d("InsightsRepo", "CGM fetch returned ${cgmHistory.size} readings")
            cgmHistory
                .filter { it.timestamp in dayStart..dayEnd }
                .map { it.bgValue.toFloat() }
        } else {
            bolusRepo.getAllLogsImmediate()
                .filter { it.timestamp in dayStart..dayEnd && it.bloodGlucose > 0 }
                .map { it.bloodGlucose.toFloat() }
        }

        Log.d("InsightsRepo", "BG readings for today: ${bgReadings.size}, isCgm=${settings.isCgmEnabled}")

        val dbLogs = withContext(Dispatchers.IO) {
            bolusRepo.getAllLogsImmediate()
                .filter { it.timestamp in dayStart..dayEnd }
        }

        val steps = try {
            withContext(Dispatchers.IO) { hcHelper?.getDailySteps() ?: 0L }
        } catch (e: Exception) { 0L }

        val metrics = MetricsCalculator.computeDayMetrics(
            dateEpochDay = today.toEpochDay(),
            bgReadings = bgReadings,
            insulinLogs = dbLogs,
            hypoLimit = settings.hypoLimit,
            hyperLimit = settings.hyperLimit,
            steps = steps,
            isCgmData = settings.isCgmEnabled && bgReadings.isNotEmpty()
        )
        metricsDao.upsert(metrics)
    }

    private suspend fun getBgReadingsForDay(
        dayStart: Long,
        dayEnd: Long,
        settings: BolusSettings
    ): List<Float> {
        return if (settings.isCgmEnabled) {
            try {
                val cgmReadings = CgmHelper.getBgHistoryFromXDrip()
                    .filter { it.timestamp in dayStart..dayEnd }
                    .map { it.bgValue.toFloat() }
                if (cgmReadings.isNotEmpty()) cgmReadings
                else fallbackBgFromDb(dayStart, dayEnd)
            } catch (e: Exception) {
                fallbackBgFromDb(dayStart, dayEnd)
            }
        } else {
            fallbackBgFromDb(dayStart, dayEnd)
        }
    }

    private suspend fun fallbackBgFromDb(dayStart: Long, dayEnd: Long): List<Float> =
        bolusRepo.getAllLogsImmediate()
            .filter { it.timestamp in dayStart..dayEnd && it.bloodGlucose > 0 }
            .map { it.bloodGlucose.toFloat() }

    suspend fun getToday(): DailyMetrics? {
        val day = LocalDate.now().toEpochDay()
        Log.d("InsightsRepo", "getToday() querying epochDay=$day")
        val result = metricsDao.getByDay(day)
        Log.d("InsightsRepo", "getToday() result=$result")
        return result
    }

    suspend fun getYesterday(): DailyMetrics? {
        val day = LocalDate.now().minusDays(1).toEpochDay()
        return metricsDao.getByDay(day)
    }

    suspend fun getLast7Days(): List<DailyMetrics> {
        val today = LocalDate.now()
        val from = today.minusDays(6).toEpochDay()
        val to = today.toEpochDay()  // ← was minusDays(1)
        return metricsDao.getRange(from, to)
    }

    suspend fun getLast30Days(): List<DailyMetrics> {
        val today = LocalDate.now()
        val from = today.minusDays(30).toEpochDay()
        val to = today.minusDays(1).toEpochDay()
        return metricsDao.getRange(from, to)
    }

    suspend fun refreshYesterday(settings: BolusSettings) {
        val yesterday = LocalDate.now().minusDays(1)
        val zone = ZoneId.systemDefault()
        val dayStart = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = yesterday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val bgReadings = withContext(Dispatchers.IO) {
            if (settings.isCgmEnabled) {
                try {
                    CgmHelper.getBgHistoryExtended(days = 2)
                        .filter { it.timestamp in dayStart..dayEnd }
                        .map { it.bgValue.toFloat() }
                } catch (e: Exception) { emptyList() }
            } else {
                bolusRepo.getAllLogsImmediate()
                    .filter { it.timestamp in dayStart..dayEnd && it.bloodGlucose > 0 }
                    .map { it.bloodGlucose.toFloat() }
            }
        }

        val dbLogs = withContext(Dispatchers.IO) {
            bolusRepo.getAllLogsImmediate()
                .filter { it.timestamp in dayStart..dayEnd }
        }

        val steps = try {
            withContext(Dispatchers.IO) { hcHelper?.getDailySteps() ?: 0L }
        } catch (e: Exception) { 0L }

        val metrics = MetricsCalculator.computeDayMetrics(
            dateEpochDay = yesterday.toEpochDay(),
            bgReadings = bgReadings,
            insulinLogs = dbLogs,
            hypoLimit = settings.hypoLimit,
            hyperLimit = settings.hyperLimit,
            steps = steps,
            isCgmData = settings.isCgmEnabled && bgReadings.isNotEmpty()
        )
        metricsDao.upsert(metrics)
    }

    suspend fun getYesterdaySportLogs(): List<BolusLog> {
        val yesterday = LocalDate.now().minusDays(1)
        val zone = ZoneId.systemDefault()
        val dayStart = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = yesterday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val result = bolusRepo.getAllLogsImmediate()
            .filter { it.timestamp in dayStart..dayEnd && it.eventType == "SPORT" }
            .distinctBy { it.timestamp }
        Log.d("Synthesis", "Sport logs yesterday: ${result.size}")
        result.forEach { log ->
            Log.d("Synthesis", "  Sport: ${log.sportType} ${log.sportIntensity} " +
                "${log.sportDuration}min at ${log.timestamp}")
        }
        return result
    }

    suspend fun getYesterdayBasalLog(): BolusLog? {
        val yesterday = LocalDate.now().minusDays(1)
        val zone = ZoneId.systemDefault()
        val dayStart = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = yesterday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return bolusRepo.getAllLogsImmediate()
            .filter { it.timestamp in dayStart..dayEnd && it.eventType == "BASAL_INSULIN" }
            .maxByOrNull { it.timestamp }
    }

    suspend fun getOvernightLow(hypoLimit: Float): BolusLog? {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val sixAm = midnight + 6 * 3600 * 1000L
        return bolusRepo.getAllLogsImmediate()
            .filter {
                it.timestamp in midnight..sixAm &&
                it.bloodGlucose > 0 &&
                it.bloodGlucose < hypoLimit
            }
            .minByOrNull { it.bloodGlucose }
    }

    suspend fun saveMetrics(metrics: DailyMetrics) {
        metricsDao.upsert(metrics)
    }

    suspend fun getLogsForDay(start: Long, end: Long): List<BolusLog> =
        bolusRepo.getAllLogsImmediate().filter { it.timestamp in start..end }

    suspend fun getMealSpikes(
        hyperLimit: Float,
        cgmHistory: List<CgmReading>
    ): List<SpikeSummary> {
        val yesterday = LocalDate.now().minusDays(1)
        val zone = ZoneId.systemDefault()
        val dayStart = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = yesterday.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val mealLogs = bolusRepo.getAllLogsImmediate()
            .filter {
                it.timestamp in dayStart..dayEnd &&
                        it.carbs > 0
            }
            .sortedBy { it.timestamp }

        Log.d("Synthesis", "getMealSpikes: checking ${mealLogs.size} meal logs")
        mealLogs.forEach { meal ->
            Log.d("Synthesis", "  Meal at ${meal.timestamp}: ${meal.carbs}g carbs, " +
                    "notes='${meal.notes}', eventType=${meal.eventType}")
        }

        val result = mealLogs.mapNotNull { meal ->
            val windowEnd = meal.timestamp + 2 * 3600 * 1000L

            val bgAtMeal = cgmHistory
                .filter {
                    it.timestamp in
                            meal.timestamp - 10 * 60000L..meal.timestamp + 10 * 60000L
                }
                .minByOrNull { Math.abs(it.timestamp - meal.timestamp) }
                ?.bgValue?.toInt() ?: 0

            val readingsAfterMeal = cgmHistory.filter {
                it.timestamp in meal.timestamp..windowEnd
            }
            val peakReading = readingsAfterMeal
                .filter { it.bgValue > hyperLimit }
                .maxByOrNull { it.bgValue }

            if (peakReading != null) {
                val minutesAfter = ((peakReading.timestamp - meal.timestamp) / 60000L).toInt()
                SpikeSummary(
                    hour = Instant.ofEpochMilli(meal.timestamp)
                        .atZone(ZoneId.systemDefault()).hour,
                    peakBg = peakReading.bgValue.toInt(),
                    bgAtMeal = bgAtMeal,
                    minutesAfterMeal = minutesAfter
                )
            } else null
        }
            .filter { it.minutesAfterMeal >= 15 }
            .groupBy { it.hour }
            .map { (_, hourSpikes) -> hourSpikes.maxByOrNull { it.peakBg }!! }
            .sortedBy { it.hour }

        Log.d("Synthesis", "getMealSpikes: found ${result.size} spikes")
        result.forEach { spike ->
            Log.d("Synthesis", "  Spike at hour ${spike.hour}: " +
                    "peak=${spike.peakBg}, bgAtMeal=${spike.bgAtMeal}, " +
                    "minutesAfter=${spike.minutesAfterMeal}")
        }
        return result
    }

    suspend fun getTodaySteps(): Long {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = System.currentTimeMillis()
        return try {
            hcHelper?.getStepsForDay(dayStart, dayEnd) ?: 0L
        } catch (e: Exception) {
            Log.e("InsightsRepo", "Steps fetch failed: ${e.message}")
            0L
        }
    }
}
