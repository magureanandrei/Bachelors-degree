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

private val STEPS_BAR_COLOR = Color(0xFF4DB6AC)
private val STEPS_YESTERDAY_COLOR = Color(0xFF00897B)

@Composable
fun StepsModal(
    yesterday: DailyMetrics,
    last30Days: List<DailyMetrics>,
    isHcConnected: Boolean,
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
                    "Daily Steps",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    "${yesterday.steps}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = STEPS_YESTERDAY_COLOR
                )
                Text("steps yesterday", fontSize = 12.sp, color = Color.Gray)

                Spacer(Modifier.height(16.dp))

                if (!isHcConnected) {
                    Text(
                        "Connect Health Connect in Settings & Integrations to track steps",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    StepsBarChart(
                        days = last30Days,
                        yesterdayEpochDay = yesterday.dateEpochDay
                    )
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = Color(0xFF00897B))
                }
            }
        }
    }
}

@Composable
private fun StepsBarChart(days: List<DailyMetrics>, yesterdayEpochDay: Long) {
    val textMeasurer = rememberTextMeasurer()
    val dateFormatter = DateTimeFormatter.ofPattern("M/d")

    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        if (days.isEmpty()) return@Canvas

        val topPad = 8f
        val bottomPad = 20f
        val chartH = size.height - topPad - bottomPad
        val maxSteps = days.maxOfOrNull { it.steps }?.toFloat()?.coerceAtLeast(1f) ?: 1f

        val count = days.size
        val gap = 3f
        val barWidth = (size.width / count) - gap

        days.forEachIndexed { i, dm ->
            val left = i * (barWidth + gap)
            val barH = chartH * (dm.steps.toFloat() / maxSteps).coerceIn(0f, 1f)
            val top = topPad + chartH - barH
            val isYesterday = dm.dateEpochDay == yesterdayEpochDay

            drawRoundRect(
                color = if (isYesterday) STEPS_YESTERDAY_COLOR else STEPS_BAR_COLOR,
                topLeft = Offset(left, top),
                size = Size(barWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
            )

            if (i % 7 == 0 || i == days.size - 1) {
                val label = LocalDate.ofEpochDay(dm.dateEpochDay).format(dateFormatter)
                val m = textMeasurer.measure(label, style = TextStyle(fontSize = 8.sp, color = Color.Gray))
                drawText(
                    m,
                    topLeft = Offset(
                        (left + barWidth / 2 - m.size.width / 2).coerceIn(0f, size.width - m.size.width),
                        size.height - bottomPad + 4f
                    )
                )
            }
        }
    }
}
