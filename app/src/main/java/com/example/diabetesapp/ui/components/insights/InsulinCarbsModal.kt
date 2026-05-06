package com.example.diabetesapp.ui.components.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.diabetesapp.data.models.DailyMetrics
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val INSULIN_COLOR = Color(0xFF1976D2)
private val CARBS_COLOR = Color(0xFFFF9800)

@Composable
fun InsulinCarbsModal(
    yesterday: DailyMetrics,
    last30Days: List<DailyMetrics>,
    onDismiss: () -> Unit
) {
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
                Text(
                    "Insulin & Carbs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text(
                            "${yesterday.insulinUnits.toInt()}U",
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = INSULIN_COLOR
                        )
                        Text("insulin yesterday", fontSize = 11.sp, color = Color.Gray)
                    }
                    Column {
                        Text(
                            "${yesterday.carbs.toInt()}g",
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = CARBS_COLOR
                        )
                        Text("carbs yesterday", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(16.dp))
                InsulinCarbsBarChart(last30Days)

                Spacer(Modifier.height(12.dp))
                Text(
                    "Insulin reflects logged bolus doses only. Basal insulin not included.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = Color(0xFF00897B))
                }
            }
        }
    }
}

@Composable
private fun InsulinCarbsBarChart(days: List<DailyMetrics>) {
    val textMeasurer = rememberTextMeasurer()
    val dateFormatter = DateTimeFormatter.ofPattern("M/d")

    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        if (days.isEmpty()) return@Canvas

        val topPad = 8f
        val bottomPad = 20f
        val chartH = size.height - topPad - bottomPad

        val maxInsulin = days.maxOfOrNull { it.insulinUnits }?.coerceAtLeast(1f) ?: 1f
        val maxCarbs = days.maxOfOrNull { it.carbs }?.coerceAtLeast(1f) ?: 1f
        val normalizer = maxOf(maxInsulin, maxCarbs / 10f).coerceAtLeast(1f)

        val count = days.size
        val pairGap = 2f
        val groupGap = 4f
        val totalSlotWidth = size.width / count
        val singleBarWidth = (totalSlotWidth - groupGap - pairGap) / 2f

        days.forEachIndexed { i, dm ->
            val groupLeft = i * totalSlotWidth
            val insulinH = chartH * (dm.insulinUnits / normalizer).coerceIn(0f, 1f)
            val carbsH = chartH * ((dm.carbs / 10f) / normalizer).coerceIn(0f, 1f)

            // Insulin bar (left of pair)
            drawRoundRect(
                color = INSULIN_COLOR,
                topLeft = Offset(groupLeft, topPad + chartH - insulinH),
                size = Size(singleBarWidth, insulinH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
            )
            // Carbs bar (right of pair)
            drawRoundRect(
                color = CARBS_COLOR,
                topLeft = Offset(groupLeft + singleBarWidth + pairGap, topPad + chartH - carbsH),
                size = Size(singleBarWidth, carbsH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
            )

            if (i % 7 == 0 || i == days.size - 1) {
                val label = LocalDate.ofEpochDay(dm.dateEpochDay).format(dateFormatter)
                val m = textMeasurer.measure(label, style = TextStyle(fontSize = 8.sp, color = Color.Gray))
                drawText(
                    m,
                    topLeft = Offset(
                        (groupLeft + totalSlotWidth / 2 - m.size.width / 2).coerceIn(0f, size.width - m.size.width),
                        size.height - bottomPad + 4f
                    )
                )
            }
        }
    }
}
