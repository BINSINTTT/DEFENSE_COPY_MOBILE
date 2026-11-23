package com.tomatoinsight.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import java.io.File

object CameraCaptureUtils {

    fun loadOrientedBitmap(file: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalStateException("Could not decode captured image.")
        return applyExifRotation(file, bitmap)
    }

    fun cropToGuideFrame(source: Bitmap): Bitmap {
        val bounds = TomatoLeafShape.guideBounds(source.width.toFloat(), source.height.toFloat())
        val side = bounds.width.toInt().coerceAtLeast(1)
        val left = bounds.left.toInt()
        val top = bounds.top.toInt()
        val region = Rect(left, top, left + side, top + side)
        val size = LeafCameraGuide.OUTPUT_SIZE
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(source, region, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return output
    }

    fun createCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, "leaf_captures").apply { mkdirs() }
        return File(dir, "leaf_${System.currentTimeMillis()}.jpg")
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val rotation = when (
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        if (rotation == 0) return bitmap

        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
