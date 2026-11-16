package com.tomatoinsight.data

import com.tomatoinsight.api.DiseasePrediction
import com.tomatoinsight.api.DiseaseScore
import com.tomatoinsight.api.ValidationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeverityDrivenRecommendationTest {

    @Test
    fun onlyTopDiagnosisReceivesCarePlan() {
        val plans = treatmentsForDetectedScores(
            scores = listOf(
                DiseaseScore("Early Blight", "EB", 78),
                DiseaseScore("Tomato Yellow Leaf Curl Virus", "TLCV", 22)
            ),
            severityLabel = "Moderate",
            affectedAreaPct = 46.0
        )

        assertEquals(1, plans.size)
        assertEquals("Early Blight", plans.single().diseaseName)
    }

    @Test
    fun severityChangesUrgencyAndTreatmentSteps() {
        val mild = treatmentForTopDisease("Early Blight", "Mild", 18.0)
        val moderate = treatmentForTopDisease("Early Blight", "Moderate", 48.0)
        val severe = treatmentForTopDisease("Early Blight", "Severe", 82.0)

        assertNotEquals(mild.urgencyNote, moderate.urgencyNote)
        assertNotEquals(moderate.urgencyNote, severe.urgencyNote)
        assertNotEquals(mild.actionSteps, moderate.actionSteps)
        assertNotEquals(moderate.actionSteps, severe.actionSteps)
    }

    @Test
    fun measuredAreaOverridesConflictingSeverityLabel() {
        val treatment = treatmentForTopDisease(
            diseaseName = "Early Blight",
            severityLabel = "Severe",
            affectedAreaPct = 18.0
        )

        assertTrue(treatment.urgencyNote.startsWith("Mild"))
    }

    @Test
    fun unassessableSeverityReturnsRescanGuidanceInsteadOfTreatment() {
        val plan = treatmentForTopDisease(
            diseaseName = "Early Blight",
            severityLabel = "Unable to Assess",
            affectedAreaPct = null
        )

        assertTrue(plan.urgencyNote.contains("clearer scan", ignoreCase = true))
        assertTrue(plan.summary.contains("Do not start treatment", ignoreCase = true))
        assertTrue(plan.actionSteps.all { step ->
            listOf("leaf", "frame", "camera", "scan").any { word -> step.contains(word, ignoreCase = true) }
        })
        assertFalse(plan.isCriticalRemoval)
    }

    @Test
    fun recommendationCacheKeyIncludesSeverityAndAffectedArea() {
        val base = ValidationResponse(
            result = "TomatoLeaf",
            topDisease = "Early Blight",
            topDiseaseConfidence = 0.82,
            predictions = listOf(DiseasePrediction(className = "Early Blight", confidence = 0.82)),
            severityAssessable = true
        )
        val mild = base.copy(severityLabel = "Mild", affectedAreaPct = 18.0)
        val severe = base.copy(severityLabel = "Severe", affectedAreaPct = 82.0)

        assertNotEquals(mild.recommendationCacheKey(), severe.recommendationCacheKey())
    }
}
