package com.example.diabetesapp.utils

/**
 * Validation utilities for Bolus Settings
 */
object ValidationUtils {

    /**
     * Validation ranges for medical values
     */
    object Ranges {
        const val DURATION_MIN = 1.0
        const val DURATION_MAX = 8.0

        const val TARGET_BG_MIN = 90.0
        const val TARGET_BG_MAX = 120.0

        const val ICR_MIN = 1.0
        const val ICR_MAX = 150.0

        const val ISF_MIN = 5.0
        const val ISF_MAX = 200.0
    }

    /**
     * Validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    ) {
        companion object {
            fun success() = ValidationResult(true, null)
            fun error(message: String) = ValidationResult(false, message)
        }
    }

    /**
     * Check if a string represents a valid number within a range
     */
    fun isValidNumber(input: String, min: Double, max: Double): ValidationResult {
        if (input.isBlank()) {
            return ValidationResult.error("This field is required")
        }

        val number = input.toDoubleOrNull()

        return when {
            number == null -> ValidationResult.error("Please enter a valid number")
            number < min -> ValidationResult.error("Value must be at least $min")
            number > max -> ValidationResult.error("Value must be at most $max")
            number == 0.0 -> ValidationResult.error("Value cannot be zero (risk of division by zero)")
            else -> ValidationResult.success()
        }
    }

    /**
     * Validate duration of action
     */
    fun validateDuration(input: String): ValidationResult {
        return isValidNumber(input, Ranges.DURATION_MIN, Ranges.DURATION_MAX)
    }

    /**
     * Validate target BG
     */
    fun validateTargetBG(input: String): ValidationResult {
        return isValidNumber(input, Ranges.TARGET_BG_MIN, Ranges.TARGET_BG_MAX)
    }

    /**
     * Validate ICR value
     */
    fun validateICR(input: String): ValidationResult {
        return isValidNumber(input, Ranges.ICR_MIN, Ranges.ICR_MAX)
    }

    /**
     * Validate ISF value
     */
    fun validateISF(input: String): ValidationResult {
        return isValidNumber(input, Ranges.ISF_MIN, Ranges.ISF_MAX)
    }

    /**
     * Validate all 4 time segment values
     */
    fun validateTimeSegments(
        morning: String,
        noon: String,
        evening: String,
        night: String,
        validator: (String) -> ValidationResult
    ): Map<String, ValidationResult> {
        return mapOf(
            "morning" to validator(morning),
            "noon" to validator(noon),
            "evening" to validator(evening),
            "night" to validator(night)
        )
    }
}

