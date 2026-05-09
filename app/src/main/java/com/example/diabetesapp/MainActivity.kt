package com.example.diabetesapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.diabetesapp.data.database.BolusDatabase
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.data.repository.InsightsRepository
import com.example.diabetesapp.ui.components.BottomNavBar
import com.example.diabetesapp.ui.components.SynthesisData
import com.example.diabetesapp.ui.components.YesterdaySynthesisModal
import com.example.diabetesapp.ui.screens.*
import com.example.diabetesapp.ui.theme.DiabetesAppTheme
import androidx.health.connect.client.HealthConnectClient
import com.example.diabetesapp.utils.CgmHelper
import com.example.diabetesapp.utils.HealthConnectHelper
import com.example.diabetesapp.utils.WorkoutNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

// Set to true to show synthesis modal on every app open (debug only)
private const val DEBUG_ALWAYS_SHOW_SYNTHESIS = false

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiabetesAppTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val repository = remember { BolusSettingsRepository.getInstance(context) }

    // Note: change true/false here to forcefully test onboarding, but otherwise it should read from repository
    var onboardingComplete by remember { mutableStateOf(repository.hasCompletedOnboarding()) }

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var showNotifModal by remember { mutableStateOf(false) }
    var synthesisData by remember { mutableStateOf<SynthesisData?>(null) }
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Declare ALL remembers at the top structure level before conditionals!
    // This stops them from "disappearing/reappearing" during recomposition, which causes the crash.
    var backStack by remember { mutableStateOf(listOf("home")) }
    var selectedRoute by remember { mutableStateOf("home") }

    // Computed value based on state
    val currentScreen = backStack.last()

    LaunchedEffect(Unit) {
        val settings = repository.settings.first()
        WorkoutNotificationManager.scheduleMorningReminder(context)
        if (settings.isMdi) {
            val midnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val db = BolusDatabase.getDatabase(context)
            val logRepo = BolusLogRepository(db.bolusLogDao())
            val basalLogs = logRepo.getBasalLogsSince(midnight)
            if (basalLogs.isEmpty()) {
                WorkoutNotificationManager.scheduleMissedBasalReminder(context)
            } else {
                WorkoutNotificationManager.cancelMissedBasalReminder(context)
            }
        }
        if (!settings.isCgmEnabled) {
            val db = BolusDatabase.getDatabase(context)
            val logRepo = BolusLogRepository(db.bolusLogDao())
            val lastBg = logRepo.getLatestManualBgLog()
            WorkoutNotificationManager.scheduleStaleBgReminder(context, lastBg?.timestamp ?: 0L)
        }
    }

    LaunchedEffect(onboardingComplete) {
        if (!onboardingComplete) return@LaunchedEffect
        val synthPrefs = context.getSharedPreferences("synthesis_prefs", Context.MODE_PRIVATE)
        val lastShownDate = synthPrefs.getString("last_shown_date", "")
        val todayDate = LocalDate.now().toString()
        if (lastShownDate != todayDate || DEBUG_ALWAYS_SHOW_SYNTHESIS) {
            launch(Dispatchers.IO) {
                try {
                    val db = BolusDatabase.getDatabase(context)
                    val logRepo = BolusLogRepository(db.bolusLogDao())
                    val metricsDao = db.dailyMetricsDao()
                    val hcClient = try {
                        HealthConnectClient.getOrCreate(context)
                    } catch (e: Exception) { null }
                    val hcHelper = if (hcClient != null) HealthConnectHelper(hcClient) else null
                    val insightsRepo = InsightsRepository(logRepo, metricsDao, hcHelper, context)
                    val settings = repository.settings.first()

                    insightsRepo.refreshYesterday(settings)

                    val metrics = insightsRepo.getYesterday() ?: return@launch
                    val sportLogs = insightsRepo.getYesterdaySportLogs()
                    val basalLog = if (settings.isMdi) insightsRepo.getYesterdayBasalLog() else null
                    val overnightLow = insightsRepo.getOvernightLow(settings.hypoLimit)

                    val cgmHistory = if (settings.isCgmEnabled) {
                        try {
                            val h = CgmHelper.getBgHistoryExtended(days = 2)
                            android.util.Log.d("Synthesis", "CGM history: ${h.size} readings")
                            h
                        }
                        catch (e: Exception) {
                            android.util.Log.e("Synthesis", "CGM fetch failed: ${e.message}")
                            emptyList()
                        }
                    } else emptyList()

                    val mealSpikes = if (settings.isCgmEnabled) {
                        insightsRepo.getMealSpikes(settings.hyperLimit, cgmHistory)
                    } else emptyList()

                    val data = SynthesisData(
                        metrics = metrics,
                        sportLogs = sportLogs,
                        basalLog = basalLog,
                        overnightLow = overnightLow,
                        mealSpikes = mealSpikes,
                        isMdi = settings.isMdi,
                        isCgmEnabled = settings.isCgmEnabled
                    )

                    withContext(Dispatchers.Main) {
                        synthesisData = data
                        if (!DEBUG_ALWAYS_SHOW_SYNTHESIS) {
                            synthPrefs.edit().putString("last_shown_date", todayDate).apply()
                        }
                    }
                } catch (e: Exception) {
                    // Skip modal if data cannot be loaded
                }
            }
        }
    }

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyAsked = prefs.getBoolean("notif_permission_asked", false)
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!alreadyAsked && !alreadyGranted) {
                showNotifModal = true
            }
        }
    }

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        }
    }

    if (!onboardingComplete) {
        OnboardingScreen(onComplete = { onboardingComplete = true })
    } else {
        // 2. Intercept the hardware Back Button
        BackHandler(enabled = backStack.size > 1) {
            backStack = backStack.dropLast(1)
            val previousScreen = backStack.last()
            if (previousScreen in listOf("home", "history", "stats", "education", "menu")) {
                selectedRoute = previousScreen
            }
        }

        // 3. Helper function to navigate forward
        val navigateTo = { route: String ->
            if (currentScreen != route) {
                backStack = backStack + route
                if (route in listOf("home", "history", "stats", "education", "menu")) {
                    selectedRoute = route
                }
            }
        }

        // 4. Helper function to go back programmatically (top left arrows)
        val navigateBack = {
            if (backStack.size > 1) {
                backStack = backStack.dropLast(1)
                val previousScreen = backStack.last()
                if (previousScreen in listOf("home", "history", "stats", "education", "menu")) {
                    selectedRoute = previousScreen
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (currentScreen in listOf("home", "history", "stats", "education", "menu")) {
                    BottomNavBar(
                        selectedRoute = selectedRoute,
                        onNavigate = { route -> navigateTo(route) }
                    )
                }
            }
        ) { innerPadding ->
            when (currentScreen) {
                "home" -> HomeScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToCalculateBolus = { navigateTo("calculate_bolus") },
                    onNavigateToLogReading = { navigateTo("log_reading") },
                    synthesisData = synthesisData,
                    onSynthesisDismissed = { synthesisData = null }
                )
                "history" -> HistoryScreen(modifier = Modifier.padding(innerPadding))
                "stats" -> InsightsScreen(modifier = Modifier.padding(innerPadding))
                "education" -> EducationScreen(modifier = Modifier.padding(innerPadding))
                "menu" -> MenuScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToBolusSettings = { navigateTo("bolus_settings") },
                    onNavigateToTherapyProfile = { navigateTo("therapy_profile") }
                )

                // Detail screens (no bottom bar)
                "bolus_settings" -> BolusSettingsScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateBack = navigateBack
                )
                "therapy_profile" -> TherapyProfileScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateBack = navigateBack
                )
                "calculate_bolus" -> CalculateBolusScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateBack = navigateBack
                )
                "log_reading" -> LogReadingScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateBack = navigateBack
                )
                else -> HomeScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToCalculateBolus = { navigateTo("calculate_bolus") },
                    onNavigateToLogReading = { navigateTo("log_reading") },
                    synthesisData = synthesisData,
                    onSynthesisDismissed = { synthesisData = null }
                )
            }
        }
    }

    if (showNotifModal) {
        AlertDialog(
            onDismissRequest = {
                showNotifModal = false
                prefs.edit().putBoolean("notif_permission_asked", true).apply()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF00897B),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Enable Notifications",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            },
            text = {
                Text(
                    "This app sends reminders for missed basal doses, post-exercise " +
                    "hypoglycemia risk, meal checks, and more. Notifications help keep " +
                    "you safe — we recommend enabling them.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF37474F)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotifModal = false
                        prefs.edit().putBoolean("notif_permission_asked", true).apply()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            requestPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00897B)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Enable", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNotifModal = false
                        prefs.edit().putBoolean("notif_permission_asked", true).apply()
                    }
                ) {
                    Text("Not Now", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
