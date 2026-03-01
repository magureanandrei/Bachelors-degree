package com.example.diabetesapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.viewmodel.TherapyProfileViewModel
import com.example.diabetesapp.viewmodel.TherapyProfileViewModelFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TherapyProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { BolusSettingsRepository.getInstance(context) }
    val viewModel: TherapyProfileViewModel = viewModel(factory = TherapyProfileViewModelFactory(repository))
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar("Therapy Profile Saved")
            delay(1000)
            onNavigateBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // --- STICKY FOOTER ---
            Surface(
                color = Color.White,
                shadowElevation = 8.dp, // Gives a nice premium shadow line above the button
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding() // Pushes up above Samsung/system controls perfectly
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveProfile() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = uiState.hasChanges,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B), disabledContainerColor = Color.LightGray),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues) // <--- MAGIC: This prevents the bottom bar from covering your content!
        ) {
            // --- CUSTOM TOP BAR ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF00897B)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Therapy Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showInfoDialog = true }) { Icon(Icons.Default.Info, "Terminology Info", tint = Color(0xFF00897B)) }
                }
            }

            // --- SCROLLABLE CONTENT ---
            Column(
                modifier = Modifier
                    .weight(1f) // Takes up remaining space
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Insulin Delivery Method", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        val methods = listOf(
                            Triple("MDI", "Insulin Pens", "Multiple Daily Injections (MDI)"),
                            Triple("PUMP_STANDARD", "Standard Pump", "Continuous basal without auto-correction"),
                            Triple("PUMP_AID", "Smart Pump (AID)", "Automated Insulin Delivery / Loop")
                        )
                        methods.forEach { (type, title, desc) ->
                            val isSelected = uiState.therapyType == type
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color(0xFFE0F2F1) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.updateTherapyType(type) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = { viewModel.updateTherapyType(type) }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00897B)))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(title, fontWeight = FontWeight.Bold, color = if (isSelected) Color(0xFF00695C) else Color.Black)
                                    Text(desc, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.therapyType == "PUMP_AID") {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AID Mode: The CDSS algorithm will reduce suggested correction boluses and sport carbs, assuming your pump is auto-adjusting.", fontSize = 13.sp, color = Color(0xFFE65100), lineHeight = 18.sp)
                        }
                    }
                }

                Text("Glucose Tracking Source", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp)).padding(4.dp)) {
                    Box(modifier = Modifier.weight(1f).background(if (uiState.glucoseSource == "MANUAL") Color.White else Color.Transparent, RoundedCornerShape(8.dp)).clickable { viewModel.updateGlucoseSource("MANUAL") }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("Fingersticks", fontWeight = FontWeight.Bold, color = if (uiState.glucoseSource == "MANUAL") Color(0xFF00695C) else Color.Gray) }
                    Box(modifier = Modifier.weight(1f).background(if (uiState.glucoseSource == "CGM") Color.White else Color.Transparent, RoundedCornerShape(8.dp)).clickable { viewModel.updateGlucoseSource("CGM") }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("CGM Sensor", fontWeight = FontWeight.Bold, color = if (uiState.glucoseSource == "CGM") Color(0xFF00695C) else Color.Gray) }
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Therapy Terminology", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("These settings help the algorithm understand your safety constraints.", fontSize = 14.sp)
                    Column { Text("MDI (Pens)", fontWeight = FontWeight.Bold, color = Color(0xFF00897B)); Text("Multiple Daily Injections. You take long-acting insulin once a day. Because you cannot suspend it during exercise, you require proactive carbs for sports.", fontSize = 13.sp, color = Color.DarkGray) }
                    Column { Text("Standard Pump", fontWeight = FontWeight.Bold, color = Color(0xFF00897B)); Text("Delivers a steady drip of fast-acting insulin. It does not auto-adjust, but you can manually suspend delivery for sports.", fontSize = 13.sp, color = Color.DarkGray) }
                    Column { Text("Smart Pump (AID)", fontWeight = FontWeight.Bold, color = Color(0xFF00897B)); Text("Automated Insulin Delivery. Connects to a CGM to automatically increase, decrease, or suspend insulin. Requires less interference from manual calculators.", fontSize = 13.sp, color = Color.DarkGray) }
                    HorizontalDivider()
                    Column { Text("CGM Sensor", fontWeight = FontWeight.Bold, color = Color(0xFF1976D2)); Text("Continuous Glucose Monitor (e.g., Dexcom, Libre). Reads glucose every 5 minutes and provides trend arrows.", fontSize = 13.sp, color = Color.DarkGray) }
                    Column { Text("Fingersticks (Manual)", fontWeight = FontWeight.Bold, color = Color(0xFF1976D2)); Text("Traditional blood glucose meter. Provides a single point in time with no momentum/trend data.", fontSize = 13.sp, color = Color.DarkGray) }
                }
            },
            confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Got it", color = Color(0xFF00897B), fontWeight = FontWeight.Bold) } },
            containerColor = Color.White, shape = RoundedCornerShape(16.dp)
        )
    }
}