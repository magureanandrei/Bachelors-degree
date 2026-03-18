package com.example.diabetesapp.data.models

data class HypoPrediction(
    val minutesUntilHypo: Int,
    val predictedBgAtHypo: Float,
    val projectionPoints: List<Pair<Long, Float>>
)