package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabetesapp.R
import com.example.diabetesapp.data.database.BolusDatabase
import com.example.diabetesapp.data.repository.BolusLogRepository
import com.example.diabetesapp.viewmodel.DashboardViewModel
import com.example.diabetesapp.viewmodel.DashboardViewModelFactory

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToCalculateBolus: () -> Unit = {},
    onNavigateToLogReading: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { BolusDatabase.getDatabase(context) }
    val repository = remember { BolusLogRepository(database.bolusLogDao()) }
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModelFactory(repository))

    val logs by viewModel.allLogs.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        HeaderSection()

        // Action Buttons at the Top
        DashboardActionButtons(
            onSmartBolusClick = onNavigateToCalculateBolus,
            onManualLogClick = onNavigateToLogReading
        )

        // The Graph Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Recent Blood Glucose Trends", fontWeight = FontWeight.Bold, color = Color(0xFF00897B))
                Spacer(modifier = Modifier.height(16.dp))

                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No data yet. Log a reading!", color = Color.Gray)
                    }
                } else {
                    SimpleBgGraph(
                        // Take last 10 readings, reverse so oldest is on left, newest on right
                        bgValues = logs.take(10).reversed().map { it.bloodGlucose.toFloat() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        DisclaimerBanner()
    }
}

@Composable
fun DashboardActionButtons(onSmartBolusClick: () -> Unit, onManualLogClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onSmartBolusClick,
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("Smart Bolus", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = onManualLogClick,
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00897B))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("Manual Log", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SimpleBgGraph(bgValues: List<Float>) {
    Canvas(modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)) {
        if (bgValues.size < 2) return@Canvas

        val maxBg = 300f
        val width = size.width
        val height = size.height
        val stepX = width / (bgValues.size - 1)

        val path = Path()
        bgValues.forEachIndexed { index, bg ->
            val x = index * stepX
            val y = height - ((bg / maxBg) * height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)

            drawCircle(
                color = if (bg > 180 || bg < 70) Color.Red else Color(0xFF00897B),
                radius = 6f,
                center = Offset(x, y)
            )
        }

        drawPath(
            path = path,
            color = Color(0xFF00897B),
            style = Stroke(width = 3f)
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