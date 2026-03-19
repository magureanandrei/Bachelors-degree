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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: BG number only
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (bgValue > 0) bgValue.toString() else "--",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = widgetColor.copy(alpha = bgAlpha),
                lineHeight = 40.sp
            )
            Text(
                " mg/dL",
                fontSize = 11.sp,
                color = widgetColor.copy(alpha = bgAlpha * 0.6f),
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
            if (isCgmEnabled && !latestReading?.trendString.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = latestReading!!.trendString,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = widgetColor.copy(alpha = bgAlpha)
                )
            }
        }

        // Right: time label
        Text(
            text = timeLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isStale) Color(0xFFE65100) else Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}