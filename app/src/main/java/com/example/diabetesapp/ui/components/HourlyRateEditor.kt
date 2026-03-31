package com.example.diabetesapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HourlyRateEditor(
    label: String,                    // "ICR" or "ISF"
    unit: String,                     // "g/U" or "mg/dL per U"
    isTimeDependent: Boolean,
    onToggleTimeDependent: (Boolean) -> Unit,
    globalValue: String,
    onGlobalValueChange: (String) -> Unit,
    hourlyValues: List<String>,       // 24 entries
    onHourValueChange: (Int, String) -> Unit,
    hourlyErrors: List<String?>,      // 24 entries, null = no error
    globalError: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Time-dependent $label", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Switch(
                checked = isTimeDependent,
                onCheckedChange = onToggleTimeDependent,
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF00897B))
            )
        }

        AnimatedVisibility(visible = !isTimeDependent) {
            Column {
                Text("$label ($unit)", fontSize = 13.sp, color = Color.Gray)
                OutlinedTextField(
                    value = globalValue,
                    onValueChange = onGlobalValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = globalError != null,
                    supportingText = globalError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00897B),
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = isTimeDependent,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$label by Hour ($unit)", fontSize = 13.sp, color = Color.Gray)
                for (hour in 0..23) {
                    val timeLabel = String.format("%02d:00", hour)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(timeLabel, fontSize = 13.sp, modifier = Modifier.width(52.dp), color = Color.DarkGray)
                        OutlinedTextField(
                            value = hourlyValues[hour],
                            onValueChange = { onHourValueChange(hour, it) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = hourlyErrors[hour] != null,
                            supportingText = hourlyErrors[hour]?.let { { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) } },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00897B),
                                unfocusedBorderColor = Color(0xFFE0E0E0)
                            )
                        )
                    }
                }
            }
        }
    }
}