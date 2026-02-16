package com.example.diabetesapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.viewmodel.CalculateBolusViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.data.database.BolusDatabase
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.viewmodel.CalculateBolusViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculateBolusScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = BolusDatabase.getDatabase(context)
    val repository = BolusLogRepository(database.bolusLogDao())

    val viewModel: CalculateBolusViewModel = viewModel(
        factory = CalculateBolusViewModelFactory(repository)
    )

    val inputState by viewModel.inputState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show warning message as Snackbar
    LaunchedEffect(inputState.warningMessage) {
        inputState.warningMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Bolus Simulator",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
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

            // The Main Calculator View
            CalculatorView(
                inputState = inputState,
                viewModel = viewModel
            )

            // Advanced Confirmation Dialog (Optional, kept for safety checks)
            if (inputState.showAdvancedConfirmationDialog) {
                AdvancedConfirmationDialog(
                    onDismiss = { viewModel.dismissAdvancedConfirmationDialog() },
                    onProceed = { viewModel.proceedWithCalculation() },
                    onAddAdjustments = {
                        viewModel.dismissAdvancedConfirmationDialog()
                        viewModel.toggleAdvancedSection()
                    }
                )
            }

            // The New Thesis Result Dialog
            if (inputState.showResultDialog && inputState.calculatedDose != null) {
                ResultDialog(
                    calculatedDose = inputState.calculatedDose!!,
                    isSportModeActive = inputState.isSportModeActive,
                    sportLog = inputState.sportReductionLog,
                    onDismiss = { viewModel.dismissResultDialog() },
                    onLogAndSave = {
                        viewModel.logEntry()
                        viewModel.dismissResultDialog()
                    },
                    onGoHome = {
                        viewModel.dismissResultDialog()
                        onNavigateBack()
                    }
                )
            }
        }
    }
}

@Composable
fun CalculatorView(
    inputState: com.example.diabetesapp.viewmodel.BolusInputState,
    viewModel: CalculateBolusViewModel
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Primary Inputs Card
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
                Text(
                    text = "Standard Inputs",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00897B)
                )

                // Blood Glucose Input - Large & Prominent
                LargeInputField(
                    label = "Current Blood Glucose",
                    value = inputState.bloodGlucose,
                    onValueChange = { viewModel.updateBloodGlucose(it) },
                    unit = "mg/dL",
                    placeholder = "Enter BG",
                    errorMessage = inputState.bloodGlucoseError
                )

                // Carbs Input - Large & Prominent
                LargeInputField(
                    label = "Carbohydrates",
                    value = inputState.carbs,
                    onValueChange = { viewModel.updateCarbs(it) },
                    unit = "g",
                    placeholder = "Enter carbs",
                    errorMessage = inputState.carbsError
                )
            }
        }

        // --- NEW THESIS FEATURE: SPORT SIMULATOR ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)), // Light teal background
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thesis Sport Algorithm",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF00695C)
                    )
                    Switch(
                        checked = inputState.isSportModeActive,
                        onCheckedChange = { viewModel.toggleSportMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00897B)
                        )
                    )
                }

                // Only show sliders if Sport Mode is ON
                AnimatedVisibility(visible = inputState.isSportModeActive) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        // 1. Sport Type (Aerobic vs Anaerobic)
                        Text(
                            text = "Activity Type (T1DEXIP Logic)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00695C)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.updateSportType("Aerobic") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (inputState.sportType == "Aerobic") Color(0xFF00897B) else Color.Transparent,
                                    contentColor = if (inputState.sportType == "Aerobic") Color.White else Color(0xFF00897B)
                                )
                            ) { Text("Aerobic (Run)") }

                            OutlinedButton(
                                onClick = { viewModel.updateSportType("Anaerobic") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (inputState.sportType == "Anaerobic") Color(0xFF00897B) else Color.Transparent,
                                    contentColor = if (inputState.sportType == "Anaerobic") Color.White else Color(0xFF00897B)
                                )
                            ) { Text("Anaerobic (Weights)") }
                        }

                        // 2. Intensity Slider
                        Text(
                            text = "Intensity: ${inputState.sportIntensity}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00695C)
                        )
                        Slider(
                            value = inputState.sportIntensityValue,
                            onValueChange = { viewModel.updateSportIntensity(it) },
                            valueRange = 1f..3f,
                            steps = 1, // Creates 3 snap points (Low, Med, High)
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00897B),
                                activeTrackColor = Color(0xFF00897B)
                            )
                        )

                        // 3. Duration Slider
                        Text(
                            text = "Duration: ${inputState.sportDurationMinutes.toInt()} mins",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00695C)
                        )
                        Slider(
                            value = inputState.sportDurationMinutes,
                            onValueChange = { viewModel.updateSportDuration(it) },
                            valueRange = 0f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00897B),
                                activeTrackColor = Color(0xFF00897B)
                            )
                        )
                    }
                }
            }
        }
        // --- END THESIS FEATURE ---

        // Advanced Adjustments - Collapsible
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Clickable Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleAdvancedSection() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF00897B),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Advanced Adjustments",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00897B)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (inputState.isAdvancedExpanded) "Collapse" else "Expand",
                        tint = Color(0xFF00897B),
                        modifier = Modifier.rotate(if (inputState.isAdvancedExpanded) 180f else 0f)
                    )
                }

                // Expandable Content
                AnimatedVisibility(
                    visible = inputState.isAdvancedExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StandardInputField(
                            label = "Correction Amount",
                            value = inputState.correctionAmount,
                            onValueChange = { viewModel.updateCorrectionAmount(it) },
                            unit = "U",
                            helperText = "Manual correction additions"
                        )

                        StandardInputField(
                            label = "Basal Rate Excess",
                            value = inputState.basalRateExcess,
                            onValueChange = { viewModel.updateBasalRateExcess(it) },
                            unit = "U",
                            helperText = "For pump users"
                        )

                        StandardInputField(
                            label = "Active Insulin (IOB)",
                            value = inputState.activeInsulin,
                            onValueChange = { viewModel.updateActiveInsulin(it) },
                            unit = "U",
                            helperText = "Insulin on board"
                        )
                    }
                }
            }
        }

        // Calculate Button
        Button(
            onClick = { viewModel.calculateBolus() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00897B)
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = "Simulate Dose",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
fun LargeInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    placeholder: String,
    errorMessage: String? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF424242)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    fontSize = 20.sp,
                    color = Color(0xFFBDBDBD)
                )
            },
            trailingIcon = {
                Text(
                    text = unit,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (errorMessage != null) Color(0xFFD32F2F) else Color(0xFF00897B),
                unfocusedBorderColor = if (errorMessage != null) Color(0xFFD32F2F) else Color(0xFFCCCCCC),
                errorBorderColor = Color(0xFFD32F2F)
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            isError = errorMessage != null
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                fontSize = 12.sp,
                color = Color(0xFFD32F2F),
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun StandardInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    helperText: String? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF424242)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Text(
                    text = unit,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00897B),
                unfocusedBorderColor = Color(0xFFCCCCCC)
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )
        if (helperText != null) {
            Text(
                text = helperText,
                fontSize = 12.sp,
                color = Color(0xFF757575),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun AdvancedConfirmationDialog(
    onDismiss: () -> Unit,
    onProceed: () -> Unit,
    onAddAdjustments: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Advanced Adjustments",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Would you like to add any advanced adjustments before calculating?",
                    fontSize = 16.sp,
                    color = Color(0xFF424242)
                )
                Text(
                    text = "• Correction Amount\n• Basal Rate Excess\n• Active Insulin (IOB)",
                    fontSize = 14.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onProceed) {
                Text(
                    text = "No, Calculate Now",
                    color = Color(0xFF00897B),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAddAdjustments,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00897B)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Add Adjustments",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White
    )
}

@Composable
fun ResultDialog(
    calculatedDose: Double,
    isSportModeActive: Boolean,
    sportLog: String,
    onDismiss: () -> Unit,
    onLogAndSave: () -> Unit,
    onGoHome: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isSportModeActive) "Sport Adjusted Dose" else "Standard Dose",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (isSportModeActive) Color(0xFF00695C) else Color(0xFF2E7D32)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Large dose display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSportModeActive) Color(0xFFE0F2F1) else Color(0xFFE8F5E9)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "%.1f".format(java.util.Locale.US, calculatedDose),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSportModeActive) Color(0xFF00695C) else Color(0xFF2E7D32)
                        )
                        Text(
                            text = "Units",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSportModeActive) Color(0xFF00695C) else Color(0xFF2E7D32)
                        )
                    }
                }

                // --- THE THESIS MATH REVEAL ---
                if (isSportModeActive && sportLog.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Algorithm Breakdown:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = sportLog,
                                fontSize = 12.sp,
                                color = Color.DarkGray,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Standard calculation based on ICR and ISF.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLogAndSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Complete Simulation", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to Editor", color = Color.Gray)
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}