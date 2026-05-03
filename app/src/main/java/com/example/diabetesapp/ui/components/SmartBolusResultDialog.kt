package com.example.diabetesapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.algorithm.BreakdownEntry
import com.example.diabetesapp.data.models.BolusSettings
import kotlin.math.roundToInt

@Composable
fun SmartBolusResultDialog(
    standardDose: Double,
    calculatedDose: Double,
    userAdjustedDose: Double?,
    isSportModeActive: Boolean,
    minutesUntilSport: Float,
    sportLog: String,
    breakdownSteps: List<BreakdownEntry> = emptyList(),
    isAid: Boolean = false,
    enteredCarbs: Double = 0.0,
    currentBG: Double = 0.0,
    settings: BolusSettings = BolusSettings(),
    rescueCarbs: Int = 0,
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
                text = when {
                    isAid && enteredCarbs > 0 -> "Pump Entry Advisor"
                    isAid -> "SmartGuard Status"
                    isPlanned -> "Pre-Workout Strategy"
                    isSportModeActive -> "Sport Adjusted Dose"
                    else -> "Standard Dose"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = when {
                    isAid -> Color(0xFF00695C)
                    isPlanned -> Color(0xFFFF9800)
                    isSportModeActive -> Color(0xFF00695C)
                    else -> Color(0xFF2E7D32)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isAid) {
                    AidResultContent(
                        standardDose = standardDose,
                        calculatedDose = calculatedDose,
                        enteredCarbs = enteredCarbs,
                        currentBG = currentBG,
                        settings = settings,
                        rescueCarbs = rescueCarbs,
                        sportLog = sportLog,
                        breakdownSteps = breakdownSteps
                    )
                } else {
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
                                IconButton(
                                    onClick = { onAdjustDose(-0.1) },
                                    modifier = Modifier.background(Color.White, RoundedCornerShape(8.dp))
                                ) {
                                    Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "%.1f".format(java.util.Locale.US, displayDose),
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPlanned) Color(0xFFFF9800) else Color(0xFF2E7D32)
                                    )
                                    Text("Units", fontSize = 16.sp, color = if (isPlanned) Color(0xFFFF9800) else Color(0xFF2E7D32))
                                }

                                IconButton(
                                    onClick = { onAdjustDose(0.1) },
                                    modifier = Modifier.background(Color.White, RoundedCornerShape(8.dp))
                                ) {
                                    Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }

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
                        rationale = sportLog,
                        breakdownSteps = breakdownSteps,
                        isAid = false
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onLogAndSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isAid -> Color(0xFF00695C)
                        isPlanned -> Color(0xFFFF9800)
                        else -> Color(0xFF2E7D32)
                    }
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = when {
                        isAid -> "Save to Log"
                        isPlanned -> "Save Planned Workout"
                        else -> "Log & Administer"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
private fun AidResultContent(
    standardDose: Double,
    calculatedDose: Double,
    enteredCarbs: Double,
    currentBG: Double,
    settings: BolusSettings,
    rescueCarbs: Int,
    sportLog: String,
    breakdownSteps: List<BreakdownEntry>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (enteredCarbs > 0) {
            val originalMealDose = enteredCarbs / settings.getCurrentIcr()
            val carbRatio = if (originalMealDose > 0) calculatedDose / originalMealDose else 1.0
            val adjustedCarbs = (enteredCarbs * carbRatio.coerceIn(0.0, 1.0)).roundToInt()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Enter into pump:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF004D40))
                    Text(
                        "${adjustedCarbs}g carbs",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00695C)
                    )
                    if (adjustedCarbs < enteredCarbs.toInt()) {
                        Text(
                            "instead of ${enteredCarbs.toInt()}g — reduced for exercise/recovery",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Your pump will calculate the appropriate insulin dose.",
                        fontSize = 12.sp,
                        color = Color(0xFF546E7A)
                    )
                }
            }
        } else {
            when {
                currentBG > 300 -> {
                    val isf = settings.getIsfForHour(java.time.LocalTime.now().hour)
                    val penCorrection = ((currentBG - settings.targetBG) / isf * 0.5).coerceAtLeast(0.0)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("BG is critically high", fontWeight = FontWeight.Bold, color = Color(0xFFE53935), fontSize = 16.sp)
                            Text("Check for ketones immediately.", fontSize = 14.sp, color = Color.DarkGray)
                            Text("If infusion set issue suspected:", fontSize = 13.sp, color = Color.Gray)
                            Text(
                                "Pen correction: ${String.format(java.util.Locale.US, "%.1f", penCorrection)}U (50% conservative)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFFE53935)
                            )
                        }
                    }
                }
                currentBG > 250 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("BG is elevated", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE65100))
                            Text(
                                "Your pump's SmartGuard is auto-correcting. Monitor closely. Check infusion set and sensor connection if BG remains high.",
                                fontSize = 13.sp,
                                color = Color(0xFF546E7A),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                else -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "No manual action needed. Your pump is managing corrections automatically.",
                            fontSize = 14.sp,
                            color = Color(0xFF00695C),
                            modifier = Modifier.padding(16.dp),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        if (rescueCarbs > 0) {
            HorizontalDivider(color = Color(0xFF80CBC4).copy(alpha = 0.3f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🍬", fontSize = 20.sp)
                Column {
                    Text(
                        "${rescueCarbs}g fast-acting carbs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        "WITHOUT entering into pump (free carbs)",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        if (breakdownSteps.isNotEmpty() || sportLog.isNotBlank()) {
            DoseBreakdownCard(
                standardDose = standardDose,
                suggestedDose = calculatedDose,
                rationale = sportLog,
                breakdownSteps = breakdownSteps,
                isAid = true
            )
        }
    }
}
