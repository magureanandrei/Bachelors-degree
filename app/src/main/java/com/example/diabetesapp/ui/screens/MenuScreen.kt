package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.ui.components.CgmTutorialModal
import com.example.diabetesapp.ui.components.MenuOptionCard
import com.example.diabetesapp.viewmodel.CgmStatus
import com.example.diabetesapp.viewmodel.MenuViewModel
import com.example.diabetesapp.viewmodel.MenuViewModelFactory

@Composable
fun MenuScreen(
    modifier: Modifier = Modifier,
    onNavigateToBolusSettings: () -> Unit = {},
    onNavigateToTherapyProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepository = remember { BolusSettingsRepository.getInstance(context) }
    val viewModel: MenuViewModel = viewModel(factory = MenuViewModelFactory(settingsRepository))
    val cgmStatus by viewModel.cgmStatus.collectAsState()

    var showCgmModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

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

        // 1. Therapy & Hardware Profile
        MenuOptionCard(
            title = "Therapy Profile",
            subtitle = "Pump vs. MDI, CGM vs. Manual",
            icon = Icons.Default.MedicalServices,
            onClick = onNavigateToTherapyProfile
        )

        // 2. Bolus Settings
        MenuOptionCard(
            title = "Insulin Parameters",
            subtitle = "ICR, ISF, Target BG",
            icon = Icons.Default.Settings,
            onClick = onNavigateToBolusSettings
        )

        // 3. CGM Integration — live status dot + tutorial modal
        val infiniteTransition = rememberInfiniteTransition(label = "cgmPulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Restart
            ),
            label = "cgmPulseScale"
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Restart
            ),
            label = "cgmPulseAlpha"
        )

        MenuOptionCard(
            title = "CGM Integration",
            subtitle = cgmStatusSubtitle(cgmStatus),
            icon = Icons.Default.Sensors,
            onClick = { showCgmModal = true },
            trailingContent = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                    if (cgmStatus !is CgmStatus.Disabled) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = cgmStatusColor(cgmStatus).copy(alpha = pulseAlpha),
                                radius = 7.dp.toPx() * pulseScale // core radius = 7dp, scale it out
                            )
                        }
                    }
                    Canvas(modifier = Modifier.size(14.dp)) {
                        drawCircle(color = cgmStatusColor(cgmStatus))
                    }
                }
            }
        )

        // 4. Wearables placeholder
        MenuOptionCard(
            title = "Wearables",
            subtitle = "Smartwatch sync (Coming Soon)",
            icon = Icons.Default.Watch,
            onClick = { }
        )
    }

    if (showCgmModal) {
        CgmTutorialModal(
            cgmStatus = cgmStatus,
            onDismiss = { showCgmModal = false }
        )
    }
}

private fun cgmStatusColor(status: CgmStatus): Color = when (status) {
    is CgmStatus.Connected -> Color(0xFF4CAF50)
    is CgmStatus.Stale -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

private fun cgmStatusSubtitle(status: CgmStatus): String = when (status) {
    is CgmStatus.Connected -> "Connected · ${status.minutesAgo} min ago"
    is CgmStatus.Stale -> "Stale — last reading ${status.minutesAgo} min ago"
    is CgmStatus.NoSignal -> if (status.minutesAgo != null)
        "No signal — last reading ${status.minutesAgo} min ago"
    else
        "No signal — no reading received yet"
    is CgmStatus.Unreachable -> "No signal — xDrip+ server not reachable"
    is CgmStatus.Disabled -> "Manual glucose source"
}