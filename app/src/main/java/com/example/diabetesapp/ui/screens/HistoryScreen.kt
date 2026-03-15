package com.example.diabetesapp.ui.screens

import com.example.diabetesapp.ui.components.LogEntryCard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.data.database.BolusDatabase
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.viewmodel.DashboardViewModel
import com.example.diabetesapp.viewmodel.DashboardViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val logRepository = remember { BolusLogRepository(database.bolusLogDao()) }
    val settingsRepository = remember { BolusSettingsRepository.getInstance(context) }

    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(logRepository, settingsRepository, context)
    )

    val historyEvents by viewModel.historyEvents.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isCgmEnabled = settings.glucoseSource == "CGM"

    LaunchedEffect(Unit) {
        viewModel.fetchHistoryData()
    }

    val groupedLogs = remember(historyEvents) {
        historyEvents
            .sortedByDescending { it.timestamp }
            .groupBy { log ->
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    .format(Date(log.timestamp))
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "History Log",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        if (historyEvents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No records found.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                groupedLogs.forEach { (dateString, dailyLogs) ->
                    item {
                        Text(
                            text = dateString,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(dailyLogs) { log ->
                        LogEntryCard(log = log, onDelete = { viewModel.deleteLog(log) })
                    }
                }
            }
        }
    }
}