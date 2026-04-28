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
            HypoGuardStep(),         // 0. BG below hypo limit — warn before any calculation
            BaselineStep(),          // 1. Meal + Correction bolus (therapy-aware)
            OutsideFactorsStep(),    // 2. Illness/Stress/Heat multipliers
            IobDeductionStep(),      // 3. Subtract active insulin
            CgmTrendStep(),          // 4. CGM velocity modifiers (AID-aware)
            SportModifierStep(),     // 5. Exercise reductions + therapy-specific advice
            BasalAwarenessStep(),    // 6. MDI basal warnings
            NighttimeSafetyStep(),   // 7. Nighttime correction reduction + overnight warnings
            MaxBolusCapStep()        // 8. Safety cap
        )
    )

    fun calculateClinicalAdvice(context: PatientContext): ClinicalDecision {
        return pipeline.execute(context)
    }
}