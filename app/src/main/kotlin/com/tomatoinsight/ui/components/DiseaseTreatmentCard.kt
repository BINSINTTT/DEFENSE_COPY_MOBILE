package com.tomatoinsight.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tomatoinsight.api.AiRecommendationSection
import com.tomatoinsight.api.ValidationResponse
import com.tomatoinsight.api.displaySeverity
import com.tomatoinsight.api.isHealthyDisease
import com.tomatoinsight.api.diseaseFullDisplayLabel
import com.tomatoinsight.api.normalizeDiseaseKey
import com.tomatoinsight.data.DiseaseTreatment
import com.tomatoinsight.data.SavedRecommendation
import com.tomatoinsight.data.asAiRecommendations
import com.tomatoinsight.data.ensureGuidesForAllDetections
import com.tomatoinsight.data.treatmentsForDetectedScores
import com.tomatoinsight.ui.theme.TomatoGreen
import com.tomatoinsight.ui.theme.TomatoRed

private fun isViralDisease(name: String): Boolean {
    val key = normalizeDiseaseKey(name)
    return key.contains("leafcurl") || key.contains("tlcv") ||
        key.contains("spottedwilt") || key.contains("tswv")
}

private fun interventionTitle(name: String): String {
    return diseaseFullDisplayLabel(name)
}

@Composable
fun DiseaseTreatmentCard(
    result: ValidationResponse,
    savedRecommendation: SavedRecommendation? = null
) {
    // Build every detected section; only the primary diagnosis uses measured severity.
    val withAllGuides = result.ensureGuidesForAllDetections()
    if (result.stage == "offline" && !withAllGuides.hasAiRecommendations) return
    if (withAllGuides.hasAiRecommendations) {
        InterventionGuideCard(withAllGuides)
        return
    }
    val detected = treatmentsForDetectedScores(
        scores = result.allDiseaseScores,
        severityLabel = result.severityLabel,
        affectedAreaPct = result.affectedAreaPct
    )
    if (detected.isNotEmpty()) {
        OfflineTreatmentCard(result, detected)
        return
    }
    val saved = savedRecommendation?.takeIf {
        it.summary.isNotBlank() || it.actionSteps.isNotEmpty() || it.guides.isNotEmpty()
    }
    if (saved != null) {
        val fromSaved = saved.asAiRecommendations()
        if (fromSaved != null) {
            InterventionGuideCard(
                result.copy(
                    aiRecommendations = fromSaved,
                    recommendationSource = "rules"
                )
            )
        } else {
            SavedRecommendationCard(saved)
        }
    }
}

@Composable
private fun SavedRecommendationCard(guide: SavedRecommendation) {
    val isHealthy = isHealthyDisease(guide.diseaseName)
    val rescanRequired = !isHealthy && !guide.severityAssessable
    val accent = if (guide.isCriticalRemoval) TomatoRed else if (isHealthy) TomatoGreen else TomatoRed
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHealthy) Color(0xFFF1F8E9) else Color(0xFFFFF8E1)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            TreatmentCardHeader(
                isHealthy = isHealthy,
                isViral = isViralDisease(guide.diseaseName),
                accent = accent,
                sourceLabel = null,
                rescanRequired = rescanRequired
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (guide.diseaseName.isNotBlank()) {
                Text(
                    text = interventionTitle(guide.diseaseName),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (guide.urgencyNote.isNotBlank()) {
                Text(
                    text = guide.urgencyNote,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (guide.summary.isNotBlank()) {
                Text(
                    text = guide.summary,
                    fontSize = 13.sp,
                    color = Color(0xFF424242),
                    lineHeight = 18.sp
                )
            }
            if (guide.actionSteps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = when {
                        rescanRequired -> "How to retake the scan"
                        isHealthy -> "What to do"
                        isViralDisease(guide.diseaseName) -> "Management actions"
                        else -> "Treatment steps"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(8.dp))
                guide.actionSteps.forEachIndexed { index, step ->
                    TreatmentStepRow(index = index + 1, text = step)
                    if (index < guide.actionSteps.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            if (guide.preventionTips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                PreventionHeader(isViralDisease(guide.diseaseName))
                guide.preventionTips.forEach { tip ->
                    Text(
                        text = "• $tip",
                        fontSize = 13.sp,
                        color = Color(0xFF424242),
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Guidance only — confirm the diagnosis before removing a plant or applying products.",
                fontSize = 11.sp,
                color = Color(0xFF9E9E9E)
            )
        }
    }
}

@Composable
internal fun InterventionGuideCard(result: ValidationResponse) {
    val ai = result.aiRecommendations ?: return
    val sections = ai.sections.orEmpty()
    if (sections.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF7)),
        border = BorderStroke(1.dp, Color(0xFFCEE3D0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            sections.forEachIndexed { index, section ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFD6E5D7))
                    Spacer(modifier = Modifier.height(14.dp))
                }
                AiRecommendationSectionBlock(
                    section = section,
                    isSecondary = index > 0,
                    severityLabel = if (index == 0) result.displaySeverity(section.disease).takeUnless { it == "—" } else null,
                    rescanRequired = index == 0 &&
                        !isHealthyDisease(section.disease.orEmpty()) &&
                        result.severityAssessable == false
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Guidance only — confirm the diagnosis before removing a plant or applying products.",
                fontSize = 11.sp,
                color = Color(0xFF757575),
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun AiRecommendationSectionBlock(
    section: AiRecommendationSection,
    isSecondary: Boolean,
    severityLabel: String?,
    rescanRequired: Boolean = false
) {
    val disease = interventionTitle(section.disease.orEmpty())
    val percent = section.confidencePercent ?: 0
    val sectionHealthy = isHealthyDisease(disease)
    val eyebrow = when {
        isSecondary -> "ADDITIONAL MODEL DETECTION · $percent% CONFIDENCE"
        sectionHealthy -> "HEALTHY CARE PLAN"
        severityLabel.equals("Severe", ignoreCase = true) -> "INTERVENTION"
        severityLabel.isNullOrBlank() || severityLabel == "Unable to Assess" ->
            "GENERAL CARE GUIDANCE · SEVERITY UNAVAILABLE"
        else -> "${severityLabel.uppercase()} CARE PLAN"
    }
    Text(
        text = eyebrow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        color = Color(0xFF6F7770),
        letterSpacing = 0.5.sp
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = disease.ifBlank { "Detected condition" },
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Color(0xFF212121)
    )
    if (isSecondary) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (sectionHealthy)
                "This healthy detection does not rule out the primary disease. Check visible symptoms before acting."
            else
                "This is another model detection, not a confirmed second diagnosis. Its severity was not assessed. Check this leaf and nearby plants before acting.",
            fontSize = 12.sp,
            color = Color(0xFF6F7770),
            lineHeight = 17.sp
        )
    }
    section.summary?.takeIf { it.isNotBlank() }?.let { summary ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = summary,
            fontSize = 13.sp,
            color = Color(0xFF424242),
            lineHeight = 18.sp
        )
    }

    val steps = section.steps.orEmpty().filter { it.isNotBlank() }
    if (steps.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when {
                rescanRequired -> "How to retake the scan"
                sectionHealthy -> "What to do"
                isViralDisease(disease) -> "Management actions"
                else -> "Treatment steps"
            }.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color(0xFF303A31)
        )
        Spacer(modifier = Modifier.height(6.dp))
        steps.forEach { step ->
            Text(
                text = "•  $step",
                fontSize = 12.sp,
                color = Color(0xFF303A31),
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }

    val prevention = section.prevention.orEmpty().filter { it.isNotBlank() }
    if (prevention.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isViralDisease(disease)) "PREVENT SPREAD TO OTHER PLANTS" else "PREVENTION",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color(0xFF303A31)
        )
        Spacer(modifier = Modifier.height(6.dp))
        prevention.forEach { tip ->
            Text(
                text = "•  $tip",
                fontSize = 12.sp,
                color = Color(0xFF303A31),
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun OfflineTreatmentCard(
    result: ValidationResponse,
    treatments: List<DiseaseTreatment>
) {
    if (treatments.isEmpty()) return
    val onlyHealthy = treatments.all { isHealthyDisease(it.diseaseName) }
    val accent = if (onlyHealthy) TomatoGreen else TomatoRed
    val rescanRequired = !onlyHealthy && result.severityAssessable == false

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (onlyHealthy) Color(0xFFF1F8E9) else Color(0xFFFFF8E1)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            TreatmentCardHeader(
                isHealthy = onlyHealthy,
                isViral = isViralDisease(treatments.first().diseaseName),
                accent = accent,
                sourceLabel = "Built-in care guide",
                rescanRequired = rescanRequired
            )

            result.aiRecommendationError?.takeIf { it.isNotBlank() }?.let { error ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Showing built-in tips (AI unavailable).",
                    fontSize = 12.sp,
                    color = Color(0xFF757575)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error,
                    fontSize = 11.sp,
                    color = Color(0xFF9E9E9E),
                    lineHeight = 15.sp
                )
            }

            treatments.forEachIndexed { index, treatment ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFE0E0E0))
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val sectionHealthy = isHealthyDisease(treatment.diseaseName)
                val sectionAccent = if (sectionHealthy) TomatoGreen else accent
                val score = result.allDiseaseScores
                    .firstOrNull { it.name.equals(treatment.diseaseName, ignoreCase = true) }
                    ?.percent
                    ?: 0

                Text(
                    text = buildString {
                        append(treatment.diseaseName)
                        if (score > 0) append(" · $score% diagnosis confidence")
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = treatment.urgencyNote,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = sectionAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = treatment.summary,
                    fontSize = 13.sp,
                    color = Color(0xFF424242),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = when {
                        rescanRequired -> "How to retake the scan"
                        sectionHealthy -> "What to do"
                        isViralDisease(treatment.diseaseName) -> "Management actions"
                        else -> "Treatment steps"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(8.dp))

                treatment.actionSteps.forEachIndexed { stepIndex, step ->
                    TreatmentStepRow(index = stepIndex + 1, text = step)
                    if (stepIndex < treatment.actionSteps.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (treatment.preventionTips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    PreventionHeader(isViralDisease(treatment.diseaseName))
                    treatment.preventionTips.forEach { tip ->
                        Text(
                            text = "• $tip",
                            fontSize = 13.sp,
                            color = Color(0xFF424242),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Guidance only — confirm the diagnosis before removing a plant or applying products.",
                fontSize = 11.sp,
                color = Color(0xFF9E9E9E)
            )
        }
    }
}

@Composable
private fun TreatmentCardHeader(
    isHealthy: Boolean,
    isViral: Boolean,
    accent: Color,
    sourceLabel: String?,
    rescanRequired: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accent.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Healing,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = when {
                    rescanRequired -> "CLEARER SCAN NEEDED"
                    isHealthy -> "CARE TIPS"
                    isViral -> "MANAGEMENT ACTIONS"
                    else -> "TREATMENT RECOMMENDATIONS"
                },
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            if (!sourceLabel.isNullOrBlank()) {
                Text(
                    text = sourceLabel,
                    fontSize = 11.sp,
                    color = Color(0xFF757575)
                )
            }
        }
    }
}

@Composable
private fun PreventionHeader(isViral: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Lightbulb,
            contentDescription = null,
            tint = Color(0xFFF9A825),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isViral) "Prevent spread to other plants" else "Prevention",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFFF57F17)
        )
    }
}

@Composable
private fun TreatmentStepRow(index: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(TomatoGreen.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TomatoGreen
            )
        }
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFF424242),
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
