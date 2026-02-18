package com.example.diabetesapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
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
    val repository = remember { BolusLogRepository(database.bolusLogDao()) }
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModelFactory(repository))

    val logs by viewModel.allLogs.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("History Log", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No records found.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { log ->
                    LogEntryCard(log)
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(log: BolusLog) {
    val formatter = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    val dateString = formatter.format(Date(log.timestamp))

    // State to track if the card is expanded
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = { isExpanded = !isExpanded }, // Make the whole card clickable!
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: Date and Event Icon
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dateString, fontSize = 12.sp, color = Color.Gray)

                // Dynamic Icon based on Event Type
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (log.eventType) {
                        "SMART_BOLUS" -> {
                            Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF9800))
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
                            Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(14.dp), tint = Color(0xFF00695C))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sport Event", fontSize = 12.sp, color = Color(0xFF00695C), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body: Values
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    if (log.bloodGlucose > 0) Text("BG: ${log.bloodGlucose} mg/dL", fontWeight = FontWeight.SemiBold)
                    if (log.carbs > 0) Text("Carbs: ${log.carbs}g", color = Color.Gray, fontSize = 14.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (log.administeredDose > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${String.format(Locale.US, "%.1f", log.administeredDose)} U", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Icon(Icons.Default.Vaccines, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.padding(start = 4.dp).size(20.dp))
                        }

                        if (log.suggestedDose != log.administeredDose && log.eventType == "SMART_BOLUS") {
                            Text("Suggested: ${String.format(Locale.US, "%.1f", log.suggestedDose)} U", color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- THE EXPANDABLE SECTION ---
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (log.notes.isNotBlank()) {
                        Text("Notes:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(log.notes, fontSize = 14.sp, color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!log.clinicalSuggestion.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("CDSS Insight Provided:", fontSize = 12.sp, color = Color(0xFF00695C), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(log.clinicalSuggestion, fontSize = 13.sp, color = Color(0xFF004D40), lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}