package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.viewmodel.InsightsViewModel
import com.example.diabetesapp.data.database.BolusDatabase
import com.example.diabetesapp.data.models.DailyMetrics
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.data.repository.InsightsRepository
import com.example.diabetesapp.ui.components.insights.CvModal
import com.example.diabetesapp.ui.components.insights.InsulinCarbsModal
import com.example.diabetesapp.ui.components.insights.StepsModal
import com.example.diabetesapp.ui.components.insights.TirModal
import com.example.diabetesapp.utils.HealthConnectHelper
import com.example.diabetesapp.viewmodel.InsightsViewModelFactory

@Composable
fun InsightsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val bolusRepo = remember { BolusLogRepository(database.bolusLogDao()) }
    val metricsDao = remember { database.dailyMetricsDao() }
    val settingsRepo = remember { BolusSettingsRepository.getInstance(context) }

    val hcHelper = remember {
        try {
            val client = HealthConnectClient.getOrCreate(context)
            HealthConnectHelper(client)
        } catch (e: Exception) {
            null
        }
    }
    val isHcConnected = hcHelper != null

    val insightsRepo = remember {
        InsightsRepository(bolusRepo, metricsDao, hcHelper, context)
    }
    val insightsViewModel: InsightsViewModel = viewModel(
        factory = InsightsViewModelFactory(insightsRepo, settingsRepo)
    )
    val uiState by insightsViewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF00897B)
                )
            }
            uiState.yesterday == null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "No data yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00897B)
                    )
                    Text(
                        "Start logging to see insights",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Insights",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    if (uiState.isBackfilling) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Computing historical data...",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF00897B)
                                )
                            }
                        }
                    }

                    item { TirCard(uiState.yesterday!!, uiState.last7Days, uiState.isCgmUser) }
                    item { CvCard(uiState.yesterday!!, uiState.last30Days) }
                    item { StepsCard(uiState.yesterday!!, uiState.last30Days, isHcConnected) }
                    item { InsulinCarbsCard(uiState.yesterday!!, uiState.last30Days) }
                }
            }
        }
    }
}

// ── Shared card shell ──────────────────────────────────────────────────────────

@Composable
private fun InsightCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    summaryContent: @Composable () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF00897B),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                summaryContent()
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Card 1: Time in Range ──────────────────────────────────────────────────────

@Composable
private fun TirCard(
    yesterday: DailyMetrics,
    last7Days: List<DailyMetrics>,
    isCgmUser: Boolean
) {
    var showModal by remember { mutableStateOf(false) }

    val tirColor = when {
        yesterday.readingCount < 3 -> Color.Gray
        yesterday.tir >= 70f -> Color(0xFF2E7D32)
        yesterday.tir >= 50f -> Color(0xFFF9A825)
        else -> Color(0xFFE53935)
    }

    InsightCard(
        title = "Time in Range",
        icon = Icons.Default.MonitorHeart,
        onClick = { showModal = true }
    ) {
        if (yesterday.readingCount < 3) {
            Text("—", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("Log more days to see trends", fontSize = 11.sp, color = Color.Gray)
        } else {
            Text(
                "${yesterday.tir.toInt()}%",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = tirColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "▼ ${yesterday.tbr.toInt()}% low",
                    fontSize = 11.sp,
                    color = Color(0xFFE53935)
                )
                Text(
                    "▲ ${yesterday.tar.toInt()}% high",
                    fontSize = 11.sp,
                    color = Color(0xFFF9A825)
                )
            }
        }
    }

    if (showModal) {
        TirModal(
            yesterday = yesterday,
            last7Days = last7Days,
            isCgmData = isCgmUser && yesterday.isCgmData,
            onDismiss = { showModal = false }
        )
    }
}

// ── Card 2: Glucose Variability ────────────────────────────────────────────────

@Composable
private fun CvCard(
    yesterday: DailyMetrics,
    last30Days: List<DailyMetrics>
) {
    var showModal by remember { mutableStateOf(false) }

    val cvColor = when {
        yesterday.readingCount < 3 -> Color.Gray
        yesterday.cv <= 36f -> Color(0xFF2E7D32)
        else -> Color(0xFFE53935)
    }
    val cvLabel = when {
        yesterday.readingCount < 3 -> ""
        yesterday.cv <= 36f -> "Stable"
        else -> "Unstable"
    }

    InsightCard(
        title = "Glucose Variability (CV)",
        icon = Icons.Default.Bloodtype,
        onClick = { showModal = true }
    ) {
        if (yesterday.readingCount < 3) {
            Text("—", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Text("Log more days to see trends", fontSize = 11.sp, color = Color.Gray)
        } else {
            Text(
                "${yesterday.cv.toInt()}%",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = cvColor
            )
            Text(cvLabel, fontSize = 12.sp, color = cvColor, fontWeight = FontWeight.Medium)
        }
    }

    if (showModal) {
        CvModal(last30Days = last30Days, onDismiss = { showModal = false })
    }
}

// ── Card 3: Steps ──────────────────────────────────────────────────────────────

@Composable
private fun StepsCard(
    yesterday: DailyMetrics,
    last30Days: List<DailyMetrics>,
    isHcConnected: Boolean
) {
    var showModal by remember { mutableStateOf(false) }

    InsightCard(
        title = "Steps",
        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
        onClick = { showModal = true }
    ) {
        Text(
            "${yesterday.steps}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00897B)
        )
        Text("steps yesterday", fontSize = 11.sp, color = Color.Gray)
    }

    if (showModal) {
        StepsModal(
            yesterday = yesterday,
            last30Days = last30Days,
            isHcConnected = isHcConnected,
            onDismiss = { showModal = false }
        )
    }
}

// ── Card 4: Insulin & Carbs ────────────────────────────────────────────────────

@Composable
private fun InsulinCarbsCard(
    yesterday: DailyMetrics,
    last30Days: List<DailyMetrics>
) {
    var showModal by remember { mutableStateOf(false) }

    InsightCard(
        title = "Insulin & Carbs",
        icon = Icons.Default.Vaccines,
        onClick = { showModal = true }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                "${yesterday.insulinUnits.toInt()}U insulin",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Text("|", fontSize = 16.sp, color = Color.LightGray)
            Text(
                "${yesterday.carbs.toInt()}g carbs",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9800)
            )
        }
        Text("yesterday", fontSize = 11.sp, color = Color.Gray)
    }

    if (showModal) {
        InsulinCarbsModal(
            yesterday = yesterday,
            last30Days = last30Days,
            onDismiss = { showModal = false }
        )
    }
}
