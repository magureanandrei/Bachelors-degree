package com.example.diabetesapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.data.models.BasalInsulinType
import com.example.diabetesapp.data.models.InsulinType
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.ui.components.DurationPickerSheet
import com.example.diabetesapp.ui.components.ExpandableSettingsCard
import com.example.diabetesapp.utils.FormatUtils
import com.example.diabetesapp.viewmodel.BolusSettingsViewModel
import com.example.diabetesapp.viewmodel.BolusSettingsViewModelFactory
import com.example.diabetesapp.viewmodel.ExpandableCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.diabetesapp.ui.components.HourlyRateEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BolusSettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BolusSettingsRepository.getInstance(context) }
    val viewModel: BolusSettingsViewModel = viewModel(
        factory = BolusSettingsViewModelFactory(repository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val draftSettings = uiState.draftSettings // Editable: working copy

    // Coroutine scope for navigation delay
    val coroutineScope = rememberCoroutineScope()

    // State for duration picker dialog
    var showDurationPicker by remember { mutableStateOf(false) }
    var showBasalDurationPicker by remember { mutableStateOf(false) }

    // Snackbar for validation messages
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when there's a save message
    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // --- STICKY FOOTER WITH SAMSUNG FIX ---
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding() // Safely pushes above system controls
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.saveSettings()
                            if (viewModel.areAllFieldsValid()) {
                                coroutineScope.launch {
                                    delay(1500) // 1.5 seconds to show success message
                                    onNavigateBack()
                                }
                            }
                        },
                        enabled = viewModel.areAllFieldsValid(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00897B),
                            disabledContainerColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Save Settings",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewModel.areAllFieldsValid()) Color.White else Color.LightGray
                        )
                    }
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues) // Prevents the content from hiding behind the sticky footer
        ) {
            // --- TOP BAR ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF00897B)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bolus Calculator Settings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            // --- SCROLLABLE CONTENT AREA ---
            Column(
                modifier = Modifier
                    .weight(1f) // Fills the remaining space between top bar and bottom bar
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: General Configuration
                val generalValueText = if (!viewModel.isCardExpanded(ExpandableCard.GENERAL)) {
                    "${draftSettings.insulinType.displayName}, ${FormatUtils.formatDurationDisplay(draftSettings.durationOfAction)}"
                } else null

                ExpandableSettingsCard(
                    title = "General",
                    valueText = generalValueText,
                    isExpanded = viewModel.isCardExpanded(ExpandableCard.GENERAL),
                    onToggleExpand = { viewModel.toggleCardExpansion(ExpandableCard.GENERAL) }
                ) {
                    // Insulin Type Dropdown
                    var insulinTypeExpanded by remember { mutableStateOf(false) }

                    Column {
                        Text(
                            text = "Insulin Type",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = insulinTypeExpanded,
                            onExpandedChange = { insulinTypeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = draftSettings.insulinType.displayName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = insulinTypeExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00897B),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = insulinTypeExpanded,
                                onDismissRequest = { insulinTypeExpanded = false }
                            ) {
                                InsulinType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(type.displayName, fontSize = 14.sp)
                                                if (type.hint.isNotEmpty()) {
                                                    Text(type.hint, fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateInsulinType(type)
                                            insulinTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Duration of Action - Wheel Picker (Bottom Sheet)
                    Column {
                        val displayValue = if (draftSettings.durationOfAction.isNotEmpty()) {
                            FormatUtils.formatDurationDisplay(draftSettings.durationOfAction)
                        } else {
                            ""
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDurationPicker = true }
                        ) {
                            OutlinedTextField(
                                value = displayValue,
                                onValueChange = { /* Read-only */ },
                                readOnly = true,
                                label = { Text("Duration of Action") },
                                placeholder = { Text("Tap to select") },
                                trailingIcon = {
                                    IconButton(onClick = { showDurationPicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = "Pick duration",
                                            tint = Color(0xFF00897B)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00897B),
                                    focusedLabelColor = Color(0xFF00897B),
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                    errorBorderColor = Color.Red,
                                    disabledTextColor = Color.Black,
                                    disabledBorderColor = Color(0xFFE0E0E0),
                                    disabledLabelColor = Color.Gray
                                ),
                                supportingText = {
                                    if (uiState.durationError != null) {
                                        Text(
                                            text = uiState.durationError!!,
                                            color = Color.Red,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Text(
                                            text = "How long your insulin stays active",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                },
                                isError = uiState.durationError != null
                            )
                        }
                    }

                    // Duration Picker Bottom Sheet
                    if (showDurationPicker) {
                        DurationPickerSheet(
                            initialValue = draftSettings.durationOfAction.toDoubleOrNull() ?: 4.0,
                            onDismiss = { showDurationPicker = false },
                            onConfirm = { selectedDuration ->
                                val formattedValue = FormatUtils.formatDoubleForUi(selectedDuration)
                                viewModel.updateDurationOfAction(formattedValue)
                                showDurationPicker = false
                            }
                        )
                    }

                    if (uiState.persistedSettings.isMdi) {
                        // Basal insulin type dropdown
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        var typeExpanded by remember { mutableStateOf(false) }
                        Text("Basal Insulin Type", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                            OutlinedTextField(
                                value = draftSettings.basalInsulinType.displayName, // Fixed here
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00897B),
                                    unfocusedBorderColor = Color(0xFFE0E0E0)
                                )
                            )
                            ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                                BasalInsulinType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(type.displayName, fontSize = 14.sp)
                                                if (type.hint.isNotEmpty()) {
                                                    Text(type.hint, fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.updateBasalInsulinType(type) // Fixed here
                                            typeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Duration field
                        Text("Duration of Action", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBasalDurationPicker = true }
                        ) {
                            OutlinedTextField(
                                value = if (draftSettings.basalDurationHours.isNotEmpty()) FormatUtils.formatDurationDisplay(draftSettings.basalDurationHours) else "",
                                onValueChange = { /* Read Only */ },
                                readOnly = true,
                                placeholder = { Text("Tap to select") },
                                trailingIcon = {
                                    IconButton(onClick = { showBasalDurationPicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = "Pick duration",
                                            tint = Color(0xFF00897B)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00897B),
                                    unfocusedBorderColor = Color(0xFFE0E0E0),
                                    errorBorderColor = Color.Red,
                                    disabledTextColor = Color.Black,
                                    disabledBorderColor = Color(0xFFE0E0E0),
                                    disabledLabelColor = Color.Gray
                                ),
                                supportingText = {
                                    if (uiState.basalDurationError != null) {
                                        Text(uiState.basalDurationError!!, color = Color.Red, fontSize = 12.sp)
                                    } else {
                                        val hint = "How many hours your long lasting insulin stays active"
                                        Text(hint, fontSize = 10.sp, color = Color.Gray)
                                    }
                                },
                                isError = uiState.basalDurationError != null
                            )
                        }

                        if (showBasalDurationPicker) {
                            DurationPickerSheet(
                                initialValue = draftSettings.basalDurationHours.toDoubleOrNull() ?: draftSettings.basalInsulinType.typicalDurationHours.toDouble(),
                                onDismiss = { showBasalDurationPicker = false },
                                onConfirm = { selectedDuration ->
                                    val formattedValue = FormatUtils.formatDoubleForUi(selectedDuration)
                                    viewModel.updateBasalDurationHours(formattedValue)
                                    showBasalDurationPicker = false
                                },
                                title = "Duration of Long-Acting Insulin",
                                isBasal = true
                            )
                        }

                        // Unconfigured nudge
                        val notConfigured = draftSettings.basalInsulinType == BasalInsulinType.NONE || draftSettings.basalDurationHours.isEmpty()
                        if (notConfigured) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "⚠ Configure your basal insulin for better algorithm accuracy.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFE65100),
                                    modifier = Modifier.padding(10.dp),
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                // Card 2: Insulin-to-Carb Ratio (ICR) - Simple/Advanced Mode
                val icrSummary = if (!uiState.icrTimeDependent) {
                    "1:${draftSettings.icrValues[0]}"
                } else {
                    val values = draftSettings.icrValues.mapNotNull { it.toIntOrNull() }.filter { it > 0 }
                    if (values.distinct().size == 1) {
                        "1:${values.first()}"
                    } else if (values.isNotEmpty()) {
                        "1:${values.minOrNull()}-${values.maxOrNull()}"
                    } else {
                        "Not Set"
                    }
                }

                ExpandableSettingsCard(
                    title = "Insulin to Carb Ratio",
                    valueText = if (!viewModel.isCardExpanded(ExpandableCard.ICR)) icrSummary else null,
                    isExpanded = viewModel.isCardExpanded(ExpandableCard.ICR),
                    onToggleExpand = { viewModel.toggleCardExpansion(ExpandableCard.ICR) }
                ) {
                    HourlyRateEditor(
                        label = "ICR",
                        unit = "g/U",
                        isTimeDependent = uiState.icrTimeDependent,
                        onToggleTimeDependent = { viewModel.toggleIcrTimeDependent(it) },
                        globalValue = draftSettings.icrValues.getOrElse(0) { "10" },
                        onGlobalValueChange = { viewModel.updateGlobalIcr(it) },
                        hourlyValues = draftSettings.icrValues,
                        onHourValueChange = { hour, value -> viewModel.updateIcrAtHour(hour, value) },
                        hourlyErrors = uiState.icrErrors,
                        globalError = uiState.icrGlobalError
                    )
                    Text(
                        text = "How many grams of carbs 1 unit of insulin covers.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // Card 3: Sensitivity Factor (ISF) - Simple/Advanced Mode
                val isfSummary = if (!uiState.isfTimeDependent) {
                    "1:${draftSettings.isfValues[0]}"
                } else {
                    val values = draftSettings.isfValues.mapNotNull { it.toIntOrNull() }.filter { it > 0 }
                    if (values.distinct().size == 1) {
                        "1:${values.first()}"
                    } else if (values.isNotEmpty()) {
                        "1:${values.minOrNull()}-${values.maxOrNull()}"
                    } else {
                        "Not Set"
                    }
                }

                ExpandableSettingsCard(
                    title = "Correction Factor",
                    valueText = if (!viewModel.isCardExpanded(ExpandableCard.ISF)) isfSummary else null,
                    isExpanded = viewModel.isCardExpanded(ExpandableCard.ISF),
                    onToggleExpand = { viewModel.toggleCardExpansion(ExpandableCard.ISF) }
                ) {
                    HourlyRateEditor(
                        label = "Correction Factor",
                        unit = "mg/dL per U",
                        isTimeDependent = uiState.isfTimeDependent,
                        onToggleTimeDependent = { viewModel.toggleIsfTimeDependent(it) },
                        globalValue = draftSettings.isfValues.getOrElse(0) { "50" },
                        onGlobalValueChange = { viewModel.updateGlobalIsf(it) },
                        hourlyValues = draftSettings.isfValues,
                        onHourValueChange = { hour, value -> viewModel.updateIsfAtHour(hour, value) },
                        hourlyErrors = uiState.isfErrors,
                        globalError = uiState.isfGlobalError
                    )
                    Text(
                        text = "How much 1 unit of insulin lowers your blood glucose (mg/dL).",
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                }

                // Card 4: Blood Glucose Target
                ExpandableSettingsCard(
                    title = "Target BG",
                    valueText = if (!viewModel.isCardExpanded(ExpandableCard.TARGET_BG)) "${draftSettings.targetBG} mg/dL" else null,
                    isExpanded = viewModel.isCardExpanded(ExpandableCard.TARGET_BG),
                    onToggleExpand = { viewModel.toggleCardExpansion(ExpandableCard.TARGET_BG) }
                ) {
                    OutlinedTextField(
                        value = draftSettings.targetBG,
                        onValueChange = {
                            viewModel.updateTargetBG(it)
                        },
                        label = { Text("Target Value (mg/dL)") },
                        placeholder = { Text("100") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00897B),
                            focusedLabelColor = Color(0xFF00897B),
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            errorBorderColor = Color.Red
                        ),
                        supportingText = {
                            if (uiState.targetBGError != null) {
                                Text(
                                    text = uiState.targetBGError!!,
                                    color = Color.Red,
                                    fontSize = 12.sp
                                )
                            } else {
                                Text(
                                    text = "Your desired blood glucose level. Required for correction formula.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        },
                        isError = uiState.targetBGError != null
                    )
                }

                // Card 5: Safety Limits
                ExpandableSettingsCard(
                    title = "Safety & Graph Limits",
                    isExpanded = viewModel.isCardExpanded(ExpandableCard.SAFETY_LIMITS),
                    onToggleExpand = { viewModel.toggleCardExpansion(ExpandableCard.SAFETY_LIMITS) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Max Bolus
                        OutlinedTextField(
                            value = draftSettings.maxBolus,
                            onValueChange = { viewModel.updateMaxBolus(it) },
                            label = { Text("Maximum Bolus (Units)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00897B))
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Hypo Limit
                            OutlinedTextField(
                                value = draftSettings.hypoLimit,
                                onValueChange = { viewModel.updateHypoLimit(it) },
                                label = { Text("Hypo Alert (mg/dL)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00897B))
                            )
                            // Hyper Limit
                            OutlinedTextField(
                                value = draftSettings.hyperLimit,
                                onValueChange = { viewModel.updateHyperLimit(it) },
                                label = { Text("Hyper Alert (mg/dL)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00897B))
                            )
                        }
                        Text(
                            text = "These limits control your app's warning system and scale your CGM graphs.",
                            fontSize = 12.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

                // Keep a small spacer at the bottom of the list for visual padding when scrolling to the very end
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}