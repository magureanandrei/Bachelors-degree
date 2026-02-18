package com.example.diabetesapp.data.models

import java.util.Calendar

enum class TimeSegment(
    val displayName: String,
    val icon: String,
    val timeRange: String,
    val startHour: Int,
    val endHour: Int
) {
    MORNING("Morning", "🌅", "06-11", 6, 11),
    NOON("Noon", "☀️", "11-16", 11, 16),
    EVENING("Evening", "🌇", "16-23", 16, 23),
    NIGHT("Night", "🌙", "23-06", 23, 6);

    companion object {
        /**
         * Get the current time segment based on system clock
         */
        fun getCurrentSegment(): TimeSegment {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            return getSegmentForHour(hour)
        }

        /**
         * Get time segment for a specific hour (0-23)
         */
        fun getSegmentForHour(hour: Int): TimeSegment {
            return when (hour) {
                in 6..10 -> MORNING
                in 11..15 -> NOON
                in 16..22 -> EVENING
                else -> NIGHT // 23-5
            }
        }
    }
}

