package com.example.diabetesapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.algorithm.BreakdownEntry
import com.example.diabetesapp.algorithm.Effect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DoseBreakdownCard(
    standardDose: Double,
    suggestedDose: Double,
    rationale: String = "",
    breakdownSteps: List<BreakdownEntry> = emptyList(),
    isAid: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = "Algorithm Insights",
                    tint = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Algorithm Rationale",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (standardDose != suggestedDose) {
                if (isAid) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pump-Managed:", fontSize = 13.sp, color = Color(0xFF00695C))
                        Text("Corrections handled by SmartGuard", fontSize = 13.sp, color = Color(0xFF00695C))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Standard Math:", fontSize = 13.sp, color = Color.Gray)
                        Text("${String.format("%.1f", standardDose)}U", fontSize = 13.sp, color = Color.Gray, textDecoration = TextDecoration.LineThrough)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Adjusted Dose:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${suggestedDose}U", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (breakdownSteps.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val visibleSteps = breakdownSteps.filter { it.description.isNotBlank() }
                    visibleSteps.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = when (entry.effect) {
                                        Effect.INCREASE -> Color(0xFFD84315)
                                        Effect.DECREASE -> Color(0xFF2E7D32)
                                        Effect.WARNING  -> Color(0xFFE65100)
                                        Effect.NEUTRAL  -> Color(0xFF004D40)
                                    }
                                )
                                Text(
                                    text = entry.description,
                                    fontSize = 12.sp,
                                    color = Color(0xFF37474F),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                        if (index < visibleSteps.lastIndex) {
                            HorizontalDivider(
                                color = Color(0xFF80CBC4).copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            } else {
                val paragraphs = rationale.split("\n\n").filter { it.isNotBlank() }
                if (paragraphs.isEmpty()) {
                    Text(
                        text = "Standard calculation applied. No active modifiers.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color.DarkGray
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        paragraphs.forEach { paragraph ->
                            Text(
                                text = paragraph.trim(),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsChangeDivider(timestamp: Long, description: String) {
    val timeStr = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFFF9800).copy(alpha = 0.4f)
        )
        Text(
            text = "  $timeStr · Settings Changed  ",
            fontSize = 9.sp,
            color = Color(0xFFFF9800).copy(alpha = 0.8f)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFFF9800).copy(alpha = 0.4f)
        )
    }
}
