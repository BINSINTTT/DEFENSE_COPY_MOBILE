package com.tomatoinsight.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.tomatoinsight.api.DISEASE_DISPLAY_ORDER
import com.tomatoinsight.api.DiseasePrediction
import com.tomatoinsight.api.ValidationResponse
import com.tomatoinsight.api.normalizeDiseaseKey
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.exp

/**
 * On-device YOLOv8 segmentation model for the two lookalike diseases.
 *
 * early_spotted_specialist.tflite (earlyspotted.tflite):
 * input float32 [1, 3, 640, 640],
 * detection [1, 39, 8400] = box + leaf / early_blight_symptom / spotted_wilt_symptom + 32 mask coeffs,
 * prototypes [1, 32, 160, 160].
 *
 * Runs only after the four-class disease model, and only when Early Blight or Spotted Wilt leads.
 */
object OfflineEarlySpottedSpecialist {
    private const val MODEL_ASSET = "models/early_spotted_specialist.tflite"
    private const val IMAGE_SIZE = 640
    private const val MIN_CONFIDENCE = 0.50
    private const val MIN_MARGIN = 0.10
    private const val MASK_SCORE_FLOOR = 0.25f
    private const val MASK_PIXEL = 0.5f
    private const val SPECIALIST_WEIGHT = 0.65
    private const val TOP_CANDIDATES = 6
    private const val MASK_CHANNELS = 32

    @Volatile private var interpreter: Interpreter? = null

    fun isAvailable(context: Context): Boolean = runCatching {
        context.assets.openFd(MODEL_ASSET).close()
        true
    }.getOrDefault(false)

    fun refine(context: Context, bitmap: Bitmap, response: ValidationResponse): ValidationResponse {
        val scores = response.allDiseaseScores.associate { normalizeDiseaseKey(it.name) to it.percent.toDouble() }
        val earlyKey = normalizeDiseaseKey("Early blight")
        val spottedKey = normalizeDiseaseKey("Spotted wilt")
        val leading = scores.maxByOrNull { it.value }?.key
        if (leading !in setOf(earlyKey, spottedKey) || !isAvailable(context)) return response

        return runCatching {
            val split = synchronized(this) {
                val runner = interpreter ?: createInterpreter(context).also { interpreter = it }
                inferSplit(runner, bitmap)
            }
            val totalScore = split.earlyScore + split.spottedScore
            if (totalScore <= 0.0) return@runCatching response
            val areaSum = split.earlyLeafFraction + split.spottedLeafFraction
            val bothMasks = areaSum > 0.0 &&
                split.earlyScore >= MASK_SCORE_FLOOR &&
                split.spottedScore >= MASK_SCORE_FLOOR
            val earlyRelative = if (bothMasks) {
                split.earlyLeafFraction / areaSum
            } else {
                split.earlyScore / totalScore
            }
            val spottedRelative = 1.0 - earlyRelative
            val separated = maxOf(split.earlyScore, split.spottedScore) >= MIN_CONFIDENCE &&
                abs(earlyRelative - spottedRelative) >= MIN_MARGIN

            var next = response
            if (separated) {
                val earlyMain = scores[earlyKey] ?: 0.0
                val spottedMain = scores[spottedKey] ?: 0.0
                val pairTotal = (earlyMain + spottedMain).takeIf { it > 0.0 } ?: 100.0
                val fusedEarly = pairTotal * (
                    (1.0 - SPECIALIST_WEIGHT) * earlyMain / pairTotal +
                        SPECIALIST_WEIGHT * earlyRelative
                    )
                val fusedSpotted = pairTotal * (
                    (1.0 - SPECIALIST_WEIGHT) * spottedMain / pairTotal +
                        SPECIALIST_WEIGHT * spottedRelative
                    )
                val refined = scores.toMutableMap().apply {
                    this[earlyKey] = fusedEarly
                    this[spottedKey] = fusedSpotted
                }
                val predictions = DISEASE_DISPLAY_ORDER.map { name ->
                    DiseasePrediction(
                        className = name,
                        confidence = ((refined[normalizeDiseaseKey(name)] ?: 0.0) / 100.0).coerceIn(0.0, 1.0)
                    )
                }
                val top = predictions.maxByOrNull { it.confidence ?: 0.0 }
                next = next.copy(
                    predictions = predictions,
                    topDisease = top?.displayName,
                    topDiseaseConfidence = top?.confidence,
                    modelPath = listOfNotNull(response.modelPath, MODEL_ASSET).joinToString(" + "),
                    message = "Early Blight and Spotted Wilt were separated by symptom segmentation."
                )
            }
            if (!separated) return@runCatching response
            next
        }.onFailure {
            Log.w("TomatoInsight", "Offline specialist model could not refine this scan", it)
        }.getOrDefault(response)
    }

    private data class SymptomSplit(
        val earlyScore: Double,
        val spottedScore: Double,
        val earlyLeafFraction: Double,
        val spottedLeafFraction: Double
    )

    private fun inferSplit(runner: Interpreter, bitmap: Bitmap): SymptomSplit {
        val outputBuffers = mutableMapOf<Int, Any>()
        for (index in 0 until runner.outputTensorCount) {
            val tensor = runner.getOutputTensor(index)
            require(tensor.dataType() == DataType.FLOAT32) {
                "The offline specialist must be exported as float32 TFLite."
            }
            outputBuffers[index] = ByteBuffer.allocateDirect(tensor.numBytes())
                .order(ByteOrder.nativeOrder())
        }
        runner.runForMultipleInputsOutputs(arrayOf(runner.bitmapInput(bitmap, IMAGE_SIZE)), outputBuffers)

        val detectionIndex = (0 until runner.outputTensorCount).firstOrNull { index ->
            val shape = runner.getOutputTensor(index).shape()
            shape.size == 3 && minOf(shape[1], shape[2]) >= 7
        } ?: error("No YOLO segmentation detection output was found.")
        val detectionShape = runner.getOutputTensor(detectionIndex).shape()
        val attributesFirst = detectionShape[1] < detectionShape[2]
        val attributes = if (attributesFirst) detectionShape[1] else detectionShape[2]
        val candidates = if (attributesFirst) detectionShape[2] else detectionShape[1]
        require(attributes >= 7) { "Expected at least 7 YOLO output attributes." }
        val detection = readFloats(outputBuffers.getValue(detectionIndex) as ByteBuffer)
        fun value(candidate: Int, attribute: Int): Float = if (attributesFirst) {
            detection[attribute * candidates + candidate]
        } else {
            detection[candidate * attributes + attribute]
        }

        val maskChannels = if (attributes >= 4 + 3 + MASK_CHANNELS) MASK_CHANNELS else 0
        val classCount = attributes - 4 - maskChannels
        require(classCount >= 3) { "Expected leaf, early blight, and spotted wilt classes." }
        val leafAttr = 4
        val earlyAttr = 5
        val spottedAttr = 6
        val coeffStart = 4 + classCount

        var earlyScore = 0f
        var spottedScore = 0f
        val leafPicks = TopCandidates(TOP_CANDIDATES)
        val earlyPicks = TopCandidates(TOP_CANDIDATES)
        val spottedPicks = TopCandidates(TOP_CANDIDATES)
        for (candidate in 0 until candidates) {
            val early = value(candidate, earlyAttr)
            val spotted = value(candidate, spottedAttr)
            if (early > earlyScore) earlyScore = early
            if (spotted > spottedScore) spottedScore = spotted
            leafPicks.consider(candidate, value(candidate, leafAttr))
            earlyPicks.consider(candidate, early)
            spottedPicks.consider(candidate, spotted)
        }

        val protoIndex = (0 until runner.outputTensorCount).firstOrNull { index ->
            val shape = runner.getOutputTensor(index).shape()
            shape.size == 4 && (shape[1] == MASK_CHANNELS || shape.last() == MASK_CHANNELS)
        }
        if (protoIndex == null || maskChannels == 0 || (leafPicks.size == 0 && earlyPicks.size == 0 && spottedPicks.size == 0)) {
            return SymptomSplit(
                earlyScore = earlyScore.coerceIn(0f, 1f).toDouble(),
                spottedScore = spottedScore.coerceIn(0f, 1f).toDouble(),
                earlyLeafFraction = 0.0,
                spottedLeafFraction = 0.0
            )
        }

        val protoShape = runner.getOutputTensor(protoIndex).shape()
        val channelsFirst = protoShape[1] == MASK_CHANNELS
        val height = if (channelsFirst) protoShape[2] else protoShape[1]
        val width = if (channelsFirst) protoShape[3] else protoShape[2]
        val protoRaw = readFloats(outputBuffers.getValue(protoIndex) as ByteBuffer)
        val proto = if (channelsFirst) {
            protoRaw
        } else {
            val packed = FloatArray(MASK_CHANNELS * height * width)
            val plane = height * width
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val spatial = y * width + x
                    for (channel in 0 until MASK_CHANNELS) {
                        packed[channel * plane + spatial] =
                            protoRaw[(spatial * MASK_CHANNELS) + channel]
                    }
                }
            }
            packed
        }
        val leafMask = FloatArray(height * width)
        val earlyMask = FloatArray(height * width)
        val spottedMask = FloatArray(height * width)
        paintCandidates(leafMask, leafPicks, ::value, coeffStart, maskChannels, proto, height, width)
        paintCandidates(earlyMask, earlyPicks, ::value, coeffStart, maskChannels, proto, height, width)
        paintCandidates(spottedMask, spottedPicks, ::value, coeffStart, maskChannels, proto, height, width)
        val coverage = coverage(leafMask, earlyMask, spottedMask)
        return SymptomSplit(
            earlyScore = earlyScore.coerceIn(0f, 1f).toDouble(),
            spottedScore = spottedScore.coerceIn(0f, 1f).toDouble(),
            earlyLeafFraction = coverage.first,
            spottedLeafFraction = coverage.second
        )
    }

    private fun paintCandidates(
        dest: FloatArray,
        picks: TopCandidates,
        value: (candidate: Int, attribute: Int) -> Float,
        coeffStart: Int,
        channels: Int,
        proto: FloatArray,
        height: Int,
        width: Int
    ) {
        val coeffs = FloatArray(channels)
        for (pick in 0 until picks.size) {
            val candidate = picks.indexes[pick]
            for (channel in 0 until channels) {
                coeffs[channel] = value(candidate, coeffStart + channel)
            }
            val cx = value(candidate, 0)
            val cy = value(candidate, 1)
            val boxW = value(candidate, 2).coerceAtLeast(0f)
            val boxH = value(candidate, 3).coerceAtLeast(0f)
            val x0 = ((cx - boxW / 2f) * width).toInt().coerceIn(0, width - 1)
            val y0 = ((cy - boxH / 2f) * height).toInt().coerceIn(0, height - 1)
            val x1 = ((cx + boxW / 2f) * width).toInt().coerceIn(x0 + 1, width)
            val y1 = ((cy + boxH / 2f) * height).toInt().coerceIn(y0 + 1, height)
            paintMask(dest, coeffs, proto, channels, height, width, x0, y0, x1, y1)
        }
    }

    private fun paintMask(
        dest: FloatArray,
        coeffs: FloatArray,
        proto: FloatArray,
        channels: Int,
        height: Int,
        width: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int
    ) {
        val plane = height * width
        for (y in y0 until y1) {
            val row = y * width
            for (x in x0 until x1) {
                val spatial = row + x
                var logit = 0f
                var channelOffset = spatial
                for (channel in 0 until channels) {
                    logit += coeffs[channel] * proto[channelOffset]
                    channelOffset += plane
                }
                val probability = 1f / (1f + exp(-logit.coerceIn(-20f, 20f)))
                if (probability > dest[spatial]) dest[spatial] = probability
            }
        }
    }

    /** Fractions of the leaf mask covered by each symptom. Falls back to the full frame. */
    private fun coverage(
        leafMask: FloatArray,
        earlyMask: FloatArray,
        spottedMask: FloatArray
    ): Pair<Double, Double> {
        var leaf = 0
        var earlyOnLeaf = 0
        var spottedOnLeaf = 0
        var earlyAny = 0
        var spottedAny = 0
        for (index in leafMask.indices) {
            val leafOn = leafMask[index] >= MASK_PIXEL
            val earlyOn = earlyMask[index] >= MASK_PIXEL
            val spottedOn = spottedMask[index] >= MASK_PIXEL
            if (leafOn) leaf++
            if (earlyOn) earlyAny++
            if (spottedOn) spottedAny++
            if (leafOn && earlyOn) earlyOnLeaf++
            if (leafOn && spottedOn) spottedOnLeaf++
        }
        val frame = leafMask.size.coerceAtLeast(1)
        val early = if (leaf > 32) earlyOnLeaf.toDouble() / leaf else earlyAny.toDouble() / frame
        val spotted = if (leaf > 32) spottedOnLeaf.toDouble() / leaf else spottedAny.toDouble() / frame
        return early.coerceIn(0.0, 1.0) to spotted.coerceIn(0.0, 1.0)
    }

    private class TopCandidates(private val limit: Int) {
        val indexes = IntArray(limit)
        private val scores = FloatArray(limit)
        var size = 0

        fun consider(index: Int, score: Float) {
            if (score < MASK_SCORE_FLOOR) return
            if (size < limit) {
                indexes[size] = index
                scores[size] = score
                size++
                return
            }
            var worst = 0
            for (slot in 1 until size) {
                if (scores[slot] < scores[worst]) worst = slot
            }
            if (score > scores[worst]) {
                scores[worst] = score
                indexes[worst] = index
            }
        }
    }

    private fun readFloats(buffer: ByteBuffer): FloatArray {
        buffer.rewind()
        val values = FloatArray(buffer.remaining() / 4)
        buffer.asFloatBuffer().get(values)
        return values
    }

    private fun createInterpreter(context: Context): Interpreter {
        context.assets.openFd(MODEL_ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                val mapped = stream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength
                )
                return Interpreter(mapped, Interpreter.Options().setNumThreads(4))
            }
        }
    }
}
