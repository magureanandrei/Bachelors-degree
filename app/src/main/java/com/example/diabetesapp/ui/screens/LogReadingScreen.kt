package com.example.diabetesapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restaurant
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

    val viewModel: LogReadingViewModel = viewModel(
        factory = LogReadingViewModelFactory(repository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSportInfoDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetState() }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) { onNavigateBack() }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Log Event", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
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

            // --- NEW: Time & Date Fields ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.eventTime,
                    onValueChange = { viewModel.updateTime(it) },
                    label = { Text("Time") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
                )
                OutlinedTextField(
                    value = uiState.eventDate,
                    onValueChange = { viewModel.updateDate(it) },
                    label = { Text("Date") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
                )
            }

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
                        label = "Blood Glucose", value = uiState.bloodGlucose,
                        onValueChange = { viewModel.updateBloodGlucose(it) },
                        unit = "mg/dL", placeholder = "0"
                    )
                    LargeInputField(
                        label = "Carbohydrates", value = uiState.carbs,
                        onValueChange = { viewModel.updateCarbs(it) },
                        unit = "g", placeholder = "0"
                    )
                    LargeInputField(
                        label = "Insulin Dose", value = uiState.manualInsulin,
                        onValueChange = { viewModel.updateManualInsulin(it) },
                        unit = "U", placeholder = "0.0"
                    )
                    StandardInputField(
                        label = "Notes / Tags", value = uiState.notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        unit = "", helperText = "e.g., 'Felt low', 'Ate pizza'"
                    )
                }
            }

            // --- SPORT MODE SECTION ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isSportModeActive) Color(0xFFE0F2F1) else Color(0xFFF5F5F5)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Log Sport/Exercise", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                        Switch(
                            checked = uiState.isSportModeActive,
                            onCheckedChange = { viewModel.toggleSportMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF00695C))
                        )
                    }

                    AnimatedVisibility(visible = uiState.isSportModeActive) {
                        Column(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Sport Type", fontSize = 14.sp, color = Color.Gray)
                                IconButton(onClick = { showSportInfoDialog = true }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                                    Icon(Icons.Default.Info, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                        Text(type, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Text("Intensity: ${uiState.sportIntensity}", fontSize = 14.sp, color = Color.Gray)
                            Slider(
                                value = uiState.sportIntensityValue,
                                onValueChange = { viewModel.updateSportIntensity(it) },
                                valueRange = 1f..3f, steps = 1,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00695C), activeTrackColor = Color(0xFF00695C))
                            )

                            Text("Duration: ${uiState.sportDurationMinutes.toInt()} min", fontSize = 14.sp, color = Color.Gray)
                            Slider(
                                value = uiState.sportDurationMinutes,
                                onValueChange = { viewModel.updateSportDuration(it) },
                                valueRange = 15f..120f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00695C), activeTrackColor = Color(0xFF00695C))
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.attemptSave() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Event", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // --- DIALOGS (Kept exactly the same as the previous response) ---
    // (Carb Suggestion Dialog)
    if (uiState.showCarbSuggestionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCarbDialog() },
            title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Restaurant, null, tint = Color(0xFFE91E63)); Spacer(modifier = Modifier.width(8.dp)); Text("Carbs Recommended", fontWeight = FontWeight.Bold, color = Color(0xFFE91E63)) } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiState.carbSuggestionMessage, fontSize = 14.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC)), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Suggested Intake:", color = Color.Gray, fontSize = 12.sp)
                            Text("${uiState.suggestedCarbs}g Carbs", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color(0xFFE91E63))
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { viewModel.updateCarbs(uiState.suggestedCarbs.toString()); viewModel.dismissCarbDialog(); viewModel.executeSave() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))) { Text("Add Carbs & Log") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissCarbDialog(); viewModel.executeSave() }) { Text("Ignore & Log Anyway", color = Color.Gray) } },
            containerColor = Color.White
        )
    }

    // (Post Sport Alert Dialog)
    if (uiState.showPostSportAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPostSportAlert() },
            title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Color(0xFF00695C)); Spacer(modifier = Modifier.width(8.dp)); Text("Clinical Insight", fontWeight = FontWeight.Bold, color = Color(0xFF00695C)) } },
            text = { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)), shape = RoundedCornerShape(12.dp)) { Text(text = uiState.postSportAlertMessage, fontSize = 14.sp, color = Color(0xFF004D40), modifier = Modifier.padding(16.dp), lineHeight = 20.sp) } },
            confirmButton = { Button(onClick = { viewModel.dismissPostSportAlert(); viewModel.executeSave() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))) { Text("Understood, Log Event") } },
            containerColor = Color.White
        )
    }

    // (Info Dialog)
    if (showSportInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSportInfoDialog = false },
            title = { Text("Sport Types Explained", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Different exercises affect blood glucose (BG) in completely different ways based on the T1DEXIP study guidelines:", fontSize = 14.sp)
                    Column { Text("🏃‍♂️ Aerobic", fontWeight = FontWeight.Bold, color = Color(0xFF00897B)); Text("Continuous cardio (running, cycling, swimming). Rapidly burns glucose. Requires the largest reduction in insulin to prevent severe lows.", fontSize = 13.sp, color = Color.DarkGray) }
                    Column { Text("⚽ Mixed", fontWeight = FontWeight.Bold, color = Color(0xFF00897B)); Text("Stop-and-go sports (soccer, basketball, tennis). A mix of cardio and adrenaline. Requires a moderate, balanced insulin approach.", fontSize = 13.sp, color = Color.DarkGray) }
                    Column { Text("🏋️‍♂️ Anaerobic", fontWeight = FontWeight.Bold, color = Color(0xFF00897B)); Text("Short, intense bursts (weightlifting, sprinting). Adrenaline spikes can actually RAISE your BG temporarily. Requires minimal insulin reduction.", fontSize = 13.sp, color = Color.DarkGray) }
                }
            },
            confirmButton = { TextButton(onClick = { showSportInfoDialog = false }) { Text("Got it", color = Color(0xFF00897B), fontWeight = FontWeight.Bold) } },
            containerColor = Color.White, shape = RoundedCornerShape(16.dp)
        )
    }
}

// ... KEEP LARGEINPUTFIELD AND STANDARDINPUTFIELD EXACTLY THE SAME ...
@Composable
fun LargeInputField(label: String, value: String, onValueChange: (String) -> Unit, unit: String, placeholder: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f), placeholder = { Text(placeholder) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp), singleLine = true)
            if (unit.isNotEmpty()) { Text(text = unit, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Gray) }
        }
    }
}
@Composable
fun StandardInputField(label: String, value: String, onValueChange: (String) -> Unit, unit: String, helperText: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
        OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), placeholder = { Text(helperText) }, shape = RoundedCornerShape(12.dp), singleLine = true)
    }
}