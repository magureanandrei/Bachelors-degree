package com.example.diabetesapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SmartBolusResultDialog(
    standardDose: Double,
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

                DoseBreakdownCard(
                    standardDose = standardDose,
                    suggestedDose = calculatedDose,
                    rationale = sportLog
                )
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