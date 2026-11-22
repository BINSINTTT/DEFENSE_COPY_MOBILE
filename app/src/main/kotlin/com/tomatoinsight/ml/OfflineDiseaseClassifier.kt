package com.tomatoinsight.ml

import android.content.Context
import android.graphics.Bitmap
import com.tomatoinsight.api.DiseasePrediction
import com.tomatoinsight.api.ValidationResponse
import com.tomatoinsight.data.ensureGuidesForAllDetections
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Ultralytics diseaseclass04.tflite metadata class indices, in output channel order. */
private val DISEASE_DETECTION_CLASSES = listOf(
    "Early blight",           // 0: early_blight
    "Yellow leaf curl virus", // 1: tlcv
    "Spotted wilt",           // 2: spotted_wilt
    "Healthy"                 // 3: healthy
)

private const val MIN_DISEASE_CONFIDENCE = 0.20

internal fun confidentDiseasePredictions(predictions: List<DiseasePrediction>): List<DiseasePrediction> =
    predictions.filter { (it.confidence ?: it.score ?: 0.0) >= MIN_DISEASE_CONFIDENCE }

/**
 * Decode the float32 [1, 4 + classes, boxes] output. Each candidate has xywh
 * channels followed by one probability per class. Only the highest-confidence
 * valid box per class is needed for the app's disease bars; duplicate-box NMS
 * cannot change those maxima.
 */
internal fun decodeDiseaseDetections(output: FloatArray, boxCount: Int): List<DiseasePrediction> {
    require(boxCount > 0 && output.size == (4 + DISEASE_DETECTION_CLASSES.size) * boxCount) {
        "Unexpected disease detector output size."
    }
    val best = FloatArray(DISEASE_DETECTION_CLASSES.size)
    for (box in 0 until boxCount) {
        val centerX = output[box]
        val centerY = output[boxCount + box]
        val width = output[2 * boxCount + box]
        val height = output[3 * boxCount + box]
        if (!centerX.isFinite() || !centerY.isFinite() || !width.isFinite() || !height.isFinite() ||
            width <= 0f || height <= 0f) continue

        for (classIndex in DISEASE_DETECTION_CLASSES.indices) {
            val confidence = output[(4 + classIndex) * boxCount + box]
            if (confidence.isFinite() && confidence in 0f..1f && confidence > best[classIndex]) {
                best[classIndex] = confidence
            }
        }
    }
    return DISEASE_DETECTION_CLASSES.mapIndexed { index, name ->
        DiseasePrediction(className = name, confidence = best[index].toDouble())
    }
}

/** Runs disease detection after the offline tomato-leaf validator accepts the image. */
object OfflineDiseaseClassifier {
    private const val MODEL_ASSET = "models/diseaseclass04.tflite"
    private const val IMAGE_SIZE = 640

    @Volatile
    private var interpreter: Interpreter? = null

    fun classify(context: Context, bitmap: Bitmap, leaf: ValidationResponse): ValidationResponse {
        val decoded = synchronized(this) {
            val runner = interpreter ?: createInterpreter(context).also { interpreter = it }
            val inputShape = runner.getInputTensor(0).shape()
            val outputTensor = runner.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            require(inputShape.contentEquals(intArrayOf(1, 3, IMAGE_SIZE, IMAGE_SIZE)) &&
                runner.getInputTensor(0).dataType() == DataType.FLOAT32 &&
                outputShape.size == 3 && outputShape[0] == 1 &&
                outputShape[1] == 4 + DISEASE_DETECTION_CLASSES.size && outputShape[2] > 0 &&
                outputTensor.dataType() == DataType.FLOAT32) {
                "The disease detector must have float32 input [1,3,640,640] " +
                    "and output [1,8,boxes]."
            }
            val outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes())
                .order(ByteOrder.nativeOrder())
            runner.run(bitmapToNchw(bitmap, IMAGE_SIZE), outputBuffer)
            outputBuffer.rewind()
            val values = FloatArray(outputTensor.numBytes() / Float.SIZE_BYTES)
            outputBuffer.asFloatBuffer().get(values)
            decodeDiseaseDetections(values, outputShape[2])
        }
        val acceptedPredictions = confidentDiseasePredictions(decoded)
        val top = acceptedPredictions.maxByOrNull { it.confidence ?: 0.0 }
        val keptOnlineCare = top != null && leaf.aiRecommendations != null
        return leaf.copy(
            predictions = acceptedPredictions,
            topDisease = top?.displayName,
            topDiseaseConfidence = top?.confidence,
            diseaseClasses = acceptedPredictions.mapNotNull { it.displayName },
            inferenceMode = "tflite_detection",
            modelPath = MODEL_ASSET,
            message = if (top == null) {
                "No disease detection reached 20% confidence. Scan another tomato leaf in bright, even light."
            } else if (keptOnlineCare) {
                leaf.message ?: "Tomato leaf detected."
            } else {
                "Detected on this phone. Scan online to measure affected leaf area and prepare the right care plan."
            },
            aiRecommendations = if (keptOnlineCare) leaf.aiRecommendations else null,
            aiRecommendationError = if (top != null) leaf.aiRecommendationError else null,
            recommendationSource = if (top != null) leaf.recommendationSource ?: "rules" else null
        ).ensureGuidesForAllDetections()
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
