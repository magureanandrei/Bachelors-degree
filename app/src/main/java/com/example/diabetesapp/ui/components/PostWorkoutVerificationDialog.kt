package com.example.diabetesapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.data.models.BolusLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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