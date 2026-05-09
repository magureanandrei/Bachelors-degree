package com.example.diabetesapp.ui.components.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.diabetesapp.data.models.DailyMetrics
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val CV_THRESHOLD = 36f
private val CV_STABLE_COLOR = Color(0xFF2E7D32)
private val CV_UNSTABLE_COLOR = Color(0xFFE53935)
private val THRESHOLD_COLOR = Color(0xFF9E9E9E)

@Composable
fun CvModal(
    last30Days: List<DailyMetrics>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Glucose Variability (CV)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "CV measures glucose fluctuation relative to your average. " +
                    "Below 36% indicates stable control (Monnier 2017).",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(16.dp))

                val validDays = last30Days.filter { it.readingCount >= 3 }
                if (validDays.size < 2) {
                    Text(
                        "Not enough data yet for trend view",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    CvLineChart(validDays)
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = Color(0xFF00897B))
                }
            }
        }
    }
}

@Composable
private fun CvLineChart(days: List<DailyMetrics>) {
    val textMeasurer = rememberTextMeasurer()
    val dateFormatter = DateTimeFormatter.ofPattern("M/d")

    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        if (days.size < 2) return@Canvas

        val topPad = 8f
        val bottomPad = 24f
        val leftPad = 32f
        val chartW = size.width - leftPad
        val chartH = size.height - topPad - bottomPad
        val maxCv = 80f

        fun cvToY(cv: Float) = topPad + chartH - (cv / maxCv).coerceIn(0f, 1f) * chartH
        fun idxToX(i: Int) = leftPad + (i.toFloat() / (days.size - 1).coerceAtLeast(1)) * chartW

        // Y-axis labels
        listOf(0f, 36f, 72f).forEach { v ->
            val y = cvToY(v)
            val label = "${v.toInt()}%"
            val m = textMeasurer.measure(label, style = TextStyle(fontSize = 8.sp, color = Color.Gray))
            drawText(m, topLeft = Offset(0f, y - m.size.height / 2f))
        }

        // Dashed threshold line at 36%
        val threshY = cvToY(CV_THRESHOLD)
        drawLine(
            color = THRESHOLD_COLOR,
            start = Offset(leftPad, threshY),
            end = Offset(size.width, threshY),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )
        val threshLabel = textMeasurer.measure(
            "Target",
            style = TextStyle(fontSize = 8.sp, color = THRESHOLD_COLOR)
        )
        drawText(threshLabel, topLeft = Offset(size.width - threshLabel.size.width - 4f, threshY - threshLabel.size.height - 2f))

        // Area fill under CV line
        val bottomY = topPad + chartH
        days.forEachIndexed { i, dm ->
            if (i == 0) return@forEachIndexed
            val prev = days[i - 1]
            val x0 = idxToX(i - 1)
            val y0 = cvToY(prev.cv)
            val x1 = idxToX(i)
            val y1 = cvToY(dm.cv)
            val avgCv = (prev.cv + dm.cv) / 2f
            val fillColor = if (avgCv <= CV_THRESHOLD)
                CV_STABLE_COLOR.copy(alpha = 0.08f)
            else
                CV_UNSTABLE_COLOR.copy(alpha = 0.08f)
            val path = Path()
            path.moveTo(x0, y0)
            path.lineTo(x1, y1)
            path.lineTo(x1, bottomY)
            path.lineTo(x0, bottomY)
            path.close()
            drawPath(path, color = fillColor)
        }

        // Colored line segments
        days.forEachIndexed { i, dm ->
            if (i == 0) return@forEachIndexed
            val prev = days[i - 1]
            val x0 = idxToX(i - 1)
            val y0 = cvToY(prev.cv)
            val x1 = idxToX(i)
            val y1 = cvToY(dm.cv)
            val segColor = if (dm.cv <= CV_THRESHOLD) CV_STABLE_COLOR else CV_UNSTABLE_COLOR
            drawLine(
                color = segColor,
                start = Offset(x0, y0),
                end = Offset(x1, y1),
                strokeWidth = 2.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Dots
        days.forEachIndexed { i, dm ->
            val x = idxToX(i)
            val y = cvToY(dm.cv)
            val dotColor = if (dm.cv <= CV_THRESHOLD) CV_STABLE_COLOR else CV_UNSTABLE_COLOR
            drawCircle(Color.White, radius = 4f, center = Offset(x, y))
            drawCircle(dotColor, radius = 3f, center = Offset(x, y))
        }

        // X-axis date labels (every 7th)
        days.forEachIndexed { i, dm ->
            if (i % 7 == 0 || i == days.size - 1) {
                val label = LocalDate.ofEpochDay(dm.dateEpochDay).format(dateFormatter)
                val m = textMeasurer.measure(label, style = TextStyle(fontSize = 8.sp, color = Color.Gray))
                val x = idxToX(i)
                drawText(m, topLeft = Offset((x - m.size.width / 2f).coerceIn(leftPad, size.width - m.size.width), size.height - bottomPad + 4f))
            }
        }
    }
}
