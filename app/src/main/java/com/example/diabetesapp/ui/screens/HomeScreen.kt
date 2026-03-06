package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.R
import com.example.diabetesapp.data.database.BolusDatabase
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.viewmodel.DashboardViewModel
import com.example.diabetesapp.viewmodel.DashboardViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.style.TextOverflow
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.ui.components.DoseBreakdownCard

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToCalculateBolus: () -> Unit = {},
    onNavigateToLogReading: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val logRepository = remember { BolusLogRepository(database.bolusLogDao()) }
    // Ensure this repository is the one that reads/writes your High/Low/Target
    val settingsRepository = remember { BolusSettingsRepository(context) }

    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(logRepository, settingsRepository)
    )

    val allLogs by viewModel.allLogs.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // Re-calculate day start when logs change
    val logicalDayStart = remember(allLogs) { getLogicalDayStartTimestamp() }
    val todaysLogs = allLogs.filter { it.timestamp >= logicalDayStart }.sortedBy { it.timestamp }

    val unverifiedWorkout by viewModel.unverifiedWorkout.collectAsState()


    var selectedLogForModal by remember { mutableStateOf<BolusLog?>(null) }

    LaunchedEffect(allLogs) {
        viewModel.checkForPendingWorkouts(allLogs)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ... Header, Disclaimer, and Action Buttons stay the same ...
        HeaderSection()
        DisclaimerBanner()
        DashboardActionButtons(onSmartBolusClick = onNavigateToCalculateBolus, onManualLogClick = onNavigateToLogReading)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Today's Glucose Trends", fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                Spacer(modifier = Modifier.height(12.dp))

                // The 'key' ensures that if Target/High/Low change, the Canvas RE-DRAWS immediately
                key(settings.targetBG, settings.hypoLimit, settings.hyperLimit) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        TimeScaledBgGraph(
                            logs = todaysLogs,
                            dayStartTimestamp = logicalDayStart,
                            targetBg = settings.targetBG,
                            hypoLimit = settings.hypoLimit,
                            hyperLimit = settings.hyperLimit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        Text("Today's Logs", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)

        if (todaysLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Your log is empty for today.", color = Color.Gray)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                todaysLogs.reversed().forEach { log ->
                    CompactLogEntryCard(log = log) {
                        selectedLogForModal = log
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    // --- MODALS ---
    selectedLogForModal?.let { log ->
        LogDetailsDialog(log = log, onDismiss = { selectedLogForModal = null })
    }

    unverifiedWorkout?.let { workout ->
        PostWorkoutVerificationDialog(
            log = workout,
            onDismiss = { viewModel.dismissVerification() },
            onConfirm = { duration, intensity, type, starttime ->
                viewModel.verifyAndCompleteWorkout(workout, duration, intensity, type, starttime)
            }
        )
    }
}
// Logical Day Start (3 AM Cutoff)
fun getLogicalDayStartTimestamp(): Long {
    // Uses the phone's LOCAL timezone settings
    val calendar = Calendar.getInstance()

    // If current time is between Midnight and 3 AM,
    // we want the graph to start at 3 AM of the PREVIOUS day.
    if (calendar.get(Calendar.HOUR_OF_DAY) < 3) {
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }

    calendar.set(Calendar.HOUR_OF_DAY, 3)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    return calendar.timeInMillis
}

@Composable
fun TimeScaledBgGraph(
    logs: List<BolusLog>,
    dayStartTimestamp: Long,
    targetBg: Float = 100f,
    hypoLimit: Float = 70f,
    hyperLimit: Float = 180f,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    val bolusPainter = rememberVectorPainter(Icons.Default.AutoFixHigh)
    val mealPainter = rememberVectorPainter(Icons.Default.Restaurant)
    val manualInsulinPainter = rememberVectorPainter(Icons.Default.Vaccines)

    // Auto-scroll to current time on load
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val twentyFourHoursMs = 24 * 60 * 60 * 1000f
            val currentTime = System.currentTimeMillis()
            val elapsedMs = (currentTime - dayStartTimestamp).coerceIn(0L, twentyFourHoursMs.toLong())
            val fraction = elapsedMs / twentyFourHoursMs
            val totalWidthPx = with(density) { 1200.dp.toPx() }
            val currentXPx = fraction * totalWidthPx
            val viewportWidthPx = totalWidthPx - scrollState.maxValue
            val targetScroll = (currentXPx - (viewportWidthPx / 2f)).toInt().coerceIn(0, scrollState.maxValue)
            scrollState.scrollTo(targetScroll)
        }
    }

    // Y-Axis Scale
    val minBg = 40f
    val maxBg = 350f
    val rangeBg = maxBg - minBg
    val topPadding = 40f
    val bottomPadding = 120f

    Row(modifier = modifier) {
        // --- FIXED Y-AXIS (Pinned to the left) ---
        Canvas(modifier = Modifier.width(42.dp).fillMaxHeight()) {
            val graphHeight = size.height - bottomPadding - topPadding
            fun bgToY(bg: Float): Float = topPadding + graphHeight - ((bg - minBg) / rangeBg) * graphHeight

            // BG Labels based on Safety Limits
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

            // Swimlane Labels
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

                // 1. DYNAMIC TARGET RANGE SHADING
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

                // 4. SPORT DURATIONS
                logs.filter { it.isSportModeActive && it.sportDuration != null }.forEach { sportLog ->
                    val startX = timeToX(sportLog.timestamp)
                    val endX = timeToX(sportLog.timestamp + (sportLog.sportDuration!!.toLong() * 60 * 1000L))
                    val isPlanned = sportLog.status == "PLANNED"
                    val shadeColor = if (isPlanned) Color(0xFFFF9800).copy(alpha = 0.15f) else Color(0xFF00695C).copy(alpha = 0.15f)

                    drawRect(color = shadeColor, topLeft = Offset(startX, topPadding), size = Size(endX - startX, graphHeight))

                    val borderColor = if (isPlanned) Color(0xFFFF9800) else Color(0xFF00695C)
                    val pathEffect = if (isPlanned) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null

                    drawLine(borderColor, Offset(startX, topPadding), Offset(startX, topPadding + graphHeight), 3f, pathEffect = pathEffect)
                    drawLine(borderColor, Offset(endX, topPadding), Offset(endX, topPadding + graphHeight), 3f, pathEffect = pathEffect)
                }

                // 5. X-AXIS TIME LABELS
                val hourStrings = listOf("3 AM", "6 AM", "9 AM", "12 PM", "3 PM", "6 PM", "9 PM", "12 AM", "3 AM")
                for (i in 0..8) {
                    // Multiplies i by 3 hours to match the labels above
                    val xPos = timeToX(dayStartTimestamp + (i * 3 * 60 * 60 * 1000L))

                    drawLine(Color(0xFFF5F5F5), Offset(xPos, topPadding), Offset(xPos, size.height - 40f), 1f)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = hourStrings[i],
                        style = TextStyle(color = Color.Gray, fontSize = 10.sp),
                        topLeft = Offset(xPos - 20f, size.height - 30f)
                    )
                }

                // 6. TREND LINE & DOTS
                val bgLogs = logs.filter { it.bloodGlucose > 0 }.sortedBy { it.timestamp }
                if (bgLogs.isNotEmpty()) {
                    val path = Path()
                    val points = mutableListOf<Offset>()

                    bgLogs.forEachIndexed { index, log ->
                        val x = timeToX(log.timestamp)
                        val y = bgToY(log.bloodGlucose.toFloat().coerceIn(minBg, maxBg))
                        points.add(Offset(x, y))
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(path = path, color = Color(0xFF00897B), style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    points.forEachIndexed { index, point ->
                        val bg = bgLogs[index].bloodGlucose.toFloat()
                        val dotColor = when {
                            bg > hyperLimit -> Color(0xFFFFB74D) // High
                            bg < hypoLimit -> Color(0xFFE53935)  // Low
                            else -> Color(0xFF00897B)            // Target
                        }
                        drawCircle(color = Color.White, radius = 8f, center = point)
                        drawCircle(color = dotColor, radius = 6f, center = point)
                    }
                }

                // 7. SWIMLANE ICONS
                logs.forEach { log ->
                    val x = timeToX(log.timestamp)
                    val iconRadius = 12f

                    fun drawSwimlaneIcon(yPos: Float, painter: androidx.compose.ui.graphics.vector.VectorPainter, tint: Color) {
                        drawCircle(color = Color.White, radius = iconRadius, center = Offset(x, yPos))
                        drawCircle(color = tint.copy(alpha = 0.2f), radius = iconRadius, center = Offset(x, yPos), style = Stroke(width = 2f))
                        translate(left = x - 8f, top = yPos - 8f) {
                            with(painter) { draw(size = Size(16f, 16f), colorFilter = ColorFilter.tint(tint)) }
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
// --- NEW: Post Workout Dialog ---

@Composable
fun PostWorkoutVerificationDialog(
    log: BolusLog,
    onDismiss: () -> Unit,
    onConfirm: (Float, Float, String, String) -> Unit // Added the 4th parameter (String) for start time
) {
    var isEditing by remember { mutableStateOf(false) }

    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Editable States
    var duration by remember { mutableStateOf(log.sportDuration ?: 45f) }
    var intensity by remember { mutableStateOf(when(log.sportIntensity) { "Low" -> 1f; "High" -> 3f; else -> 2f }) }
    var sportType by remember { mutableStateOf(log.sportType ?: "Aerobic") }
    var startTimeStr by remember { mutableStateOf(formatter.format(Date(log.timestamp))) } // NEW state

    // --- NEW: Time Picker Setup ---
    val context = LocalContext.current
    val timeParts = startTimeStr.split(":")
    val initialHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 12
    val initialMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, selectedHour, selectedMinute ->
            startTimeStr = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
        },
        initialHour,
        initialMinute,
        true // 24-hour clock
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color(0xFF00695C))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Workout Complete?", fontWeight = FontWeight.Bold, color = Color(0xFF00695C), fontSize = 20.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Show original plan in the helper text
                val originalTime = formatter.format(Date(log.timestamp))
                Text("You planned a ${log.sportDuration?.toInt()} min ${log.sportType} workout at $originalTime.", color = Color.DarkGray)

                if (!isEditing) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)), modifier = Modifier.fillMaxWidth()) {
                        Text("Did everything go as planned?", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium, color = Color(0xFF004D40))
                    }
                } else {
                    // EDIT MODE
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        Text("Update Details:", fontWeight = FontWeight.Bold)

                        // --- NEW: Start Time Editor ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Actual Start Time:", fontSize = 14.sp)
                            Box(modifier = Modifier.clickable { timePickerDialog.show() }) {
                                OutlinedTextField(
                                    value = startTimeStr,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = false,
                                    modifier = Modifier.width(90.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = Color.Black,
                                        disabledBorderColor = Color(0xFF00695C)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true,
                                    textStyle = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Aerobic", "Mixed", "Anaerobic").forEach { type ->
                                OutlinedButton(
                                    onClick = { sportType = type },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (sportType == type) Color(0xFF00695C) else Color.Transparent,
                                        contentColor = if (sportType == type) Color.White else Color(0xFF00695C)
                                    )
                                ) { Text(type, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }

                        Text("Actual Duration: ${duration.toInt()} min", fontSize = 14.sp)
                        Slider(value = duration, onValueChange = { duration = it }, valueRange = 5f..120f, colors = SliderDefaults.colors(thumbColor = Color(0xFF00695C), activeTrackColor = Color(0xFF00695C)))

                        val intString = when(intensity.toInt()) { 1 -> "Low"; 3 -> "High"; else -> "Medium" }
                        Text("Actual Intensity: $intString", fontSize = 14.sp)
                        Slider(value = intensity, onValueChange = { intensity = it }, valueRange = 1f..3f, steps = 1, colors = SliderDefaults.colors(thumbColor = Color(0xFF00695C), activeTrackColor = Color(0xFF00695C)))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(duration, intensity, sportType, startTimeStr) }, // Pass startTimeStr here
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) { Text(if (isEditing) "Save Updates" else "Yes, Completed", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            if (!isEditing) {
                TextButton(onClick = { isEditing = true }) { Text("No, I need to edit", color = Color.Gray) }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
            }
        },
        containerColor = Color.White, shape = RoundedCornerShape(16.dp)
    )
}
// --- COMPACT COMPONENTS ---

@Composable
fun CompactLogEntryCard(log: BolusLog, onClick: () -> Unit) {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = formatter.format(Date(log.timestamp))

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
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
            }

            // --- THE DYNAMIC UI SWITCH ---
            if (log.eventType == "SPORT") {
                // SPORT-ONLY DESIGN
                // Middle: Text aligned with BG columns of other cards
                Row(modifier = Modifier.weight(1.5f)) {
                    Text("${log.sportDuration?.toInt()}m ${log.sportType}", fontSize = 13.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium, maxLines = 1)
                }

                // Right: Badge pushed to the end
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.weight(1f)) {
                    if (log.status == "PLANNED") {
                        Text("Pending", fontSize = 11.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFFFFF3E0), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    } else {
                        Text("Done", fontSize = 11.sp, color = Color(0xFF00695C), fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFFE0F2F1), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            } else {
                // STANDARD DIABETES DESIGN
                // Middle: BG & Carbs
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1.5f)) {
                    if (log.bloodGlucose > 0) {
                        val color = if (log.bloodGlucose > 180 || log.bloodGlucose < 70) Color.Red else Color(0xFF00897B)
                        Text("${log.bloodGlucose}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
                    } else { Text("-", color = Color.LightGray) }

                    if (log.carbs > 0) { Text("${log.carbs}g", fontSize = 14.sp, color = Color.Gray) }
                }

                // Right: Insulin
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.weight(1f)) {
                    if (log.administeredDose > 0) {
                        Text("${String.format(Locale.US, "%.1f", log.administeredDose)}U", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    } else { Text("-", color = Color.LightGray) }
                }
            }
        }
    }
}

@Composable
fun LogDetailsDialog(log: BolusLog, onDismiss: () -> Unit) {
    val formatter = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entry Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatter.format(Date(log.timestamp)), fontSize = 12.sp, color = Color.Gray)

                HorizontalDivider()

                if (log.eventType == "SPORT") {
                    // --- SPORT ONLY MODAL DETAILS ---
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sport Type:", color = Color.Gray)
                        Text("${log.sportType ?: "Unknown"}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Duration:", color = Color.Gray)
                        Text("${log.sportDuration?.toInt() ?: 0} mins", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Intensity:", color = Color.Gray)
                        Text("${log.sportIntensity ?: "Medium"}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status:", color = Color.Gray)
                        val statusColor = if (log.status == "PLANNED") Color(0xFFFF9800) else Color(0xFF00695C)
                        val statusText = if (log.status == "PLANNED") "Pending Verification" else "Completed"
                        Text(statusText, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                } else {
                    // --- STANDARD DIABETES MODAL DETAILS ---
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Blood Glucose:", color = Color.Gray)
                        Text(if (log.bloodGlucose > 0) "${log.bloodGlucose} mg/dL" else "None", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Carbohydrates:", color = Color.Gray)
                        Text(if (log.carbs > 0) "${log.carbs} g" else "None", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Insulin Given:", color = Color.Gray)
                        Text(if (log.administeredDose > 0) "${String.format(Locale.US, "%.1f", log.administeredDose)} U" else "None", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }

                if (log.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notes:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(log.notes, fontSize = 14.sp, color = Color.DarkGray)
                }

// Replaced with shared DoseBreakdownCard
                if (!log.clinicalSuggestion.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DoseBreakdownCard(
                        standardDose = log.standardDose ?: 0.0,
                        suggestedDose = log.suggestedDose ?: 0.0,
                        rationale = log.clinicalSuggestion
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF00897B), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun DashboardActionButtons(onSmartBolusClick: () -> Unit, onManualLogClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onSmartBolusClick,
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("Smart Bolus", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = onManualLogClick,
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00897B))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("Manual Log", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "App Icon",
                tint = Color.Unspecified,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Diabetes Manager", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Make sports and diabetes easier!", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun DisclaimerBanner() {
    Surface(
        color = Color(0xFFFFF4E6),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Educational Prototype: This app is for research purposes only.",
                fontSize = 11.sp, color = Color(0xFFE65100), lineHeight = 14.sp
            )
        }
    }
}