package com.example.diabetesapp.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.R
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.ui.components.ActivityTutorialModal
import com.example.diabetesapp.ui.components.CgmTutorialModal
import com.example.diabetesapp.ui.components.MenuOptionCard
import com.example.diabetesapp.ui.components.OnboardingProgressDots
import com.example.diabetesapp.viewmodel.ActivityStatus
import com.example.diabetesapp.viewmodel.CgmStatus
import com.example.diabetesapp.viewmodel.MenuViewModel
import com.example.diabetesapp.viewmodel.MenuViewModelFactory

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(1) }

    when (currentStep) {
        1 -> OnboardingWelcomeStep(onNext = { currentStep = 2 })
        2 -> OnboardingTherapyStep(onNext = { currentStep = 3 })
        3 -> OnboardingInsulinStep(onNext = { currentStep = 4 })
        4 -> OnboardingIntegrationsStep(onComplete = onComplete)
    }
}

// ──────────────────────────────────────────────────────────
// Step 1 — Welcome
// ──────────────────────────────────────────────────────────

@Composable
private fun OnboardingWelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .statusBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color(0xFF00897B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "App logo",
                modifier = Modifier.size(96.dp).clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome to DiabetesApp",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Let's get you set up. This will only take a few minutes.",
            fontSize = 16.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "We'll ask about your therapy type and insulin settings so the app can give you accurate, personalised suggestions.",
            fontSize = 13.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Get Started →", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ──────────────────────────────────────────────────────────
// Step 2 — Therapy Profile  (dots: ●○○)
// ──────────────────────────────────────────────────────────

@Composable
private fun OnboardingTherapyStep(onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        OnboardingStepHeader(title = "Therapy Profile", filledDots = 1)
        Box(modifier = Modifier.weight(1f)) {
            TherapyProfileScreen(
                showTopBar = false,
                onNavigateBack = onNext
            )
        }
    }
}

// ──────────────────────────────────────────────────────────
// Step 3 — Insulin Parameters  (dots: ●●○)
// ──────────────────────────────────────────────────────────

@Composable
private fun OnboardingInsulinStep(onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        OnboardingStepHeader(title = "Insulin Parameters", filledDots = 2)
        Box(modifier = Modifier.weight(1f)) {
            BolusSettingsScreen(
                showTopBar = false,
                onNavigateBack = onNext
            )
        }
    }
}

// ──────────────────────────────────────────────────────────
// Step 4 — Connect Integrations  (dots: ●●●)
// ──────────────────────────────────────────────────────────

@Composable
private fun OnboardingIntegrationsStep(onComplete: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { BolusSettingsRepository.getInstance(context) }
    val viewModel: MenuViewModel = viewModel(
        factory = MenuViewModelFactory(repository, context)
    )
    val cgmStatus by viewModel.cgmStatus.collectAsState()
    val activityStatus by viewModel.activityStatus.collectAsState()

    var showCgmModal by remember { mutableStateOf(false) }
    var showActivityModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Pulse animation — same as MenuScreen
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "pulseAlpha"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        OnboardingStepHeader(title = "Connect Integrations", filledDots = 3)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "These are optional but unlock the full power of the app.",
                fontSize = 14.sp,
                color = Color.Gray,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // CGM card — identical to MenuScreen
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

            // Activity card — identical to MenuScreen
            MenuOptionCard(
                title = "Activity & Sports",
                subtitle = if (activityStatus is ActivityStatus.Connected)
                    "Health Connect connected" else "Health Connect not connected",
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
                                color = if (activityStatus is ActivityStatus.Connected)
                                    Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    }
                }
            )
        }

        Surface(
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        repository.setOnboardingComplete()
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Get Started", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You can set these up any time from the Menu tab.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showCgmModal) {
        CgmTutorialModal(cgmStatus = cgmStatus, onDismiss = { showCgmModal = false })
    }
    if (showActivityModal) {
        ActivityTutorialModal(activityStatus = activityStatus, onDismiss = { showActivityModal = false })
    }
}

// ──────────────────────────────────────────────────────────
// Shared sub-composables
// ──────────────────────────────────────────────────────────

@Composable
private fun OnboardingStepHeader(title: String, filledDots: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            OnboardingProgressDots(filledDots = filledDots)
        }
    }
}

// ──────────────────────────────────────────────────────────
// Status helpers (mirrored from MenuScreen)
// ──────────────────────────────────────────────────────────

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