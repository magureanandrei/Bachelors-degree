package com.example.diabetesapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
fun CompactLogEntryCard(
    log: BolusLog,
    hypoLimit: Float = 70f,
    hyperLimit: Float = 180f,
    onClick: () -> Unit
) {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = formatter.format(Date(log.timestamp))
    val isAutoEntry = log.notes == "Auto-entry via CareLink"
            || log.notes?.startsWith("Auto-imported") == true
            || log.notes?.startsWith("Auto-detected") == true
    val isWalk = log.eventType == "SPORT" && log.sportType == "Walking" && isAutoEntry

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(
            if (isWalk) Modifier.border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(8.dp))
            else Modifier
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isWalk -> Color(0xFFECEFF1)        // more visible grey
                isAutoEntry -> Color(0xFFF5FFFE)
                else -> Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isWalk) 0.dp else 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Time & Icon
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.2f)) {
                Text(timeString, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Spacer(modifier = Modifier.width(8.dp))
                when {
                    log.eventType == "SPORT" -> Icon(
                        Icons.Default.DirectionsRun, null,
                        tint = if (log.status == "PLANNED") Color(0xFFFF9800)
                        else if (isWalk) Color(0xFF90A4AE)  // grey for auto walks
                        else Color(0xFF00695C),
                        modifier = Modifier.size(16.dp)
                    )
                    log.eventType == "SMART_BOLUS" -> Icon(Icons.Default.AutoFixHigh, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    log.carbs > 0 && log.administeredDose >= 0.0 -> Icon(Icons.Default.Restaurant, null, tint = Color(0xFFE91E63), modifier = Modifier.size(16.dp))
                    log.administeredDose > 0 -> Icon(Icons.Default.Vaccines, null, tint = Color(0xFF1976D2), modifier = Modifier.size(16.dp))
                    else -> Icon(Icons.Default.Bloodtype, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                }
                // Auto-entry sync badge
                if (isAutoEntry) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Auto-entry",
                        tint = Color(0xFF00897B),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            if (log.eventType == "SPORT") {
                Row(modifier = Modifier.weight(1.5f)) {
                    Text(
                        "${log.sportDuration?.toInt()}m ${log.sportType}",
                        fontSize = 13.sp,
                        color = if (isWalk) Color(0xFF90A4AE) else Color.DarkGray,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.weight(1f)) {
                    val isAutoActivity = log.notes?.contains("Auto-imported") == true
                            || log.notes?.startsWith("Auto-detected") == true
                    when {
                        log.status == "PLANNED" -> Text(
                            "Pending", fontSize = 11.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xFFFFF3E0), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        isAutoActivity -> Text(
                            "Auto", fontSize = 11.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xFFE3F2FD), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        else -> Text(
                            "Done", fontSize = 11.sp, color = Color(0xFF00695C), fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color(0xFFE0F2F1), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1.5f)) {
                    if (log.bloodGlucose > 0) {
                        val color = when {
                            log.bloodGlucose < hypoLimit -> Color(0xFFE53935)
                            log.bloodGlucose > hyperLimit -> Color(0xFFFFB74D)
                            else -> Color(0xFF00897B)
                        }
                        Text("${log.bloodGlucose}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
                    } else {
                        // Show "Auto-entry" label where BG would normally be
                        if (isAutoEntry) {
                            Text("auto", fontSize = 11.sp, color = Color(0xFF00897B),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        } else {
                            Text("-", color = Color.LightGray)
                        }
                    }
                    if (log.carbs > 0) { Text("${log.carbs}g", fontSize = 14.sp, color = Color.Gray) }
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.weight(1f)) {
                    if (log.administeredDose > 0) {
                        Text("${String.format(Locale.US, "%.1f", log.administeredDose)}U",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (isAutoEntry) Color(0xFF00897B) else Color(0xFF2E7D32))
                    } else { Text("-", color = Color.LightGray) }
                }
            }
        }
    }
}
