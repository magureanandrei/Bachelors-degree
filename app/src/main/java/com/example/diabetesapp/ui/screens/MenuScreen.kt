package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
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
import com.example.diabetesapp.ui.components.ActivityTutorialModal
import com.example.diabetesapp.ui.components.CgmTutorialModal
import com.example.diabetesapp.ui.components.MenuOptionCard
import com.example.diabetesapp.viewmodel.ActivityStatus
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
    val viewModel: MenuViewModel = viewModel(factory = MenuViewModelFactory(settingsRepository, context))
    val cgmStatus by viewModel.cgmStatus.collectAsState()
    val activityStatus by viewModel.activityStatus.collectAsState()

    var showCgmModal by remember { mutableStateOf(false) }
    var showActivityModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

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

        // Disclaimer card
        Surface(
            color = Color(0xFFE0F2F1),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF00897B),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Educational Prototype: This app is for research purposes only.",
                    fontSize = 11.sp,
                    color = Color(0xFF00695C),
                    lineHeight = 14.sp
                )
            }
        }

        // Section 1: Settings
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Settings",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF757575),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            )
            MenuOptionCard(
                title = "Therapy Profile",
                subtitle = "Pump vs. MDI, CGM vs. Manual",
                icon = Icons.Default.MedicalServices,
                onClick = onNavigateToTherapyProfile
            )
            MenuOptionCard(
                title = "Insulin Parameters",
                subtitle = "ICR, ISF, Target BG",
                icon = Icons.Default.Settings,
                onClick = onNavigateToBolusSettings
            )
        }

        // Section 2: Integrations
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Integrations",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF757575),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
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
                                    radius = 7.dp.toPx() * pulseScale
                                )
                            }
                        }
                        Canvas(modifier = Modifier.size(14.dp)) {
                            drawCircle(color = cgmStatusColor(cgmStatus))
                        }
                    }
                }
            )
            MenuOptionCard(
                title = "Activity & Sports",
                subtitle = if (activityStatus is ActivityStatus.Connected) "Health Connect connected" else "Health Connect not connected",
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                onClick = { showActivityModal = true },
                trailingContent = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                        if (activityStatus is ActivityStatus.Connected) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = Color(0xFF4CAF50).copy(alpha = pulseAlpha),
                                    radius = 7.dp.toPx() * pulseScale
                                )
                            }
                        }
                        Canvas(modifier = Modifier.size(14.dp)) {
                            drawCircle(
                                color = if (activityStatus is ActivityStatus.Connected) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    }
                }
            )
        }
    }

    if (showCgmModal) {
        CgmTutorialModal(
            cgmStatus = cgmStatus,
            onDismiss = { showCgmModal = false }
        )
    }

    if (showActivityModal) {
        ActivityTutorialModal(
            activityStatus = activityStatus,
            onDismiss = { showActivityModal = false }
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