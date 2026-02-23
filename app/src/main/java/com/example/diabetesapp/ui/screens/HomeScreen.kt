package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToCalculateBolus: () -> Unit = {},
    onNavigateToLogReading: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val repository = remember { BolusLogRepository(database.bolusLogDao()) }
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModelFactory(repository))

    val allLogs by viewModel.allLogs.collectAsState()
    val logicalDayStart = remember { getLogicalDayStartTimestamp() }
    val todaysLogs = allLogs.filter { it.timestamp >= logicalDayStart }.sortedBy { it.timestamp }

    var selectedLogForModal by remember { mutableStateOf<BolusLog?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp) // Slightly tightened spacing
    ) {
        HeaderSection()

        // --- NEW PLACEMENT: Disclaimer always visible at the top ---
        DisclaimerBanner()

        DashboardActionButtons(
            onSmartBolusClick = onNavigateToCalculateBolus,
            onManualLogClick = onNavigateToLogReading
        )

        // 1. The Scrollable Dashboard Graph Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Today's Glucose Trends", fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                Spacer(modifier = Modifier.height(12.dp))

                if (todaysLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                        Text("No data logged today.", color = Color.Gray)
                    }
                } else {
                    TimeScaledBgGraph(
                        logs = todaysLogs,
                        dayStartTimestamp = logicalDayStart,
                        modifier = Modifier.fillMaxWidth().height(250.dp)
                    )
                }
            }
        }

        // 2. Today's Entries Section (Compact)
        Text("Today's Logs", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)

        if (todaysLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Your log is empty for today.", color = Color.Gray)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                todaysLogs.reversed().forEach { log ->
                    CompactLogEntryCard(log = log) {
                        selectedLogForModal = log // Open modal on click
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Log Details Modal
    selectedLogForModal?.let { log ->
        LogDetailsDialog(log = log, onDismiss = { selectedLogForModal = null })
    }
}
// Logical Day Start (3 AM Cutoff)
fun getLogicalDayStartTimestamp(): Long {
    val calendar = Calendar.getInstance()
    if (calendar.get(Calendar.HOUR_OF_DAY) < 3) calendar.add(Calendar.DAY_OF_YEAR, -1)
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
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Icon Painters for Canvas
    val sportPainter = rememberVectorPainter(Icons.Default.DirectionsRun)
    val bolusPainter = rememberVectorPainter(Icons.Default.AutoFixHigh)
    val mealPainter = rememberVectorPainter(Icons.Default.Restaurant)
    val manualInsulinPainter = rememberVectorPainter(Icons.Default.Vaccines)
    val checkPainter = rememberVectorPainter(Icons.Default.Bloodtype)

    // Auto-scroll so the "Current Time" is centered on the screen when the graph loads
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val twentyFourHoursMs = 24 * 60 * 60 * 1000f
            val currentTime = System.currentTimeMillis()
            val elapsedMs = (currentTime - dayStartTimestamp).coerceIn(0L, twentyFourHoursMs.toLong())

            // What percentage of the day has passed?
            val fraction = elapsedMs / twentyFourHoursMs

            // Calculate pixel positions
            val totalWidthPx = with(density) { 1200.dp.toPx() }
            val currentXPx = fraction * totalWidthPx
            val viewportWidthPx = totalWidthPx - scrollState.maxValue

            // Scroll to the current time, subtracting half the screen width so it sits in the middle
            val targetScroll = (currentXPx - (viewportWidthPx / 2f)).toInt().coerceIn(0, scrollState.maxValue)

            scrollState.scrollTo(targetScroll)
        }
    }

    val minBg = 40f
    val maxBg = 350f
    val rangeBg = maxBg - minBg
    val topPadding = 50f
    val bottomPadding = 60f

    Row(modifier = modifier) {
        // --- FIXED Y-AXIS (Pinned to the left) ---
        Canvas(modifier = Modifier.width(36.dp).fillMaxHeight()) {
            val graphHeight = size.height - bottomPadding - topPadding

            fun bgToY(bg: Float): Float {
                return topPadding + graphHeight - ((bg - minBg) / rangeBg) * graphHeight
            }

            val yLabels = listOf(70f, 180f, 300f)
            yLabels.forEach { bg ->
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${bg.toInt()}",
                    style = TextStyle(color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    topLeft = Offset(0f, bgToY(bg) - 20f)
                )
            }
        }

        // --- SCROLLABLE GRAPH AREA ---
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState)
        ) {
            Canvas(modifier = Modifier.width(1200.dp).fillMaxHeight()) {
                val graphWidth = size.width
                val graphHeight = size.height - bottomPadding - topPadding
                val twentyFourHoursMs = 24 * 60 * 60 * 1000f

                fun bgToY(bg: Float): Float = topPadding + graphHeight - ((bg - minBg) / rangeBg) * graphHeight
                fun timeToX(timestamp: Long): Float {
                    val elapsedMs = (timestamp - dayStartTimestamp).coerceIn(0, twentyFourHoursMs.toLong())
                    return (elapsedMs / twentyFourHoursMs) * graphWidth
                }

                // 1. Draw Target Range Background
                val targetTopY = bgToY(180f)
                val targetBottomY = bgToY(70f)

                drawRoundRect(
                    color = Color(0xFFE8F5E9),
                    topLeft = Offset(0f, targetTopY),
                    size = Size(graphWidth, targetBottomY - targetTopY),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // Grid lines matching the Y-axis
                listOf(70f, 180f, 300f).forEach { bg ->
                    val yLine = bgToY(bg)
                    val color = if (bg == 70f || bg == 180f) Color(0xFFA5D6A7) else Color(0xFFEEEEEE)
                    val stroke = if (bg == 70f || bg == 180f) 2f else 1f
                    drawLine(color, Offset(0f, yLine), Offset(graphWidth, yLine), stroke)
                }

                // 2. Draw X-Axis Labels
                val xLabels = listOf(0, 3, 6, 9, 12, 15, 18, 21, 24)
                val hourStrings = listOf("3 AM", "6 AM", "9 AM", "12 PM", "3 PM", "6 PM", "9 PM", "12 AM", "3 AM")

                xLabels.forEachIndexed { index, hoursElapsed ->
                    val xMs = dayStartTimestamp + (hoursElapsed * 60 * 60 * 1000L)
                    val xPos = timeToX(xMs)

                    drawLine(Color(0xFFF5F5F5), Offset(xPos, topPadding), Offset(xPos, size.height - bottomPadding), 1f)
                    drawLine(Color.LightGray, Offset(xPos, size.height - bottomPadding), Offset(xPos, size.height - bottomPadding + 10f), 2f)

                    drawText(
                        textMeasurer = textMeasurer,
                        text = hourStrings[index],
                        style = TextStyle(color = Color.Gray, fontSize = 10.sp),
                        topLeft = Offset(xPos - 25f, size.height - 40f)
                    )
                }

                if (logs.isEmpty()) return@Canvas

                // 3. Draw Data Path (Only connect lines for logs with BG > 0)
                val bgLogs = logs.filter { it.bloodGlucose > 0 }
                if (bgLogs.isNotEmpty()) {
                    val path = Path()
                    val points = mutableListOf<Offset>()

                    bgLogs.forEachIndexed { index, log ->
                        val x = timeToX(log.timestamp)
                        val y = bgToY(log.bloodGlucose.toFloat().coerceIn(minBg, maxBg))
                        val point = Offset(x, y)
                        points.add(point)

                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(path = path, color = Color(0xFF00897B), style = Stroke(width = 4f))

                    // Draw Data Points
                    points.forEachIndexed { index, point ->
                        val bg = bgLogs[index].bloodGlucose.toFloat()
                        val dotColor = when {
                            bg > 180 -> Color(0xFFFFB74D)
                            bg < 70 -> Color(0xFFE53935)
                            else -> Color(0xFF00897B)
                        }
                        drawCircle(color = Color.White, radius = 10f, center = point)
                        drawCircle(color = dotColor, radius = 7f, center = point)
                    }
                }

                // 4. DRAW EVENT ICONS
                logs.forEach { log ->
                    val hasEvent = log.carbs > 0 || log.administeredDose > 0 || log.isSportModeActive || log.eventType == "SMART_BOLUS" || log.eventType == "BG_CHECK"

                    if (hasEvent) {
                        val x = timeToX(log.timestamp)
                        val y = if (log.bloodGlucose > 0) {
                            bgToY(log.bloodGlucose.toFloat().coerceIn(minBg, maxBg)) - 45f
                        } else {
                            size.height - bottomPadding - 35f
                        }

                        val (painter, tintColor) = when {
                            log.isSportModeActive || log.eventType == "SPORT" -> sportPainter to Color(0xFF00695C)
                            log.eventType == "SMART_BOLUS" -> bolusPainter to Color(0xFFFF9800)
                            log.carbs > 0 && log.administeredDose == 0.0 -> mealPainter to Color(0xFFE91E63)
                            log.administeredDose > 0 -> manualInsulinPainter to Color(0xFF1976D2)
                            else -> checkPainter to Color(0xFFD32F2F)
                        }

                        drawCircle(
                            color = Color.White.copy(alpha = 0.95f),
                            radius = 24f,
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = tintColor.copy(alpha = 0.2f),
                            radius = 24f,
                            center = Offset(x, y),
                            style = Stroke(width = 2f)
                        )

                        val iconSize = 32f
                        translate(left = x - iconSize / 2f, top = y - iconSize / 2f) {
                            with(painter) {
                                draw(
                                    size = Size(iconSize, iconSize),
                                    colorFilter = ColorFilter.tint(tintColor)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(timeString, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Spacer(modifier = Modifier.width(8.dp))
                when {
                    log.isSportModeActive || log.eventType == "SPORT" -> Icon(Icons.Default.DirectionsRun, null, tint = Color(0xFF00695C), modifier = Modifier.size(16.dp))
                    log.eventType == "SMART_BOLUS" -> Icon(Icons.Default.AutoFixHigh, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    log.carbs > 0 && log.administeredDose == 0.0 -> Icon(Icons.Default.Restaurant, null, tint = Color(0xFFE91E63), modifier = Modifier.size(16.dp))
                    log.administeredDose > 0 -> Icon(Icons.Default.Vaccines, null, tint = Color(0xFF1976D2), modifier = Modifier.size(16.dp))
                    else -> Icon(Icons.Default.Bloodtype, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                }
            }

            // Middle: BG & Carbs
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1.5f)) {
                if (log.bloodGlucose > 0) {
                    val color = if (log.bloodGlucose > 180 || log.bloodGlucose < 70) Color.Red else Color(0xFF00897B)
                    Text("${log.bloodGlucose}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
                } else {
                    Text("-", color = Color.LightGray)
                }

                if (log.carbs > 0) {
                    Text("${log.carbs}g", fontSize = 14.sp, color = Color.Gray)
                }
            }

            // Right: Insulin
            if (log.administeredDose > 0) {
                Text("${String.format(Locale.US, "%.1f", log.administeredDose)}U", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            } else {
                Text("-", color = Color.LightGray)
            }
        }
    }
}

@Composable
fun LogDetailsDialog(log: BolusLog, onDismiss: () -> Unit) {
    val formatter = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Entry Details", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatter.format(Date(log.timestamp)), fontSize = 12.sp, color = Color.Gray)

                HorizontalDivider()

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

                if (log.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notes:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(log.notes, fontSize = 14.sp, color = Color.DarkGray)
                }

                if (!log.clinicalSuggestion.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Algorithm Insight", fontSize = 12.sp, color = Color(0xFF00695C), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(log.clinicalSuggestion, fontSize = 13.sp, color = Color(0xFF004D40), lineHeight = 18.sp)
                        }
                    }
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