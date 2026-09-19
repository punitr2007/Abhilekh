package com.abhilekh.app.core.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrManager {
    // Initialized with Devanagari options (supporting Hindi, Marathi, Sanskrit + Latin/English)
    private val recognizer = TextRecognition.getClient(
        DevanagariTextRecognizerOptions.Builder().build()
    )

    /**
     * Executes on-device OCR on the provided bitmap using ML Kit v2.
     */
    suspend fun recognizeText(bitmap: Bitmap): Text = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { text ->
                continuation.resume(text)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }
}
