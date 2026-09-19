package com.abhilekh.app.core.cv

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Native C++ Bridge for high-performance image transformations and pixel-level redaction.
 */
object OpenCVNativeBridge {
    init {
        System.loadLibrary("abhilekh-native")
    }

    /**
     * Applies Morphological Background Estimation & Illumination Division filter.
     * Turns yellowed, shadowed Indian photocopy and ruled paper into clean white paper.
     */
    external fun nativeApplyIlluminationDivision(bitmap: Bitmap)

    /**
     * Permanently blacks out rectangular regions in the native pixel buffer.
     */
    external fun nativeMaskBoundingBox(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int)

    fun maskRect(bitmap: Bitmap, rect: Rect) {
        nativeMaskBoundingBox(bitmap, rect.left, rect.top, rect.right, rect.bottom)
    }
}
