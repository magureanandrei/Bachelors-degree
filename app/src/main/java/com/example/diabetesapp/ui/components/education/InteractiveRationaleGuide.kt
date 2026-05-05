package com.example.diabetesapp.ui.components.education

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.algorithm.BreakdownEntry
import com.example.diabetesapp.algorithm.Effect
import com.example.diabetesapp.ui.screens.INTERACTIVE_ENTRY_EXPLANATIONS
import com.example.diabetesapp.ui.screens.INTERACTIVE_HINT_TEXT
import com.example.diabetesapp.ui.screens.INTERACTIVE_SAMPLE_CONTEXT
import com.example.diabetesapp.ui.screens.INTERACTIVE_SAMPLE_NOTE
import com.example.diabetesapp.ui.screens.INTERACTIVE_TOPIC_TITLE
import com.example.diabetesapp.ui.screens.SAMPLE_ENTRY_BASAL_DESC
import com.example.diabetesapp.ui.screens.SAMPLE_ENTRY_BASAL_LABEL
import com.example.diabetesapp.ui.screens.SAMPLE_ENTRY_CORRECTION_DESC
import com.example.diabetesapp.ui.screens.SAMPLE_ENTRY_CORRECTION_LABEL
import com.example.diabetesapp.ui.screens.SAMPLE_ENTRY_IOB_DESC
import com.example.diabetesapp.ui.screens.SAMPLE_ENTRY_IOB_LABEL
import com.example.diabetesapp.ui.screens.SAMPLE_ENTRY_MEAL_DESC
import com.example.diabetesapp.ui.screens.SAMPLE_ENTRY_MEAL_LABEL
import com.example.diabetesapp.ui.screens.SAMPLE_ENTRY_SPORT_DESC
import com.example.diabetesapp.ui.screens.SAMPLE_ENTRY_SPORT_LABEL

private val sampleEntries = listOf(
    BreakdownEntry(
        stepName = "Baseline",
        label = SAMPLE_ENTRY_MEAL_LABEL,
        emoji = "🍽️",
        description = SAMPLE_ENTRY_MEAL_DESC,
        effect = Effect.INCREASE,
        valueChange = 3.0,
        percentChange = null,
        runningTotal = 3.0
    ),
    BreakdownEntry(
        stepName = "Correction",
        label = SAMPLE_ENTRY_CORRECTION_LABEL,
        emoji = "💉",
        description = SAMPLE_ENTRY_CORRECTION_DESC,
        effect = Effect.INCREASE,
        valueChange = 1.6,
        percentChange = null,
        runningTotal = 4.6
    ),
    BreakdownEntry(
        stepName = "Sport",
        label = SAMPLE_ENTRY_SPORT_LABEL,
        emoji = "🏃",
        description = SAMPLE_ENTRY_SPORT_DESC,
        effect = Effect.DECREASE,
        valueChange = -1.5,
        percentChange = -0.5,
        runningTotal = 3.1
    ),
    BreakdownEntry(
        stepName = "IOB",
        label = SAMPLE_ENTRY_IOB_LABEL,
        emoji = "⬇️",
        description = SAMPLE_ENTRY_IOB_DESC,
        effect = Effect.DECREASE,
        valueChange = -1.5,
        percentChange = null,
        runningTotal = 1.6
    ),
    BreakdownEntry(
        stepName = "Basal",
        label = SAMPLE_ENTRY_BASAL_LABEL,
        emoji = "⚠️",
        description = SAMPLE_ENTRY_BASAL_DESC,
        effect = Effect.WARNING,
        valueChange = null,
        percentChange = null,
        runningTotal = 1.6
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveRationaleGuide(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedEntry by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFF5F5F5)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = INTERACTIVE_TOPIC_TITLE,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00897B)
            )
            Text(
                text = INTERACTIVE_SAMPLE_CONTEXT,
                fontSize = 12.sp,
                color = Color.Gray
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Algorithm Rationale",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    sampleEntries.forEachIndexed { index, entry ->
                        val isSelected = selectedEntry == index
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isSelected) Color(0xFFDCEDC8) else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    selectedEntry = if (selectedEntry == index) null else index
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = entry.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = when (entry.effect) {
                                    Effect.INCREASE -> Color(0xFFD84315)
                                    Effect.DECREASE -> Color(0xFF2E7D32)
                                    Effect.WARNING  -> Color(0xFFE65100)
                                    Effect.NEUTRAL  -> Color(0xFF004D40)
                                }
                            )
                            Text(
                                text = entry.description,
                                fontSize = 12.sp,
                                color = Color(0xFF37474F),
                                lineHeight = 16.sp
                            )
                        }
                        if (index < sampleEntries.lastIndex) {
                            HorizontalDivider(
                                color = Color(0xFF80CBC4).copy(alpha = 0.3f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedEntry != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                selectedEntry?.let { idx ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = INTERACTIVE_ENTRY_EXPLANATIONS.getOrElse(idx) { "" },
                            modifier = Modifier.padding(14.dp),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedEntry == null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Text(
                    text = INTERACTIVE_HINT_TEXT,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontStyle = FontStyle.Italic
                )
            }

            Text(
                text = INTERACTIVE_SAMPLE_NOTE,
                fontSize = 12.sp,
                color = Color.Gray,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
