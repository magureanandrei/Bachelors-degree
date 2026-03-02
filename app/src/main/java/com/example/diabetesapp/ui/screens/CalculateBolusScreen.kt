package com.example.diabetesapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
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
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.viewmodel.BolusInputState
import com.example.diabetesapp.viewmodel.CalculateBolusViewModel
import com.example.diabetesapp.viewmodel.CalculateBolusViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculateBolusScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val repository = remember { BolusLogRepository(database.bolusLogDao()) }

    val viewModel: CalculateBolusViewModel = viewModel(
        factory = CalculateBolusViewModelFactory(repository)
    )

    val inputState by viewModel.inputState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Reset state when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetState()
        }
    }

    LaunchedEffect(inputState.warningMessage) {
        inputState.warningMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.clearWarningMessage() // Clears it so it doesn't pop up again on rotation
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Smart Bolus", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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

            CalculatorView(inputState = inputState, viewModel = viewModel)

            if (inputState.showResultDialog && inputState.calculatedDose != null) {
                ResultDialog(
                    calculatedDose = inputState.calculatedDose!!,
                    userAdjustedDose = inputState.userAdjustedDose,
                    isSportModeActive = inputState.isSportModeActive,
                    minutesUntilSport = inputState.minutesUntilSport,
                    sportLog = inputState.sportReductionLog,
                    onAdjustDose = { delta -> viewModel.adjustSuggestedDose(delta) },
                    onDismiss = { viewModel.dismissResultDialog() },
                    onLogAndSave = {
                        viewModel.logEntry(context)
                        viewModel.dismissResultDialog()
                        onNavigateBack() // Navigate Back to Home
                    }
                )
            }
        }
    }
}

@Composable
fun CalculatorView(
    inputState: BolusInputState,
    viewModel: CalculateBolusViewModel
) {
    var showSportInfoDialog by remember { mutableStateOf(false) }

    // --- NEW: Time Picker Setup ---
    val context = LocalContext.current

    // Parse the current planned time to set the picker's initial position
    val timeParts = inputState.plannedSportTime.split(":")
    val initialHour = timeParts.getOrNull(0)?.toIntOrNull() ?: java.time.LocalTime.now().hour
    val initialMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: java.time.LocalTime.now().minute

    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, selectedHour, selectedMinute ->
            // Format back to HH:mm and send to ViewModel
            val formattedTime = String.format(java.util.Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
            viewModel.updatePlannedSportTime(formattedTime)
        },
        initialHour,
        initialMinute,
        true // Use 24-hour clock
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Blood Glucose",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = inputState.bloodGlucose,
                    onValueChange = { viewModel.updateBloodGlucose(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter BG (mg/dL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = inputState.bloodGlucoseError != null,
                    supportingText = inputState.bloodGlucoseError?.let {
                        {
                            Text(
                                it,
                                color = Color.Red
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "Carbohydrates",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = inputState.carbs,
                    onValueChange = { viewModel.updateCarbs(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter carbs (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = inputState.carbsError != null,
                    supportingText = inputState.carbsError?.let { { Text(it, color = Color.Red) } },
                    shape = RoundedCornerShape(12.dp)
                )

                // Sport Mode Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (inputState.isSportModeActive) Color(0xFFE0F2F1) else Color(
                            0xFFF5F5F5
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sport Mode",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = inputState.isSportModeActive,
                                onCheckedChange = { viewModel.toggleSportMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF00695C)
                                )
                            )
                        }

                        AnimatedVisibility(visible = inputState.isSportModeActive) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Sport Type Header
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Sport Type", fontSize = 14.sp, color = Color.Gray)
                                    IconButton(
                                        onClick = { showSportInfoDialog = true },
                                        modifier = Modifier.size(24.dp).padding(start = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "Sport Info",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
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
                                            contentPadding = PaddingValues(
                                                horizontal = 0.dp,
                                                vertical = 8.dp
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (inputState.sportType == type) Color(
                                                    0xFF00695C
                                                ) else Color.Transparent,
                                                contentColor = if (inputState.sportType == type) Color.White else Color(
                                                    0xFF00695C
                                                )
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

                                // --- NATIVE TIME PICKER BUTTON ---
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Start Time",
                                            fontSize = 14.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (inputState.minutesUntilSport > 0) {
                                            Text(
                                                "In ~${inputState.minutesUntilSport.toInt()} mins",
                                                fontSize = 12.sp,
                                                color = Color(0xFFFF9800)
                                            )
                                        } else {
                                            Text(
                                                "Right Now",
                                                fontSize = 12.sp,
                                                color = Color(0xFF00695C)
                                            )
                                        }
                                    }

                                    // Make the text field act like a button
                                    Box(modifier = Modifier.clickable { timePickerDialog.show() }) {
                                        OutlinedTextField(
                                            value = inputState.plannedSportTime,
                                            onValueChange = { }, // Ignored, handled by picker
                                            modifier = Modifier.width(100.dp),
                                            readOnly = true, // PREVENTS KEYBOARD FROM OPENING
                                            enabled = false, // Makes the box ignore standard focus
                                            shape = RoundedCornerShape(8.dp),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                disabledTextColor = Color.Black,
                                                disabledBorderColor = if (inputState.minutesUntilSport > 0) Color(
                                                    0xFFFF9800
                                                ) else Color.LightGray
                                            )
                                        )
                                    }
                                }

                                // Intensity Slider
                                Text(
                                    "Intensity: ${inputState.sportIntensity}",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Slider(
                                    value = inputState.sportIntensityValue,
                                    onValueChange = { viewModel.updateSportIntensity(it) },
                                    valueRange = 1f..3f, steps = 1,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00695C),
                                        activeTrackColor = Color(0xFF00695C)
                                    )
                                )

                                // Duration Slider
                                Text(
                                    "Duration: ${inputState.sportDurationMinutes.toInt()} min",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Slider(
                                    value = inputState.sportDurationMinutes,
                                    onValueChange = { viewModel.updateSportDuration(it) },
                                    valueRange = 15f..120f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00695C),
                                        activeTrackColor = Color(0xFF00695C)
                                    )
                                )
                            }
                        }
                    }
                }

                ContextFactorSection(
                    selectedFactor = inputState.selectedFactor,
                    isExpanded = inputState.isContextExpanded,
                    onToggle = { viewModel.toggleContextSection() },
                    onFactorSelected = { viewModel.updateSelectedFactor(it) }
                )

                OutlinedTextField(
                    value = inputState.notes,
                    onValueChange = { viewModel.updateNotes(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Notes (optional)") },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                Button(
                    onClick = { viewModel.calculateBolus() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Calculate Bolus", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } // 1. THIS closes the inner Column containing all the inputs
        } // 2. THIS closes the main white Input Card. (You were likely missing this one!)

        // 3. NOW we place the Info Card safely in the parent scrollable Column
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), // Light Blue
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1976D2))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Smart Bolus is a proactive tool for calculating doses and planning future activities. For past events, use the Manual Log.",
                    fontSize = 13.sp,
                    color = Color(0xFF0D47A1),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    } // 4. THIS closes the parent scrollable Column
// 5. THIS closes the Scaffold/Screen

    // The Educational Dialog
    if (showSportInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSportInfoDialog = false },
            title = {
                Text("Sport Types Explained", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Different exercises affect blood glucose (BG) in completely different ways based on the T1DEXIP study guidelines:",
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
fun ResultDialog(
    calculatedDose: Double,
    userAdjustedDose: Double?,
    isSportModeActive: Boolean,
    minutesUntilSport: Float,
    sportLog: String,
    onAdjustDose: (Double) -> Unit,
    onDismiss: () -> Unit,
    onLogAndSave: () -> Unit
) {
    val displayDose = userAdjustedDose ?: calculatedDose
    val isPlanned = isSportModeActive && minutesUntilSport > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPlanned) "Pre-Workout Strategy" else if (isSportModeActive) "Sport Adjusted Dose" else "Standard Dose",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (isPlanned) Color(0xFFFF9800) else if (isSportModeActive) Color(0xFF00695C) else Color(0xFF2E7D32)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Large dose display with Manual Override
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlanned) Color(0xFFFFF3E0) else if (isSportModeActive) Color(0xFFE0F2F1) else Color(0xFFE8F5E9)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Administering:", fontSize = 14.sp, color = Color.Gray)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Minus Button
                            IconButton(
                                onClick = { onAdjustDose(-0.1) },
                                modifier = Modifier.background(Color.White, RoundedCornerShape(8.dp))
                            ) {
                                Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }

                            // The Number
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "%.1f".format(java.util.Locale.US, displayDose),
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPlanned) Color(0xFFFF9800) else Color(0xFF2E7D32)
                                )
                                Text("Units", fontSize = 16.sp, color = if (isPlanned) Color(0xFFFF9800) else Color(0xFF2E7D32))
                            }

                            // Plus Button
                            IconButton(
                                onClick = { onAdjustDose(0.1) },
                                modifier = Modifier.background(Color.White, RoundedCornerShape(8.dp))
                            ) {
                                Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Show deviation if user changed it
                        if (displayDose != calculatedDose) {
                            Text(
                                text = "Suggested: %.1f U".format(java.util.Locale.US, calculatedDose),
                                fontSize = 12.sp,
                                color = if (isPlanned) Color(0xFFFFB74D) else Color(0xFF81C784),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                if (isSportModeActive && sportLog.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Algorithm Breakdown:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(sportLog, fontSize = 12.sp, color = Color.DarkGray, lineHeight = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onLogAndSave,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isPlanned) Color(0xFFFF9800) else Color(0xFF2E7D32)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isPlanned) "Save Planned Workout" else "Log & Administer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
fun ContextFactorSection(
    selectedFactor: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onFactorSelected: (String) -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00897B))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Outside Factors", fontWeight = FontWeight.Bold)

                // Info Icon
                IconButton(
                    onClick = { showInfo = !showInfo },
                    modifier = Modifier.size(24.dp).padding(start = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }

        if (showInfo) {
             FactorInfoCard()
             Spacer(modifier = Modifier.height(16.dp))
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp)) // Reduced spacing since info might not be there

            // 2x2 Grid Layout
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // Row 1: None & Stress
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FactorOption(
                        label = "None",
                        isSelected = selectedFactor == "None",
                        onClick = { onFactorSelected("None") },
                        modifier = Modifier.weight(1f)
                    )
                    FactorOption(
                        label = "Stress",
                        isSelected = selectedFactor == "Stress",
                        onClick = { onFactorSelected("Stress") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Illness & Heat
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FactorOption(
                        label = "Illness",
                        isSelected = selectedFactor == "Illness",
                        onClick = { onFactorSelected("Illness") },
                        modifier = Modifier.weight(1f)
                    )
                    FactorOption(
                        label = "Heat",
                        isSelected = selectedFactor == "Heat",
                        onClick = { onFactorSelected("Heat") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun FactorOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(2f) // Rectangular shape for better text fit
            .background(
                if (isSelected) Color(0xFF00897B) else Color(0xFFF5F5F5),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FactorInfoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("How Advice is Adjusted", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1565C0))
            Spacer(modifier = Modifier.height(6.dp))

            // Manual Factors
            Text("• Illness & Stress: Increases insulin resistance. The algorithm adds a percentage-based buffer to your dose.", fontSize = 12.sp)
            Text("• Extreme Heat: Increases absorption speed. The algorithm reduces the dose slightly to prevent hypoglycemia.", fontSize = 12.sp)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.5f))

            // Time-of-Day Settings (Educational)
            Text("• Time of Day: The algorithm automatically switches between your Morning, Noon, Evening, and Night ratios.", fontSize = 12.sp)
            Text("• Personalization: You can adjust these specific Ratios (ICR) and Sensitivity (ISF) values in the Settings menu.",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0D47A1))
        }
    }
}
