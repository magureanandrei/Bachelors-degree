package com.example.diabetesapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.data.database.BolusDatabase
import com.example.diabetesapp.data.models.BgFetchStatus
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.viewmodel.InsightType
import com.example.diabetesapp.viewmodel.LogReadingViewModel
import com.example.diabetesapp.viewmodel.LogReadingViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogReadingScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val repository = remember { BolusLogRepository(database.bolusLogDao()) }
    val settingsRepository = remember { BolusSettingsRepository.getInstance(context) }

    val viewModel: LogReadingViewModel = viewModel(
        factory = LogReadingViewModelFactory(repository, settingsRepository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSportInfoDialog by remember { mutableStateOf(false) }
    var showDateTimeDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetState() }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) { onNavigateBack() }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchBgForTimestamp(viewModel.getEventTimestamp())
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // Time picker for the custom dialog
    val timeParts = uiState.eventTime.split(":")
    val initialHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 12
    val initialMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, selectedHour, selectedMinute ->
            val formattedTime = String.format(
                java.util.Locale.getDefault(),
                "%02d:%02d", selectedHour, selectedMinute
            )
            viewModel.updateTime(formattedTime)
            showDateTimeDialog = false
        },
        initialHour,
        initialMinute,
        true
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Log Event", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1. SEGMENTED TAB TOGGLE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (!uiState.isSportModeActive) Color.White else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.toggleSportMode(false) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Diet & Insulin",
                        fontWeight = FontWeight.Bold,
                        color = if (!uiState.isSportModeActive) Color(0xFF1976D2) else Color.Gray
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (uiState.isSportModeActive) Color.White else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.toggleSportMode(true) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Past Sport",
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isSportModeActive) Color(0xFF00695C) else Color.Gray
                    )
                }
            }

            // 2. TIME OF EVENT CARD
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showDateTimeDialog = true },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Time of Event", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = uiState.eventTime,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isSportModeActive) Color(0xFF00695C) else Color(0xFF1976D2)
                            )
                            Text(
                                text = uiState.eventDate,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // 3. DYNAMIC INPUT AREA
            Crossfade(targetState = uiState.isSportModeActive, label = "Log Mode") { isSport ->
                if (isSport) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Sport Type", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { showSportInfoDialog = true },
                                    modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                ) {
                                    Icon(Icons.Default.Info, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Aerobic", "Mixed", "Anaerobic").forEach { type ->
                                    OutlinedButton(
                                        onClick = { viewModel.updateSportType(type) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (uiState.sportType == type) Color(0xFF00695C) else Color.Transparent,
                                            contentColor = if (uiState.sportType == type) Color.White else Color(0xFF00695C)
                                        )
                                    ) {
                                        Text(
                                            type,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFF5F5F5))

                            Text("Duration: ${uiState.sportDurationMinutes.toInt()} min", fontSize = 14.sp, color = Color.Gray)
                            Slider(
                                value = uiState.sportDurationMinutes,
                                onValueChange = { viewModel.updateSportDuration(it) },
                                valueRange = 15f..120f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00695C),
                                    activeTrackColor = Color(0xFF00695C)
                                )
                            )

                            Text("Intensity: ${uiState.sportIntensity}", fontSize = 14.sp, color = Color.Gray)
                            Slider(
                                value = uiState.sportIntensityValue,
                                onValueChange = { viewModel.updateSportIntensity(it) },
                                valueRange = 1f..3f,
                                steps = 1,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00695C),
                                    activeTrackColor = Color(0xFF00695C)
                                )
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            LargeInputField(
                                label = "Blood Glucose",
                                value = uiState.bloodGlucose,
                                onValueChange = { viewModel.updateBloodGlucose(it) },
                                unit = "mg/dL",
                                placeholder = "0"
                            )
                            when (uiState.bgFetchStatus) {
                                BgFetchStatus.FOUND -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00897B), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "BG auto-filled from ${if (settings.isCgmEnabled) "CGM history" else "recent log"}",
                                        fontSize = 11.sp, color = Color(0xFF00897B),
                                        maxLines =1
                                    )
                                }
                                BgFetchStatus.NOT_FOUND -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "No reading found near this time — enter BG manually",
                                        fontSize = 11.sp, color = Color.Gray,
                                        maxLines =1
                                    )
                                }
                                BgFetchStatus.IDLE -> {}
                            }
                            LargeInputField(
                                label = "Carbohydrates",
                                value = uiState.carbs,
                                onValueChange = { viewModel.updateCarbs(it) },
                                unit = "g",
                                placeholder = "0"
                            )
                            LargeInputField(
                                label = if (settings.isAidPump) "Pen Correction" else "Insulin Dose",
                                value = uiState.manualInsulin,
                                onValueChange = { viewModel.updateManualInsulin(it) },
                                unit = "U",
                                placeholder = "0.0"
                            )
                            if (settings.isMdi) {
                                LargeInputField(
                                    label = "Basal Dose (${settings.basalInsulinType.displayName})",
                                    value = uiState.basalInsulin,
                                    onValueChange = { viewModel.updateBasalInsulin(it) },
                                    unit = "U",
                                    placeholder = "0.0"
                                )
                                // Warning if basal not configured yet
                                if (!settings.hasBasalConfigured) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                tint = Color(0xFFFF9800),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Configure your basal insulin in Settings for better tracking.",
                                                fontSize = 11.sp,
                                                color = Color(0xFFE65100),
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. NOTES
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StandardInputField(
                        label = "Notes / Tags",
                        value = uiState.notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        unit = "",
                        helperText = "e.g., 'Felt low', 'Morning run'"
                    )
                }
            }

            Button(
                onClick = { viewModel.analyzeLog() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isSportModeActive) Color(0xFF00695C) else Color(0xFF1976D2)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Analyze & Log", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1976D2))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Use the Manual Log to record past events so your history and active insulin tracking stay accurate. For future planning and dose calculations, use Smart Bolus.",
                        fontSize = 13.sp,
                        color = Color(0xFF0D47A1),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // DATE/TIME DIALOG
    if (showDateTimeDialog) {
        AlertDialog(
            onDismissRequest = { showDateTimeDialog = false },
            title = {
                Text("When did this happen?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Today / Yesterday toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                            .padding(4.dp)
                    ) {
                        listOf("Today", "Yesterday").forEach { date ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (uiState.eventDate == date) Color.White else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.updateEventDate(date) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    date,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.eventDate == date) Color(0xFF1976D2) else Color.Gray
                                )
                            }
                        }
                    }

                    // Time selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Time", fontSize = 14.sp, color = Color.Gray)
                        OutlinedButton(
                            onClick = { timePickerDialog.show() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF1976D2)
                            )
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                uiState.eventTime,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDateTimeDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateTimeDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // AID PEN WARNING DIALOG
    if (uiState.showAidPenWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAidWarning() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pen Correction on AID?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "You're logging manual insulin while using an AID pump. This is only recommended for pen corrections when your pump is suspended or malfunctioning.",
                        fontSize = 14.sp,
                        color = Color.DarkGray,
                        lineHeight = 20.sp
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "⚠️ Your pump's won't be accurate while the pen insulin is active — double-dosing can cause severe hypoglycemia.",
                            fontSize = 13.sp,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAidPenCorrection() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Yes, log pen correction")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAidWarning() }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // INSIGHT DIALOG
    uiState.currentInsight?.let { insight ->
        val (icon, color, bgColor) = when (insight.type) {
            InsightType.ON_TRACK -> Triple(Icons.Default.CheckCircle, Color(0xFF2E7D32), Color(0xFFE8F5E9))
            InsightType.WARNING -> Triple(Icons.Default.Warning, Color(0xFFD32F2F), Color(0xFFFFEBEE))
            InsightType.SUGGESTION -> Triple(Icons.Default.Info, Color(0xFF1976D2), Color(0xFFE3F2FD))
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissInsightDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = color)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(insight.title, fontWeight = FontWeight.Bold, color = color, fontSize = 20.sp)
                }
            },
            text = {
                Card(
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = insight.message,
                        fontSize = 15.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(16.dp),
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executeSave() },
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm & Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissInsightDialog() }) {
                    Text("Edit Entry", color = Color.Gray)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // SPORT INFO DIALOG
    if (showSportInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSportInfoDialog = false },
            title = { Text("Sport Types Explained", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Different exercises affect blood glucose (BG) in completely different ways based on the T1DEXIP study guidelines:",
                        fontSize = 14.sp
                    )
                    Column {
                        Text("🏃‍♂️ Aerobic", fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                        Text("Continuous cardio (running, cycling, swimming). Rapidly burns glucose. Requires the largest reduction in insulin to prevent severe lows.", fontSize = 13.sp, color = Color.DarkGray)
                    }
                    Column {
                        Text("⚽ Mixed", fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                        Text("Stop-and-go sports (soccer, basketball, tennis). A mix of cardio and adrenaline. Requires a moderate, balanced insulin approach.", fontSize = 13.sp, color = Color.DarkGray)
                    }
                    Column {
                        Text("🏋️‍♂️ Anaerobic", fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                        Text("Short, intense bursts (weightlifting, sprinting). Adrenaline spikes can actually RAISE your BG temporarily. Requires minimal insulin reduction.", fontSize = 13.sp, color = Color.DarkGray)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSportInfoDialog = false }) {
                    Text("Got it", color = Color(0xFF00897B), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun LargeInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    placeholder: String = ""
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            if (unit.isNotEmpty()) {
                Text(text = unit, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            }
        }
    }
}

@Composable
fun StandardInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    helperText: String = ""
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(helperText) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}