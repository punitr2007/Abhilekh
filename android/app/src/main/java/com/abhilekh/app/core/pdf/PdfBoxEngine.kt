package com.abhilekh.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.text.Text
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class PdfPageInput(
    val bitmap: Bitmap,
    val ocrText: Text?,
    val maskedAadhaarText: String? = null
)

object PdfBoxEngine {

    fun init(context: Context) {
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Builds a dual-layer searchable PDF where the OCR text layer uses
     * PDF Text Rendering Mode 3 ('3 Tr' / RenderingMode.NEITHER).
     */
    suspend fun createSearchablePdf(
        pages: List<PdfPageInput>,
        outputFile: File,
        targetQuality: Float = 0.82f
    ): File = withContext(Dispatchers.IO) {
        val document = PDDocument()

        try {
            for (pageInput in pages) {
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)

                val pdImage = JPEGFactory.createFromImage(document, pageInput.bitmap, targetQuality)
                val pageWidth = PDRectangle.A4.width
                val pageHeight = PDRectangle.A4.height

                PDPageContentStream(document, page).use { contentStream ->
                    // 1. Draw base raster image
                    contentStream.drawImage(pdImage, 0f, 0f, pageWidth, pageHeight)

                    // 2. Set Text Rendering Mode 3 (Neither fill nor stroke)
                    // Glyphs are indexed in the PDF text stream for selection/search, but visually invisible
                    contentStream.setRenderingMode(RenderingMode.NEITHER)
                    contentStream.setFont(PDType1Font.HELVETICA, 10f)

                    // 3. Write OCR text glyphs
                    val visionText = pageInput.ocrText
                    if (visionText != null) {
                        val imgWidth = pageInput.bitmap.width.toFloat()
                        val imgHeight = pageInput.bitmap.height.toFloat()

                        for (block in visionText.textBlocks) {
                            for (line in block.lines) {
                                val box = line.boundingBox ?: continue
                                val scaleX = pageWidth / imgWidth
                                val scaleY = pageHeight / imgHeight

                                // PDF coordinate system origin is at the BOTTOM-LEFT
                                val pdfX = box.left * scaleX
                                val pdfY = pageHeight - (box.bottom * scaleY)
                                val fontSize = maxOf(6f, box.height() * scaleY * 0.8f)

                                var textToWrite = line.text
                                // If Aadhaar was masked on this page, replace raw digits with masked placeholder
                                if (pageInput.maskedAadhaarText != null && textToWrite.contains(pageInput.maskedAadhaarText)) {
                                    textToWrite = pageInput.maskedAadhaarText
                                }

                                try {
                                    contentStream.beginText()
                                    contentStream.setFont(PDType1Font.HELVETICA, fontSize)
                                    contentStream.newLineAtOffset(pdfX, pdfY)
                                    contentStream.showText(cleanAscii(textToWrite))
                                    contentStream.endText()
                                } catch (_: Exception) {
                                    // Skip unencodable glyphs gracefully
                                }
                            }
                        }
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.save(out)
            }
        } finally {
            document.close()
        }

        outputFile
    }

    /**
     * Merges multiple existing PDF files into a single unified document ("Combine Files" tool).
     */
    suspend fun mergePdfFiles(sourceFiles: List<File>, destinationFile: File): File = withContext(Dispatchers.IO) {
        val mergedDoc = PDDocument()
        try {
            for (file in sourceFiles) {
                val doc = PDDocument.load(file)
                for (i in 0 until doc.numberOfPages) {
                    mergedDoc.addPage(doc.getPage(i))
                }
                doc.close()
            }
            FileOutputStream(destinationFile).use { out ->
                mergedDoc.save(out)
            }
        } finally {
            mergedDoc.close()
        }
        destinationFile
    }

    private fun cleanAscii(input: String): String {
        return input.filter { it.code in 32..126 }
    }
}
