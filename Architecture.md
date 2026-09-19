# Technical Architecture & Systems Engineering
## Project: Abhilekh (अभिलेख) — Lean Android Native Architecture (v3)

---

## 1. System Architecture Overview

Abhilekh is built as a **pure Kotlin + Jetpack Compose native Android application**. It pairs Google Play Services **ML Kit Document Scanner API** with lightweight on-device C++ OpenCV paper enhancement, Google ML Kit Text Recognition v2, and **Apache PDFBox-Android** for robust, low-level PDF stream manipulation.

```
+───────────────────────────────────────────────────────────────────────────────────────────────────+
|                                    ANDROID JETPACK COMPOSE UI LAYER                               |
|  [ML Kit Document Scanner Intent] [7-Tab Capture Picker] [Filter Carousel] [Grid Reorder/Combine] |
|  [Aadhaar Masking HUD & Manual Brush] [PDF Export & Compression Slider] [1-Tap WhatsApp Button]    |
+───────────────────────────────────────────────────────────────────────────────────────────────────+
                                                  │
                                                  ▼
+───────────────────────────────────────────────────────────────────────────────────────────────────+
|                                  APPLICATION CORE CONTROLLER (KOTLIN)                             |
|  - ScanSessionManager (Multi-page batch & high-speed rapid burst handler)                         |
|  - GlobalAadhaarDetector (Regex + Verhoeff + Contextual Keyword Heuristics on ALL pages)          |
|  - PdfBoxAssembler (Dual-layer PDF engine with Text Rendering Mode 3 / '3 Tr' Content Streams)    |
|  - AdaptiveCompressor (Target size optimizer: <500KB Govt Mode, <1MB WhatsApp)                    |
+───────────────────────────────────────────────────────────────────────────────────────────────────+
                                                  │
                        ┌─────────────────────────┴─────────────────────────┐
                        ▼                                                   ▼
+───────────────────────────────────────────────────+   +───────────────────────────────────────────+
|             NATIVE OPENCV ENGINE (C++ / JNI)      |   |       ON-DEVICE OCR ENGINE (ML KIT)       |
|  - Morphological Illumination Division Filter      |   |  - Google ML Kit Text Recognition v2      |
|    (Background Normalization: Dilate+Median+Divide|   |  - Bundled English + Devanagari (Hindi)   |
|  - Adaptive Sauvola & Otsu Binarization           |   |  - On-Device Token & Bounding Box Extractor|
|  - Thermal Paper Contrast Enhancement Curve       |   +───────────────────────────────────────────+
+───────────────────────────────────────────────────+                         │
                        │                                                     │
                        └─────────────────────────┬───────────────────────────┘
                                                  ▼
+───────────────────────────────────────────────────────────────────────────────────────────────────+
|                               APACHE PDFBOX-ANDROID ASSEMBLY ENGINE                               |
|  - com.tom-roush:pdfbox-android (Apache 2.0 Licensed)                                             |
|  - Dual-Layer Searchable PDF Construction:                                                        |
|    • Layer 1 (Base): Enhanced raster image (JPEG / WebP compressed)                               |
|    • Layer 2 (Top): PDPageContentStream with RenderingMode.NEITHER ('3 Tr') for invisible text   |
|  - Export Mode Specialization: Standard Searchable PDF, PDF/A-1b Archival, AES-256 Encrypted      |
+───────────────────────────────────────────────────────────────────────────────────────────────────+
                                                  │
                                                  ▼
+───────────────────────────────────────────────────────────────────────────────────────────────────+
|                                   OFFLINE STORAGE & ROOM DATABASE                                 |
|  - SQLite / Room DB with FTS5 Full-Text Search                                                    |
|  - App Sandboxed Storage: `/data/user/0/com.abhilekh.app/files/documents/{doc_id}`                |
|  - DPDP Erasure Manager: 1-Tap complete local and cloud data wipe                                 |
+───────────────────────────────────────────────────────────────────────────────────────────────────+
```

---

## 2. Capture & Computer Vision Pipeline

```
[Camera Capture via ML Kit Document Scanner API]
                      │
                      ▼
[Auto-Detected Quad & Homography Rectified Bitmap (12MP)]
                      │
                      ▼
[Native C++ Illumination Division Filter: enhanceDocumentPaper()]
                      │
                      ├───────────────────────────────────────────┐
                      ▼                                           ▼
         [ML Kit Text Recognition v2]                 [Enhanced Color / B&W Bitmap]
                      │                                           │
  [Global Aadhaar Multi-Signal Pattern Matcher]                   │
                      │                                           │
        [Auto-Mask 8 Digits in Bitmap]                            │
                      │                                           │
                      └─────────────────────┬─────────────────────┘
                                            ▼
                  [Apache PDFBox Dual-Layer Searchable PDF ('3 Tr')]
```

### 2.1 The Illumination Division Filter (Background Normalization)
Indian documents (yellowed photocopy stock, notebook ruled paper, government forms) frequently exhibit heavy shadows from overhead tube lights. Abhilekh uses a **Morphological Background Estimation & Illumination Division** filter:

```cpp
#include <opencv2/opencv.hpp>

// Morphological Background Estimation & Illumination Division
cv::Mat enhanceDocumentPaper(const cv::Mat& srcRgb) {
    cv::Mat gray, bgIllumination, normalized, result;
    cv::cvtColor(srcRgb, gray, cv::COLOR_RGB2GRAY);
    
    // Step 1: Estimate low-frequency background illumination
    // Dilate removes dark text strokes, leaving the paper background estimate
    cv::dilate(gray, bgIllumination, cv::getStructuringElement(cv::MORPH_RECT, cv::Size(15, 15)));
    // Median blur smooths out abrupt transitions and noise
    cv::medianBlur(bgIllumination, bgIllumination, 21);
    
    // Step 2: Illumination division (Pixel Value / Background Value * 255)
    cv::Mat grayFloat, bgFloat;
    gray.convertTo(grayFloat, CV_32F);
    bgIllumination.convertTo(bgFloat, CV_32F);
    
    cv::divide(grayFloat, bgFloat, normalized, 255.0);
    normalized.convertTo(normalized, CV_8U);
    
    // Step 3: Contrast Stretching (Normalize luminance to full 0-255 dynamic range)
    cv::normalize(normalized, normalized, 0, 255, cv::NORM_MINMAX);
    
    // Step 4: Recombine with original chrominance (YCrCb) to preserve blue/red ink stamps
    std::vector<cv::Mat> ycrcb;
    cv::cvtColor(srcRgb, result, cv::COLOR_RGB2YCrCb);
    cv::split(result, ycrcb);
    ycrcb[0] = normalized; // Replace luminance with clean normalized white paper
    cv::merge(ycrcb, result);
    cv::cvtColor(result, result, cv::COLOR_YCrCb2RGB);
    
    return result;
}
```

---

## 3. Global Aadhaar Masking & Multi-Signal Detection

```
[OCR Output of Current Page (Any Capture Mode)]
                     │
                     ▼
[Permissive Regex: \b[2-9]{1}[0-9]{3}[\s-]?[0-9]{4}[\s-]?[0-9]{4}\b]
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
    [Match Found]           [No Match] ──► Normal Flow
         │
         ▼
[Verhoeff Checksum Calculation]
         │
         ▼
[Context Keyword Evaluation ("आधार", "Aadhaar", "UID", "DOB", "Male", "Female")]
         │
         ├───────────────────────────────────────────┐
         ▼                                           ▼
[Confidence >= 90% (Regex + Verhoeff + Keyword)]  [Borderline / Unverified UID]
         │                                           │
[Auto-Mask First 8 Digits in Bitmap & PDF Text]    [FAIL LOUD: Show Amber Alert Toast]
         │                                           │
         ▼                                           ▼
[Display Emerald 🛡️ MASKED Badge]                 [Route to Manual Redaction Brush]
```

### 3.1 Dual-Layer Redaction Logic
1. **Pixel Buffer Redaction**:
   ```cpp
   cv::Rect maskRect(box.x, box.y, (box.width * 8) / 12, box.height);
   cv::rectangle(imageMat, maskRect, cv::Scalar(0, 0, 0), cv::FILLED);
   ```
2. **Searchable Text Stream Sanitization**: The text emitted into the PDF stream replaces the first 8 digits with `XXXX XXXX `, ensuring that copy-pasting from the PDF cannot leak the Aadhaar number.

---

## 4. Searchable PDF Construction via Apache PDFBox (`3 Tr`)

```kotlin
import com.tomroush.pdfbox.pdmodel.PDDocument
import com.tomroush.pdfbox.pdmodel.PDPage
import com.tomroush.pdfbox.pdmodel.PDPageContentStream
import com.tomroush.pdfbox.pdmodel.common.PDRectangle
import com.tomroush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tomroush.pdfbox.pdmodel.font.PDType1Font
import com.tomroush.pdfbox.pdmodel.graphics.state.RenderingMode

fun assembleSearchablePdfPage(
    document: PDDocument,
    enhancedBitmap: Bitmap,
    ocrBlocks: List<OcrTextBlock>
) {
    val page = PDPage(PDRectangle.A4)
    document.addPage(page)
    
    val pdImage = JPEGFactory.createFromImage(document, enhancedBitmap, 0.82f)
    
    PDPageContentStream(document, page).use { contentStream ->
        // 1. Draw raster background image
        contentStream.drawImage(pdImage, 0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height)
        
        // 2. Set Text Rendering Mode 3 ('3 Tr' -> Neither fill nor stroke text)
        // Glyphs are indexed and selectable, but completely invisible
        contentStream.setRenderingMode(RenderingMode.NEITHER)
        contentStream.setFont(PDType1Font.HELVETICA, 10f)
        
        // 3. Draw OCR text glyphs at exact bounding box coordinates
        for (block in ocrBlocks) {
            for (word in block.words) {
                val (pdfX, pdfY, fontSize) = mapToPdfCoordinates(word.boundingBox, PDRectangle.A4)
                contentStream.beginText()
                contentStream.setFont(PDType1Font.HELVETICA, fontSize)
                contentStream.newLineAtOffset(pdfX, pdfY)
                contentStream.showText(word.text)
                contentStream.endText()
            }
        }
    }
}
```

---

## 5. Local Database Schema (Room / SQLite)

```sql
-- Documents Table
CREATE TABLE documents (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    folder_id TEXT,
    page_count INTEGER DEFAULT 1,
    file_size_bytes INTEGER NOT NULL,
    thumbnail_path TEXT NOT NULL,
    pdf_path TEXT,
    export_type TEXT CHECK(export_type IN ('standard', 'pdf_a', 'encrypted')),
    is_favorite INTEGER DEFAULT 0,
    FOREIGN KEY(folder_id) REFERENCES folders(id) ON DELETE SET NULL
);

-- Pages Table
CREATE TABLE pages (
    id TEXT PRIMARY KEY NOT NULL,
    document_id TEXT NOT NULL,
    page_number INTEGER NOT NULL,
    image_path TEXT NOT NULL,
    thumb_path TEXT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    filter_type TEXT DEFAULT 'illumination_division',
    ocr_text TEXT,
    is_masked INTEGER DEFAULT 0,
    FOREIGN KEY(document_id) REFERENCES documents(id) ON DELETE CASCADE
);

-- Folders Table
CREATE TABLE folders (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    color_hex TEXT DEFAULT '#1E88E5',
    created_at INTEGER NOT NULL
);

-- Full-Text Search (FTS5) Table
CREATE VIRTUAL TABLE document_search USING fts5(
    document_id UNINDEXED,
    page_number UNINDEXED,
    ocr_text
);
```
