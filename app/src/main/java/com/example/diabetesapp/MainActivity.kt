package com.example.diabetesapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.ui.components.BottomNavBar
import com.example.diabetesapp.ui.screens.*
import com.example.diabetesapp.ui.theme.DiabetesAppTheme

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

    // Declare ALL remembers at the top structure level before conditionals! 
    // This stops them from "disappearing/reappearing" during recomposition, which causes the crash.
    var backStack by remember { mutableStateOf(listOf("home")) }
    var selectedRoute by remember { mutableStateOf("home") }
    
    // Computed value based on state
    val currentScreen = backStack.last()

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
                    onNavigateToLogReading = { navigateTo("log_reading") }
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
                    onNavigateToLogReading = { navigateTo("log_reading") }
                )
            }
        }
    }
}