package com.example.diabetesapp.data.models

enum class DurationOfAction(val displayName: String, val hours: Int) {
    THREE_HOURS("3 hours", 3),
    FOUR_HOURS("4 hours", 4),
    FIVE_HOURS("5 hours", 5);

    companion object {
        fun fromHours(hours: Int): DurationOfAction {
            return entries.find { it.hours == hours } ?: FOUR_HOURS
        }
    }
}

