package com.tomatoinsight.ml

import com.tomatoinsight.api.DiseasePrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDiseaseClassifierConfidenceTest {
    @Test
    fun detectorUsesMetadataClassOrderAndStrongestValidBox() {
        val count = 3
        val output = FloatArray(8 * count)
        fun box(index: Int, width: Float, scores: FloatArray) {
            output[index] = 0.5f
            output[count + index] = 0.5f
            output[2 * count + index] = width
            output[3 * count + index] = 0.2f
            scores.forEachIndexed { classIndex, score ->
                output[(4 + classIndex) * count + index] = score
            }
        }
        box(0, 0.2f, floatArrayOf(0.81f, 0.11f, 0.2f, 0.1f))
        box(1, 0.3f, floatArrayOf(0.66f, 0.63f, 0.9f, 0.2f))
        box(2, 0f, floatArrayOf(0f, 0f, 0f, 0.99f))

        val decoded = decodeDiseaseDetections(output, count)
        assertEquals(
            listOf("Early blight", "Yellow leaf curl virus", "Spotted wilt", "Healthy"),
            decoded.map { it.displayName }
        )
        assertEquals(0.81, decoded[0].confidence!!, 0.0001)
        assertEquals(0.63, decoded[1].confidence!!, 0.0001)
        assertEquals(0.9, decoded[2].confidence!!, 0.0001)
        assertEquals(0.2, decoded[3].confidence!!, 0.0001)
        assertEquals(4, confidentDiseasePredictions(decoded).size)
    }

    @Test
    fun acceptsTwentyPercentAndRejectsLowerScores() {
        val predictions = listOf(
            DiseasePrediction(className = "Healthy", confidence = 0.19),
            DiseasePrediction(className = "Early blight", confidence = 0.20),
            DiseasePrediction(className = "Spotted wilt", confidence = 0.41)
        )

        val accepted = confidentDiseasePredictions(predictions)

        assertEquals(listOf(predictions[1], predictions[2]), accepted)
    }

    @Test
    fun noDiseaseIsReportedWhenEveryScoreIsBelowTwentyPercent() {
        assertTrue(
            confidentDiseasePredictions(
                listOf(DiseasePrediction(className = "Healthy", confidence = 0.19))
            ).isEmpty()
        )
    }
}
