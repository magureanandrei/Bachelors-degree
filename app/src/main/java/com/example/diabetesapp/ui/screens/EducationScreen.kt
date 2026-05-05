package com.example.diabetesapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diabetesapp.ui.components.education.EducationTopicSheet
import com.example.diabetesapp.ui.components.education.InteractiveRationaleGuide

private val section1Icons: List<ImageVector> = listOf(
    Icons.Default.WbSunny,
    Icons.Default.Science,
    Icons.Default.Psychology,
    Icons.Default.Layers
)

private val section2Icons: List<ImageVector> = listOf(
    Icons.AutoMirrored.Filled.DirectionsRun,
    Icons.AutoMirrored.Filled.DirectionsWalk,
    Icons.Default.Schedule
)

@Composable
fun EducationScreen(modifier: Modifier = Modifier) {
    var selectedTopic by remember { mutableStateOf<TopicDef?>(null) }
    var showRationaleGuide by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Education",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        item { SectionHeader(SECTION_1_HEADER) }
        itemsIndexed(SECTION_1_TOPICS) { index, topic ->
            EducationTopicCard(
                icon = section1Icons[index],
                title = topic.title,
                subtitle = topic.subtitle,
                onClick = { selectedTopic = topic }
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }
        item { SectionHeader(SECTION_2_HEADER) }
        itemsIndexed(SECTION_2_TOPICS) { index, topic ->
            EducationTopicCard(
                icon = section2Icons[index],
                title = topic.title,
                subtitle = topic.subtitle,
                onClick = { selectedTopic = topic }
            )
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }
        item { SectionHeader(SECTION_3_HEADER) }
        itemsIndexed(SECTION_3_TOPICS) { _, topic ->
            EducationTopicCard(
                icon = Icons.Default.Warning,
                iconTint = Color(0xFFF9A825),
                title = topic.title,
                subtitle = topic.subtitle,
                onClick = { selectedTopic = topic }
            )
        }
        item {
            EducationTopicCard(
                icon = Icons.Default.Analytics,
                title = INTERACTIVE_TOPIC_TITLE,
                subtitle = INTERACTIVE_TOPIC_SUBTITLE,
                onClick = { showRationaleGuide = true }
            )
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    selectedTopic?.let { topic ->
        EducationTopicSheet(
            topic = topic,
            onDismiss = { selectedTopic = null }
        )
    }

    if (showRationaleGuide) {
        InteractiveRationaleGuide(
            onDismiss = { showRationaleGuide = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF00897B),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun EducationTopicCard(
    icon: ImageVector,
    iconTint: Color = Color(0xFF00897B),
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
