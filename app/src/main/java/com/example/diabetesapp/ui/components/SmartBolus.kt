package com.example.diabetesapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.ui.components.ContextFactorSection

import com.example.diabetesapp.viewmodel.BolusInputState
import com.example.diabetesapp.viewmodel.CalculateBolusViewModel

@Composable
fun SmartBolus(
    inputState: BolusInputState,
    viewModel: CalculateBolusViewModel
) {
    var showSportInfoDialog by remember { mutableStateOf(false) }

    // --- NEW: Time Picker Setup ---
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.autoFetchLiveBgData()
    }

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