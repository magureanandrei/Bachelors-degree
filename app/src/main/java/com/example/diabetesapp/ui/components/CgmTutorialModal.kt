package com.example.diabetesapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.viewmodel.CgmStatus

private val TealPrimary = Color(0xFF00897B)
private val TealDark = Color(0xFF00695C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CgmTutorialModal(
    cgmStatus: CgmStatus,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Sensor & companion app", "Medtronic + medtronic connect")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .navigationBarsPadding()
        ) {
            // Status banner
            StatusBanner(cgmStatus = cgmStatus)

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = TealPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = TealPrimary
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) TealPrimary else Color.Gray
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (selectedTab == 0) {
                    TabSensorCompanionApp(uriHandler = uriHandler)
                } else {
                    TabMedtronic(uriHandler = uriHandler)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer note
                Text(
                    text = "This app connects to xDrip+ via local HTTP on port 17580. Both apps must be on the same device. No internet connection is required.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(cgmStatus: CgmStatus) {
    val (dotColor, bannerBg, bannerText) = when (cgmStatus) {
        is CgmStatus.Connected -> Triple(
            Color(0xFF4CAF50),
            Color(0xFFE8F5E9),
            "Connected · ${cgmStatus.minutesAgo} min ago"
        )
        is CgmStatus.Stale -> Triple(
            Color(0xFFFFC107),
            Color(0xFFFFFDE7),
            "Stale — last reading ${cgmStatus.minutesAgo} min ago"
        )
        is CgmStatus.NoSignal -> Triple(
            Color(0xFFF44336),
            Color(0xFFFFEBEE),
            if (cgmStatus.minutesAgo != null) "No signal — last reading ${cgmStatus.minutesAgo} min ago"
            else "No signal — no reading received yet"
        )
        is CgmStatus.Unreachable -> Triple(
            Color(0xFFF44336),
            Color(0xFFFFEBEE),
            "xDrip+ server not reachable"
        )
        is CgmStatus.Disabled -> Triple(
            Color(0xFFF44336),
            Color(0xFFFFEBEE),
            "Manual glucose source — CGM disabled"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(bannerBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = dotColor)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = bannerText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.DarkGray
        )
    }
}

@Composable
private fun TabSensorCompanionApp(uriHandler: androidx.compose.ui.platform.UriHandler) {
    val xdripUrl = "https://github.com/NightscoutFoundation/xDrip"
    val steps = listOf(
        "Install your CGM companion app (Dexcom G6/G7, LibreLink, Spike, etc.) on this phone.",
        "Make sure the companion app is running and has an active persistent notification — this means it is broadcasting data.",
        null, // special: contains link
        "In xDrip+: go to Settings → Hardware Data Source and select your sensor type.",
        "In xDrip+: enable \"Enable xDrip Web Server\" under Settings → Inter-app settings.",
        "Open this app — Indicator should show green and BG readings will now appear automatically."
    )

    steps.forEachIndexed { index, step ->
        StepRow(
            number = index + 1,
            content = {
                if (index == 2) {
                    // Step 3: link
                    val annotated = buildAnnotatedString {
                        append("Install the latest Release of ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("xDrip+") }
                        append(" from GitHub (not Play Store) — ")
                        pushStringAnnotation(tag = "URL", annotation = xdripUrl)
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF1976D2),
                                textDecoration = TextDecoration.Underline
                            )
                        ) { append(xdripUrl) }
                        pop()
                    }
                    ClickableText(
                        text = annotated,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = Color.DarkGray,
                            lineHeight = 18.sp
                        ),
                        onClick = { offset ->
                            annotated.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { uriHandler.openUri(it.item) }
                        }
                    )
                } else {
                    Text(
                        text = step ?: "",
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        lineHeight = 18.sp
                    )
                }
            }
        )
    }
}

@Composable
private fun TabMedtronic(uriHandler: androidx.compose.ui.platform.UriHandler) {
    val xdripUrl = "https://github.com/NightscoutFoundation/xDrip"
    val steps = listOf(
        "Make sure you have the Minimed Mobile app installed and your pump paired with it.",
        "Ensure your pump is uploading data to CareLink (check via Minimed Mobile or carelink.minimed.eu).",
        null, // special: contains link
        "In xDrip+: go to Settings → Hardware Data Source → select \"CareLink Follower\".",
        "Enter your CareLink username and password when prompted in xDrip+.",
        "In xDrip+: enable \"Enable xDrip Web Server\" under Settings → Inter-app settings.",
        "Set your Therapy Profile in this app to \"Smart Pump (AID)\" — this unlocks full IOB, insulin, and carb data from CareLink sync.",
        "Open this app — BG, IOB, and treatment history will now sync automatically."
    )

    steps.forEachIndexed { index, step ->
        StepRow(
            number = index + 1,
            content = {
                if (index == 2) {
                    val annotated = buildAnnotatedString {
                        append("Install ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("xDrip+") }
                        append(" from GitHub: ")
                        pushStringAnnotation(tag = "URL", annotation = xdripUrl)
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF1976D2),
                                textDecoration = TextDecoration.Underline
                            )
                        ) { append(xdripUrl) }
                        pop()
                    }
                    ClickableText(
                        text = annotated,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = Color.DarkGray,
                            lineHeight = 18.sp
                        ),
                        onClick = { offset ->
                            annotated.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { uriHandler.openUri(it.item) }
                        }
                    )
                } else {
                    Text(
                        text = step ?: "",
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        lineHeight = 18.sp
                    )
                }
            }
        )
    }
}

@Composable
private fun StepRow(
    number: Int,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFF00897B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
            content()
        }
    }
}