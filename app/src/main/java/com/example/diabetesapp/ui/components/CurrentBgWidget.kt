package com.example.diabetesapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.utils.CgmReading

@Composable
fun CurrentBgWidget(
    latestReading: com.example.diabetesapp.utils.CgmReading?,
    isCgmEnabled: Boolean,
    hypoLimit: Float,
    hyperLimit: Float,
    modifier: Modifier = Modifier
) {
    val bgValue = latestReading?.bgValue ?: 0
    val diffMs = latestReading?.let { System.currentTimeMillis() - it.timestamp } ?: 0L
    val minutesAgo = (diffMs / (60 * 1000L)).toInt()

    // Threshold for "Stale" data (> 20 mins)
    val isStale = minutesAgo > 20 || latestReading == null

    val timeLabel = when {
        latestReading == null -> "--m ago"
        minutesAgo < 1 -> "now"
        else -> "${minutesAgo}m ago"
    }

    val targetColor = Color(0xFF00897B) // Your Teal
    val widgetColor = when {
        bgValue == 0 -> Color.LightGray
        bgValue < hypoLimit -> Color(0xFFE53935)
        bgValue > hyperLimit -> Color(0xFFFBC02D)
        else -> targetColor
    }

    // Fading only for the BG-related elements
    val bgAlpha = if (isStale) 0.4f else 1.0f

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // --- BG VALUE COLUMN ---
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (bgValue > 0) bgValue.toString() else "--",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = widgetColor.copy(alpha = bgAlpha), // FADED
                    lineHeight = 44.sp
                )

                Text(
                    text = " mg/dL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = widgetColor.copy(alpha = bgAlpha * 0.6f), // FADED
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                )
            }

            // Time label stays bright to explain the fading
            Text(
                text = timeLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = targetColor,
                modifier = Modifier.padding(start = 2.dp, top = 0.dp)
            )
        }

        // --- TREND ARROW ---
        if (isCgmEnabled && !latestReading?.trendString.isNullOrEmpty()) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = latestReading!!.trendString,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = widgetColor.copy(alpha = bgAlpha), // FADED
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // --- IOB (STAYS FULL BRIGHTNESS) ---
        if (isCgmEnabled && latestReading?.iob != null) {
            Spacer(modifier = Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("IOB", fontSize = 10.sp, color = Color.Gray)
                Text(
                    text = "${latestReading.iob}U",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2) // FULL OPACITY
                )
            }
        }
    }
}