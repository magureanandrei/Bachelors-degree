package com.example.diabetesapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.data.models.TherapyType
import com.example.diabetesapp.utils.IobResult
import java.util.Locale

@Composable
fun IobWidget(
    iobResult: IobResult?,
    therapyType: TherapyType,
    modifier: Modifier = Modifier
) {
    val iob = iobResult?.totalIob ?: 0.0
    val hasWarning = iobResult?.hasManualOnTopOfPump == true
    val isEstimated = iobResult?.isEstimated == true
    val minutesStale = iobResult?.minutesSinceLastReading ?: 0

    val iobColor = when {
        hasWarning -> Color(0xFFE65100)
        isEstimated && minutesStale > 30 -> Color(0xFFE65100)
        isEstimated -> Color(0xFFF9A825)
        therapyType == TherapyType.PUMP_STANDARD -> Color(0xFF1976D2).copy(alpha = 0.7f)
        else -> Color(0xFF1976D2)
    }

    // Status tag varies by therapy type
    val statusText = when {
        // AID pump states
        hasWarning -> "⚠ manual dose added"
        isEstimated && minutesStale > 30 -> "⚠ may be inaccurate"
        isEstimated -> "~ estimated (${minutesStale}m)"
        therapyType == TherapyType.PUMP_AID && iobResult?.fromPump != null -> "from pump"

        // Standard pump — bolus only, basal missing
        therapyType == TherapyType.PUMP_STANDARD -> "bolus only"

        // MDI — bolus tracked, basal is separate long-acting
        therapyType == TherapyType.MDI -> "bolus only"

        else -> ""
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "IOB",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    String.format(Locale.US, "%.2f", iob),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = iobColor
                )
                Text(
                    " U",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            Text(
                statusText,
                fontSize = 10.sp,
                color = iobColor.copy(alpha = 0.85f),
                fontWeight = if (hasWarning || (isEstimated && minutesStale > 30))
                    FontWeight.Bold else FontWeight.Normal
            )
        }

        // AID: manual on top of pump warning badge
        if (hasWarning) {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Pump IOB may be inaccurate",
                    fontSize = 10.sp,
                    color = Color(0xFFE65100),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // MDI: basal awareness note
        if (therapyType == TherapyType.MDI) {
            Text(
                text = "Long-acting basal not tracked",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Standard pump: basal missing note
        if (therapyType == TherapyType.PUMP_STANDARD) {
            Text(
                text = "Basal contribution not yet included",
                fontSize = 10.sp,
                color = Color(0xFF1976D2).copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
