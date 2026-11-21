package com.tomatoinsight.api

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tomatoinsight.data.ensureGuidesForAllDetections
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ScanAnotherLeafException(message: String) : IOException(message)

/**
 * Uses the website's validation, classification and severity routes in order.
 */
object WebsiteScanClient {
    private const val LEAF_MIN_CONFIDENCE = 0.50
    private const val DISEASE_MIN_CONFIDENCE = 0.20
    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun scan(imageBytes: ByteArray, onStage: suspend (String) -> Unit = {}): ValidationResponse {
        val payload = postFile("validate") { imageBytes.toLeafPart() }
        parseFlaskValidation(payload)?.let { verdict ->
            if (!verdict.confirmsTomatoLeaf()) return verdict.copy(predictions = emptyList())
            onStage("Isolating leaf and detecting disease…")
            val detection = diseasePredictionsFrom(imageBytes)
            return tomatoResponse(
                leafName = verdict.detectedClass ?: "TomatoLeaf",
                leafConfidence = verdict.confidence ?: 1.0,
                minConfidence = verdict.minConfidence ?: 0.0,
                stage = "website",
                message = "Tomato leaf detected.",
                predictions = detection.first,
                leafMaskPng = detection.second
            )
        }

        val leafHits = extractHits(payload)
        val bestLeaf = leafHits.maxByOrNull { it.second }
        val leafName = bestLeaf?.first
        val leafConfidence = bestLeaf?.second ?: 0.0
        val isTomato = leafName != null &&
            !isRejectedLeafClass(leafName) &&
            isTomatoLeafClass(leafName) &&
            leafConfidence >= LEAF_MIN_CONFIDENCE

        if (!isTomato) {
            val shown = "%.1f".format(leafConfidence * 100)
            return ValidationResponse(
                result = "NotTomato",
                confidence = leafConfidence,
                minConfidence = LEAF_MIN_CONFIDENCE,
                detectedClass = leafName,
                stage = "website",
                message = if (leafName.isNullOrBlank()) {
                    "No tomato leaf was detected in this image."
                } else {
                    "This photo was labeled “$leafName” at $shown%."
                },
                predictions = emptyList()
            )
        }

        onStage("Isolating leaf and detecting disease…")
        val detection = diseasePredictionsFrom(imageBytes)
        return tomatoResponse(
            leafName = leafName,
            leafConfidence = leafConfidence,
            minConfidence = LEAF_MIN_CONFIDENCE,
            stage = "website",
            message = "Tomato leaf detected.",
            predictions = detection.first,
            leafMaskPng = detection.second
        )
    }

    private fun parseFlaskValidation(payload: JsonObject): ValidationResponse? {
        val result = payload.text("result") ?: return null
        if (result.equals("Error", ignoreCase = true)) {
            throw IOException(payload.text("message") ?: "Validation error.")
        }
        if (!result.equals("TomatoLeaf", ignoreCase = true) &&
            !result.equals("NotTomato", ignoreCase = true)
        ) {
            return null
        }
        val parsed = gson.fromJson(payload, ValidationResponse::class.java)
        val explicitlyRejected = listOf(parsed.result, parsed.detectedClass)
            .any { it != null && isRejectedLeafClass(it) }
        if (explicitlyRejected) return parsed.copy(
            result = "NotTomato",
            message = "This image was identified as not a tomato leaf. Please upload a clear photo of a tomato leaf.",
            predictions = emptyList()
        )
        // The website trusts a legacy server's final TomatoLeaf verdict.
        val minConfidence = 0.0
        return parsed.copy(
            minConfidence = minConfidence,
            aiRecommendations = null,
            recommendationSource = null,
            groqModel = null
        )
    }

    private suspend fun diseasePredictionsFrom(imageBytes: ByteArray): Pair<List<DiseasePrediction>, String?> {
        val payload = postFile("classify") { imageBytes.toLeafPart() }
        val hits = extractHits(payload).filter {
            it.second >= DISEASE_MIN_CONFIDENCE && barKeyOrNull(it.first) != null
        }
        return hitsToPredictions(hits) to payload.text("leaf_mask_png")
    }

    private suspend fun tomatoResponse(
        leafName: String?,
        leafConfidence: Double,
        minConfidence: Double,
        stage: String,
        message: String,
        predictions: List<DiseasePrediction>,
        leafMaskPng: String? = null
    ): ValidationResponse {
        val mapped = predictions
        val top = mapped.maxByOrNull { it.confidence ?: 0.0 }
        return ValidationResponse(
            result = "TomatoLeaf",
            confidence = leafConfidence,
            minConfidence = minConfidence,
            detectedClass = leafName,
            stage = stage,
            message = message,
            predictions = mapped,
            topDisease = top?.displayName,
            topDiseaseConfidence = top?.confidence,
            diseaseClasses = DISEASE_DISPLAY_ORDER,
            leafMaskPng = leafMaskPng
        )
    }

    private suspend fun postFile(
        route: String,
        part: () -> MultipartBody.Part
    ): JsonObject {
        val response = when (route) {
            "validate" -> ValidationRetrofitClient.instance.validateLeaf(part())
            else -> ValidationRetrofitClient.instance.classifyLeaf(part())
        }
        return readObject(response, route)
    }

    /**
     * Calls Render OpenCV `/assess-severity` using the classified top disease class.
     * Does not use AI confidence. Falls back to Unable to Assess / Healthy 0%.
     */
    suspend fun withSegmentationSeverity(
        imageBytes: ByteArray,
        response: ValidationResponse
    ): ValidationResponse {
        if (!response.confirmsTomatoLeaf()) return response
        val top = response.allDiseaseScores.maxByOrNull { it.percent }?.takeIf { it.percent > 0 }
            ?: return response.copy(
                severityLabel = "Unable to Assess",
                affectedAreaPct = null,
                severityAssessable = false,
                severityReason = "no_top_disease"
            ).ensureGuidesForAllDetections()
        val barKey = barKeyOrNull(top.name) ?: "early_blight"
        val withSeverity = try {
            mergeSeverity(response, postSeverity(imageBytes, barKey, response.leafMaskPng))
        } catch (e: IOException) {
            if (response.severityLabel != null || response.affectedAreaPct != null) return response
            return response.copy(severityReason = AWAITING_OPENCV_SEVERITY)
        }
        return withSeverityDrivenRecommendation(withSeverity)
    }

    /** Generates one care plan after severity is known, using only the top diagnosis score. */
    suspend fun withSeverityDrivenRecommendation(response: ValidationResponse): ValidationResponse {
        if (!response.confirmsTomatoLeaf()) return response
        val top = response.allDiseaseScores.maxByOrNull { it.percent }?.takeIf { it.percent > 0 }
            ?: return response
        val severity = diseaseSeverityLabel(top.name, response.affectedAreaPct, response.severityLabel)
        val assessable = isHealthyDisease(top.name) ||
            (response.severityAssessable == true && severity != "Unable to Assess")
        if (!assessable) {
            return response.copy(
                aiRecommendations = null,
                aiRecommendationError = null,
                recommendationSource = "rules",
                groqModel = null
            ).ensureGuidesForAllDetections()
        }

        val topKey = barKeyOrNull(top.name) ?: return response.ensureGuidesForAllDetections()
        val topOnlyScores = linkedMapOf(
            "healthy" to 0.0,
            "tlcv" to 0.0,
            "early_blight" to 0.0,
            "spotted_wilt" to 0.0
        ).apply { this[topKey] = top.percent.toDouble() }
        val care = runCatching {
            requestRecommendation(
                scores = topOnlyScores,
                severityPct = response.affectedAreaPct,
                severityLabel = severity
            )
        }.getOrElse { CareResult(error = it.message) }
        return response.copy(
            aiRecommendations = care.recommendations,
            aiRecommendationError = care.error,
            recommendationSource = care.source ?: "rules",
            groqModel = care.model
        ).ensureGuidesForAllDetections()
    }

    private data class SeverityFields(
        val severityLabel: String,
        val affectedAreaPct: Double?,
        val severityAssessable: Boolean,
        val severityReason: String?
    )

    private fun mergeSeverity(base: ValidationResponse, severity: SeverityFields): ValidationResponse =
        base.copy(
            severityLabel = severity.severityLabel,
            affectedAreaPct = severity.affectedAreaPct,
            severityAssessable = severity.severityAssessable,
            severityReason = severity.severityReason
        )

    private suspend fun postSeverity(imageBytes: ByteArray, diseaseKey: String, leafMaskPng: String?): SeverityFields {
        // Use the website's leaf mask for severity. Disease scores come from the phone.
        val mask = leafMaskPng ?: try {
            postFile("classify") { imageBytes.toLeafPart() }.text("leaf_mask_png")
        } catch (e: IOException) {
            // OpenCV segments the original image when no mask is supplied.
            null
        }
        val diseaseBody = diseaseKey.toRequestBody("text/plain".toMediaType())
        val http = ValidationRetrofitClient.instance.assessSeverity(
            imageBytes.toLeafPart(),
            diseaseBody,
            diseaseBody,
            mask?.toRequestBody("text/plain".toMediaType())
        )
        val payload = readObject(http, "assess-severity")
        val label = payload.text("severity") ?: "Unable to Assess"
        val measurable = payload.bool("measurable") == true
        val area = payload.number("affected_area_pct")
        val isHealthy = measurable && label.equals("Healthy", ignoreCase = true)
        val areaPct = when {
            isHealthy -> area?.coerceIn(0.0, 100.0) ?: 0.0
            measurable && area != null -> area.coerceIn(0.0, 100.0)
            else -> null
        }
        return SeverityFields(
            severityLabel = if (isHealthy) "Healthy" else label,
            affectedAreaPct = areaPct,
            severityAssessable = if (isHealthy) {
                true
            } else {
                measurable && !label.equals("Unable to Assess", ignoreCase = true)
            },
            severityReason = payload.text("reason")
        )
    }

    private suspend fun requestRecommendation(
        scores: Map<String, Double>,
        severityPct: Double? = null,
        severityLabel: String? = null
    ): CareResult {
        val requestMap = mutableMapOf<String, Any>("scores" to scores)
        if (severityPct != null && severityPct.isFinite() && severityPct >= 0) {
            requestMap["severity_pct"] = severityPct.coerceIn(0.0, 100.0)
        }
        if (!severityLabel.isNullOrBlank()) {
            requestMap["severity_label"] = severityLabel
        }
        val body = gson.toJson(requestMap).toRequestBody(jsonType)
        val response = ValidationRetrofitClient.instance.recommendCare(body)
        if (!response.isSuccessful) {
            val message = errorMessage(response.body(), response.errorBody()?.string(), response.code())
            return CareResult(error = message)
        }
        val payload = response.body()?.asObject()
            ?: return CareResult(error = "The website recommendation response was empty.")
        val detected = payload.getAsJsonArray("detected")
        val guideObjects = mutableListOf<JsonObject>()
        payload.getAsJsonArray("guides")?.forEach { item ->
            item?.asObject()?.let { guideObjects += it }
        }
        if (guideObjects.isEmpty()) {
            payload.getAsJsonObject("guide")?.let { guideObjects += it }
        }
        if (guideObjects.isEmpty()) {
            return CareResult(error = payload.text("message") ?: "No care recommendation was returned.")
        }
        val sections = guideObjects.take(1).mapIndexed { index, guide ->
            val matchPct = detected
                ?.takeIf { index in 0 until it.size() }
                ?.get(index)
                ?.asObject()
                ?.get("pct")
                ?.asDouble
                ?.let { value -> (value + 0.5).toInt().coerceIn(0, 100) }
            AiRecommendationSection(
                disease = guide.text("diseaseName"),
                confidencePercent = matchPct,
                urgency = guide.text("urgencyNote"),
                summary = guide.text("summary"),
                steps = guide.stringList("actionSteps"),
                prevention = guide.stringList("preventionTips"),
                isCriticalRemoval = guide.bool("isCriticalRemoval")
            )
        }
        val primary = sections.first()
        val combined = primary.summary
        val groqUsed = payload.bool("groq_used") == true
        return CareResult(
            recommendations = AiRecommendations(
                primaryDisease = primary.disease,
                combinedNote = combined,
                sections = sections
            ),
            source = if (groqUsed) "groq" else "rules",
            model = payload.text("model")
        )
    }

    private fun readObject(response: retrofit2.Response<JsonElement>, route: String): JsonObject {
        if (!response.isSuccessful) {
            val raw = response.errorBody()?.string()
            val error = runCatching { gson.fromJson(raw, JsonObject::class.java) }.getOrNull()
            if (route == "classify" && response.code() == 422 &&
                error?.text("error") == "leaf_isolation_failed") {
                throw ScanAnotherLeafException(
                    error.text("message") ?: "Please scan another tomato leaf in bright, even light."
                )
            }
            throw IOException(errorMessage(response.body(), raw, response.code(), route))
        }
        return response.body()?.asObject()
            ?: throw IOException("The website $route response was empty.")
    }

    private fun errorMessage(
        body: JsonElement?,
        raw: String?,
        code: Int,
        route: String? = null
    ): String {
        val payload = body?.asObject()
        val message = payload?.text("message") ?: raw?.take(300)
        val where = route?.let { " /$it" }.orEmpty()
        return "Website$where HTTP $code${message?.let { ": $it" } ?: ""}"
    }

    private fun ByteArray.toLeafPart(): MultipartBody.Part {
        val body = toRequestBody("image/jpeg".toMediaType())
        return MultipartBody.Part.createFormData("file", "leaf_scan.jpg", body)
    }

    private fun extractHits(
        payload: JsonObject,
        includePredictions: Boolean = false
    ): List<Pair<String, Double>> {
        val hits = mutableListOf<Pair<String, Double>>()
        val metadata = payload.get("metadata")?.asObject()
        val names = metadata?.get("classNames") ?: metadata?.get("names") ?: payload.get("names")
        fun add(item: JsonObject) {
            val classId = item.text("class_id") ?: item.text("classId")
            val mapped = classId?.let { id ->
                when {
                    names?.isJsonObject == true -> names.asJsonObject.get(id)?.asString
                    names?.isJsonArray == true -> id.toIntOrNull()
                        ?.takeIf { it in 0 until names.asJsonArray.size() }
                        ?.let { names.asJsonArray.get(it).asString }
                    else -> null
                }
            }
            val name = item.text("name") ?: item.text("class_name") ?: item.text("class")
                ?: item.text("label") ?: mapped
            if (name.isNullOrBlank()) return
            val confidence = item.number("confidence") ?: item.number("score") ?: 0.0
            hits += name to if (confidence > 1.0) confidence / 100.0 else confidence
        }
        payload.array("images").forEach { image ->
            image.asObject()?.array("results")?.forEach { it.asObject()?.let(::add) }
        }
        payload.array("results").forEach { it.asObject()?.let(::add) }
        if (includePredictions) {
            payload.array("predictions").forEach { it.asObject()?.let(::add) }
        }
        return hits
    }

    private fun hitsToPredictions(hits: List<Pair<String, Double>>): List<DiseasePrediction> {
        if (hits.isEmpty()) return emptyList()
        val scores = barScores(hits)
        return DISEASE_DISPLAY_ORDER.map { name ->
            DiseasePrediction(
                className = name,
                confidence = (scores[barKey(name)] ?: 0.0) / 100.0
            )
        }
    }

    private fun barScores(hits: List<Pair<String, Double>>): Map<String, Double> {
        val byKey = hits.groupBy { normalizeDiseaseKey(it.first) }
            .mapValues { (_, matches) -> matches.maxOf { it.second } }
        fun best(vararg names: String): Double {
            var value = 0.0
            names.forEach { label ->
                val key = normalizeDiseaseKey(label)
                byKey[key]?.let { value = maxOf(value, it) }
                byKey.forEach { (candidate, confidence) ->
                    if (candidate.contains(key) || key.contains(candidate)) {
                        value = maxOf(value, confidence)
                    }
                }
            }
            return (kotlin.math.round(value * 1000.0) / 10.0).coerceIn(0.0, 100.0)
        }
        return mapOf(
            "healthy" to best("healthy"),
            "tlcv" to best("yellowleafcurlvirus", "yellow leaf curl virus", "tlcv", "yellow leaf curl"),
            "early_blight" to best("earlyblightvirus", "early blight", "earlyblight"),
            "spotted_wilt" to best("spottedwiltvirus", "spotted wilt", "spottedwilt", "tswv")
        )
    }

    private fun barKey(displayName: String): String = barKeyOrNull(displayName) ?: "spotted_wilt"

    private fun barKeyOrNull(displayName: String): String? {
        val key = normalizeDiseaseKey(displayName)
        return when {
            key.contains("healthy") -> "healthy"
            key.contains("yellowleafcurl") || key == "tlcv" -> "tlcv"
            key.contains("earlyblight") -> "early_blight"
            key.contains("spottedwilt") || key == "tswv" -> "spotted_wilt"
            else -> null
        }
    }

    private fun isTomatoLeafClass(name: String): Boolean {
        val key = normalizeDiseaseKey(name)
        return !isRejectedLeafClass(name) && (key == "tomatoleaf" || key == "tomatoleaves")
    }

    private fun isRejectedLeafClass(name: String): Boolean {
        val key = normalizeDiseaseKey(name)
        return key.contains("nontomato") || key.contains("nottomato") ||
            key.contains("nonleaf") || key.contains("notleaf")
    }

    private fun JsonElement.asObject(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.text(key: String): String? {
        val value = get(key) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return value.asString.trim().ifBlank { null }
    }

    private fun JsonObject.number(key: String): Double? {
        val value = get(key) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return runCatching { value.asDouble }.getOrNull()
    }

    private fun JsonObject.bool(key: String): Boolean? {
        val value = get(key) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) return null
        return value.asBoolean
    }

    private fun JsonObject.array(key: String): List<JsonElement> {
        val value = get(key) ?: return emptyList()
        if (!value.isJsonArray) return emptyList()
        return value.asJsonArray.toList()
    }

    private fun JsonObject.stringList(key: String): List<String> =
        array(key).mapNotNull { item ->
            if (!item.isJsonPrimitive) null else item.asString.trim().ifBlank { null }
        }

    private data class CareResult(
        val recommendations: AiRecommendations? = null,
        val source: String? = null,
        val model: String? = null,
        val error: String? = null
    )
}
