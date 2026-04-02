package com.example.diabetesapp.algorithm

import com.example.diabetesapp.data.models.ClinicalDecision
import com.example.diabetesapp.data.models.PatientContext

/**
 * Executes the CDSS algorithm as an ordered pipeline of steps.
 * Each step transforms the CalculationState in sequence.
 * The step order is defined by the list passed to the constructor.
 */
class AlgorithmPipeline(private val steps: List<AlgorithmStep>) {

    fun execute(context: PatientContext): ClinicalDecision {
        val initialState = CalculationState()

        val finalState = steps.fold(initialState) { state, step ->
            step.apply(state, context)
        }

        val finalDose = roundDose(finalState.currentDose)

        return ClinicalDecision(
            suggestedInsulinDose = finalDose,
            suggestedRescueCarbs = finalState.rescueCarbs,
            clinicalRationale = buildRationaleString(finalState),
            breakdownSteps = finalState.breakdown
        )
    }

    /**
     * Reconstructs the flat rationale string from breakdown entries + warnings.
     * This maintains backward compatibility with all existing UI components
     * that read clinicalRationale (SmartBolusResultDialog, LogDetailsDialog, etc.)
     */
    private fun buildRationaleString(state: CalculationState): String {
        val parts = mutableListOf<String>()

        for (entry in state.breakdown) {
            parts.add(entry.description)
        }

        for (warning in state.warnings) {
            parts.add(warning)
        }

        return parts.joinToString("\n").trim()
    }

    private fun roundDose(dose: Double): Double {
        return Math.round(maxOf(0.0, dose) * 10.0) / 10.0
    }
}