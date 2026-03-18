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
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.utils.CgmReading

@Composable
fun TimeScaledBgGraph(
    logs: List<BolusLog>,
    cgmReadings: List<CgmReading> = emptyList(),
    dayStartTimestamp: Long,
    endTimestamp: Long,
    targetBg: Float = 100f,
    hypoLimit: Float = 70f,
    hyperLimit: Float = 180f,
    isCgmEnabled: Boolean,
    settings: BolusSettings,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()

    val bolusPainter = rememberVectorPainter(Icons.Default.AutoFixHigh)
    val mealPainter = rememberVectorPainter(Icons.Default.Restaurant)
    val manualInsulinPainter = rememberVectorPainter(Icons.Default.Vaccines)

    LaunchedEffect(scrollState.maxValue) {
        val totalWindowMs = (endTimestamp - dayStartTimestamp).toFloat()
        val thirtyMinMs = 60 * 60 * 1000f
        val futureBufferFraction = thirtyMinMs / totalWindowMs
        val targetScroll = (scrollState.maxValue * (1f - futureBufferFraction)).toInt()
            .coerceIn(0, scrollState.maxValue)
        scrollState.scrollTo(targetScroll)
    }

    val minBg = 40f
    val maxBg = 350f
    val rangeBg = maxBg - minBg
    val topPadding = 40f
    val bottomPadding = 120f

    Row(modifier = modifier) {
        // --- FIXED Y-AXIS ---
        Canvas(modifier = Modifier.width(42.dp).fillMaxHeight()) {
            if (size.width <= 0 || size.height <= 0) return@Canvas
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
                if (size.width <= 0 || size.height <= 0) return@Canvas

                val graphWidth = size.width
                val graphHeight = size.height - bottomPadding - topPadding
                val windowMs = (endTimestamp - dayStartTimestamp).toFloat()

                fun bgToY(bg: Float): Float = topPadding + graphHeight - ((bg - minBg) / rangeBg) * graphHeight
                fun timeToX(timestamp: Long): Float = ((timestamp - dayStartTimestamp).coerceIn(0, windowMs.toLong()) / windowMs) * graphWidth
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
                    val lowBridgePath = Path()
                    var isFirstPoint = true
                    var lastValidX = 0f
                    var lastValidY = 0f
                    var hadLowGap = false
                    var previousTimestamp = 0L
                    val maxGapMs = 30 * 60 * 1000L  // 30 min = true sensor dropout, no bridge
                    val windowEnd = dayStartTimestamp + windowMs.toLong()

                    cgmReadings.sortedBy { it.timestamp }.forEach { reading ->
                        val inWindow = reading.timestamp >= dayStartTimestamp && reading.timestamp <= windowEnd
                        val timeSinceLast = if (previousTimestamp > 0L) reading.timestamp - previousTimestamp else 0L
                        val isTrueGap = previousTimestamp > 0L && timeSinceLast > maxGapMs

                        if (reading.bgValue <= 0) {
                            // xDrip sent a LOW reading — flag it if it's not a true gap
                            if (inWindow && !isTrueGap) hadLowGap = true
                            previousTimestamp = reading.timestamp
                            return@forEach
                        }

                        val x = timeToX(reading.timestamp)
                        val y = bgToY(reading.bgValue.toFloat().coerceIn(minBg, maxBg))

                        if (inWindow) {
                            when {
                                isFirstPoint -> {
                                    cgmPath.moveTo(x, y)
                                    isFirstPoint = false
                                }
                                isTrueGap -> {
                                    // >30 min gap — just break the line, no bridge
                                    cgmPath.moveTo(x, y)
                                    hadLowGap = false
                                }
                                hadLowGap -> {
                                    // Came back up from LOW — draw red bridge
                                    lowBridgePath.moveTo(lastValidX, lastValidY)
                                    lowBridgePath.lineTo(x, y)
                                    cgmPath.moveTo(x, y)
                                    hadLowGap = false
                                }
                                else -> {
                                    cgmPath.lineTo(x, y)
                                }
                            }
                            lastValidX = x
                            lastValidY = y
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

                    // Solid red bridge for LOW periods (under 50)
                    drawPath(
                        path = lowBridgePath,
                        color = Color(0xFFE53935).copy(alpha = 0.85f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // 4. SPORT DURATIONS
                logs.filter { it.isSportModeActive && it.sportDuration != null }.forEach { sportLog ->
                    val startX = timeToX(sportLog.timestamp)
                    val endX = timeToX(sportLog.timestamp + (sportLog.sportDuration!!.toLong() * 60 * 1000L))
                    val bandWidth = (endX - startX).coerceAtLeast(4f)
                    val isPlanned  = sportLog.status == "PLANNED"
                    val isStepWalk = sportLog.notes.startsWith("Auto-detected")

                    val sportColor = when {
                        isPlanned  -> Color(0xFFFF9800)
                        isStepWalk -> Color(0xFF78909C)  // muted blue-grey
                        else       -> Color(0xFF26A69A)  // teal — Strava, manual, all the same
                    }

                    val fillAlpha   = if (isStepWalk) 0.08f else 0.13f
                    val borderAlpha = if (isStepWalk) 0.55f else 1.0f
                    val strokeWidth = if (isStepWalk) 2f    else 3f
                    val pathEffect  = if (isPlanned) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null

                    drawRect(
                        color = sportColor.copy(alpha = fillAlpha),
                        topLeft = Offset(startX, topPadding),
                        size = Size(bandWidth, graphHeight)
                    )
                    drawLine(sportColor.copy(alpha = borderAlpha), Offset(startX, topPadding), Offset(startX, topPadding + graphHeight), strokeWidth, pathEffect = pathEffect)
                    drawLine(sportColor.copy(alpha = borderAlpha), Offset(startX + bandWidth, topPadding), Offset(startX + bandWidth, topPadding + graphHeight), strokeWidth, pathEffect = pathEffect)

                    val label = "${sportLog.sportType ?: "Workout"} · ${sportLog.sportDuration.toInt()}m"
                    val labelResult = textMeasurer.measure(
                        label,
                        style = TextStyle(
                            color = sportColor.copy(alpha = if (isStepWalk) 0.65f else 1.0f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (bandWidth > labelResult.size.height + 4f) {
                        translate(left = startX + 4f, top = topPadding + graphHeight / 2f - labelResult.size.width / 2f) {
                            rotate(90f) { drawText(labelResult) }
                        }
                    }
                }

                // 5. X-AXIS TIME LABELS
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

                // 6. MANUAL BG DOTS
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
                        drawCircle(color = Color.White, radius = 10f, center = Offset(x, y))
                        drawCircle(color = dotColor, radius = 8f, center = Offset(x, y))
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
                val dayEndTimestamp = dayStartTimestamp + windowMs.toLong()
                logs.filter { it.timestamp in dayStartTimestamp..dayEndTimestamp }.forEach { log ->
                    val x = timeToX(log.timestamp)
                    val iconRadius = 16f

                    fun drawSwimlaneIcon(
                        yPos: Float,
                        painter: androidx.compose.ui.graphics.vector.VectorPainter,
                        tint: Color
                    ) {
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