package com.tomatoinsight.ui.camera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/** The preview and oriented CameraX viewport crop share this square. */
object TomatoLeafShape {
    fun guideBounds(canvasWidth: Float, canvasHeight: Float): Rect {
        val side = min(canvasWidth * 0.80f, canvasHeight * 0.62f)
        return Rect(
            offset = Offset((canvasWidth - side) / 2f, (canvasHeight - side) * 0.40f),
            size = Size(side, side)
        )
    }
}
