package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.data.database.BolusDatabase
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.viewmodel.LogReadingViewModel
import com.example.diabetesapp.viewmodel.LogReadingViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogReadingScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val repository = remember { BolusLogRepository(database.bolusLogDao()) }

    val viewModel: LogReadingViewModel = viewModel(
        factory = LogReadingViewModelFactory(repository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Reset state when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetState()
        }
    }

    // Navigate back automatically when saved successfully
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    // Show error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Log Manual Reading", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text("Manual Entry", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00897B))

                    LargeInputField(
                        label = "Blood Glucose", value = uiState.bloodGlucose,
                        onValueChange = { viewModel.updateBloodGlucose(it) },
                        unit = "mg/dL", placeholder = "0"
                    )

                    LargeInputField(
                        label = "Carbohydrates", value = uiState.carbs,
                        onValueChange = { viewModel.updateCarbs(it) },
                        unit = "g", placeholder = "0"
                    )

                    LargeInputField(
                        label = "Insulin Dose", value = uiState.manualInsulin,
                        onValueChange = { viewModel.updateManualInsulin(it) },
                        unit = "U", placeholder = "0.0"
                    )

                    StandardInputField(
                        label = "Notes / Tags", value = uiState.notes,
                        onValueChange = { viewModel.updateNotes(it) },
                        unit = "", helperText = "e.g., 'Felt low', 'Ate pizza'"
                    )
                }
            }

            Button(
                onClick = { viewModel.saveEntry() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Entry", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LargeInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    placeholder: String = ""
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun StandardInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    helperText: String = ""
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(helperText) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
    }
}
