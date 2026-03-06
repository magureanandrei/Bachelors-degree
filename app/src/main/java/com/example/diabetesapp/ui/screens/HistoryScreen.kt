package com.example.diabetesapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
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
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.viewmodel.DashboardViewModel
import com.example.diabetesapp.viewmodel.DashboardViewModelFactory
import com.example.diabetesapp.ui.components.DoseBreakdownCard
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val logRepository = remember { BolusLogRepository(database.bolusLogDao()) }
    val settingsRepository = remember { BolusSettingsRepository(context) }

    // 2. Pass both to the factory
    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(logRepository, settingsRepository)
    )

    val logs by viewModel.allLogs.collectAsState()

    val groupedLogs = remember(logs) {
        logs.groupBy { log ->
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            formatter.format(Date(log.timestamp))
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

        if (logs.isEmpty()) {
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

@Composable
fun LogEntryCard(log: BolusLog, onDelete: () -> Unit) {
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = timeFormatter.format(Date(log.timestamp))

    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        onClick = { isExpanded = !isExpanded },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Header: Time and Event Icon
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(timeString, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (log.eventType) {
                        "SMART_BOLUS" -> {
                            Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF9800))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Smart Calculation", fontSize = 12.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                        }
                        "MEAL" -> {
                            Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(14.dp), tint = Color(0xFFE91E63))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Meal Log", fontSize = 12.sp, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                        }
                        "BG_CHECK" -> {
                            Icon(Icons.Default.Bloodtype, null, modifier = Modifier.size(14.dp), tint = Color(0xFFD32F2F))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BG Check", fontSize = 12.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                        }
                        "MANUAL_INSULIN", "MIXED_LOG" -> {
                            Icon(Icons.Default.Vaccines, null, modifier = Modifier.size(14.dp), tint = Color(0xFF1976D2))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manual Entry", fontSize = 12.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                        }
                        "SPORT" -> {
                            val sportColor = if (log.status == "PLANNED") Color(0xFFFF9800) else Color(0xFF00695C)
                            Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(14.dp), tint = sportColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sport Event", fontSize = 12.sp, color = sportColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- THE DYNAMIC BODY ---
            if (log.eventType == "SPORT") {
                // SPORT-ONLY LAYOUT
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${log.sportDuration?.toInt()} min ${log.sportType ?: ""}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.DarkGray)
                        Text("Intensity: ${log.sportIntensity ?: ""}", fontSize = 13.sp, color = Color.Gray)
                    }
                    if (log.status == "PLANNED") {
                        Text("Pending", fontSize = 12.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFFFFF3E0), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                    } else {
                        Text("Completed", fontSize = 12.sp, color = Color(0xFF00695C), fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFFE0F2F1), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            } else {
                // STANDARD DIABETES LAYOUT (BG, Carbs, Insulin)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        if (log.bloodGlucose > 0) {
                            val color = if (log.bloodGlucose > 180 || log.bloodGlucose < 70) Color.Red else Color(0xFF00897B)
                            Text("BG: ${log.bloodGlucose} mg/dL", fontWeight = FontWeight.Bold, color = color)
                        }
                        if (log.carbs > 0) Text("Carbs: ${log.carbs}g", color = Color.Gray, fontSize = 13.sp)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        if (log.administeredDose > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${String.format(Locale.US, "%.1f", log.administeredDose)} U", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Icon(Icons.Default.Vaccines, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.padding(start = 4.dp).size(16.dp))
                            }
                            if (log.suggestedDose != log.administeredDose && log.eventType == "SMART_BOLUS") {
                                Text("Suggested: ${String.format(Locale.US, "%.1f", log.suggestedDose)} U", color = Color(0xFF81C784), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // --- THE EXPANDABLE SECTION (Unchanged) ---
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (log.notes.isNotBlank()) {
                        Text("Notes:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(log.notes, fontSize = 13.sp, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

// Replaced with shared DoseBreakdownCard
                    if (!log.clinicalSuggestion.isNullOrBlank()) {
                        DoseBreakdownCard(
                            standardDose = log.standardDose ?: 0.0,
                            suggestedDose = log.suggestedDose ?: 0.0,
                            rationale = log.clinicalSuggestion
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Entry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Entry?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete this log? This will remove it from your history and graphs.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(12.dp)
        )
    }
}