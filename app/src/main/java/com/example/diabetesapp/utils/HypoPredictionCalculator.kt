package com.example.diabetesapp.utils

import com.example.diabetesapp.data.models.HypoPrediction
import com.example.diabetesapp.utils.CgmReading

object HypoPredictionCalculator {

    fun calculate(
        readings: List<CgmReading>,
        hypoLimit: Float
    ): HypoPrediction? {
        val recent = readings
            .filter { it.bgValue > 0 }
            .sortedByDescending { it.timestamp }
            .take(8)
            .reversed()

        if (recent.size < 4) return null

        // Staleness check — don't predict from stale data
        val minutesSinceLastReading = ((System.currentTimeMillis() - recent.last().timestamp) / 60000).toInt()
        if (minutesSinceLastReading > 15) return null

        // Recent trend check — only predict if last 3 readings are actually dropping
        val veryRecent = recent.takeLast(4)
        val recentSlope = (veryRecent.last().bgValue - veryRecent.first().bgValue).toFloat() /
                ((veryRecent.last().timestamp - veryRecent.first().timestamp) / 60000f)
        if (recentSlope >= -0.3f) return null

        val tBase = recent.first().timestamp
        val xs = recent.map { (it.timestamp - tBase).toFloat() / 60000f }
        val ys = recent.map { it.bgValue.toFloat() }

        // Recency weights
        val weights = xs.indices.map { i -> 0.2f + 0.8f * (i.toFloat() / (xs.size - 1)) }

        // Weighted quadratic regression
        var sw = 0.0; var swx = 0.0; var swx2 = 0.0
        var swx3 = 0.0; var swx4 = 0.0
        var swy = 0.0; var swxy = 0.0; var swx2y = 0.0

        for (i in xs.indices) {
            val w = weights[i].toDouble()
            val x = xs[i].toDouble()
            val y = ys[i].toDouble()
            val x2 = x * x
            sw += w; swx += w * x; swx2 += w * x2
            swx3 += w * x2 * x; swx4 += w * x2 * x2
            swy += w * y; swxy += w * x * y; swx2y += w * x2 * y
        }

        // Gaussian elimination
        val mat = Array(3) { DoubleArray(4) }
        mat[0][0] = swx4; mat[0][1] = swx3; mat[0][2] = swx2; mat[0][3] = swx2y
        mat[1][0] = swx3; mat[1][1] = swx2; mat[1][2] = swx;  mat[1][3] = swxy
        mat[2][0] = swx2; mat[2][1] = swx;  mat[2][2] = sw;   mat[2][3] = swy

        for (col in 0..1) {
            for (row in col + 1..2) {
                if (mat[col][col] == 0.0) return null
                val factor = mat[row][col] / mat[col][col]
                for (j in col..3) mat[row][j] -= factor * mat[col][j]
            }
        }

        if (mat[2][2] == 0.0 || mat[1][1] == 0.0 || mat[0][0] == 0.0) return null

        val c = mat[2][3] / mat[2][2]
        val b = (mat[1][3] - mat[1][2] * c) / mat[1][1]
        val a = (mat[0][3] - mat[0][2] * c - mat[0][1] * b) / mat[0][0]

        val latestX = xs.last().toDouble()
        val currentBg = (a * latestX * latestX + b * latestX + c).toFloat()
        if (currentBg <= hypoLimit) return null

        // Project forward
        val now = System.currentTimeMillis()
        val latestTimestamp = recent.last().timestamp
        val projectionPoints = mutableListOf<Pair<Long, Float>>()
        projectionPoints.add(Pair(latestTimestamp, recent.last().bgValue.toFloat()))
        var minutesFromNow = -1

        for (m in 1..90) {
            val t = latestTimestamp + m * 60 * 1000L
            val minutesFromBase = (t - tBase).toDouble() / 60000.0
            val projectedBg = (a * minutesFromBase * minutesFromBase + b * minutesFromBase + c).toFloat()
            projectionPoints.add(Pair(t, projectedBg))

            if (projectedBg <= hypoLimit && minutesFromNow == -1) {
                minutesFromNow = ((t - now) / 60000).toInt()
            }
            if (projectedBg <= hypoLimit - 15f) break
        }

        if (minutesFromNow == -1 || minutesFromNow > 60) return null

        return HypoPrediction(
            minutesUntilHypo = minutesFromNow,
            predictedBgAtHypo = hypoLimit,
            projectionPoints = projectionPoints
        )
    }
}