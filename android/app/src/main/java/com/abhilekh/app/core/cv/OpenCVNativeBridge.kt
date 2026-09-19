package com.abhilekh.app.core.cv

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect

enum class FilterMode(val displayName: String) {
    ORIGINAL("Original"),
    ILLUMINATION_DIVISION("Magic Clean"),
    COLOR_BOOST("Color Boost"),
    GRAYSCALE("Grayscale"),
    CLEAN_BW("Clean B&W")
}

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
     * Applies fast native Grayscale transformation.
     */
    external fun nativeApplyGrayscale(bitmap: Bitmap)

    /**
     * Applies adaptive local threshold binarization (Clean B&W).
     */
    external fun nativeApplyBinarization(bitmap: Bitmap)

    /**
     * Enhances saturation, vibrancy and contrast for stamps, colored text, and signatures.
     */
    external fun nativeApplyColorBoost(bitmap: Bitmap)

    /**
     * Permanently blacks out rectangular regions in the native pixel buffer.
     */
    external fun nativeMaskBoundingBox(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int)

    fun maskRect(bitmap: Bitmap, rect: Rect) {
        nativeMaskBoundingBox(bitmap, rect.left, rect.top, rect.right, rect.bottom)
    }

    /**
     * Applies the requested FilterMode to a fresh copy of the source bitmap.
     */
    fun applyFilter(source: Bitmap, filter: FilterMode): Bitmap {
        val workingBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        when (filter) {
            FilterMode.ORIGINAL -> return workingBitmap
            FilterMode.ILLUMINATION_DIVISION -> nativeApplyIlluminationDivision(workingBitmap)
            FilterMode.COLOR_BOOST -> nativeApplyColorBoost(workingBitmap)
            FilterMode.GRAYSCALE -> nativeApplyGrayscale(workingBitmap)
            FilterMode.CLEAN_BW -> nativeApplyBinarization(workingBitmap)
        }
        return workingBitmap
    }

    /**
     * Rotates bitmap by degrees clockwise.
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Crops bitmap to specified rectangular bounds.
     */
    fun cropBitmap(source: Bitmap, cropRect: Rect): Bitmap {
        val safeLeft = cropRect.left.coerceIn(0, source.width - 1)
        val safeTop = cropRect.top.coerceIn(0, source.height - 1)
        val safeWidth = cropRect.width().coerceIn(1, source.width - safeLeft)
        val safeHeight = cropRect.height().coerceIn(1, source.height - safeTop)
        return Bitmap.createBitmap(source, safeLeft, safeTop, safeWidth, safeHeight)
    }
}
