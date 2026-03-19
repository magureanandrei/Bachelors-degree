package com.example.diabetesapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.ui.components.DoseBreakdownCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogEntryCard(
    log: BolusLog,
    hypoLimit: Float = 70f,
    hyperLimit: Float = 180f,
    onDelete: () -> Unit
){
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = timeFormatter.format(Date(log.timestamp))

    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isAutoEntry = log.notes?.contains("Auto-imported") == true
            || log.notes?.startsWith("Auto-detected") == true
            || log.notes == "Auto-entry via CareLink"
    val isWalk = log.eventType == "SPORT" && log.sportType == "Walking" && isAutoEntry

    Card(
        onClick = { isExpanded = !isExpanded },
        modifier = Modifier.fillMaxWidth().then(
            if (isWalk) Modifier.border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(10.dp))
            else Modifier
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isWalk -> Color(0xFFF5F7F8)
                log.eventType == "SPORT" && log.status?.uppercase() == "PLANNED" -> Color(0xFFFFF3E0) // Optional: Light orange background for pending
                log.eventType == "SPORT" && !isWalk -> Color(0xFF4DB6AC) // <-- YOUR DARK TEAL BACKGROUND HERE
                isAutoEntry -> Color(0xFFE0F2F1)
                else -> Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isWalk) 0.dp else 1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(
            horizontal = 12.dp,
            vertical = if (isWalk) 4.dp else 8.dp
        )){

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
                        "CORRECTION" -> {
                            Icon(Icons.Default.Vaccines, null, modifier = Modifier.size(14.dp), tint = Color(0xFF1976D2))
                            Spacer(modifier = Modifier.width(4.dp))
                            val label = if (log.notes == "Auto-entry via CareLink") "CareLink Sync" else "Correction"
                            Text(label, fontSize = 12.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                        }
                        "MANUAL_INSULIN", "MANUAL_PEN", "MIXED_LOG" -> {
                            Icon(
                                Icons.Default.Vaccines,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF1976D2)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (log.eventType == "MANUAL_PEN") "Pen Correction" else "Manual Entry",
                                fontSize = 12.sp,
                                color = Color(0xFF1976D2),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        "SPORT" -> {
                            val sportColor = when {
                                log.status?.uppercase() == "PLANNED" -> Color(0xFFFF9800) // Added uppercase() just in case!
                                isWalk -> Color(0xFF90A4AE)
                                else -> Color(0xFF00695C) // <-- Changed to match compact card
                            }
                            Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(14.dp), tint = sportColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isWalk) "Walk" else "Sport Event", fontSize = 12.sp, color = sportColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- THE DYNAMIC BODY ---
            if (log.eventType == "SPORT") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "${log.sportDuration?.toInt()} min ${log.sportType ?: ""}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isWalk) Color(0xFF78909C) else Color.DarkGray
                        )
                        if (!isWalk && !log.sportIntensity.isNullOrBlank()) {
                            Text("· ${log.sportIntensity}", fontSize = 12.sp, color = Color.DarkGray)
                        }
                    }
                    val isAutoActivity = log.notes?.contains("Auto-imported") == true
                            || log.notes?.startsWith("Auto-detected") == true
                    when {
                        log.status == "PLANNED" -> Text(
                            "Pending", fontSize = 12.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xFFFFF3E0), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        isAutoActivity -> Text(
                            "Auto", fontSize = 12.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xFFE3F2FD), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        else -> Text(
                            "Completed", fontSize = 12.sp, color = Color(0xFF00695C), fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xFFE0F2F1), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                // STANDARD DIABETES LAYOUT (BG, Carbs, Insulin)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (log.bloodGlucose > 0) {
                            val color = when {
                                log.bloodGlucose < hypoLimit -> Color(0xFFE53935)
                                log.bloodGlucose > hyperLimit -> Color(0xFFFFB74D)
                                else -> Color(0xFF00897B)
                            }
                            Text("BG: ${log.bloodGlucose.toInt()}", fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
                        }
                        if (log.carbs > 0) Text("${log.carbs.toInt()}g carbs", color = Color.Gray, fontSize = 12.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (log.administeredDose > 0) {
                            if (log.suggestedDose != log.administeredDose && log.eventType == "SMART_BOLUS") {
                                Text(
                                    "${String.format(Locale.US, "%.1f", log.suggestedDose)}U ",
                                    color = Color(0xFF81C784),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "${String.format(Locale.US, "%.1f", log.administeredDose)} U",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                            Icon(
                                Icons.Default.Vaccines,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.padding(start = 4.dp).size(14.dp)
                            )
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
                        Text("Notes:", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
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