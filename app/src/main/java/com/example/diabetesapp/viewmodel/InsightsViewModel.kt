package com.example.diabetesapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.models.DailyMetrics
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.data.repository.InsightsRepository
import com.example.diabetesapp.utils.CgmHelper
import com.example.diabetesapp.utils.MetricsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class InsightsUiState(
    val isLoading: Boolean = true,
    val isBackfilling: Boolean = false,
    val yesterday: DailyMetrics? = null,
    val last7Days: List<DailyMetrics> = emptyList(),
    val last30Days: List<DailyMetrics> = emptyList(),
    val isCgmUser: Boolean = false
)

class InsightsViewModel(
    private val insightsRepo: InsightsRepository,
    private val settingsRepo: BolusSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    suspend fun getToday(): DailyMetrics? {
        return insightsRepo.getToday()
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = settingsRepo.getSettingsImmediate()

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            // Live TIR from xDrip
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = System.currentTimeMillis()

            val bgReadings = try {
                CgmHelper.getBgHistoryExtended(days = 2)
                    .filter { it.timestamp in dayStart..dayEnd }
                    .map { it.bgValue.toFloat() }
            } catch (e: Exception) {
                emptyList()
            }

            val todayDbLogs = withContext(Dispatchers.IO) {
                insightsRepo.getLogsForDay(dayStart, dayEnd)
            }

            val todaySteps = try {
                withContext(Dispatchers.IO) { insightsRepo.getTodaySteps() }
            } catch (e: Exception) { 0L }

            val liveMetrics = if (bgReadings.isNotEmpty()) {
                MetricsCalculator.computeDayMetrics(
                    dateEpochDay = today.toEpochDay(),
                    bgReadings = bgReadings,
                    insulinLogs = todayDbLogs,
                    hypoLimit = settings.hypoLimit,
                    hyperLimit = settings.hyperLimit,
                    steps = todaySteps,
                    isCgmData = true
                )
            } else null

            if (liveMetrics != null) {
                insightsRepo.saveMetrics(liveMetrics)
            }

            // 7 and 30 day from DB (best effort)
            val last7 = try {
                insightsRepo.getLast7Days()
            } catch (e: Exception) {
                emptyList()
            }
            val last30 = try {
                insightsRepo.getLast30Days()
            } catch (e: Exception) {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                _uiState.value = InsightsUiState(
                    isLoading = false,
                    isBackfilling = false,
                    yesterday = liveMetrics,
                    last7Days = last7,
                    last30Days = last30,
                    isCgmUser = settings.isCgmEnabled
                )
            }
        }
    }
}


class InsightsViewModelFactory(
    private val insightsRepo: InsightsRepository,
    private val settingsRepo: BolusSettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return InsightsViewModel(insightsRepo, settingsRepo) as T
    }
}
