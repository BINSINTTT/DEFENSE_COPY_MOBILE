package com.tomatoinsight.data

import com.tomatoinsight.api.AWAITING_OPENCV_SEVERITY
import com.tomatoinsight.api.AiRecommendationSection
import com.tomatoinsight.api.AiRecommendations
import com.tomatoinsight.api.DiseasePrediction
import com.tomatoinsight.api.DiseaseScore
import com.tomatoinsight.api.ValidationResponse
import com.tomatoinsight.api.diseaseFullDisplayLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeverityDrivenRecommendationTest {

    @Test
    fun oldAndNewLeafCurlNamesUseOneMobileDisplayName() {
        for (name in listOf("Yellow leaf curl virus", "Tomato Yellow Leaf Curl Virus", "TLCV", "Tomato Leaf Curl Virus")) {
            assertEquals("Tomato Leaf Curl Virus (TLCV)", diseaseFullDisplayLabel(name))
        }
        assertEquals("Early Blight", diseaseFullDisplayLabel("Early Blight"))
    }

    @Test
    fun offlineDiseaseResultDoesNotGenerateOrSaveLocalCarePlan() {
        val offline = listOf(DiseaseScore("Early Blight", "EB", 88))
            .asSavedValidationResponse()
            .copy(stage = "offline", severityLabel = "Moderate", affectedAreaPct = 48.0)

        val result = offline.ensureGuidesForAllDetections()
        assertFalse(result.hasAiRecommendations)
        assertEquals(null, result.asSavedRecommendation())

        val olderOfflineGuide = offline.copy(
            severityAssessable = false,
            recommendationSource = "rules",
            aiRecommendations = AiRecommendations(
                primaryDisease = "Early Blight",
                sections = listOf(AiRecommendationSection(
                    disease = "Early Blight",
                    summary = "Old on-device care plan",
                    steps = listOf("Old local action")
                ))
            )
        )
        assertFalse(olderOfflineGuide.ensureGuidesForAllDetections().hasAiRecommendations)

        val onlineGuide = result.copy(
            aiRecommendations = AiRecommendations(
                primaryDisease = "Early Blight",
                sections = listOf(AiRecommendationSection(
                    disease = "Early Blight",
                    summary = "Server guidance",
                    steps = listOf("Check the plant")
                ))
            ),
            recommendationSource = "groq"
        ).ensureGuidesForAllDetections()
        assertTrue(onlineGuide.hasAiRecommendations)
    }

    @Test
    fun reopeningSameHealthyScanPreservesPendingOrMeasuredSeverity() {
        val classified = listOf(DiseaseScore("Healthy", "H", 95)).asSavedValidationResponse()
        val pending = classified.copy(severityReason = AWAITING_OPENCV_SEVERITY)
        val assessed = classified.copy(severityLabel = "Healthy", affectedAreaPct = 0.0, severityAssessable = true)
        fun scan(response: ValidationResponse) = response.toTileScanResult(1)!!.copy(
            scanId = 1L, scannedAtMillis = 1000L, weekNumber = 4
        )
        fun reopen(local: ValidationResponse, cached: ValidationResponse) =
            mergeScanLists(listOf(scan(local)), listOf(scan(cached))).single().response

        assertTrue(reopen(pending, classified).awaitingOpenCvSeverity())
        assertEquals(assessed, reopen(assessed, pending))
        assertEquals(assessed, reopen(pending, assessed))
        val nextScan = scan(pending).copy(scannedAtMillis = 2000L)
        assertTrue(mergeScanLists(listOf(scan(assessed)), listOf(nextScan)).single().response.awaitingOpenCvSeverity())
    }

    @Test
    fun everyDetectedClassReceivesCareAndPreventionWithoutSharingSeverity() {
        val plans = treatmentsForDetectedScores(
            scores = listOf(
                DiseaseScore("Early Blight", "EB", 78),
                DiseaseScore("Tomato Yellow Leaf Curl Virus", "TLCV", 22)
            ),
            severityLabel = "Moderate",
            affectedAreaPct = 46.0
        )

        assertEquals(2, plans.size)
        assertEquals("Early Blight", plans[0].diseaseName)
        assertEquals("Tomato Leaf Curl Virus (TLCV)", plans[1].diseaseName)
        assertEquals(BAR_TLCV, barKeyForDiseaseName(plans[1].diseaseName))
        assertTrue(plans[1].preventionTips.joinToString().contains("whiteflies", ignoreCase = true))
        assertFalse(plans[1].urgencyNote.contains("Moderate", ignoreCase = true))
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
    fun viralRecommendationsManageSpreadByTheCorrectInsect() {
        for ((disease, insect) in listOf(
            "Tomato Yellow Leaf Curl Virus" to "whiteflies",
            "Tomato Spotted Wilt Virus" to "thrips"
        )) {
            val mild = treatmentForTopDisease(disease, "Mild", 18.0)
            val severe = treatmentForTopDisease(disease, "Severe", 82.0)
            assertTrue(mild.summary.contains("no cure", ignoreCase = true))
            assertTrue(mild.preventionTips.joinToString().contains(insect, ignoreCase = true))
            assertNotEquals(mild.actionSteps, severe.actionSteps)
            assertTrue(severe.actionSteps.first().contains("if infection is confirmed", ignoreCase = true))
            assertFalse(mild.isCriticalRemoval)
        }
    }

    @Test
    fun serverCannotReplaceViralManagementWithCureClaim() {
        val result = listOf(DiseaseScore("Spotted wilt", "TSWV", 95))
            .asSavedValidationResponse()
            .copy(
                severityLabel = "Severe",
                affectedAreaPct = 82.0,
                severityAssessable = true,
                aiRecommendations = AiRecommendations(
                    primaryDisease = "Tomato Spotted Wilt Virus",
                    sections = listOf(AiRecommendationSection(
                        disease = "Tomato Spotted Wilt Virus",
                        summary = "A spray cures the virus.",
                        steps = listOf("Spray to cure the infected plant."),
                        prevention = listOf("Ignore thrips.")
                    ))
                ),
                recommendationSource = "groq"
            )

        val section = result.ensureGuidesForAllDetections().aiRecommendations!!.sections!!.single()
        assertTrue(section.summary, section.summary!!.contains("no cure", ignoreCase = true))
        assertTrue(section.prevention!!.joinToString().contains("thrips", ignoreCase = true))
        assertFalse(section.steps!!.joinToString().contains("spray to cure", ignoreCase = true))
        assertTrue(result.recommendationCacheKey().startsWith("multi-guides-v2|"))
    }

    @Test
    fun healthyTopWithSpottedWiltKeepsBothPlansWhenSavedAndReopened() {
        val result = listOf(
            DiseaseScore("Healthy", "H", 31),
            DiseaseScore("Spotted wilt", "TSWV", 23)
        ).asSavedValidationResponse().copy(
            severityLabel = "Healthy",
            affectedAreaPct = 0.0,
            severityAssessable = true,
            aiRecommendations = AiRecommendations(
                primaryDisease = "Healthy",
                sections = listOf(AiRecommendationSection(
                    disease = "Healthy",
                    summary = "This leaf is healthy.",
                    steps = listOf("Keep monitoring.")
                ))
            ),
            recommendationSource = "groq"
        )
        val filled = result.ensureGuidesForAllDetections()
        val sections = filled.aiRecommendations!!.sections!!
        assertEquals(2, sections.size)
        assertTrue(sections[0].summary!!.contains("does not rule out", ignoreCase = true))
        assertEquals("Spotted wilt", sections[1].disease)
        assertTrue(sections[1].prevention!!.joinToString().contains("thrips", ignoreCase = true))
        assertFalse(sections[1].urgency.orEmpty().contains("Healthy"))

        val saved = filled.asSavedRecommendation()!!
        assertEquals(2, saved.guides.size)
        assertEquals(2, saved.asAiRecommendations()!!.sections!!.size)
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
