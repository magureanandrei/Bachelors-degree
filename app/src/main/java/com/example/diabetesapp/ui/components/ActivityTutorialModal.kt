package com.example.diabetesapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.diabetesapp.viewmodel.ActivityStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTutorialModal(
    activityStatus: ActivityStatus,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .navigationBarsPadding()
        ) {
            // Status banner
            ActivityStatusBanner(activityStatus = activityStatus)

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                val healthConnectUrl =
                    "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
                val stravaUrl =
                    "https://play.google.com/store/apps/details?id=com.strava"

                // Step 1
                ActivityStepRow(number = 1) {
                    val annotated = buildAnnotatedString {
                        append("Install ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Health Connect") }
                        append(" from the Play Store if not already present — on Android 14+ it is built in. ")
                        pushStringAnnotation(tag = "URL", annotation = healthConnectUrl)
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF1976D2),
                                textDecoration = TextDecoration.Underline
                            )
                        ) { append("Open Play Store") }
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
                }

                // Step 2
                ActivityStepRow(number = 2) {
                    Text(
                        text = "Open Health Connect and grant permissions for Exercise and Heart Rate when prompted.",
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        lineHeight = 18.sp
                    )
                }

                // Step 3
                ActivityStepRow(number = 3) {
                    val annotated = buildAnnotatedString {
                        append("Install ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Strava") }
                        append(" (recommended) or any fitness app that writes workouts to Health Connect. ")
                        pushStringAnnotation(tag = "URL", annotation = stravaUrl)
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF1976D2),
                                textDecoration = TextDecoration.Underline
                            )
                        ) { append("Open Play Store") }
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
                }

                // Step 4
                ActivityStepRow(number = 4) {
                    Text(
                        text = "In your sports app, enable Health Connect sync in its settings.",
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        lineHeight = 18.sp
                    )
                }

                // Step 5
                ActivityStepRow(number = 5) {
                    Text(
                        text = "Open this app — workouts will be automatically detected and imported after each activity.",
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Workout data is read directly from Health Connect on your device. No account login is required by this app.",
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
private fun ActivityStatusBanner(activityStatus: ActivityStatus) {
    val (dotColor, bannerBg, bannerText) = when (activityStatus) {
        is ActivityStatus.Connected -> Triple(
            Color(0xFF4CAF50),
            Color(0xFFE8F5E9),
            "Health Connect connected"
        )
        is ActivityStatus.NotConnected -> Triple(
            Color(0xFFF44336),
            Color(0xFFFFEBEE),
            "Health Connect not connected"
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
private fun ActivityStepRow(
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