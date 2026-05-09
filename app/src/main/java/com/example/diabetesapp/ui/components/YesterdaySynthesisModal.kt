package com.example.diabetesapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.DailyMetrics
import com.example.diabetesapp.data.repository.SpikeSummary
import com.example.diabetesapp.ui.components.insights.TBR_COLOR
import com.example.diabetesapp.ui.components.insights.TIR_COLOR
import com.example.diabetesapp.ui.components.insights.TAR_COLOR
import com.example.diabetesapp.ui.components.insights.TirStackedBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SynthesisData(
    val metrics: DailyMetrics,
    val sportLogs: List<BolusLog>,
    val basalLog: BolusLog?,
    val overnightLow: BolusLog?,
    val mealSpikes: List<SpikeSummary> = emptyList(),
    val isMdi: Boolean,
    val isCgmEnabled: Boolean
)

@Composable
fun YesterdaySynthesisModal(
    data: SynthesisData,
    onDismiss: () -> Unit
) {
    val yesterday = LocalDate.now().minusDays(1)
    val headerDate = yesterday.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    val m = data.metrics

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header row with date chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Yesterday",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00897B)
                    )
                    Surface(
                        color = Color(0xFFE0F2F1),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            headerDate,
                            fontSize = 11.sp,
                            color = Color(0xFF00897B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // TIR bar
                val hasData = m.readingCount >= 3
                if (hasData) {
                    TirStackedBar(
                        tbr = m.tbr,
                        tir = m.tir,
                        tar = m.tar,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                        heightDp = 28.dp
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${m.tbr.toInt()}% low", fontSize = 11.sp, color = TBR_COLOR, fontWeight = FontWeight.Medium)
                        Text("·", fontSize = 11.sp, color = Color.Gray)
                        Text("${m.tir.toInt()}% in range", fontSize = 11.sp, color = TIR_COLOR, fontWeight = FontWeight.Medium)
                        Text("·", fontSize = 11.sp, color = Color.Gray)
                        Text("${m.tar.toInt()}% high", fontSize = 11.sp, color = TAR_COLOR, fontWeight = FontWeight.Medium)
                    }
                } else {
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Limited data — fewer than 3 readings",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = Color(0xFFE0E0E0),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Metrics grid (2x2 Card cells)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCell(
                        label = "Avg BG",
                        value = if (hasData) "${m.avgBg.toInt()} mg/dL" else "—",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCell(
                        label = "CV",
                        value = if (hasData) "${m.cv.toInt()}% (${if (m.cv <= 36f) "Stable" else "Variable"})" else "—",
                        valueColor = if (!hasData) Color.Black else if (m.cv <= 36f) Color(0xFF2E7D32) else Color(0xFFE53935),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCell(
                        label = "Steps",
                        value = if (m.steps > 0) "%,d".format(m.steps) else "Not tracked",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCell(
                        label = "Insulin · Carbs",
                        value = "${m.insulinUnits.toInt()}U · ${m.carbs.toInt()}g",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Event rows
                val showEvents = data.sportLogs.isNotEmpty() ||
                    data.isMdi ||
                    data.overnightLow != null ||
                    (data.isCgmEnabled && data.mealSpikes.isNotEmpty())

                if (showEvents) {
                    HorizontalDivider(
                        color = Color(0xFFE0E0E0),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val walkingLogs = data.sportLogs.filter { it.sportType == "Walking" }
                        val sportLogs = data.sportLogs.filter { it.sportType != "Walking" }

                        if (walkingLogs.isNotEmpty()) {
                            val totalWalkingMins = walkingLogs.sumOf { it.sportDuration?.toInt() ?: 0 }
                            Surface(
                                color = Color(0xFFE0F2F1),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.DirectionsWalk,
                                        contentDescription = null,
                                        tint = Color(0xFF00897B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "$totalWalkingMins min walked",
                                        fontSize = 13.sp,
                                        color = Color(0xFF00695C),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        sportLogs.forEach { log ->
                            val duration = log.sportDuration?.let { "${it.toInt()}min" } ?: ""
                            val type = log.sportType ?: ""
                            val intensity = log.sportIntensity ?: ""
                            EventRow(
                                icon = { Icon(Icons.Default.DirectionsRun, null, tint = Color(0xFF00897B), modifier = Modifier.size(16.dp)) },
                                text = listOf(duration, type, intensity).filter { it.isNotEmpty() }.joinToString(" ")
                            )
                        }

                        if (data.isMdi) {
                            if (data.basalLog != null) {
                                val time = Instant.ofEpochMilli(data.basalLog.timestamp)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                                EventRow(
                                    icon = { Icon(Icons.Default.Check, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp)) },
                                    text = "Basal logged at $time"
                                )
                            } else {
                                EventRow(
                                    icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFFFA000), modifier = Modifier.size(16.dp)) },
                                    text = "No basal logged yesterday"
                                )
                            }
                        }

                        data.overnightLow?.let { low ->
                            val time = Instant.ofEpochMilli(low.timestamp)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("h:mm a"))
                            EventRow(
                                icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp)) },
                                text = "Overnight low: ${low.bloodGlucose.toInt()} mg/dL at $time"
                            )
                        }

                        if (data.isCgmEnabled && data.mealSpikes.isNotEmpty()) {
                            data.mealSpikes.forEach { spike ->
                                val mealPeriod = when (spike.hour) {
                                    in 5..10 -> "Breakfast"
                                    in 11..14 -> "Lunch"
                                    in 15..17 -> "Snack"
                                    in 18..22 -> "Dinner"
                                    else -> "Late-night meal"
                                }
                                val icrPeriod = when (spike.hour) {
                                    in 5..10 -> "morning ICR"
                                    in 11..14 -> "noon ICR"
                                    in 15..17 -> "afternoon ICR"
                                    in 18..22 -> "evening ICR"
                                    else -> "ICR settings"
                                }
                                val rise = spike.peakBg - spike.bgAtMeal
                                val riseText = if (spike.bgAtMeal > 0 && rise > 0)
                                    ", ↑${rise} mg/dL from ${spike.bgAtMeal} mg/dL"
                                else ""
                                val timeText = String.format("%02d:00", spike.hour)

                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("—", fontSize = 13.sp, color = Color(0xFFF9A825))
                                    Text(
                                        "$mealPeriod (~$timeText): peaked at ${spike.peakBg} mg/dL" +
                                                "$riseText (~${spike.minutesAfterMeal} min after eating). " +
                                                "Consider reviewing your $icrPeriod.",
                                        fontSize = 13.sp,
                                        color = Color(0xFFF9A825),
                                        lineHeight = 18.sp
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }

                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tip: Pre-bolusing 10–15 min before meals can help reduce post-meal spikes.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.5.dp, Color(0xFF00897B)),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00897B))
                ) {
                    Text("Got it", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    valueColor: Color = Color.Black,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun EventRow(
    icon: @Composable () -> Unit,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon()
        Text(text, fontSize = 12.sp, color = Color(0xFF37474F))
    }
}
