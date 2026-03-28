package com.example.diabetesapp.ui.screens

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.R
import com.example.diabetesapp.data.database.BolusDatabase
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.ui.components.CompactLogEntryCard
import com.example.diabetesapp.ui.components.CurrentBgWidget
import com.example.diabetesapp.ui.components.DashboardActionButtons
import com.example.diabetesapp.ui.components.IobWidget
import com.example.diabetesapp.ui.components.LogDetailsDialog
import com.example.diabetesapp.ui.components.PostWorkoutVerificationDialog
import com.example.diabetesapp.ui.components.SettingsChangeDivider
import com.example.diabetesapp.ui.components.TimeScaledBgGraph
import com.example.diabetesapp.utils.CgmReading
import com.example.diabetesapp.utils.DateTimeUtils
import com.example.diabetesapp.utils.GraphDataBuilder
import com.example.diabetesapp.utils.GraphDataBuilder.mergeSettingsChanges
import com.example.diabetesapp.utils.GraphMode
import com.example.diabetesapp.utils.InsulinEventJoiner
import com.example.diabetesapp.viewmodel.DashboardViewModel
import com.example.diabetesapp.viewmodel.DashboardViewModelFactory
import kotlinx.coroutines.isActive

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToCalculateBolus: () -> Unit = {},
    onNavigateToLogReading: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val logRepository = remember { BolusLogRepository(database.bolusLogDao()) }
    // Ensure this repository is the one that reads/writes your High/Low/Target
    val settingsRepository = remember { BolusSettingsRepository.getInstance(context) }

    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(logRepository, settingsRepository, context)
    )

    val allLogs by viewModel.allLogs.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // Re-calculate day start when logs change
    val logicalDayStart = remember { DateTimeUtils.get24hStartTimestamp() }
    val graphEndTimestamp = remember { System.currentTimeMillis() + (2.5 * 60 * 60 * 1000L).toLong() }
    val todaysLogs by viewModel.graphEvents.collectAsState()
    val unverifiedWorkout by viewModel.unverifiedWorkout.collectAsState()
    val graphEvents by viewModel.graphEvents.collectAsState()
    val hypoPrediction by viewModel.hypoPrediction.collectAsState()
    val iobResult by viewModel.iobResult.collectAsState()



    var selectedLogForModal by remember { mutableStateOf<BolusLog?>(null) }

    // Health Connect runtime permission
    val healthPermissions: Set<String> = remember {
        setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
    }
    val permissionContract = remember {
        PermissionController.createRequestPermissionResultContract()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = permissionContract
    ) { granted ->
        Log.d("HealthConnect", "Permissions granted: $granted")
        if (granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
            viewModel.fetchRecentWorkouts()
        }
    }

    // getSdkStatus without a provider package works on platform-native HC (Android 14+)
    // and also on devices that have the standalone Google HC app installed.
    val isHealthConnectAvailable = remember {
        try {
            val status = HealthConnectClient.getSdkStatus(context)
            Log.d("HealthConnect", "HC SDK status: $status")
            status == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            Log.w("HealthConnect", "SDK status check failed: ${e.message}")
            false
        }
    }

    LaunchedEffect(isHealthConnectAvailable) {
        if (!isHealthConnectAvailable) {
            Log.w("HealthConnect", "Health Connect not available on this device; skipping")
            return@LaunchedEffect
        }

        try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()

            if (granted.containsAll(healthPermissions)) {
                Log.d("HealthConnect", "Permissions already granted: $granted")
                viewModel.fetchRecentWorkouts()
                return@LaunchedEffect
            }

            Log.d("HealthConnect", "Launching HC permission UI (granted so far: $granted)")
            permissionLauncher.launch(healthPermissions)
        } catch (e: ActivityNotFoundException) {
            Log.w("HealthConnect", "HC permission UI not available on this device: ${e.message}")
        } catch (e: Exception) {
            Log.w("HealthConnect", "HC permission flow failed: ${e.message}")
        }
    }

    LaunchedEffect(allLogs) {
        viewModel.checkForPendingWorkouts(allLogs)
    }
    val cgmReadings by viewModel.cgmReadings.collectAsState()

    // Fetch the history when the logical day changes (or screen opens)
    val isCgmEnabled = settings.isCgmEnabled
    val latestReading by viewModel.latestReading.collectAsState()
    val latestManualBg = remember(todaysLogs) {
        todaysLogs.filter { it.bloodGlucose > 0 }
            .maxByOrNull { it.timestamp }
    }

    val widgetReading = if (isCgmEnabled) {
        latestReading
    } else {
        latestManualBg?.let {
            CgmReading(
                timestamp = it.timestamp,
                bgValue = it.bloodGlucose.toInt(),
                trendString = ""
            )
        }
    }

    // --- The 5-Minute Polling Engine ---
    LaunchedEffect(Unit) {
        // while(isActive) keeps this loop running endlessly in the background
        // as long as the Home Screen is open.
        while (isActive) {
            viewModel.fetchDashboardData()
            kotlinx.coroutines.delay(1 * 60 * 1000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ... Header, Disclaimer, and Action Buttons stay the same ...
        HeaderSection()
        DisclaimerBanner()
        DashboardActionButtons(
            onSmartBolusClick = onNavigateToCalculateBolus,
            onManualLogClick = onNavigateToLogReading,
            settings = settings
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 8.dp
                )
            ) {
                Text("Glucose Monitoring", fontWeight = FontWeight.Bold, color = Color(0xFF00897B))

                CurrentBgWidget(
                    latestReading = widgetReading,
                    isCgmEnabled = isCgmEnabled,
                    hypoLimit = settings.hypoLimit,
                    hyperLimit = settings.hyperLimit,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(
                    color = Color(0xFFF0F0F0),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                IobWidget(
                    iobResult = iobResult,
                    therapyType = settings.therapyTypeEnum,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                HorizontalDivider(
                    color = Color(0xFFF0F0F0),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                val graphMode = GraphDataBuilder.resolveGraphMode(
                    isCgmEnabled = settings.isCgmEnabled,
                    isAidPump = settings.isAidPump
                )

                // Conditionally pass data based on mode
                val graphCgmReadings =
                    if (graphMode != GraphMode.MANUAL) cgmReadings else emptyList()
                val graphHypoPrediction =
                    if (graphMode != GraphMode.MANUAL) hypoPrediction else null

                key(settings.targetBG, settings.hypoLimit, settings.hyperLimit, graphMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        TimeScaledBgGraph(
                            logs = graphEvents,
                            cgmReadings = graphCgmReadings,
                            dayStartTimestamp = logicalDayStart,
                            endTimestamp = graphEndTimestamp,
                            targetBg = settings.targetBG,
                            hypoLimit = settings.hypoLimit,
                            hyperLimit = settings.hyperLimit,
                            graphMode = graphMode,
                            settings = settings,
                            hypoPrediction = graphHypoPrediction,
                            graphHeightDp = 200.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Hypo prediction warning — only for CGM modes
                if (graphMode != GraphMode.MANUAL) {
                    hypoPrediction?.let { prediction ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Expected low in ~${prediction.minutesUntilHypo} min",
                                fontSize = 12.sp,
                                color = Color(0xFFE53935),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Text("Last 24h", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)

        if (todaysLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No entries in the last 24 hours.", color = Color.Gray)
            }
        } else {
            // Compute joined events once
            val joinedEvents = remember(todaysLogs) {
                InsulinEventJoiner.joinForHistory(todaysLogs)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // We still need SETTINGS_CHANGE dividers, so we work from the original
                // reversed+merged list for those, but use joinedEvents for actual log cards
                val settingsChanges = todaysLogs.reversed().mergeSettingsChanges()
                    .filter { it.eventType == "SETTINGS_CHANGE" }

                // Build the display list: joinedEvents reversed, with SETTINGS_CHANGE
                // dividers inserted at the right positions
                val basalIds = joinedEvents
                    .filter { !it.isBasalOnly && it.associatedBasal != null }
                    .map { it.associatedBasal!!.id }
                    .toSet()

                todaysLogs.reversed().mergeSettingsChanges().forEach { log ->
                    when {
                        log.eventType == "SETTINGS_CHANGE" -> {
                            SettingsChangeDivider(
                                timestamp = log.timestamp,
                                description = log.notes
                            )
                        }
                        // Skip standalone basal logs that got merged into a bolus card
                        log.eventType == "BASAL_INSULIN" && log.id in basalIds -> {
                            // consumed by its bolus card, don't render separately
                        }

                        else -> {
                            val joined = joinedEvents.find { it.primaryLog.id == log.id }
                            CompactLogEntryCard(
                                log = log,
                                hypoLimit = settings.hypoLimit,
                                hyperLimit = settings.hyperLimit,
                                associatedBasal = joined?.associatedBasal
                            ) { selectedLogForModal = log }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    // --- MODALS ---
    selectedLogForModal?.let { log ->
        // Find the joined event for this log
        val joined = remember(todaysLogs) {
            InsulinEventJoiner.joinForHistory(todaysLogs)
        }.find { it.primaryLog.id == log.id }

        LogDetailsDialog(
            log = log,
            associatedBasal = joined?.associatedBasal,
            onDismiss = { selectedLogForModal = null }
        )
    }

    unverifiedWorkout?.let { workout ->
        PostWorkoutVerificationDialog(
            log = workout,
            onDismiss = { viewModel.dismissVerification() },
            onConfirm = { duration, intensity, type, starttime ->
                viewModel.verifyAndCompleteWorkout(workout, duration, intensity, type, starttime)
            }
        )
    }
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "App Icon",
                tint = Color.Unspecified,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Diabetes Manager", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Make sports and diabetes easier!", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun DisclaimerBanner() {
    Surface(
        color = Color(0xFFFFF4E6),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Educational Prototype: This app is for research purposes only.",
                fontSize = 11.sp, color = Color(0xFFE65100), lineHeight = 14.sp
            )
        }
    }
}