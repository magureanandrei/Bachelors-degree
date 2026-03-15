package com.example.diabetesapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.ui.components.DoseBreakdownCard
import com.example.diabetesapp.data.database.BolusDatabase
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.ui.components.SmartBolus
import com.example.diabetesapp.ui.components.SmartBolusResultDialog
import com.example.diabetesapp.viewmodel.BolusInputState
import com.example.diabetesapp.viewmodel.CalculateBolusViewModel
import com.example.diabetesapp.viewmodel.CalculateBolusViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculateBolusScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val repository = remember { BolusLogRepository(database.bolusLogDao()) }
    val settingsRepository = remember { BolusSettingsRepository.getInstance(context) }

    val viewModel: CalculateBolusViewModel = viewModel(
        factory = CalculateBolusViewModelFactory(repository, settingsRepository)
    )

    val inputState by viewModel.inputState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by viewModel.settings.collectAsState()

    // Reset state when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetState()
        }
    }

    LaunchedEffect(inputState.warningMessage) {
        inputState.warningMessage?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            viewModel.clearWarningMessage() // Clears it so it doesn't pop up again on rotation
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(
                    text = if (settings.isAidPump) "Sport & Meal Advisor" else "Smart Bolus",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                ) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            SmartBolus(inputState = inputState, viewModel = viewModel, settings = settings)

            if (inputState.showResultDialog && inputState.calculatedDose != null) {
                SmartBolusResultDialog(
                    standardDose = inputState.standardDose ?: 0.0,
                    calculatedDose = inputState.calculatedDose!!,
                    userAdjustedDose = inputState.userAdjustedDose,
                    isSportModeActive = inputState.isSportModeActive,
                    minutesUntilSport = inputState.minutesUntilSport,
                    sportLog = inputState.sportReductionLog,
                    onAdjustDose = { delta -> viewModel.adjustSuggestedDose(delta) },
                    onDismiss = { viewModel.dismissResultDialog() },
                    onLogAndSave = {
                        viewModel.logEntry(context)
                        viewModel.dismissResultDialog()
                        onNavigateBack() // Navigate Back to Home
                    }
                )
            }
        }
    }
}


