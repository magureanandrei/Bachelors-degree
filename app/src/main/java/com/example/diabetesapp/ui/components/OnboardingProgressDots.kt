package com.example.diabetesapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingProgressDots(
    filledDots: Int,
    totalDots: Int = 3
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(totalDots) { index ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (index < filledDots) Color(0xFF00897B) else Color(0xFFE0E0E0),
                        shape = CircleShape
                    )
            )
        }
    }
}