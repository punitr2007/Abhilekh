package com.abhilekh.app.core.masking

import android.graphics.Bitmap
import android.graphics.Rect
import com.abhilekh.app.core.cv.OpenCVNativeBridge
import com.google.mlkit.vision.text.Text

data class AadhaarDetectionResult(
    val hasAadhaar: Boolean,
    val isAutoMasked: Boolean,
    val requiresManualReview: Boolean,
    val rawMatchedText: String?,
    val maskedText: String?,
    val maskRects: List<Rect>
)

object AadhaarMaskingEngine {
    // Permissive 12-digit regex with optional spaces/hyphens
    private val AADHAAR_REGEX = Regex("""\b([2-9]{1}[0-9]{3})[\s-]?([0-9]{4})[\s-]?([0-9]{4})\b""")

    // Contextual keywords common on Aadhaar cards
    private val CONTEXT_KEYWORDS = listOf(
        "आधार", "aadhaar", "uid", "government of india", "भारत सरकार",
        "dob", "जन्म", "male", "female", "पुरुष", "महिला", "enrollment"
    )

    /**
     * Evaluates OCR text across the full page using multi-signal heuristics.
     */
    fun evaluateAndMask(bitmap: Bitmap, visionText: Text): AadhaarDetectionResult {
        val fullText = visionText.text.lowercase()
        val match = AADHAAR_REGEX.find(visionText.text)
        
        if (match == null) {
            // Check if context keywords exist (potential distorted scan)
            val hasStrongKeywords = CONTEXT_KEYWORDS.count { fullText.contains(it) } >= 2
            return AadhaarDetectionResult(
                hasAadhaar = hasStrongKeywords,
                isAutoMasked = false,
                requiresManualReview = hasStrongKeywords, // Prompt user to check
                rawMatchedText = null,
                maskedText = null,
                maskRects = emptyList()
            )
        }

        val rawDigits = match.value.filter { it.isDigit() }
        val isVerhoeffValid = VerhoeffAlgorithm.validateVerhoeff(rawDigits)
        val hasKeywordContext = CONTEXT_KEYWORDS.any { fullText.contains(it) }

        val maskRects = mutableListOf<Rect>()

        // Locate bounding boxes for the first 8 digits
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                if (line.text.contains(match.value)) {
                    val box = line.boundingBox
                    if (box != null) {
                        // Calculate width for the first 8 digits (approx ~66% of text block width)
                        val maskWidth = (box.width() * 8) / 12
                        val targetRect = Rect(box.left, box.top, box.left + maskWidth, box.bottom)
                        maskRects.add(targetRect)
                    }
                }
            }
        }

        val highConfidence = isVerhoeffValid || (hasKeywordContext && rawDigits.length == 12)

        if (highConfidence && maskRects.isNotEmpty()) {
            // Permanently redact pixels in the bitmap
            for (rect in maskRects) {
                OpenCVNativeBridge.maskRect(bitmap, rect)
            }
            val last4 = rawDigits.takeLast(4)
            return AadhaarDetectionResult(
                hasAadhaar = true,
                isAutoMasked = true,
                requiresManualReview = false,
                rawMatchedText = match.value,
                maskedText = "XXXX XXXX $last4",
                maskRects = maskRects
            )
        } else {
            // Fail loud: flag for manual review
            return AadhaarDetectionResult(
                hasAadhaar = true,
                isAutoMasked = false,
                requiresManualReview = true,
                rawMatchedText = match.value,
                maskedText = null,
                maskRects = maskRects
            )
        }
    }
}
