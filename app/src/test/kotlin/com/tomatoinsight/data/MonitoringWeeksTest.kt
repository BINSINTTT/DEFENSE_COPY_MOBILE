package com.tomatoinsight.data

import com.tomatoinsight.api.AWAITING_OPENCV_SEVERITY
import com.tomatoinsight.api.DiseaseScore
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringWeeksTest {
    @Test
    fun monitoringRestoresOnlyTheGuideMatchingThatWeeksScan() {
        val guide = mapOf(
            "diseaseName" to "Early blight", "pct" to 92, "severity" to "Mild",
            "affectedAreaPct" to 12.0, "summary" to "Saved Week 4 guidance",
            "scoresKey" to "healthy:0|tlcv:0|early_blight:92|spotted_wilt:0"
        )
        val first = mapOf(
            "scores" to mapOf("early_blight" to 92), "severityLabel" to "Mild",
            "affectedAreaPct" to 12.0, "severityAssessable" to true, "intervention" to guide
        )
        val later = first + mapOf("severityLabel" to "Severe", "affectedAreaPct" to 74.0)
        val record = mapOf("weekNumber" to 6, "weeks" to mapOf("4" to first, "6" to later))
        val weeks = mapOf(1 to record.toTileScans(1)).toMonitoringTiles().single().weeks
        val saved = weeks.first().response
        assertEquals("Saved Week 4 guidance", saved.ensureGuidesForAllDetections().aiRecommendations?.sections?.first()?.summary)
        val fallback = weeks.last().response
        assertEquals(false, fallback.hasAiRecommendations)
        assertEquals(true, fallback.ensureGuidesForAllDetections().hasAiRecommendations)
        assertEquals("Severe", weeks.last().severity)
    }

    @Test
    fun comparisonUsesEachWeeksMeasuredSeverityWithTheSameClassificationScore() {
        val response = listOf(DiseaseScore("Early blight", "EB", 92)).asSavedValidationResponse()
        val first = requireNotNull(response.copy(affectedAreaPct = 74.0).toTileScanResult(1))
            .copy(weekNumber = 4)
        val later = first.copy(weekNumber = 6, response = response.copy(affectedAreaPct = 12.0))

        val weeks = mapOf(1 to listOf(later, first)).toMonitoringTiles().single().weeks
        assertEquals("Mild", weeks.last().severity)
        assertEquals("Severe", previousSavedWeek(weeks, weeks.last())?.severity)
    }

    @Test
    fun missingPendingAndUnassessableMeasurementsKeepTheirTileLabels() {
        val response = listOf(DiseaseScore("Early blight", "EB", 92)).asSavedValidationResponse()
        val scan = requireNotNull(response.toTileScanResult(1))
        val legacyScans = listOf(
            scan.copy(scannedAtMillis = 1),
            scan.copy(scannedAtMillis = 2, response = response.copy(severityReason = AWAITING_OPENCV_SEVERITY)),
            scan.copy(scannedAtMillis = 3, response = response.copy(severityAssessable = false))
        )
        val weeks = mapOf(1 to legacyScans).toMonitoringTiles().single().weeks
        assertEquals(listOf("—", "—", "Unable to Assess"), weeks.map { it.severity })
    }
}
