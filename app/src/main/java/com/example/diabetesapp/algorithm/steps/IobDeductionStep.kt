package com.example.diabetesapp.algorithm.steps

import com.example.diabetesapp.algorithm.*
import com.example.diabetesapp.data.models.PatientContext

/**
 * Step 3: Active Insulin (IOB) Deduction
 * Subtracts insulin-on-board from the running dose, floors at 0.
 * Source: Standard IOB deduction (universal in bolus calculators).
 */
class IobDeductionStep : AlgorithmStep {
    override val name = "IOB Deduction"

    override fun apply(state: CalculationState, context: PatientContext): CalculationState {
        if (context.activeInsulinIOB <= 0 || state.currentDose <= 0) return state

        val deduction = minOf(context.activeInsulinIOB, state.currentDose)
        val newDose = maxOf(0.0, state.currentDose - context.activeInsulinIOB)

        return state
            .addEntry(BreakdownEntry(
                stepName = name,
                label = "Active Insulin (IOB)",
                emoji = "💉",
                description = "💉 Active IOB: -${String.format("%.1f", deduction)}U deducted.",
                effect = Effect.DECREASE,
                valueChange = -deduction,
                runningTotal = newDose
            ))
            .copy(currentDose = newDose)
    }
}