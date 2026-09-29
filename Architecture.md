# Technical Architecture & Systems Engineering
## Project: Abhilekh (अभिलेख) — Hardened Multiplatform Architecture (v4)

---

## 1. Executive Summary & Critical Tradeoff Resolutions

Abhilekh is built on a **Kotlin Multiplatform (KMP) shared logic core** paired with high-performance **native OS frontends** (Jetpack Compose on Android, SwiftUI on iOS) and a unified **cross-platform C++17 Computer Vision core** linked via JNI (Android) and `cinterop` (iOS).

```
                                      ABHILEKH UNIFIED SYSTEM TOPOLOGY
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       KOTLIN MULTIPLATFORM (KMP) CORE                                   │
│  • Aadhaar Multi-Signal Redactor (Regex + Verhoeff D5 Checksum + Indic Context Keyword Scoring)          │
│  • Document State Machine (Session, Batch Queue, Compression Profiles, Merge Logic)                     │
│  • Cross-Platform Thermal State Governor & Ephemeral Crypto-Shredding Session Storage Contracts         │
└───────────────────────────────────────────────────┬─────────────────────────────────────────────────────┘
                                                    │
                   ┌────────────────────────────────┴────────────────────────────────┐
                   ▼                                                                 ▼
┌──────────────────────────────────────────────────┐┌────────────────────────────────────────────────────┐
│              ANDROID NATIVE ENGINE               ││                 IOS NATIVE ENGINE                  │
│ • UI: Jetpack Compose (Owned Design Tokens)      ││ • UI: SwiftUI (Owned Design Tokens)                │
│ • Camera HUD: GMS ML Kit Document Scanner        ││ • Camera HUD: VisionKit (VNDocumentCamera)         │
│ • OCR: Bundled ML Kit Text Recognition v2        ││ • OCR: Apple Vision (VNRecognizeTextRequest)       │
│ • PDF: Apache PDFBox-Android ('3 Tr' PDF/A-1b)   ││ • PDF: PDFKit / CoreGraphics ('3 Tr' PDF/A-1b)     │
│ • Island: Android 16 Live Updates / FG Service   ││ • Island: Hand-Rolled ActivityKit Widget           │
│ • Thermal: PowerManager Headroom + Battery Fallbk││ • Thermal: ProcessInfo.processInfo.thermalState    │
└─────────────────────────┬────────────────────────┘└─────────────────────────┬──────────────────────────┘
                          │                                                   │
                          └─────────────────────────┬─────────────────────────┘
                                                    ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   UNIFIED C++17 COMPUTER VISION CORE                                    │
│                       (Compiled Once -> Android JNI + iOS Kotlin/Native cinterop)                       │
│  • Background Normalization via Morphological Illumination Division                                     │
│  • Sauvola / Otsu Adaptive Binarization & Dynamic Contrast Stretching                                   │
│  • In-Place Destructive Pixel Zeroing (`std::fill` / `memset`)                                          │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Hardening Solutions to Key Architectural Critiques

### 2.1 Unified C++17 Computer Vision Core (`cinterop` + JNI)
* **Critique**: Duplicating filters in C++/OpenCV for Android and Metal/Accelerate for iOS causes math divergence, rounding discrepancies, and doubled maintenance.
* **Resolution**: A single C++17 filter core (`abhilekh_cv.hpp` & `abhilekh_cv.cpp`) is compiled once into `libabhilekh_cv`. It connects via JNI on Android and Kotlin/Native `cinterop` on iOS.

### 2.2 Dual-Platform Thermal & Battery Throttling Governor
* **Critique**: Android 11 (`getThermalHeadroom`) has a minSdk floor of API 30, and iOS had no equivalent thermal governor.
* **Resolution**:
  * **Android API 30+**: Uses `PowerManager.getThermalHeadroom(30)`.
  * **Android API 24–29 (Fallback)**: Monitors `Intent.ACTION_BATTERY_CHANGED` for battery temperatures exceeding 42°C (`EXTRA_TEMPERATURE > 420`).
  * **iOS**: Monitors `ProcessInfo.processInfo.thermalState` (`.serious` / `.critical`).
  * **Throttle Action**: When entering throttled states, the batch worker automatically downscales raster inputs from 300 DPI to 200 DPI and yields the coroutine/task for 50ms per page.

### 2.3 Flash-Safe Ephemeral Crypto-Shredding & Deterministic Memory Zeroing
* **Critique**: `System.gc()` is a non-binding JVM hint, and standard file `delete()` fails DPDP right-to-erasure on wear-leveled flash storage.
* **Resolution**:
  * **In-Memory**: Aadhaar coordinates undergo destructive in-place zeroing (`memset(0)` / `std::fill(0)`) directly on raw pixel buffers before PDF rendering.
  * **Storage**: Temporary page bitmaps are encrypted with an ephemeral in-memory AES-256-GCM key upon capture. On session compilation or cancellation, the AES key is wiped with `0x00` from RAM, rendering all unlinked flash blocks cryptographically irrecoverable.

### 2.4 Strict PDF/A-1b Conformance & Automated veraPDF CI Testing
* **Critique**: Claiming PDF/A-1b without explicit validation often masks compliance violations (missing ICC color profiles, un-embedded fonts).
* **Resolution**: Output streams include embedded sRGB ICC color profiles, TrueType/Type1 font subsets for invisible Mode 3 (`3 Tr`) text layers, and XMP metadata packets. CI pipelines validate generated sample PDFs using **veraPDF CLI**.

### 2.5 Programmatic Synthetic Aadhaar Benchmark Suite
* **Critique**: Cannot ethically collect real citizens' Aadhaar cards for $>99\%$ precision benchmarks.
* **Resolution**: Programmatic generation of 1,000 synthetic Aadhaar test cards with valid Verhoeff dihedral group $D_5$ checksums, Devanagari text, synthetic Gaussian blur, perspective skews, and lighting gradients for automated regression testing.

---

## 3. Computer Vision & Paper Enhancement Pipeline

```
[Camera Capture via ML Kit (Android) or VisionKit (iOS)]
                         │
                         ▼
[Auto-Detected Quad & Homography Rectified Bitmap (12MP)]
                         │
                         ▼
[Unified C++ Illumination Division Filter: enhanceDocumentPaper()]
                         │
                         ├───────────────────────────────────────────┐
                         ▼                                           ▼
            [On-Device OCR Engine]                      [Enhanced Color / B&W Bitmap]
                         │                                           │
     [Global Aadhaar Multi-Signal Pattern Matcher]                   │
                         │                                           │
         [Auto-Mask 8 Digits in Bitmap]                              │
                         │                                           │
                         └─────────────────────┬─────────────────────┘
                                               ▼
               [Dual-Layer Searchable PDF Assembler ('3 Tr' PDF/A-1b)]
```

### 3.1 The Morphological Illumination Division Algorithm
```cpp
#include <vector>
#include <algorithm>
#include <cmath>
#include <cstdint>

void processIlluminationDivision(uint8_t* pixels, int32_t width, int32_t height, int32_t stride) {
    std::vector<uint8_t> gray(width * height);
    std::vector<uint8_t> bgDilated(width * height);

    // 1. Convert RGBA to Grayscale
    for (int32_t y = 0; y < height; ++y) {
        const uint32_t* row = reinterpret_cast<const uint32_t*>(pixels + y * stride);
        for (int32_t x = 0; x < width; ++x) {
            uint32_t c = row[x];
            uint8_t r = (c >> 16) & 0xFF;
            uint8_t g = (c >> 8) & 0xFF;
            uint8_t b = c & 0xFF;
            gray[y * width + x] = static_cast<uint8_t>((299 * r + 587 * g + 114 * b) / 1000);
        }
    }

    // 2. Morphological Dilation (Radius: 7px box max filter)
    const int32_t kRadius = 7;
    for (int32_t y = 0; y < height; ++y) {
        int32_t yMin = std::max(0, y - kRadius);
        int32_t yMax = std::min(height - 1, y + kRadius);
        for (int32_t x = 0; x < width; ++x) {
            int32_t xMin = std::max(0, x - kRadius);
            int32_t xMax = std::min(width - 1, x + kRadius);
            uint8_t maxVal = 0;

            for (int32_t ky = yMin; ky <= yMax; ky += 2) {
                for (int32_t kx = xMin; kx <= xMax; kx += 2) {
                    uint8_t v = gray[ky * width + kx];
                    if (v > maxVal) maxVal = v;
                }
            }
            bgDilated[y * width + x] = maxVal;
        }
    }

    // 3. Illumination Division + Contrast Stretching
    for (int32_t y = 0; y < height; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(pixels + y * stride);
        for (int32_t x = 0; x < width; ++x) {
            uint32_t c = row[x];
            uint8_t a = (c >> 24) & 0xFF;
            uint8_t r = (c >> 16) & 0xFF;
            uint8_t g = (c >> 8) & 0xFF;
            uint8_t b = c & 0xFF;

            float bg = std::max(1.0f, static_cast<float>(bgDilated[y * width + x]));
            float normR = std::min(255.0f, (r / bg) * 255.0f);
            float normG = std::min(255.0f, (g / bg) * 255.0f);
            float normB = std::min(255.0f, (b / bg) * 255.0f);

            uint8_t finalR = static_cast<uint8_t>(std::clamp(normR * 1.05f - 5.0f, 0.0f, 255.0f));
            uint8_t finalG = static_cast<uint8_t>(std::clamp(normG * 1.05f - 5.0f, 0.0f, 255.0f));
            uint8_t finalB = static_cast<uint8_t>(std::clamp(normB * 1.05f - 5.0f, 0.0f, 255.0f));

            row[x] = (a << 24) | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}
```

---

## 4. Multi-Signal Global Aadhaar Redaction Engine

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
[Verhoeff D5 Checksum Calculation]
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

---

## 5. Dual-Layer Searchable PDF Construction (`3 Tr` & PDF/A-1b)

```kotlin
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode

fun assembleSearchablePdfPage(
    document: PDDocument,
    page: PDPage,
    enhancedBitmap: Bitmap,
    ocrWords: List<OcrWord>
) {
    val pdImage = JPEGFactory.createFromImage(document, enhancedBitmap, 0.82f)
    
    PDPageContentStream(document, page).use { contentStream ->
        // 1. Draw base raster image
        contentStream.drawImage(pdImage, 0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height)
        
        // 2. Set Text Rendering Mode 3 (Neither fill nor stroke)
        // Glyphs are indexed and selectable, but visually invisible
        contentStream.setRenderingMode(RenderingMode.NEITHER)
        contentStream.setFont(PDType1Font.HELVETICA, 10f)
        
        // 3. Draw OCR text glyphs at exact bounding box coordinates
        for (word in ocrWords) {
            val (pdfX, pdfY, fontSize) = mapToPdfCoordinates(word.boundingBox, PDRectangle.A4)
            contentStream.beginText()
            contentStream.setFont(PDType1Font.HELVETICA, fontSize)
            contentStream.newLineAtOffset(pdfX, pdfY)
            contentStream.showText(word.sanitizedText)
            contentStream.endText()
        }
    }
}
```

---

## 6. Owned UI Design System Architecture

Abhilekh implements an **Owned Component Architecture** in Compose and SwiftUI:
* **Tokenized Design System**: Centralized color palette, typographic hierarchy, elevation, corner radii, and haptic profiles.
* **High-Performance Canvas Crop Overlay**: Magnetic quadrant snap and magnifier loupe built directly on Jetpack Compose Canvas / SwiftUI Path.
* **Amber Warning & Redaction Brush**: Custom HUD modal with in-memory brush coordinates for fail-loud user review.
* **Unified Island / Progress Capsule**: Dynamic updates reflecting OCR and PDF assembly progress in real time.
