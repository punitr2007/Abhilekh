# Memory & Architecture Decision Records (ADRs)
## Project: Abhilekh (अभिलेख) — Sovereign Indian Multiplatform Document Scanner

---

## 1. Project Identity & Positioning

- **Project Code**: `Abhilekh` (अभिलेख)
- **Repository Location**: `/home/punit/Local_Codebase/Projects/Ideas/Abhilekh`
- **Core Positioning**: A privacy-first, sovereign cross-platform document scanner built for Indian citizens, students, MSMEs, CAs, and lawyers with 100% on-device processing, Aadhaar auto-masking, GST bill capture, and direct WhatsApp sharing.

---

## 2. Architecture Decision Records (ADRs)

### ADR-001: Platform Architecture — Kotlin Multiplatform (KMP) with Native UI & Shared C++ Core
- **Decision**: Kotlin Multiplatform (`commonMain`) for business logic + Native Jetpack Compose (Android) & SwiftUI (iOS) + Shared C++17 Core (`libabhilekh_cv`).
- **Rationale**: Eliminates duplicate OpenCV filter implementations and prevents math/visual drift. Single source of truth for Verhoeff Aadhaar verification, batch state machines, and thermal throttling.
- **Status**: Approved & Hardened.

### ADR-002: Computer Vision Pipeline & Illumination Division
- **Decision**: Unified C++17 library compiled for Android (via JNI) and iOS (via Kotlin/Native `cinterop`).
- **Filter**: Morphological Background Estimation & Illumination Division (`cv::dilate` $\rightarrow$ `cv::medianBlur` $\rightarrow$ `cv::divide` $\rightarrow$ luminance normalization).
- **Status**: Approved.

### ADR-003: Dual-Platform Thermal Governor with API 30+ Fallback
- **Decision**:
  - Android 11+ (API 30+): `PowerManager.getThermalHeadroom(30)`.
  - Android 7.0–10 (API 24–29): `Intent.ACTION_BATTERY_CHANGED` battery temperature monitoring (>42°C).
  - iOS: `ProcessInfo.processInfo.thermalState` (`.serious` / `.critical`).
  - Action: Downscale raster inputs to 200 DPI and yield 50ms per page during elevated temperatures.
- **Status**: Approved.

### ADR-004: Ephemeral Per-Session AES-256-GCM Crypto-Shredding & Deterministic Memory Masking
- **Decision**: Replace non-binding `System.gc()` with in-place pixel buffer zeroing (`memset(0)`). Temporary cache files are encrypted with an ephemeral in-memory AES-256 key wiped upon PDF generation (guaranteeing flash-safe right-to-erasure).
- **Status**: Approved.

### ADR-005: Archival PDF/A-1b Engine & Automated veraPDF CI Verification
- **Decision**: Dual-layer searchable PDF using Apache PDFBox with Text Rendering Mode 3 (`3 Tr`). Output includes embedded sRGB ICC profile, subsetted fonts, and XMP metadata verified in CI via **veraPDF CLI**.
- **Status**: Approved.

### ADR-006: Synthetic Aadhaar Card Benchmark Test Bed
- **Decision**: Programmatic generation of 1,000 synthetic Aadhaar cards with valid Verhoeff checksums, Devanagari text, and synthetic lighting/noise to verify $>99.5\%$ masking precision without exfiltrating real citizen data.
- **Status**: Approved.

### ADR-007: Owned Design System Architecture (Compose + SwiftUI)
- **Decision**: Fully owned UI design tokens (`AbhilekhTokens`) and components (Canvas Crop Overlay, Filter Carousel, Amber Aadhaar HUD) instead of opaque third-party UI packages.
- **Status**: Approved.

---

## 3. Technical Invariants & Core Rules

1. **Zero Unconsented Data Transmission**: No scan images, thumbnails, or OCR text may ever be uploaded without explicit user confirmation.
2. **Text Rendering Mode 3 Invariant**: Searchable text layers must use PDF Rendering Mode 3 (`3 Tr`) to ensure universal PDF reader searchability without violating PDF/A-1b rules.
3. **100% Offline Daily Driver**: The full capture, crop, filter, Aadhaar mask, OCR, and PDF export loop must function without internet connectivity.
4. **Deterministic Crypto-Shredding**: Session temp cache keys must be zeroed immediately upon PDF compilation or session cancellation.
