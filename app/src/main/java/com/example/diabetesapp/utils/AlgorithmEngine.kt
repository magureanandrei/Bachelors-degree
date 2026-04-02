package com.example.diabetesapp.utils

import com.example.diabetesapp.algorithm.AlgorithmPipeline
import com.example.diabetesapp.algorithm.steps.*
import com.example.diabetesapp.data.models.ClinicalDecision
import com.example.diabetesapp.data.models.PatientContext

/**
 * The single entry point for the Clinical Decision Support System (CDSS).
 * Delegates all calculation to the modular AlgorithmPipeline.
 *
 * Step execution order matters — do not reorder without clinical review.
 */
object AlgorithmEngine {

    private val pipeline = AlgorithmPipeline(
        listOf(
            BaselineStep(),          // 1. Meal + Correction bolus
            OutsideFactorsStep(),    // 2. Illness/Stress/Heat multipliers
            IobDeductionStep(),      // 3. Subtract active insulin
            CgmTrendStep(),          // 4. CGM velocity modifiers
            SportModifierStep()      // 5. Exercise reductions + carb advice
        )
    )

    fun calculateClinicalAdvice(context: PatientContext): ClinicalDecision {
        return pipeline.execute(context)
    }
}