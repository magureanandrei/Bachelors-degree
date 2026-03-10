package com.example.diabetesapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun ContextFactorSection(
    selectedFactor: String?,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onFactorSelected: (String) -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00897B))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Outside Factors", fontWeight = FontWeight.Bold)

                // Info Icon
                IconButton(
                    onClick = { showInfo = !showInfo },
                    modifier = Modifier.size(24.dp).padding(start = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }

        if (showInfo) {
            FactorInfoCard()
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp)) // Reduced spacing since info might not be there

            // 2x2 Grid Layout
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // Row 1: None & Stress
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FactorOption(
                        label = "None",
                        isSelected = selectedFactor == "None" || selectedFactor == null,
                        onClick = { onFactorSelected("None") },
                        modifier = Modifier.weight(1f)
                    )
                    FactorOption(
                        label = "Stress",
                        isSelected = selectedFactor == "Stress",
                        onClick = { onFactorSelected("Stress") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Illness & Heat
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FactorOption(
                        label = "Illness",
                        isSelected = selectedFactor == "Illness",
                        onClick = { onFactorSelected("Illness") },
                        modifier = Modifier.weight(1f)
                    )
                    FactorOption(
                        label = "Heat",
                        isSelected = selectedFactor == "Heat",
                        onClick = { onFactorSelected("Heat") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun FactorOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Color(0xFFE0F2F1) else Color.Transparent
    val borderColor = if (isSelected) Color(0xFF00695C) else Color(0xFFE0E0E0)
    val contentColor = if (isSelected) Color(0xFF00695C) else Color.Gray
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = modifier
            .height(48.dp)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontWeight = fontWeight,
            fontSize = 14.sp
        )
    }
}

@Composable
fun FactorInfoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "How Factors Affect You",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFFE65100)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Stress & Illness typically raise blood sugar (requires more insulin). Heat can lower blood sugar (requires less insulin).",
                fontSize = 12.sp,
                color = Color(0xFFEF6C00),
                lineHeight = 16.sp
            )
        }
    }
}
