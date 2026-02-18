package com.example.diabetesapp.ui.components

import android.view.ContextThemeWrapper
import android.widget.NumberPicker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Duration Picker Bottom Sheet
 * Uses NumberPicker wheels to select hours and minutes (15-min increments)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationPickerSheet(
    initialValue: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    // Calculate initial hours and minutes
    val initialHours = initialValue.toInt().coerceIn(2, 8)
    val initialMinutesDecimal = initialValue - initialHours
    val initialMinutesIndex = when {
        initialMinutesDecimal < 0.125 -> 0  // 0 min
        initialMinutesDecimal < 0.375 -> 1  // 15 min
        initialMinutesDecimal < 0.625 -> 2  // 30 min
        else -> 3                            // 45 min
    }.coerceIn(0, 3)

    var selectedHours by remember { mutableStateOf(initialHours) }
    var selectedMinutesIndex by remember { mutableStateOf(initialMinutesIndex) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Duration of Action",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Helper text
            Text(
                text = "How long does your insulin stay active?",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Wheel Pickers Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hours Picker
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AndroidView(
                        factory = { context ->
                            // Apply high-contrast theme for visible text
                            val themedContext = ContextThemeWrapper(context, android.R.style.Theme_Material_Light_Dialog)

                            NumberPicker(themedContext).apply {
                                // Set range
                                minValue = 2
                                maxValue = 8
                                value = initialHours

                                // Disable wrap and keyboard
                                wrapSelectorWheel = false
                                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

                                // Set listener
                                setOnValueChangedListener { _, _, newVal ->
                                    selectedHours = newVal
                                }

                                // Style text for maximum visibility
                                postDelayed({
                                    try {
                                        // Access the selector wheel paint to make center text larger
                                        val paintField = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
                                        paintField.isAccessible = true
                                        val paint = paintField.get(this) as android.graphics.Paint
                                        paint.color = android.graphics.Color.BLACK
                                        paint.textSize = 72f // Larger text for prominence
                                        paint.isFakeBoldText = true // Make it bold
                                    } catch (_: Exception) {}

                                    for (i in 0 until childCount) {
                                        val child = getChildAt(i)
                                        if (child is android.widget.EditText) {
                                            child.setTextColor(android.graphics.Color.BLACK)
                                            child.textSize = 32f // Larger selected text
                                            child.isFocusable = false
                                            child.isCursorVisible = false
                                            child.typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        }
                                    }
                                    invalidate()
                                }, 100)
                            }
                        },
                        update = { picker ->
                            if (picker.value != selectedHours) {
                                picker.value = selectedHours
                            }
                        },
                        modifier = Modifier
                            .width(100.dp)
                            .height(150.dp)
                    )
                    Text(
                        text = "hours",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.width(40.dp))

                // Minutes Picker
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AndroidView(
                        factory = { context ->
                            // Apply high-contrast theme for visible text
                            val themedContext = ContextThemeWrapper(context, android.R.style.Theme_Material_Light_Dialog)

                            NumberPicker(themedContext).apply {
                                // CRITICAL: Set displayedValues BEFORE min/max
                                val minuteValues = arrayOf("00", "15", "30", "45")
                                displayedValues = minuteValues
                                minValue = 0
                                maxValue = 3
                                value = initialMinutesIndex

                                // Disable wrap and keyboard
                                wrapSelectorWheel = false
                                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

                                // Set listener
                                setOnValueChangedListener { _, _, newVal ->
                                    selectedMinutesIndex = newVal
                                }

                                // Style text for maximum visibility
                                postDelayed({
                                    try {
                                        // Access the selector wheel paint to make center text larger
                                        val paintField = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
                                        paintField.isAccessible = true
                                        val paint = paintField.get(this) as android.graphics.Paint
                                        paint.color = android.graphics.Color.BLACK
                                        paint.textSize = 72f // Larger text for prominence
                                        paint.isFakeBoldText = true // Make it bold
                                    } catch (_: Exception) {}

                                    for (i in 0 until childCount) {
                                        val child = getChildAt(i)
                                        if (child is android.widget.EditText) {
                                            child.setTextColor(android.graphics.Color.BLACK)
                                            child.textSize = 32f // Larger selected text
                                            child.isFocusable = false
                                            child.isCursorVisible = false
                                            child.typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        }
                                    }
                                    invalidate()
                                }, 100)
                            }
                        },
                        update = { picker ->
                            if (picker.value != selectedMinutesIndex) {
                                picker.value = selectedMinutesIndex
                            }
                        },
                        modifier = Modifier
                            .width(100.dp)
                            .height(150.dp)
                    )
                    Text(
                        text = "minutes",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Confirm Button
            Button(
                onClick = {
                    // Calculate the final duration
                    val minutesInHours = selectedMinutesIndex * 15 / 60.0
                    val finalDuration = selectedHours + minutesInHours
                    onConfirm(finalDuration)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00897B)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Confirm",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Legacy dialog - kept for compatibility
 * Use DurationPickerSheet instead
 */
@Composable
fun DurationPickerDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    minHours: Double = 1.0,
    maxHours: Double = 8.0
) {
    // Convert to new API
    val initialValue = currentValue.toDoubleOrNull() ?: 4.0
    DurationPickerSheet(
        initialValue = initialValue,
        onDismiss = onDismiss,
        onConfirm = { newValue ->
            onConfirm(newValue.toString())
        }
    )
}

