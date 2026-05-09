package com.example.diabetesapp.ui.components.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TBR_COLOR = Color(0xFFE53935)
val TIR_COLOR = Color(0xFF2E7D32)
val TAR_COLOR = Color(0xFFF9A825)

@Composable
fun TirStackedBar(
    tbr: Float,
    tir: Float,
    tar: Float,
    modifier: Modifier = Modifier,
    heightDp: Dp = 28.dp,
    showLabels: Boolean = true
) {
    Canvas(modifier = Modifier.fillMaxWidth().height(heightDp).then(modifier)) {
        val w = size.width
        val h = size.height
        val tbrW = w * (tbr / 100f).coerceIn(0f, 1f)
        val tirW = w * (tir / 100f).coerceIn(0f, 1f)
        val tarW = w - tbrW - tirW

        drawRect(TBR_COLOR, topLeft = Offset(0f, 0f), size = Size(tbrW, h))
        drawRect(TIR_COLOR, topLeft = Offset(tbrW, 0f), size = Size(tirW, h))
        drawRect(TAR_COLOR, topLeft = Offset(tbrW + tirW, 0f), size = Size(tarW, h))

        if (showLabels) {
            val textSizePx = 11.sp.toPx()
            val barCenterY = h / 2f + textSizePx / 3f

            data class Seg(val pct: Float, val startX: Float, val segW: Float)
            val segments = listOf(
                Seg(tbr, 0f, tbrW),
                Seg(tir, tbrW, tirW),
                Seg(tar, tbrW + tirW, tarW)
            )

            drawIntoCanvas { composeCanvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = textSizePx
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                segments.forEach { (pct, startX, segW) ->
                    if (pct >= 15f) {
                        composeCanvas.nativeCanvas.drawText(
                            "${pct.toInt()}%",
                            startX + segW / 2f,
                            barCenterY,
                            paint
                        )
                    }
                }
            }
        }
    }
}
