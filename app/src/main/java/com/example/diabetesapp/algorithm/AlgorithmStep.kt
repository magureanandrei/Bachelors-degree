package com.example.diabetesapp.algorithm

import com.example.diabetesapp.data.models.PatientContext

/**
 * A single step in the CDSS algorithm pipeline.
 * Each step receives the running calculation state, applies its logic,
 * and returns an updated state. Steps must NOT reorder — the pipeline
 * sequence in AlgorithmPipeline defines execution order.
 */
interface AlgorithmStep {
    val name: String
    fun apply(state: CalculationState, context: PatientContext): CalculationState
}

/**
 * The intermediate state that flows through the pipeline.
 * Immutable — each step returns a new copy.
 */
data class CalculationState(
    val currentDose: Double = 0.0,
    val rescueCarbs: Int = 0,
    val breakdown: List<BreakdownEntry> = emptyList(),
    val warnings: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /** Convenience: add a breakdown entry and return updated state */
    fun addEntry(entry: BreakdownEntry): CalculationState =
        copy(breakdown = breakdown + entry)

    /** Convenience: add a warning and return updated state */
    fun addWarning(warning: String): CalculationState =
        copy(warnings = warnings + warning)

    /** Convenience: store metadata for inter-step communication */
    fun withMeta(key: String, value: Any): CalculationState =
        copy(metadata = metadata + (key to value))
}

/**
 * A single entry in the structured dose breakdown.
 * Each one represents one modifier the algorithm applied.
 * These feed the DoseBreakdownCard UI and the clinician export.
 */
data class BreakdownEntry(
    val stepName: String,        // "Baseline", "Sport Modifier", etc.
    val label: String,           // "Meal Bolus", "Aerobic Reduction"
    val emoji: String,           // "🍽️", "🏃", "💉"
    val description: String,     // "30g carbs ÷ 10 ICR = 3.0U"
    val effect: Effect,          // categorization for UI coloring
    val valueChange: Double? = null,   // +3.0, -1.5, null for warnings
    val percentChange: Double? = null, // 0.25 for +25%, null if absolute
    val runningTotal: Double     // dose after this entry was applied
)

enum class Effect {
    INCREASE,   // dose went up (meal bolus, illness, CGM rising)
    DECREASE,   // dose went down (IOB, sport, heat, CGM falling)
    NEUTRAL,    // informational (no dose change)
    WARNING     // safety alert (rescue carbs, hypo risk)
}