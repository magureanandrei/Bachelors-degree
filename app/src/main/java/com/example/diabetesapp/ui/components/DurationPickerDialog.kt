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
    onConfirm: (Double) -> Unit,
    title: String = "Duration of Action",
    isBasal: Boolean = false
) {
    // Generate valid options based on insulin type
    val hourOptions = if (isBasal) {
        listOf(12, 20, 24, 36, 42, 48)
    } else {
        (2..8).toList()
    }

    val minuteOptions = if (isBasal) {
        listOf("00")
    } else {
        listOf("00", "15", "30", "45")
    }

    // Calculate initial indices
    val initialHours = initialValue.toInt()
    var initialHourIndex = hourOptions.indexOfFirst { it == initialHours }
    if (initialHourIndex == -1) {
        // Fallback to nearest if exact match not found
        initialHourIndex = hourOptions.indices.minByOrNull { kotlin.math.abs(hourOptions[it] - initialHours) } ?: 0
    }

    val initialMinutesDecimal = initialValue - initialHours
    val initialMinutesIndex = if (isBasal) 0 else when {
        initialMinutesDecimal < 0.125 -> 0  // 0 min
        initialMinutesDecimal < 0.375 -> 1  // 15 min
        initialMinutesDecimal < 0.625 -> 2  // 30 min
        else -> 3                            // 45 min
    }.coerceIn(0, minuteOptions.size - 1)

    var selectedHourIndex by remember { mutableStateOf(initialHourIndex) }
    var selectedMinutesIndex by remember { mutableStateOf(initialMinutesIndex) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Helper to format NumberPicker safely and persistently
    val formatPicker = { picker: NumberPicker ->
        for (i in 0 until picker.childCount) {
            val child = picker.getChildAt(i)
            if (child is android.widget.EditText) {
                child.setTextColor(android.graphics.Color.BLACK)
                child.isFocusable = false
                child.isCursorVisible = false
                // Removed explicit textSize and typeface to match the rest of the wheel
            }
        }

        // Scale the entire picker slightly to make all wheel items appear bigger
        picker.scaleX = 1.25f
        picker.scaleY = 1.25f
    }

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
                text = title,
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
                            val themedContext = ContextThemeWrapper(context, android.R.style.Theme_Material_Light_Dialog)

                            NumberPicker(themedContext).apply {
                                val displayValuesStr = hourOptions.map { it.toString() }.toTypedArray()
                                displayedValues = displayValuesStr
                                minValue = 0
                                maxValue = displayValuesStr.size - 1
                                value = selectedHourIndex

                                wrapSelectorWheel = false
                                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

                                setOnValueChangedListener { _, _, newVal ->
                                    selectedHourIndex = newVal
                                    formatPicker(this)
                                }

                                setOnScrollListener { view, scrollState ->
                                    if (scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
                                        formatPicker(view)
                                    }
                                }

                                postDelayed({ formatPicker(this) }, 100)
                            }
                        },
                        update = { picker ->
                            if (picker.value != selectedHourIndex) {
                                picker.value = selectedHourIndex
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
                            val themedContext = ContextThemeWrapper(context, android.R.style.Theme_Material_Light_Dialog)

                            NumberPicker(themedContext).apply {
                                val minuteValuesArray = minuteOptions.toTypedArray()
                                displayedValues = minuteValuesArray
                                minValue = 0
                                maxValue = minuteValuesArray.size - 1
                                value = selectedMinutesIndex

                                wrapSelectorWheel = false
                                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

                                setOnValueChangedListener { _, _, newVal ->
                                    selectedMinutesIndex = newVal
                                    formatPicker(this)
                                }

                                setOnScrollListener { view, scrollState ->
                                    if (scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
                                        formatPicker(view)
                                    }
                                }

                                postDelayed({ formatPicker(this) }, 100)
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
                    val hourVal = hourOptions[selectedHourIndex]
                    val minuteStr = minuteOptions[selectedMinutesIndex]
                    val minutesInHours = (minuteStr.toDoubleOrNull() ?: 0.0) / 60.0
                    val finalDuration = hourVal + minutesInHours
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
    title: String = "Duration of Action",
    isBasal: Boolean = false
) {
    val initialValue = currentValue.toDoubleOrNull() ?: 4.0
    DurationPickerSheet(
        initialValue = initialValue,
        onDismiss = onDismiss,
        onConfirm = { newValue ->
            onConfirm(newValue.toString())
        },
        title = title,
        isBasal = isBasal
    )
}
