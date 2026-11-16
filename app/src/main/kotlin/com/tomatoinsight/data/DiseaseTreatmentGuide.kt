package com.tomatoinsight.data

import com.tomatoinsight.api.AiRecommendationSection
import com.tomatoinsight.api.AiRecommendations
import com.tomatoinsight.api.DISEASE_DISPLAY_ORDER
import com.tomatoinsight.api.DiseaseScore
import com.tomatoinsight.api.ValidationResponse
import com.tomatoinsight.api.diseaseSeverityLabel
import com.tomatoinsight.api.isHealthyDisease
import com.tomatoinsight.api.normalizeDiseaseKey

data class DiseaseTreatment(
    val diseaseName: String,
    val urgencyNote: String,
    val summary: String,
    val actionSteps: List<String>,
    val preventionTips: List<String> = emptyList(),
    val isCriticalRemoval: Boolean = false
)

/** One built-in care plan for the top diagnosis only. Class confidence never selects severity. */
fun treatmentsForDetectedScores(
    scores: List<DiseaseScore>,
    severityLabel: String? = null,
    affectedAreaPct: Double? = null
): List<DiseaseTreatment> {
    val top = scores.maxByOrNull { it.percent }?.takeIf { it.percent > 0 } ?: return emptyList()
    return listOf(
        treatmentForTopDisease(
            diseaseName = top.name,
            severityLabel = severityLabel,
            affectedAreaPct = affectedAreaPct
        )
    )
}

fun treatmentForTopDisease(
    diseaseName: String,
    severityLabel: String? = null,
    affectedAreaPct: Double? = null
): DiseaseTreatment {
    val severity = diseaseSeverityLabel(
        name = diseaseName,
        affectedAreaPct = affectedAreaPct,
        explicitLabel = severityLabel
    )
    val key = normalizeDiseaseKey(diseaseName)
    val extentPct = affectedAreaPct?.let { kotlin.math.round(it).toInt().coerceIn(0, 100) }

    if (severity == "Unable to Assess" && !isHealthyDisease(diseaseName)) {
        return DiseaseTreatment(
            diseaseName = diseaseName,
            urgencyNote = "A clearer scan is needed before a care plan can be selected.",
            summary = "The disease classifier identified the most likely condition, but the affected leaf area could not be measured reliably. Do not start treatment from this image alone.",
            actionSteps = listOf(
                "Place one tomato leaf against a plain, evenly lit background.",
                "Keep the full leaf visible and remove hands, soil, other leaves, and shadows from the frame.",
                "Hold the camera steady, focus on the leaf, and scan again."
            )
        )
    }

    val base = when {
        key.contains("earlyblight") || key == "eb" -> earlyBlightTreatment()
        key.contains("spottedwilt") || key.contains("tswv") -> spottedWiltTreatment()
        key.contains("yellowleafcurl") || key.contains("tlcv") -> yellowLeafCurlTreatment()
        isHealthyDisease(diseaseName) -> healthyPlantCare()
        else -> genericDiseaseTreatment(diseaseName)
    }

    val critical =
        !isHealthyDisease(diseaseName) &&
            severity != "Unable to Assess" &&
            (extentPct ?: -1) >= 100
    val urgency = when {
        isHealthyDisease(diseaseName) && severity != "Unable to Assess" ->
            "Keep up regular monitoring to catch problems early."
        critical ->
            "Critical (severity 100%): remove this leaf immediately and destroy it away from the farm. Do not compost."
        severity == "Severe" ->
            "Urgent (estimated extent ${extentPct ?: "—"}%): start treatment within 24–48 hours."
        severity == "Moderate" ->
            "Moderate (estimated extent ${extentPct ?: "—"}%): treat this week before it spreads."
        severity == "Mild" ->
            "Mild (estimated extent ${extentPct ?: "—"}%): monitor closely and treat if symptoms worsen."
        else ->
            "Confirm symptoms on the plant before treating."
    }
    val steps = when {
        critical -> listOf(
            "Isolate the plant immediately.",
            "Remove this leaf now; bag it and dispose of it away from healthy plants. Do not compost it."
        ) + base.actionSteps
        severity == "Severe" -> listOf(
            "Isolate the affected plant from healthy plants today.",
            "Remove heavily affected leaves with sanitized tools and dispose of them away from the farm."
        ) + base.actionSteps
        severity == "Moderate" -> listOf(
            "Mark the affected plant and inspect nearby plants for the same symptoms.",
            "Remove clearly affected leaves with sanitized tools before applying disease-specific controls."
        ) + base.actionSteps.take(4)
        severity == "Mild" -> listOf(
            "Mark the affected leaf and rescan it in 48 hours to check whether the affected area is increasing.",
            "Improve airflow and keep the foliage dry while monitoring nearby leaves."
        ) + base.actionSteps.take(3)
        else -> base.actionSteps
    }

    return base.copy(
        diseaseName = diseaseName,
        urgencyNote = urgency,
        actionSteps = steps.distinct(),
        isCriticalRemoval = critical
    )
}

private fun earlyBlightTreatment() = DiseaseTreatment(
    diseaseName = "Early blight",
    urgencyNote = "",
    summary = "Early blight (Alternaria) causes brown spots with rings on lower leaves and can defoliate plants.",
    actionSteps = listOf(
        "Remove and destroy infected leaves; do not compost them.",
        "Improve airflow by spacing plants and pruning lower foliage.",
        "Avoid overhead watering; water at the base in the morning.",
        "Apply a copper-based or chlorothalonil fungicide per label directions.",
        "Rotate tomatoes to a new area next season; do not replant in the same spot."
    ),
    preventionTips = listOf(
        "Mulch around plants to reduce soil splash onto leaves.",
        "Inspect lower leaves weekly during humid weather."
    )
)

private fun spottedWiltTreatment() = DiseaseTreatment(
    diseaseName = "Spotted wilt",
    urgencyNote = "",
    summary = "Tomato spotted wilt virus (TSWV) is spread by thrips and causes bronze spots, rings, and stunted growth.",
    actionSteps = listOf(
        "Remove and destroy infected plants immediately to limit spread.",
        "Control thrips with insecticidal soap or approved products; focus on undersides of leaves.",
        "Use reflective mulch early in the season to repel thrips.",
        "Remove weeds near the farm that host thrips and the virus.",
        "Do not replant tomatoes in the same bed without thrips management."    
    ),
    preventionTips = listOf(
        "Use TSWV-resistant tomato varieties when available.",
        "Install fine mesh row covers until plants flower, if practical."
    )
)

private fun yellowLeafCurlTreatment() = DiseaseTreatment(
    diseaseName = "Yellow leaf curl virus",
    urgencyNote = "",
    summary = "Tomato yellow leaf curl virus (TYLCV) causes upward curling, yellowing, and reduced fruit set; whiteflies spread it.",
    actionSteps = listOf(
        "Remove severely infected plants to protect nearby healthy ones.",
        "Control whiteflies with yellow sticky traps and approved insecticides.",
        "Spray undersides of leaves where whiteflies feed; repeat as directed.",
        "Remove solanaceous weeds (tomato relatives) around the field.",
        "Avoid bringing new plants from unknown sources into the farm."
    ),
    preventionTips = listOf(
        "Plant TYLCV-tolerant varieties where the virus is common.",
        "Inspect new growth twice weekly for curling and whitefly activity."
    )
)

private fun healthyPlantCare() = DiseaseTreatment(
    diseaseName = "Healthy",
    urgencyNote = "",
    summary = "This leaf looks healthy. Continue good practices to keep plants productive.",
    actionSteps = listOf(
        "Maintain even watering; avoid long dry periods followed by heavy soaking.",
        "Feed with balanced fertilizer according to plant stage.",
        "Stake or trellis plants to keep foliage off the soil.",
        "Scan other tiles regularly so problems are caught early."
    ),
    preventionTips = listOf(
        "Remove yellowing lower leaves to improve airflow.",
        "Sanitize tools if you work on a diseased plant elsewhere."
    )
)

private fun genericDiseaseTreatment(name: String) = DiseaseTreatment(
    diseaseName = name,
    urgencyNote = "",
    summary = "A plant disease was detected. Isolate the affected plant and confirm symptoms before treating.",
    actionSteps = listOf(
        "Photograph symptoms and note which leaves are affected.",
        "Remove heavily damaged tissue and dispose of it away from the farm.",
        "Avoid working wet plants to reduce spread.",
        "Consult a local agricultural extension office for a confirmed diagnosis."
    )
)

/** One care guide for a single detected class (matches website `guides[]`). */
data class SavedGuide(
    val diseaseName: String = "",
    val confidencePercent: Int = 0,
    val severityLabel: String = "",
    val affectedAreaPct: Double? = null,
    val urgencyNote: String = "",
    val summary: String = "",
    val actionSteps: List<String> = emptyList(),
    val preventionTips: List<String> = emptyList(),
    val isCriticalRemoval: Boolean = false
)

/** Care text stored on users/{uid}.plantNames.{tile}.recommendation. */
data class SavedRecommendation(
    val diseaseName: String = "",
    val diagnosisConfidencePercent: Int = 0,
    val severityLabel: String = "",
    val affectedAreaPct: Double? = null,
    val severityAssessable: Boolean = false,
    val cacheKey: String = "",
    val urgencyNote: String = "",
    val summary: String = "",
    val actionSteps: List<String> = emptyList(),
    val preventionTips: List<String> = emptyList(),
    val isCriticalRemoval: Boolean = false,
    /** Every detection above 0%, same shape as the website recommendation guides. */
    val guides: List<SavedGuide> = emptyList()
) {
    fun toFirestore(
        topBarKey: String = "",
        topPct: Double? = null,
        weekNumber: Int? = null,
        source: String = "",
        savedAt: String = "",
        scores: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        val record = linkedMapOf<String, Any>(
            "urgencyNote" to urgencyNote.take(400),
            "summary" to summary.take(800),
            "actionSteps" to actionSteps.take(12),
            "isCriticalRemoval" to isCriticalRemoval
        )
        if (barKeyForDiseaseName(diseaseName) != BAR_HEALTHY && diseaseName.isNotBlank()) {
            record["diseaseName"] = diseaseName.take(80)
        }
        if (topBarKey.isNotBlank()) record["topBarKey"] = topBarKey
        if (topPct != null && topPct > 0.0) record["topPct"] = topPct
        if (diagnosisConfidencePercent > 0) record["diagnosisConfidencePercent"] = diagnosisConfidencePercent
        if (severityLabel.isNotBlank()) record["severityLabel"] = severityLabel
        if (affectedAreaPct != null && affectedAreaPct.isFinite()) {
            record["affectedAreaPct"] = affectedAreaPct.coerceIn(0.0, 100.0)
        }
        record["severityAssessable"] = severityAssessable
        if (cacheKey.isNotBlank()) record["cacheKey"] = cacheKey
        if (weekNumber != null) record["weekNumber"] = weekNumber
        if (preventionTips.isNotEmpty()) record["preventionTips"] = preventionTips.take(8)
        if (source == "groq" || source == "rules") record["source"] = source
        if (savedAt.isNotBlank()) record["savedAt"] = savedAt
        if (scores.isNotEmpty()) record["scores"] = scores
        if (guides.isNotEmpty()) {
            record["guides"] = guides.map { guide ->
                linkedMapOf<String, Any>(
                    "diseaseName" to guide.diseaseName.take(80),
                    "confidencePercent" to guide.confidencePercent.coerceIn(0, 100),
                    "severityLabel" to guide.severityLabel.take(40),
                    "urgencyNote" to guide.urgencyNote.take(400),
                    "summary" to guide.summary.take(800),
                    "actionSteps" to guide.actionSteps.take(12),
                    "preventionTips" to guide.preventionTips.take(8),
                    "isCriticalRemoval" to guide.isCriticalRemoval
                ).apply {
                    if (guide.affectedAreaPct != null && guide.affectedAreaPct.isFinite()) {
                        this["affectedAreaPct"] = guide.affectedAreaPct.coerceIn(0.0, 100.0)
                    }
                }
            }
        }
        return record
    }
}

/** Keeps exactly one care section for the top diagnosis and the measured severity. */
fun ValidationResponse.ensureGuidesForAllDetections(): ValidationResponse {
    if (awaitingOpenCvSeverity()) return this
    val top = allDiseaseScores
        .filter { it.percent > 0 }
        .sortedWith(
            compareByDescending<DiseaseScore> { it.percent }
                .thenBy { score ->
                    DISEASE_DISPLAY_ORDER.indexOfFirst {
                        normalizeDiseaseKey(it) == normalizeDiseaseKey(score.name)
                    }.let { if (it < 0) Int.MAX_VALUE else it }
                }
        )
        .firstOrNull() ?: return this

    fun sectionMatches(section: AiRecommendationSection, score: DiseaseScore): Boolean {
        val a = normalizeDiseaseKey(section.disease.orEmpty())
        val b = normalizeDiseaseKey(score.name)
        if (a.isBlank() || b.isBlank()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    val severity = diseaseSeverityLabel(top.name, affectedAreaPct, severityLabel)
    val mustUseRescanGuide = !isHealthyDisease(top.name) &&
        (severityAssessable == false || severity == "Unable to Assess")
    val fromServer = aiRecommendations?.sections.orEmpty().firstOrNull { sectionMatches(it, top) }
    val section = if (fromServer != null && !mustUseRescanGuide) {
        fromServer.copy(
            disease = fromServer.disease?.ifBlank { top.name } ?: top.name,
            confidencePercent = top.percent
        )
    } else {
        treatmentForTopDisease(
            diseaseName = top.name,
            severityLabel = severity,
            affectedAreaPct = affectedAreaPct
        ).toAiSection(top.percent)
    }
    return copy(
        aiRecommendations = AiRecommendations(
            primaryDisease = section.disease ?: top.name,
            combinedNote = section.summary,
            sections = listOf(section)
        ),
        recommendationSource = recommendationSource ?: "rules"
    )
}

private fun DiseaseTreatment.toAiSection(percent: Int): AiRecommendationSection =
    AiRecommendationSection(
        disease = diseaseName,
        confidencePercent = percent,
        urgency = urgencyNote,
        summary = summary,
        steps = actionSteps,
        prevention = preventionTips,
        isCriticalRemoval = isCriticalRemoval
    )

fun List<DiseaseScore>.recommendationScoresKey(): String =
    MONITORING_BAR_KEYS.joinToString("|") { key ->
        val percent = firstOrNull { barKeyForDiseaseName(it.name) == key }?.percent ?: 0
        "$key:$percent"
    }

fun ValidationResponse.recommendationCacheKey(): String {
    val top = allDiseaseScores.maxByOrNull { it.percent }
    val topKey = top?.name?.let(::normalizeDiseaseKey).orEmpty()
    val area = affectedAreaPct
        ?.takeIf { it.isFinite() }
        ?.let { kotlin.math.round(it.coerceIn(0.0, 100.0) * 10.0) / 10.0 }
        ?.toString()
        ?: "na"
    return listOf(
        topKey,
        (top?.percent ?: 0).toString(),
        severityLabel.orEmpty().lowercase(),
        area,
        (severityAssessable == true).toString()
    ).joinToString("|")
}

/** Care guide returned by the server for this scan, in the shape stored on plantNames. */
fun ValidationResponse.asSavedRecommendation(): SavedRecommendation? {
    val filled = ensureGuidesForAllDetections()
    val top = filled.allDiseaseScores.maxByOrNull { it.percent } ?: return null
    if (top.percent <= 0) return null
    val sections = filled.aiRecommendations?.sections.orEmpty().filter { section ->
        !section.summary.isNullOrBlank() || !section.steps.isNullOrEmpty()
    }
    if (sections.isEmpty()) return null
    val section = sections.firstOrNull { item ->
        val disease = item.disease.orEmpty()
        disease.isNotBlank() && (
            disease.equals(top.name, ignoreCase = true) ||
                normalizeDiseaseKey(disease) == normalizeDiseaseKey(top.name)
            )
    } ?: sections.first()
    val guides = listOf(section).map { item ->
        SavedGuide(
            diseaseName = item.disease?.trim().orEmpty().ifBlank { top.name },
            confidencePercent = item.confidencePercent ?: 0,
            severityLabel = filled.severityLabel.orEmpty(),
            affectedAreaPct = filled.affectedAreaPct,
            urgencyNote = item.urgency?.trim().orEmpty(),
            summary = item.summary?.trim().orEmpty(),
            actionSteps = item.steps.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(12),
            preventionTips = item.prevention.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(8),
            isCriticalRemoval = item.isCriticalRemoval
                ?: (!isHealthyDisease(item.disease.orEmpty()) && (item.confidencePercent ?: 0) >= 100)
        )
    }
    val critical = section.isCriticalRemoval
        ?: (!isHealthyDisease(top.name) && top.percent >= 100)
    val summary = listOfNotNull(
        filled.aiRecommendations?.combinedNote?.trim()?.ifBlank { null },
        section.summary?.trim()
    ).distinct().joinToString("\n\n")
    return SavedRecommendation(
        diseaseName = section.disease?.trim().orEmpty().ifBlank { top.name },
        diagnosisConfidencePercent = top.percent,
        severityLabel = filled.severityLabel.orEmpty(),
        affectedAreaPct = filled.affectedAreaPct,
        severityAssessable = filled.severityAssessable == true,
        cacheKey = filled.recommendationCacheKey(),
        urgencyNote = section.urgency?.trim().orEmpty(),
        summary = summary,
        actionSteps = section.steps.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(12),
        preventionTips = section.prevention.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(8),
        isCriticalRemoval = critical,
        guides = guides
    )
}

/** Rebuild the single top-diagnosis section from a saved recommendation. */
fun SavedRecommendation.asAiRecommendations(): AiRecommendations? {
    val sections = when {
        guides.isNotEmpty() -> guides.take(1).map { guide ->
            AiRecommendationSection(
                disease = guide.diseaseName,
                confidencePercent = guide.confidencePercent,
                urgency = guide.urgencyNote,
                summary = guide.summary,
                steps = guide.actionSteps,
                prevention = guide.preventionTips,
                isCriticalRemoval = guide.isCriticalRemoval
            )
        }
        summary.isNotBlank() || actionSteps.isNotEmpty() -> listOf(
            AiRecommendationSection(
                disease = diseaseName,
                confidencePercent = diagnosisConfidencePercent.takeIf { it > 0 },
                urgency = urgencyNote,
                summary = summary,
                steps = actionSteps,
                prevention = preventionTips,
                isCriticalRemoval = isCriticalRemoval
            )
        )
        else -> emptyList()
    }
    if (sections.isEmpty()) return null
    return AiRecommendations(
        primaryDisease = sections.first().disease ?: diseaseName,
        combinedNote = summary.ifBlank { sections.first().summary },
        sections = listOf(sections.first())
    )
}
