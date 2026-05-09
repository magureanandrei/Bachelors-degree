package com.example.diabetesapp.ui.components.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.diabetesapp.data.models.DailyMetrics
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TirModal(
    yesterday: DailyMetrics,
    last7Days: List<DailyMetrics>,
    isCgmData: Boolean,
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
                    "Time in Range",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
                Spacer(Modifier.height(16.dp))

                Text("Yesterday", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                TirStackedBar(
                    tbr = yesterday.tbr,
                    tir = yesterday.tir,
                    tar = yesterday.tar,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                    heightDp = 32.dp
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(color = TBR_COLOR, label = "${yesterday.tbr.toInt()}% low")
                    LegendDot(color = TIR_COLOR, label = "${yesterday.tir.toInt()}% in range")
                    LegendDot(color = TAR_COLOR, label = "${yesterday.tar.toInt()}% above")
                }

                Spacer(Modifier.height(20.dp))
                Text("Last 7 days", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                TirWeekBars(last7Days)

                Spacer(Modifier.height(16.dp))
                Text(
                    "Target: >70% time in range (Battelino 2019)",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )

                if (!isCgmData && yesterday.readingCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Based on ${yesterday.readingCount} manual readings — CGM recommended for accuracy",
                        fontSize = 11.sp,
                        color = Color(0xFFF9A825)
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
private fun TirWeekBars(days: List<DailyMetrics>) {
    val dateFormatter = DateTimeFormatter.ofPattern("M/d")
    val today = LocalDate.now()

    // Build exactly 7 slots: last 6 days + today, null if no data
    val slots = (6 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        val data = days.firstOrNull { it.dateEpochDay == date.toEpochDay() }
        Pair(date, data)
    }

    Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
        val count = 7
        val barAreaHeight = size.height - 36f
        val totalWidth = size.width
        val gap = 4f
        val barWidth = (totalWidth / count) - gap

        // Grid lines at 25%, 50%, 75%
        val gridLines = listOf(0.25f, 0.50f, 0.75f)
        gridLines.forEach { pct ->
            val y = barAreaHeight * (1f - pct)
            drawLine(
                color = Color(0xFFE0E0E0),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 0.5.dp.toPx()
            )
        }

        slots.forEachIndexed { i, (date, dm) ->
            val left = i * (barWidth + gap)

            if (dm != null && dm.readingCount >= 3) {
                // Draw real data
                val tbrH = barAreaHeight * (dm.tbr / 100f).coerceIn(0f, 1f)
                val tirH = barAreaHeight * (dm.tir / 100f).coerceIn(0f, 1f)
                val tarH = barAreaHeight * (dm.tar / 100f).coerceIn(0f, 1f)

                var top = 0f
                drawRect(TBR_COLOR, topLeft = Offset(left, top), size = Size(barWidth, tbrH))
                top += tbrH
                drawRect(TIR_COLOR, topLeft = Offset(left, top), size = Size(barWidth, tirH))
                top += tirH
                drawRect(TAR_COLOR, topLeft = Offset(left, top), size = Size(barWidth, tarH))

                // TIR % label above bar
                val tirPct = dm.tir.toInt()
                if (tirPct > 0) {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.DKGRAY
                            textSize = 8.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        canvas.nativeCanvas.drawText(
                            "$tirPct%",
                            left + barWidth / 2f,
                            -4f,
                            paint
                        )
                    }
                }
            } else {
                // Empty slot — draw gray outline only
                drawRect(
                    color = Color(0xFFE0E0E0),
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, barAreaHeight)
                )
            }

            // Date label below
            val label = date.format(dateFormatter)
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 9.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText(
                    label,
                    left + barWidth / 2f,
                    size.height - 2f,
                    paint
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(label, fontSize = 11.sp, color = Color.DarkGray)
    }
}