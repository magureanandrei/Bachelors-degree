package com.example.diabetesapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.data.models.BolusLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogDetailsDialog(log: BolusLog, onDismiss: () -> Unit) {
    val formatter = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entry Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatter.format(Date(log.timestamp)), fontSize = 12.sp, color = Color.Gray)

                HorizontalDivider()

                if (log.eventType == "BASAL_INSULIN") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Type:", color = Color.Gray)
                        Text("Long-Acting (Basal)", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Dose:", color = Color.Gray)
                        Text(
                            "${String.format(Locale.US, "%.1f", log.administeredDose)} U",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                } else if (log.eventType == "SPORT") {
                    // --- SPORT ONLY MODAL DETAILS ---
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sport Type:", color = Color.Gray)
                        Text("${log.sportType ?: "Unknown"}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Duration:", color = Color.Gray)
                        Text("${log.sportDuration?.toInt() ?: 0} mins", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Intensity:", color = Color.Gray)
                        Text("${log.sportIntensity ?: "Medium"}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status:", color = Color.Gray)
                        val statusColor = if (log.status == "PLANNED") Color(0xFFFF9800) else Color(0xFF00695C)
                        val statusText = if (log.status == "PLANNED") "Pending Verification" else "Completed"
                        Text(statusText, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                } else {
                    // --- STANDARD DIABETES MODAL DETAILS ---
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Blood Glucose:", color = Color.Gray)
                        Text(if (log.bloodGlucose > 0) "${log.bloodGlucose} mg/dL" else "None", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Carbohydrates:", color = Color.Gray)
                        Text(if (log.carbs > 0) "${log.carbs} g" else "None", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Insulin Given:", color = Color.Gray)
                        Text(if (log.administeredDose > 0) "${String.format(Locale.US, "%.1f", log.administeredDose)} U" else "None", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }

                if (log.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notes:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(log.notes, fontSize = 14.sp, color = Color.DarkGray)
                }

// Replaced with shared DoseBreakdownCard
                if (!log.clinicalSuggestion.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DoseBreakdownCard(
                        standardDose = log.standardDose ?: 0.0,
                        suggestedDose = log.suggestedDose ?: 0.0,
                        rationale = log.clinicalSuggestion
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF00897B), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White
    )
}