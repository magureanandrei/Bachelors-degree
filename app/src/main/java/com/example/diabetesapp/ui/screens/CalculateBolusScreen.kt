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
import com.example.diabetesapp.viewmodel.InputMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculateBolusScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    viewModel: CalculateBolusViewModel = viewModel()
) {
    @Composable
    fun CalculatorView(
        inputState: com.example.diabetesapp.viewmodel.BolusInputState,
        viewModel: CalculateBolusViewModel,
        onNavigateBack: () -> Unit = {}
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Primary Inputs Card (Kept mostly the same)
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

                    LargeInputField(
                        label = "Current Blood Glucose",
                        value = inputState.bloodGlucose,
                        onValueChange = { viewModel.updateBloodGlucose(it) },
                        unit = "mg/dL",
                        placeholder = "Enter BG",
                        errorMessage = inputState.bloodGlucoseError
                    )

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
                    text = "Calculate Dose",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

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
                        text = "Calculate Bolus",
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
        ) {
            // Tab Selector
            ModeSelectorTabs(
                selectedMode = inputState.inputMode,
                onModeSelected = { viewModel.setInputMode(it) }
            )

            // Content based on selected mode
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (inputState.inputMode) {
                    InputMode.CALCULATE -> CalculatorView(
                        inputState = inputState,
                        viewModel = viewModel,
                        onNavigateBack = onNavigateBack
                    )
                    InputMode.MANUAL -> ManualInputView(
                        inputState = inputState,
                        viewModel = viewModel
                    )
                }
            }

            // Advanced Confirmation Dialog
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

            // Result Dialog
            if (inputState.showResultDialog && inputState.calculatedDose != null) {
                ResultDialog(
                    calculatedDose = inputState.calculatedDose!!,
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
fun ModeSelectorTabs(
    selectedMode: InputMode,
    onModeSelected: (InputMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Calculator Tab
            Button(
                onClick = { onModeSelected(InputMode.CALCULATE) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMode == InputMode.CALCULATE)
                        Color(0xFF00897B)
                    else
                        Color.Transparent,
                    contentColor = if (selectedMode == InputMode.CALCULATE)
                        Color.White
                    else
                        Color(0xFF00897B)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (selectedMode == InputMode.CALCULATE) 2.dp else 0.dp
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "CALCULATOR",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Manual Input Tab
            Button(
                onClick = { onModeSelected(InputMode.MANUAL) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedMode == InputMode.MANUAL)
                        Color(0xFF00897B)
                    else
                        Color.Transparent,
                    contentColor = if (selectedMode == InputMode.MANUAL)
                        Color.White
                    else
                        Color(0xFF00897B)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (selectedMode == InputMode.MANUAL) 2.dp else 0.dp
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "MANUAL INPUT",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CalculatorView(
    inputState: com.example.diabetesapp.viewmodel.BolusInputState,
    viewModel: CalculateBolusViewModel,
    onNavigateBack: () -> Unit = {}
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
                    text = "Primary Inputs",
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

        // Future Feature Placeholder
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sport Mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF757575)
                    )
                    Text(
                        text = "Coming soon",
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
                Switch(
                    checked = false,
                    onCheckedChange = null,
                    enabled = false,
                    colors = SwitchDefaults.colors(
                        disabledCheckedThumbColor = Color(0xFFBDBDBD),
                        disabledUncheckedThumbColor = Color(0xFFBDBDBD)
                    )
                )
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
                text = "Calculate Dose",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ManualInputView(
    inputState: com.example.diabetesapp.viewmodel.BolusInputState,
    viewModel: CalculateBolusViewModel
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Manual Input Card
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
                    text = "Log Insulin Dose",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00897B)
                )

                // Manual Insulin Input
                LargeInputField(
                    label = "Total Insulin Dose",
                    value = inputState.manualInsulin,
                    onValueChange = { viewModel.updateManualInsulin(it) },
                    unit = "U",
                    placeholder = "Enter dose",
                    errorMessage = inputState.manualInsulinError
                )

                HorizontalDivider(color = Color(0xFFE0E0E0))

                // Time Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Date",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF424242)
                        )
                        OutlinedTextField(
                            value = inputState.currentDate,
                            onValueChange = { viewModel.updateDate(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("DD/MM/YYYY", fontSize = 14.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00897B),
                                unfocusedBorderColor = Color(0xFFCCCCCC)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Time",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF424242)
                        )
                        OutlinedTextField(
                            value = inputState.currentTime,
                            onValueChange = { viewModel.updateTime(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("HH:MM", fontSize = 14.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00897B),
                                unfocusedBorderColor = Color(0xFFCCCCCC)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }

                // Notes Input
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Notes / Tags",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF424242)
                    )
                    OutlinedTextField(
                        value = inputState.notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        placeholder = { Text("Add notes or tags...", fontSize = 14.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00897B),
                            unfocusedBorderColor = Color(0xFFCCCCCC)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 4
                    )
                }
            }
        }

        // Log Entry Button
        Button(
            onClick = { viewModel.logEntry() },
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
                text = "Log Entry",
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
    onDismiss: () -> Unit,
    onLogAndSave: () -> Unit,
    onGoHome: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Suggested Dose",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF2E7D32)
                )
            }
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
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
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "Units",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                Text(
                    text = "Please verify the dose before administering.",
                    fontSize = 14.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Log & Save Button
                Button(
                    onClick = onLogAndSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Log & Save",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Go Home Button
                OutlinedButton(
                    onClick = onGoHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF00897B)
                    ),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00897B)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Go Home",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}
