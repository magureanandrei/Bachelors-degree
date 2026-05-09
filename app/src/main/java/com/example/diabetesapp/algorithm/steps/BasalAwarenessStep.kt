package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext
import com.example.diabetesapp.data.models.TherapyType

/**
 * Step 6: Basal Awareness (MDI only)
 * Warns if no basal insulin was logged today, and reminds that long-acting
 * insulin cannot be suspended during exercise.
 *
 * MDI basal constraint: ISPAD 2022 Exercise Chapter (adolfsson2022ispad)
 * Basal omission DKA risk: ISPAD Sick Day Guidelines
 */
class BasalAwarenessStep : AlgorithmStep {
    override val name = "Basal Awareness"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        if (context.therapyType != TherapyType.MDI) return state

        var result = state

        if (context.bolusSettings.hasBasalConfigured && context.basalDoseToday == 0.0) {
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Missing Basal Insulin",
                emoji = "⚠️",
                description = "No basal insulin has been logged today. Missing long-acting insulin " +
                    "can lead to dangerously high blood glucose and risk of diabetic ketoacidosis (DKA). " +
                    "Please verify your basal dose.",
                effect = Effect.WARNING,
                runningTotal = result.currentDose
            ))
        }

        if (context.isDoingSport && context.basalDoseToday > 0) {
            val basalType = context.bolusSettings.basalInsulinType.displayName
            val dose = String.format("%.1f", context.basalDoseToday)
            result = result.addEntry(BreakdownEntry(
                stepName = name,
                label = "Basal Insulin Active",
                emoji = "💉",
                description = "Your long-acting basal insulin ($basalType, ${dose}U) is still active and " +
                    "cannot be suspended. Carbohydrate intake is your primary tool to prevent " +
                    "exercise-induced hypoglycemia.",
                effect = Effect.NEUTRAL,
                runningTotal = result.currentDose
            ))
        }

        return result
    }
}
