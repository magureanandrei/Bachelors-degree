package com.example.diabetesapp.utils

import com.example.diabetesapp.algorithm.AlgorithmPipeline
import com.example.diabetesapp.algorithm.steps.BasalAwarenessStep
import com.example.diabetesapp.algorithm.steps.BaselineStep
import com.example.diabetesapp.algorithm.steps.CgmTrendStep
import com.example.diabetesapp.algorithm.steps.ContextualModifierStep
import com.example.diabetesapp.algorithm.steps.HypoGuardStep
import com.example.diabetesapp.algorithm.steps.IobDeductionStep
import com.example.diabetesapp.algorithm.steps.MaxBolusCapStep
import com.example.diabetesapp.algorithm.steps.NighttimeSafetyStep
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
            HypoGuardStep(),              // 0. Immediate BG safety
            BaselineStep(),               // 1. Raw meal + correction (therapy-aware)
            IobDeductionStep(),           // 2. Subtract active insulin
            CgmTrendStep(),               // 3. Real-time BG trend
            ContextualModifierStep(),     // 4. ONE dominant modifier (exercise/illness/stress/heat)
            NighttimeSafetyStep(),        // 5. Nighttime warnings (no dose change)
            BasalAwarenessStep(),         // 6. MDI basal warnings
            MaxBolusCapStep()             // 7. Safety cap
        )
    )

    fun calculateClinicalAdvice(context: PatientContext): ClinicalDecision {
        return pipeline.execute(context)
    }
}