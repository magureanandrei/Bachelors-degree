package com.example.diabetesapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DoseBreakdownCard(
    standardDose: Double,
    suggestedDose: Double,
    rationale: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)), // Light, calming green
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = "Algorithm Insights",
                    tint = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Algorithm Rationale",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Show the mathematical difference if modifiers were applied
            if (standardDose != suggestedDose) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Standard Math:", fontSize = 13.sp, color = Color.Gray)
                    Text("${standardDose}U", fontSize = 13.sp, color = Color.Gray, textDecoration = TextDecoration.LineThrough)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Adjusted Dose:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${suggestedDose}U", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2E7D32))
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Display the detailed log from the AlgorithmEngine
            Text(
                text = rationale.ifEmpty { "Standard calculation applied. No active modifiers." },
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color.DarkGray
            )
        }
    }

}
@Composable
fun SettingsChangeDivider(timestamp: Long, description: String) {
    val timeStr = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFFF9800).copy(alpha = 0.4f)
        )
        Text(
            text = "  $timeStr · Settings Changed  ",
            fontSize = 9.sp,
            color = Color(0xFFFF9800).copy(alpha = 0.8f)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFFF9800).copy(alpha = 0.4f)
        )
    }
}

