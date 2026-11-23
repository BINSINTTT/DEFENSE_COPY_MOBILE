package com.tomatoinsight.data

import com.tomatoinsight.api.AiRecommendationSection
import com.tomatoinsight.api.AiRecommendations
import com.tomatoinsight.api.DISEASE_DISPLAY_ORDER
import com.tomatoinsight.api.DiseaseScore
import com.tomatoinsight.api.ValidationResponse
import com.tomatoinsight.api.diseaseSeverityLabel
import com.tomatoinsight.api.diseaseFullDisplayLabel
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

private fun isViralKey(key: String): Boolean =
    key.contains("spottedwilt") || key.contains("tswv") ||
        key.contains("leafcurl") || key.contains("tlcv")

private fun interventionDiseaseName(name: String): String {
    return diseaseFullDisplayLabel(name)
}

/** Built-in care plans for each detected class. Only the top diagnosis uses measured severity. */
fun treatmentsForDetectedScores(
    scores: List<DiseaseScore>,
    severityLabel: String? = null,
    affectedAreaPct: Double? = null
): List<DiseaseTreatment> {
    val detected = scores.filter { it.percent > 0 }.sortedByDescending { it.percent }
    return detected.mapIndexed { index, score ->
        if (index == 0) {
            treatmentForTopDisease(score.name, severityLabel, affectedAreaPct)
        } else {
            treatmentForSecondaryDisease(score.name)
        }
    }
}

private fun treatmentForSecondaryDisease(diseaseName: String): DiseaseTreatment {
    val key = normalizeDiseaseKey(diseaseName)
    val base = when {
        key.contains("earlyblight") || key == "eb" -> earlyBlightTreatment()
        key.contains("spottedwilt") || key.contains("tswv") -> spottedWiltTreatment()
        key.contains("leafcurl") || key.contains("tlcv") -> yellowLeafCurlTreatment()
        isHealthyDisease(diseaseName) -> healthyPlantCare()
        else -> genericDiseaseTreatment(diseaseName)
    }
    if (isHealthyDisease(diseaseName)) {
        return base.copy(
            diseaseName = interventionDiseaseName(diseaseName),
            urgencyNote = "",
            summary = "Some tissue may appear healthy, but this result does not rule out the other disease detection. Check visible symptoms before acting."
        )
    }
    val steps = if (isViralKey(key)) {
        listOf(
            "Check the whole plant and nearby plants for symptoms before deciding whether to remove it.",
            if (key.contains("spottedwilt") || key.contains("tswv"))
                "Monitor and manage thrips to protect nearby plants."
            else "Monitor and manage whiteflies to protect nearby plants."
        )
    } else {
        listOf(
            "Check the leaf and nearby plants for the listed symptoms before treating.",
            "Keep foliage dry, improve airflow, and monitor for worsening spots."
        )
    }
    return base.copy(
        diseaseName = interventionDiseaseName(diseaseName),
        urgencyNote = "",
        actionSteps = steps,
        isCriticalRemoval = false
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
    val viral = isViralKey(key)
    val extentPct = affectedAreaPct?.let { kotlin.math.round(it).toInt().coerceIn(0, 100) }

    if (severity == "Unable to Assess" && !isHealthyDisease(diseaseName)) {
        return DiseaseTreatment(
            diseaseName = interventionDiseaseName(diseaseName),
            urgencyNote = "A clearer scan is needed before a care plan can be selected.",
            summary = if (viral) "A viral disease is suspected, but the affected leaf area could not be measured. Viral infections have no cure; check the whole plant before deciding whether to remove it."
                else "The disease classifier identified the most likely condition, but the affected leaf area could not be measured reliably. Do not start treatment from this image alone.",
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
        key.contains("leafcurl") || key.contains("tlcv") -> yellowLeafCurlTreatment()
        isHealthyDisease(diseaseName) -> healthyPlantCare()
        else -> genericDiseaseTreatment(diseaseName)
    }

    if (viral) {
        val spottedWilt = key.contains("spottedwilt") || key.contains("tswv")
        val steps = when {
            spottedWilt && severity == "Mild" -> listOf(
                "Check the whole plant and nearby plants; confirm the suspected virus before removing a plant.",
                "Monitor for thrips and manage them to protect nearby plants, following local product labels.",
                "Remove nearby weeds that can host thrips and the virus."
            )
            spottedWilt && severity == "Moderate" -> listOf(
                "Check the whole plant and seek confirmation promptly; consider removing it if infection is confirmed, especially when young.",
                "Monitor and manage thrips to limit spread to nearby plants, following local product labels.",
                "Remove nearby weeds that can host thrips and the virus.",
                "Inspect neighboring tomato plants for new symptoms."
            )
            spottedWilt -> listOf(
                "Seek confirmation urgently; if infection is confirmed, remove the whole plant to limit virus spread.",
                "Monitor and manage thrips to protect nearby plants, following local product labels.",
                "Remove nearby weeds that can host thrips and the virus.",
                "Inspect neighboring tomato plants promptly."
            )
            severity == "Mild" -> listOf(
                "Check new growth and nearby plants; confirm the suspected virus before removing a plant.",
                "Monitor and manage whiteflies, especially under leaves, to protect nearby plants.",
                "Rescan if visible symptoms increase."
            )
            severity == "Moderate" -> listOf(
                "Check the whole plant and seek confirmation promptly; consider removing confirmed infected plants when infection is limited in the plot.",
                "Monitor and manage whiteflies to protect nearby plants, following local product labels.",
                "Remove nearby weeds and inspect neighboring plants for curling and yellowing."
            )
            else -> listOf(
                "Seek confirmation urgently; if infection is confirmed, remove the whole plant to protect nearby plants.",
                "Monitor and manage whiteflies to limit spread, following local product labels.",
                "Remove nearby weeds and inspect neighboring tomato plants promptly."
            )
        }
        val extent = extentPct?.let { " · $it% affected" }.orEmpty()
        val urgency = when (severity) {
            "Severe" -> "Severe$extent: follow the urgent management steps now."
            "Moderate" -> "Moderate$extent: begin the management steps this week."
            else -> "Mild$extent: use the early management steps and monitor closely."
        }
        return base.copy(
            diseaseName = interventionDiseaseName(diseaseName),
            urgencyNote = urgency,
            actionSteps = steps,
            isCriticalRemoval = severity == "Severe"
        )
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
        diseaseName = interventionDiseaseName(diseaseName),
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
    summary = "Suspected tomato spotted wilt virus (TSWV) has no cure. Thrips spread it between plants; this leaf's visible severity does not confirm whole-plant infection.",
    actionSteps = listOf(
        "Check the whole plant and confirm the suspected virus before removing it.",
        "Monitor and manage thrips to protect nearby plants; follow locally approved product labels."
    ),
    preventionTips = listOf(
        "Thrips carry tomato spotted wilt virus from infected plants to healthy plants; monitor and manage them.",
        "Use TSWV-resistant tomato varieties when available.",
        "Use healthy transplants and remove weeds that may host thrips."
    )
)

private fun yellowLeafCurlTreatment() = DiseaseTreatment(
    diseaseName = "Tomato Leaf Curl Virus (TLCV)",
    urgencyNote = "",
    summary = "Suspected tomato leaf curl virus has no cure. Whiteflies spread leaf curl viruses between plants; this leaf's visible severity does not confirm whole-plant infection.",
    actionSteps = listOf(
        "Check the whole plant and confirm the suspected virus before removing it.",
        "Monitor and manage whiteflies to protect nearby plants; follow locally approved product labels."
    ),
    preventionTips = listOf(
        "Whiteflies carry tomato leaf curl virus from infected plants to healthy plants; monitor and manage them.",
        "Use healthy transplants and leaf curl virus-resistant varieties where available.",
        "Inspect nearby plants for curling, yellowing, and whitefly activity."
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

/** Adds safe guidance for each returned detection without sharing the top result's severity. */
fun ValidationResponse.ensureGuidesForAllDetections(): ValidationResponse {
    // Offline classification may show a disease result, but cannot create a care plan.
    // A server response can still carry a guide after this scan reconnects.
    if (stage == "offline" && recommendationSource == "rules" && severityAssessable != true) {
        return copy(aiRecommendations = null, recommendationSource = null)
    }
    if (stage == "offline" && !hasAiRecommendations) return this
    if (awaitingOpenCvSeverity()) return this
    val detected = allDiseaseScores
        .filter { it.percent > 0 }
        .sortedWith(
            compareByDescending<DiseaseScore> { it.percent }
                .thenBy { score ->
                    DISEASE_DISPLAY_ORDER.indexOfFirst {
                        normalizeDiseaseKey(it) == normalizeDiseaseKey(score.name)
                    }.let { if (it < 0) Int.MAX_VALUE else it }
                }
        )
    val top = detected.firstOrNull() ?: return this

    fun sectionMatches(section: AiRecommendationSection, score: DiseaseScore): Boolean {
        val sectionBar = barKeyForDiseaseName(section.disease.orEmpty())
        val scoreBar = barKeyForDiseaseName(score.name)
        if (sectionBar != null && scoreBar != null) return sectionBar == scoreBar
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
        if (isViralKey(normalizeDiseaseKey(top.name))) {
            // Mirror the website: Groq cannot replace reviewed viral spread guidance.
            treatmentForTopDisease(top.name, severity, affectedAreaPct).toAiSection(top.percent)
        } else {
            fromServer.copy(
                disease = fromServer.disease?.ifBlank { top.name } ?: top.name,
                confidencePercent = top.percent
            )
        }
    } else {
        treatmentForTopDisease(
            diseaseName = top.name,
            severityLabel = severity,
            affectedAreaPct = affectedAreaPct
        ).toAiSection(top.percent)
    }
    val primary = if (isHealthyDisease(top.name) && detected.any { !isHealthyDisease(it.name) }) {
        section.copy(
            urgency = "Review the possible disease detection below and continue monitoring.",
            summary = "Healthy tissue may be present, but this result does not rule out the other model detection. Check visible symptoms before acting."
        )
    } else section
    val additional = detected.drop(1).map { score ->
        treatmentForSecondaryDisease(score.name).toAiSection(score.percent)
    }
    return copy(
        aiRecommendations = AiRecommendations(
            primaryDisease = primary.disease ?: top.name,
            combinedNote = primary.summary,
            sections = listOf(primary) + additional
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
        "multi-guides-v2",
        topKey,
        allDiseaseScores.recommendationScoresKey(),
        severityLabel.orEmpty().lowercase(),
        area,
        (severityAssessable == true).toString()
    ).joinToString("|")
}

/** Care guide returned by the server for this scan, in the shape stored on plantNames. */
fun ValidationResponse.asSavedRecommendation(): SavedRecommendation? {
    val filled = ensureGuidesForAllDetections()
    if (filled.stage == "offline" && !filled.hasAiRecommendations) return null
    val top = filled.allDiseaseScores.maxByOrNull { it.percent } ?: return null
    if (top.percent <= 0) return null
    val sections = filled.aiRecommendations?.sections.orEmpty().filter { section ->
        !section.summary.isNullOrBlank() || !section.steps.isNullOrEmpty()
    }
    if (sections.isEmpty()) return null
    val section = sections.firstOrNull { item ->
        val disease = item.disease.orEmpty()
        disease.isNotBlank() && (
            barKeyForDiseaseName(disease) == barKeyForDiseaseName(top.name) ||
                disease.equals(top.name, ignoreCase = true) ||
                normalizeDiseaseKey(disease) == normalizeDiseaseKey(top.name)
            )
    } ?: sections.first()
    val guides = sections.mapIndexed { index, item ->
        SavedGuide(
            diseaseName = item.disease?.trim().orEmpty().ifBlank { top.name },
            confidencePercent = item.confidencePercent ?: 0,
            severityLabel = if (index == 0) filled.severityLabel.orEmpty() else "Unable to Assess",
            affectedAreaPct = if (index == 0) filled.affectedAreaPct else null,
            urgencyNote = item.urgency?.trim().orEmpty(),
            summary = item.summary?.trim().orEmpty(),
            actionSteps = item.steps.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(12),
            preventionTips = item.prevention.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.take(8),
            isCriticalRemoval = item.isCriticalRemoval == true
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

/** Rebuild every saved detection's care section. */
fun SavedRecommendation.asAiRecommendations(): AiRecommendations? {
    val sections = when {
        guides.isNotEmpty() -> guides.take(DISEASE_DISPLAY_ORDER.size).map { guide ->
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
        sections = sections
    )
}
