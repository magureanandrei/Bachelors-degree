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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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

private val TBR_COLOR = Color(0xFFE53935)
private val TIR_COLOR = Color(0xFF2E7D32)
private val TAR_COLOR = Color(0xFFF9A825)

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
                TirStackedBar(tbr = yesterday.tbr, tir = yesterday.tir, tar = yesterday.tar)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${yesterday.tbr.toInt()}% below",
                        fontSize = 11.sp,
                        color = TBR_COLOR,
                        fontWeight = FontWeight.Medium
                    )
                    Text("·", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        "${yesterday.tir.toInt()}% in range",
                        fontSize = 11.sp,
                        color = TIR_COLOR,
                        fontWeight = FontWeight.Medium
                    )
                    Text("·", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        "${yesterday.tar.toInt()}% above",
                        fontSize = 11.sp,
                        color = TAR_COLOR,
                        fontWeight = FontWeight.Medium
                    )
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
private fun TirStackedBar(tbr: Float, tir: Float, tar: Float) {
    Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        val w = size.width
        val h = size.height
        val tbrW = w * (tbr / 100f).coerceIn(0f, 1f)
        val tirW = w * (tir / 100f).coerceIn(0f, 1f)
        val tarW = w - tbrW - tirW

        drawRect(TBR_COLOR, topLeft = Offset(0f, 0f), size = Size(tbrW, h))
        drawRect(TIR_COLOR, topLeft = Offset(tbrW, 0f), size = Size(tirW, h))
        drawRect(TAR_COLOR, topLeft = Offset(tbrW + tirW, 0f), size = Size(tarW, h))

        val textSizePx = 11.sp.toPx()
        val barCenterY = h / 2f + textSizePx / 3f

        data class Seg(val pct: Float, val startX: Float, val segW: Float)
        val segments = listOf(
            Seg(tbr, 0f, tbrW),
            Seg(tir, tbrW, tirW),
            Seg(tar, tbrW + tirW, tarW)
        )

        drawIntoCanvas { composeCanvas ->
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = textSizePx
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            segments.forEach { (pct, startX, segW) ->
                if (pct >= 15f) {
                    composeCanvas.nativeCanvas.drawText(
                        "${pct.toInt()}%",
                        startX + segW / 2f,
                        barCenterY,
                        paint
                    )
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

    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val count = 7
        val barAreaHeight = size.height - 36f
        val totalWidth = size.width
        val gap = 4f
        val barWidth = (totalWidth / count) - gap

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
                    textSize = 8.sp.toPx()
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