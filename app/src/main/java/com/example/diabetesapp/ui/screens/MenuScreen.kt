package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.ui.components.MenuOptionCard

@Composable
fun MenuScreen(
    modifier: Modifier = Modifier,
    onNavigateToBolusSettings: () -> Unit = {},
    onNavigateToTherapyProfile: () -> Unit = {} // FIXED TYPO HERE
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings & Integrations",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 1. Therapy & Hardware Profile (NEW)
        MenuOptionCard(
            title = "Therapy Profile",
            subtitle = "Pump vs. MDI, CGM vs. Manual",
            icon = Icons.Default.MedicalServices, // Added a nice medical icon
            onClick = onNavigateToTherapyProfile
        )

        // 2. Bolus Settings (Existing)
        MenuOptionCard(
            title = "Insulin Parameters",
            subtitle = "ICR, ISF, Target BG",
            icon = Icons.Default.Settings,
            onClick = onNavigateToBolusSettings
        )

        // 3. Future Placeholder: Dexcom
        MenuOptionCard(
            title = "CGM Integration",
            subtitle = "Connect Dexcom / Nightscout (Coming Soon)",
            icon = Icons.Default.Sensors,
            onClick = { /* TODO in the future */ }
        )

        // 4. Future Placeholder: Smartwatch
        MenuOptionCard(
            title = "Wearables",
            subtitle = "Smartwatch sync (Coming Soon)",
            icon = Icons.Default.Watch,
            onClick = { /* TODO in the future */ }
        )
    }
}
