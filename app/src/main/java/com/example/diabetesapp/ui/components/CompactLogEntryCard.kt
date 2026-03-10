package com.example.diabetesapp.ui.components

import androidx.compose.foundation.background
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
import androidx.room.jarjarred.org.antlr.v4.codegen.model.Sync
import com.example.diabetesapp.data.models.BolusLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CompactLogEntryCard(log: BolusLog, onClick: () -> Unit) {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = formatter.format(Date(log.timestamp))
    val isAutoEntry = log.notes == "Auto-entry via CareLink"

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAutoEntry) Color(0xFFF5FFFE) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                    log.eventType == "SPORT" -> Icon(Icons.Default.DirectionsRun, null, tint = if(log.status == "PLANNED") Color(0xFFFF9800) else Color(0xFF00695C), modifier = Modifier.size(16.dp))
                    log.eventType == "SMART_BOLUS" -> Icon(Icons.Default.AutoFixHigh, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    log.carbs > 0 && log.administeredDose == 0.0 -> Icon(Icons.Default.Restaurant, null, tint = Color(0xFFE91E63), modifier = Modifier.size(16.dp))
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
                    Text("${log.sportDuration?.toInt()}m ${log.sportType}", fontSize = 13.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.weight(1f)) {
                    if (log.status == "PLANNED") {
                        Text("Pending", fontSize = 11.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, modifier = Modifier
                            .background(Color(0xFFFFF3E0), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                    } else {
                        Text("Done", fontSize = 11.sp, color = Color(0xFF00695C), fontWeight = FontWeight.Bold, modifier = Modifier
                            .background(Color(0xFFE0F2F1), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1.5f)) {
                    if (log.bloodGlucose > 0) {
                        val color = if (log.bloodGlucose > 180 || log.bloodGlucose < 70) Color.Red else Color(0xFF00897B)
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
