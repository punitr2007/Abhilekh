# Memory & Architecture Decision Records (ADRs)
## Project: Abhilekh (अभिलेख) — Sovereign Indian Document Scanner

---

## 1. Project Identity & Positioning

- **Project Code**: `Abhilekh` (अभिलेख)
- **Repository Location**: `/home/punit/Local_Codebase/Projects/Ideas/Abhilekh`
- **Core Positioning**: A privacy-first, ultra-fast sovereign document scanner for Android built to serve Indian students, MSMEs, CAs, and lawyers with 100% on-device processing, Aadhaar auto-masking, GST bill capture, and direct WhatsApp sharing.

---

## 2. Architecture Decision Records (ADRs)

### ADR-001: Mobile Framework Selection
- **Decision**: **Pure Kotlin + Jetpack Compose** for Android-first MVP.
- **Rationale**: Eliminates JSI/TurboModule bridging overhead. Enables direct zero-copy integration with Android CameraX, Google ML Kit Document Scanner API, and native OpenCV JNI. Aligns with proven Compose experience (BitChord) and the MakeACopy reference architecture.
- **Status**: Approved & Finalized.

### ADR-002: Computer Vision & Scanner Pipeline
- **Decision**: **Google Play Services ML Kit Document Scanner API** for base capture, live quad detection, and perspective warping + **Custom OpenCV C++ Illumination Division Filter** for paper enhancement.
- **Rationale**: ML Kit Document Scanner is maintained by Google (shipped in Google Drive/Pixel Camera), works fully on-device, and requires zero maintenance. Custom OpenCV logic is reserved for Indian paper enhancement where off-the-shelf tools fail.
- **Status**: Approved.

### ADR-003: On-Device OCR Strategy
- **Decision**: **Google ML Kit Text Recognition v2** (bundled English + Devanagari/Hindi).
- **Rationale**: ML Kit v2 runs 100% on-device at zero network cost, covers English and Hindi/Devanagari with high accuracy, and adds negligible APK overhead. Dedicated regional Indic models (Tamil, Telugu, Bengali) are deferred to Phase 2.
- **Status**: Approved.

### ADR-004: Global Aadhaar Masking & Multi-Signal Heuristics
- **Decision**: Aadhaar detection runs globally across **every page's OCR output regardless of selected capture mode**.
- **Multi-Signal Heuristics**:
  - Permissive regex: `\b[2-9]{1}[0-9]{3}[\s-]?[0-9]{4}[\s-]?[0-9]{4}\b`
  - Verhoeff checksum validation.
  - Contextual keyword verification ("आधार", "Aadhaar", "UID", "DOB", "Male", "Female", "Government of India").
- **Fail-Loud Invariant**: If confidence is borderline or checksum fails on a suspected ID, the UI displays an amber warning banner and routes the user to the **Manual Redaction Brush**.
- **Status**: Approved.

### ADR-005: PDF Engine & Invisible Text Layer (PDFBox-Android)
- **Decision**: **Apache PDFBox for Android (`com.tom-roush:pdfbox-android`)** using **PDF Text Rendering Mode 3 (`3 Tr` / `RenderingMode.NEITHER`)**.
- **Rationale**:
  - Stock `android.graphics.pdf.PdfDocument` cannot do encryption, password protection, or PDF/A metadata.
  - iText is AGPL-licensed (risk of license contagion in commercial distribution).
  - Commercial SDKs (PSPDFKit/Nutrient, Foxit) are costly for a bootstrapped MVP.
  - PDFBox is Apache 2.0 licensed and provides direct content-stream control.
  - Text Rendering Mode 3 places glyphs in the PDF text stream for selection/search without visual rendering or alpha compositing (which violates PDF/A-1b).
- **Status**: Approved.

### ADR-006: DPDP Act 2023 / 2025 Phased Regulatory Alignment
- **Decision**: On-device processing by default with a user-facing **1-Tap Data Erasure Flow** in Settings.
- **Context**: DPDP rules were notified on 13 Nov 2025; substantive obligations take effect around May 2027. We establish the privacy architecture today without over-engineering moving regulatory targets.
- **Status**: Approved.

---

## 3. Algorithm Correction & Technical Notes

| Item | Previous Approach | Corrected Specification |
|---|---|---|
| **Enhancement Filter** | "Difference of Gaussians (DoG) Filter" | **Morphological Background Estimation & Illumination Division** (`cv::dilate` $\rightarrow$ `cv::medianBlur` $\rightarrow$ `cv::divide` $\rightarrow$ luminance replacement in YCrCb). |
| **Searchable PDF Text** | Alpha transparent text (`Color.TRANSPARENT`) | **PDF Text Rendering Mode 3 (`3 Tr`)** via PDFBox content stream. |
| **PDF Library** | Stock Android `PdfDocument` | **Apache PDFBox for Android** (`com.tom-roush:pdfbox-android`). |
| **Aadhaar Trigger** | Gated only to "Aadhaar/PAN" tab | **Global OCR Pipeline Scan** on every page + Multi-Signal Heuristics. |
| **UI Chrome Language** | Full Hindi chrome at MVP | **English Chrome at MVP** with Hindi/English OCR; full Hindi UI chrome in Phase 2. |
| **Compression Target** | "Guaranteed <500KB" | **Adaptive Iterative Compression** with legibility floor and warning toast if page density exceeds threshold. |

---

## 4. Technical Invariants & Core Rules

1. **Zero Unconsented Data Transmission**: No scan images, thumbnails, or OCR text may ever be uploaded without explicit user confirmation.
2. **Text Rendering Mode 3 Invariant**: Searchable text layers must use PDF Rendering Mode 3 (`3 Tr`) to ensure universal PDF reader searchability without violating PDF/A-1b rules.
3. **100% Offline Daily Driver**: The full capture, crop, filter, Aadhaar mask, OCR, and PDF export loop must function without internet connectivity.
