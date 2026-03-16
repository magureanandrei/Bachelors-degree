package com.example.diabetesapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.utils.CgmReading
import kotlin.collections.forEach

@Composable
fun TimeScaledBgGraph(
    logs: List<BolusLog>,
    cgmReadings: List<CgmReading> = emptyList(),
    dayStartTimestamp: Long,
    targetBg: Float = 100f,
    hypoLimit: Float = 70f,
    hyperLimit: Float = 180f,
    isCgmEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()

    val bolusPainter = rememberVectorPainter(Icons.Default.AutoFixHigh)
    val mealPainter = rememberVectorPainter(Icons.Default.Restaurant)
    val manualInsulinPainter = rememberVectorPainter(Icons.Default.Vaccines)

    // Always scroll to right edge (current time) since we're showing last 24h
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val minBg = 40f
    val maxBg = 350f
    val rangeBg = maxBg - minBg
    val topPadding = 40f
    val bottomPadding = 120f

    Row(modifier = modifier) {
        // --- FIXED Y-AXIS ---
        Canvas(modifier = Modifier.width(42.dp).fillMaxHeight()) {
            val graphHeight = size.height - bottomPadding - topPadding
            fun bgToY(bg: Float): Float = topPadding + graphHeight - ((bg - minBg) / rangeBg) * graphHeight

            val yLabels = listOf(hypoLimit, targetBg, hyperLimit, 300f)
            yLabels.forEach { bg ->
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${bg.toInt()}",
                    style = TextStyle(
                        color = if (bg == targetBg) Color(0xFF00897B) else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = if (bg == targetBg) FontWeight.Bold else FontWeight.Medium
                    ),
                    topLeft = Offset(0f, bgToY(bg) - 15f)
                )
            }

            val carbsY = size.height - 100f
            val insulinY = size.height - 60f
            drawText(textMeasurer = textMeasurer, text = "Carbs", style = TextStyle(color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold), topLeft = Offset(0f, carbsY - 15f))
            drawText(textMeasurer = textMeasurer, text = "Ins", style = TextStyle(color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold), topLeft = Offset(0f, insulinY - 15f))
        }

        // --- SCROLLABLE GRAPH AREA ---
        Box(modifier = Modifier.weight(1f).horizontalScroll(scrollState)) {
            Canvas(modifier = Modifier.width(1200.dp).fillMaxHeight()) {
                val graphWidth = size.width
                val graphHeight = size.height - bottomPadding - topPadding
                val twentyFourHoursMs = 24 * 60 * 60 * 1000f

                fun bgToY(bg: Float): Float = topPadding + graphHeight - ((bg - minBg) / rangeBg) * graphHeight
                fun timeToX(timestamp: Long): Float = ((timestamp - dayStartTimestamp).coerceIn(0, twentyFourHoursMs.toLong()) / twentyFourHoursMs) * graphWidth

                val carbsY = size.height - 100f
                val insulinY = size.height - 60f

                // 1. TARGET RANGE SHADING
                drawRect(
                    color = Color(0xFFE8F5E9).copy(alpha = 0.7f),
                    topLeft = Offset(0f, bgToY(hyperLimit)),
                    size = Size(graphWidth, bgToY(hypoLimit) - bgToY(hyperLimit))
                )

                // 2. SAFETY HORIZONTAL LINES
                listOf(hypoLimit, targetBg, hyperLimit).forEach { bg ->
                    val isTarget = bg == targetBg
                    val color = when (bg) {
                        hypoLimit -> Color(0xFFE53935).copy(alpha = 0.4f)
                        hyperLimit -> Color(0xFFFFB74D).copy(alpha = 0.4f)
                        else -> Color(0xFFA5D6A7)
                    }
                    drawLine(
                        color = color,
                        start = Offset(0f, bgToY(bg)),
                        end = Offset(graphWidth, bgToY(bg)),
                        strokeWidth = if (isTarget) 3f else 2f,
                        pathEffect = if (!isTarget) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                    )
                }

                // 3. SWIMLANE DIVIDERS
                drawLine(Color(0xFFEEEEEE), Offset(0f, size.height - 120f), Offset(graphWidth, size.height - 120f), 2f)
                drawLine(Color(0xFFF5F5F5), Offset(0f, size.height - 80f), Offset(graphWidth, size.height - 80f), 1f)
                drawLine(Color(0xFFEEEEEE), Offset(0f, size.height - 40f), Offset(graphWidth, size.height - 40f), 2f)

                // 3.5 CONTINUOUS CGM LINE
                if (cgmReadings.isNotEmpty()) {
                    val cgmPath = Path()
                    var isFirstPoint = true
                    var previousTimestamp = 0L
                    val maxGapMs = 16 * 60 * 1000L

                    val validReadings = cgmReadings.filter { it.bgValue > 0 }

                    validReadings.forEach { reading ->
                        val x = timeToX(reading.timestamp)
                        val y = bgToY(reading.bgValue.toFloat().coerceIn(minBg, maxBg))
                        val isGap = previousTimestamp > 0L && (reading.timestamp - previousTimestamp > maxGapMs)

                        if (reading.timestamp >= dayStartTimestamp && reading.timestamp <= dayStartTimestamp + twentyFourHoursMs.toLong()) {
                            if (isFirstPoint || isGap) {
                                cgmPath.moveTo(x, y)
                                isFirstPoint = false
                            } else {
                                cgmPath.lineTo(x, y)
                            }
                        }
                        previousTimestamp = reading.timestamp
                    }

                    val hyperY = bgToY(hyperLimit)
                    val hypoY = bgToY(hypoLimit)
                    val hyperFraction = (hyperY / size.height).coerceIn(0f, 1f)
                    val hypoFraction = (hypoY / size.height).coerceIn(0f, 1f)

                    val cgmBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.0f to Color(0xFFFFB74D).copy(alpha = 0.9f),
                        hyperFraction to Color(0xFFFFB74D).copy(alpha = 0.9f),
                        hyperFraction + 0.001f to Color(0xFF00897B).copy(alpha = 0.8f),
                        hypoFraction to Color(0xFF00897B).copy(alpha = 0.8f),
                        hypoFraction + 0.001f to Color(0xFFE53935).copy(alpha = 0.9f),
                        1.0f to Color(0xFFE53935).copy(alpha = 0.9f),
                        startY = 0f,
                        endY = size.height
                    )

                    drawPath(
                        path = cgmPath,
                        brush = cgmBrush,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // 4. SPORT DURATIONS
                logs.filter { it.isSportModeActive && it.sportDuration != null }.forEach { sportLog ->
                    val startX = timeToX(sportLog.timestamp)
                    val endX = timeToX(sportLog.timestamp + (sportLog.sportDuration!!.toLong() * 60 * 1000L))
                    val bandWidth = (endX - startX).coerceAtLeast(4f)
                    val isPlanned = sportLog.status == "PLANNED"
                    val sportColor = Color(0xFF26A69A)
                    val fillColor = if (isPlanned) Color(0xFFFF9800).copy(alpha = 0.15f) else sportColor.copy(alpha = 0.13f)
                    val borderColor = if (isPlanned) Color(0xFFFF9800) else sportColor
                    val pathEffect = if (isPlanned) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null

                    drawRect(color = fillColor, topLeft = Offset(startX, topPadding), size = Size(bandWidth, graphHeight))
                    drawLine(borderColor, Offset(startX, topPadding), Offset(startX, topPadding + graphHeight), 3f, pathEffect = pathEffect)
                    drawLine(borderColor, Offset(startX + bandWidth, topPadding), Offset(startX + bandWidth, topPadding + graphHeight), 3f, pathEffect = pathEffect)

                    val label = "${sportLog.sportType ?: "Workout"} · ${sportLog.sportDuration.toInt()}m"
                    val labelResult = textMeasurer.measure(
                        label,
                        style = TextStyle(color = borderColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    )
                    if (bandWidth > labelResult.size.height + 4f) {
                        val labelX = startX + 4f
                        val labelY = topPadding + graphHeight / 2f - labelResult.size.width / 2f
                        translate(left = labelX, top = labelY) {
                            rotate(90f) { drawText(labelResult) }
                        }
                    }
                }

                // 5. X-AXIS TIME LABELS — computed from actual timestamps for rolling 24h
                for (i in 0..8) {
                    val tickTimestamp = dayStartTimestamp + (i * 3 * 60 * 60 * 1000L)
                    val xPos = timeToX(tickTimestamp)
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = tickTimestamp }
                    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                    val label = when {
                        hour == 0 -> "12 AM"
                        hour < 12 -> "$hour AM"
                        hour == 12 -> "12 PM"
                        else -> "${hour - 12} PM"
                    }
                    drawLine(Color(0xFFF5F5F5), Offset(xPos, topPadding), Offset(xPos, size.height - 40f), 1f)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        style = TextStyle(color = Color.Gray, fontSize = 10.sp),
                        topLeft = Offset(xPos - 20f, size.height - 30f)
                    )
                }

                // 6. MANUAL BG DOTS (no line — fingerstick readings are discrete points)
                if (!isCgmEnabled) {
                    val bgLogs = logs.filter {
                        it.bloodGlucose > 0
                                && it.notes != "Auto-entry via CareLink"
                                && it.notes?.startsWith("Auto-imported") != true
                                && it.notes?.startsWith("Auto-detected") != true
                    }.sortedBy { it.timestamp }

                    bgLogs.forEach { log ->
                        val x = timeToX(log.timestamp)
                        val y = bgToY(log.bloodGlucose.toFloat().coerceIn(minBg, maxBg))
                        val bg = log.bloodGlucose.toFloat()
                        val dotColor = when {
                            bg > hyperLimit -> Color(0xFFFFB74D)
                            bg < hypoLimit -> Color(0xFFE53935)
                            else -> Color(0xFF00897B)
                        }
                        // Larger dots, no connecting line
                        drawCircle(color = Color.White, radius = 10f, center = Offset(x, y))
                        drawCircle(color = dotColor, radius = 8f, center = Offset(x, y))
                        // BG value label above dot
                        drawText(
                            textMeasurer = textMeasurer,
                            text = log.bloodGlucose.toInt().toString(),
                            style = TextStyle(
                                color = dotColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            topLeft = Offset(x - 10f, y - 24f)
                        )
                    }
                }

                // 7. SWIMLANE ICONS
                val dayEndTimestamp = dayStartTimestamp + twentyFourHoursMs.toLong()
                logs.filter { it.timestamp in dayStartTimestamp..dayEndTimestamp }.forEach { log ->
                    val x = timeToX(log.timestamp)
                    val iconRadius = 16f

                    fun drawSwimlaneIcon(yPos: Float, painter: androidx.compose.ui.graphics.vector.VectorPainter, tint: Color) {
                        drawCircle(color = Color.White, radius = iconRadius, center = Offset(x, yPos))
                        drawCircle(color = tint.copy(alpha = 0.2f), radius = iconRadius, center = Offset(x, yPos), style = Stroke(width = 2f))
                        translate(left = x - 11f, top = yPos - 11f) {
                            with(painter) { draw(size = Size(22f, 22f), colorFilter = ColorFilter.tint(tint)) }
                        }
                    }

                    if (log.carbs > 0) drawSwimlaneIcon(carbsY, mealPainter, Color(0xFFE91E63))
                    if (log.administeredDose > 0 || log.eventType == "SMART_BOLUS") {
                        val tint = if (log.eventType == "SMART_BOLUS") Color(0xFFFF9800) else Color(0xFF1976D2)
                        val painter = if (log.eventType == "SMART_BOLUS") bolusPainter else manualInsulinPainter
                        drawSwimlaneIcon(insulinY, painter, tint)
                    }
                }
            }
        }
    }
}