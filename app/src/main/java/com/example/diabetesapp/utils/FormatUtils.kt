package com.example.diabetesapp.utils

/**
 * UI formatting utilities
 */
object FormatUtils {

    /**
     * Format a float value for display, removing trailing zeros
     * Examples: 8.0 -> "8", 8.5 -> "8.5", 10.25 -> "10.25"
     */
    fun formatDecimal(value: Float): String {
        return if (value % 1.0f == 0.0f) {
            value.toInt().toString()
        } else {
            value.toString().trimEnd('0').trimEnd('.')
        }
    }

    /**
     * Format a double value for UI display
     * If it's a whole number (e.g., 4.0), return "4"
     * If it has decimals (e.g., 4.5), return "4.5"
     *
     * Examples:
     * - 4.0 -> "4"
     * - 4.5 -> "4.5"
     * - 10.0 -> "10"
     * - 10.25 -> "10.25"
     * - 8.00 -> "8"
     */
    fun formatDoubleForUi(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            // Remove unnecessary trailing zeros
            val formatted = String.format("%.10f", value).trimEnd('0').trimEnd('.')
            formatted
        }
    }

    /**
     * Check if input text is a valid partial decimal number
     * Allows: empty, digits, single decimal point
     * Examples: "", "1", "1.", "1.2" are valid
     * Examples: "1.2.3", "abc", "1 2" are invalid
     */
    fun isValidPartialDecimal(text: String): Boolean {
        if (text.isEmpty()) return true
        return text.matches(Regex("^\\d*\\.?\\d*$"))
    }

    /**
     * Smart decimal input filtering for duration field
     * Allows users to delete the decimal point and type naturally
     * Validates that the value stays within min/max bounds
     */
    fun filterDurationInput(
        newText: String,
        currentText: String,
        min: Double = 1.0,
        max: Double = 8.0
    ): String? {
        // Allow empty input
        if (newText.isEmpty()) return newText

        // Allow single decimal point
        if (newText == ".") return newText

        // Check if it's a valid partial decimal
        if (!isValidPartialDecimal(newText)) return null

        // If there's a complete number, check bounds
        val number = newText.toDoubleOrNull()
        if (number != null) {
            // Allow typing if within bounds or still building the number
            if (number > max && newText.length > currentText.length) {
                // User is adding digits and exceeded max - reject
                return null
            }
        }

        return newText
    }

    /**
     * Generate duration options in 15-minute increments
     * From min to max hours
     */
    fun generateDurationOptions(min: Double = 1.0, max: Double = 8.0): List<String> {
        val options = mutableListOf<String>()
        var current = min

        while (current <= max) {
            options.add(formatDoubleForUi(current))
            current += 0.25 // 15 minutes = 0.25 hours
        }

        return options
    }

    /**
     * Convert hours string to display format
     * Examples: "4" -> "4 hours", "4.5" -> "4h 30m", "4.25" -> "4h 15m"
     */
    fun formatDurationDisplay(hours: String): String {
        val value = hours.toDoubleOrNull() ?: return hours
        val wholeHours = value.toInt()
        val minutesDecimal = value - wholeHours
        val minutes = (minutesDecimal * 60).toInt()

        return when {
            minutes == 0 -> if (wholeHours == 1) "1 hour" else "$wholeHours hours"
            wholeHours == 0 -> "${minutes} min"
            else -> "${wholeHours}h ${minutes} min"
        }
    }

    /**
     * Parse input string to Double safely
     * Returns null if the string is empty or invalid
     */
    fun parseDoubleOrNull(text: String): Double? {
        if (text.isEmpty() || text == ".") return null
        return text.toDoubleOrNull()
    }
}

