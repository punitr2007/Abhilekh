# Product Requirements Document (PRD) — Lean & Pragmatic v3
## Project: Abhilekh (अभिलेख) — Sovereign Indian Document Scanner

---

## 1. Executive Summary & Market Thesis

### 1.1 Product Vision
**Abhilekh (अभिलेख)** is a fast, offline-first, sovereign document scanner for Android tailored for the Indian ecosystem. It fills the vacuum left by the CamScanner ban and Adobe Scan’s expensive ($ pegged), enterprise-heavy model.

Abhilekh prioritizes what matters most to Indian users: **100% on-device privacy, global Aadhaar auto-masking across all scans, GST invoice handling, Hindi + English OCR, 1-tap WhatsApp sharing, and adaptive compression for government exam portals (<500KB)**.

### 1.2 The Pragmatic Strategy
- **Turnkey Off-the-Shelf**: Google Play Services **ML Kit Document Scanner API** (live edge detection, perspective warping, auto-shutter) + **ML Kit Text Recognition v2** (bundled English + Devanagari/Hindi).
- **Custom Built Moat**: Indian photocopy paper illumination division filter, global Aadhaar auto-masking with multi-signal heuristics (regex + Verhoeff + keywords) and fail-loud manual brush, Apache PDFBox-Android searchable PDF engine with Text Rendering Mode 3 (`3 Tr`), and WhatsApp direct distribution.
- **Phased Scope Alignment**: Focus 8-week MVP on high-value core utilities (Hindi OCR, Aadhaar privacy, PDFBox assembler, WhatsApp share); defer full Hindi UI chrome, DigiLocker OAuth, and UPI Autopay to Phase 2.

---

## 2. Target User Personas

| Persona | Environment & Device | Core Pain Points | High-Value Abhilekh Features |
|---|---|---|---|
| **Aryan (Student / Aspirant)** | Kota / Prayagraj. Budget phone (Realme/Redmi, 3GB RAM), prepaid data. | Needs to scan 20-30 handwritten pages of mock tests/notes. Portals (UPSC, SSC, NTA) enforce strict `<500KB` file limits. | High-Speed burst capture, Illumination Division filter for yellowed paper, <500KB portal compressor, WhatsApp share. |
| **Ramesh (Kirana / MSME Trader)** | Surat wholesale textile market. Single tube light, crumpled bills. | Invoices have tiny print, faint thermal paper ink, complex GST tables. Manual typing into Tally causes errors. | GST Bill capture tab, thermal paper contrast booster, tabular scan enhancement. |
| **Advocate Sharma (Lawyer)** | District Court. Scans green ledger sheets, stamp papers, affidavits. | Blue/black stamps wash out; needs clean contrast, multi-page indexing, and official attestation stamps. | Color-preserve binarization, "Combine Files" PDF merger, "Self-Attested" e-stamp overlay, PDFBox engine. |
| **Priya (Chartered Accountant)** | Bengaluru. Manages KYC, tax filings, client records. | Regulated by UIDAI & IT Act; manual redacting of first 8 digits of Aadhaar is tedious. | Global Aadhaar auto-detection + 8-digit masking, 2-in-1 ID card template, Secure Share (AES-256 password). |

---

## 3. Core Functional Requirements

```
                                  ABHILEKH PRODUCT ARCHITECTURE
+───────────────────────────────────────────────────────────────────────────────────────────────+
|                                    1. CAPTURE & INPUT MODES                                   |
|  - ML Kit Live Document Scanner HUD     - High-Speed Rapid Burst Mode   - Create from Photos  |
|  - 7-Mode Horizontal Tab Picker:                                                              |
|    [ Whiteboard │ Book │ Document │ Business Card │ ID Card (2-in-1) │ GST Bill │ Aadhaar/PAN ]  |
+───────────────────────────────────────────────────────────────────────────────────────────────+
                                               │
                                               ▼
+───────────────────────────────────────────────────────────────────────────────────────────────+
|                                  2. IMAGE ENHANCEMENT PIPELINE                                |
|  - Morphological Background Estimation & Illumination Division ("Magic White Paper" Filter)   |
|  - Crisp B&W High-Contrast       - Grayscale / Color Boost      - Thermal Receipt Recovery    |
+───────────────────────────────────────────────────────────────────────────────────────────────+
                                               │
                                               ▼
+───────────────────────────────────────────────────────────────────────────────────────────────+
|                                3. INDIA-FIRST TRUST & OCR LAYER                               |
|  - Global Aadhaar 8-Digit Masker (Permissive Regex + Verhoeff + Context Keywords on ALL pages)|
|  - Fail-Loud Policy: Amber Warning Banner + Manual Redaction Brush Fallback                   |
|  - Google ML Kit Text Recognition v2 (Bundled English + Hindi / Devanagari on-device)        |
|  - Dual-Layer Searchable PDF Generator via Apache PDFBox (Text Rendering Mode 3 / '3 Tr')     |
+───────────────────────────────────────────────────────────────────────────────────────────────+
                                               │
                                               ▼
+───────────────────────────────────────────────────────────────────────────────────────────────+
|                                4. EXPORT, COMPRESSION & SHARING                               |
|  - Standard Searchable PDF Export via Apache PDFBox (PDF/A & AES-256 Password ready)          |
|  - Adaptive Target-Size Compression (Iterative target toward <500KB with legibility floor)    |
|  - Library Tools: Search, Folders, Multi-Select Bulk Actions, Sort, and "Combine Files" (Merge)|
|  - 1-Tap Direct WhatsApp Share   - DPDP Right-to-Erasure ("Erase My Cloud Data")              |
+───────────────────────────────────────────────────────────────────────────────────────────────+
```

### 3.1 Camera Capture & Input Engine
- **F1.1 Live Viewfinder & Auto-Cropping**: Powered by Google ML Kit Document Scanner API for instant edge detection, perspective warping, and auto-shutter stabilization.
- **F1.2 7-Mode Horizontal Tab Picker**:
  1. *Document*: Standard single/multi-page capture.
  2. *Book*: Double-page split guide.
  3. *Whiteboard*: Reflection and glare removal.
  4. *Business Card*: Standard ratio auto-crop.
  5. *ID Card (2-in-1)*: Front + Back capture combined onto a single A4 page.
  6. *GST Bill*: Dense tabular contrast optimization.
  7. *Aadhaar/PAN*: Auto aspect ratio crop guide.
- **F1.3 High-Speed Scan (Rapid Burst)**: Dedicated rapid-fire batch capture mode bypassing the per-page crop confirmation step for rapid 20+ page book/notes scanning.
- **F1.4 Create from Photos (Gallery Ingestion)**: Ingest existing camera-roll stills, run automated edge detection, enhancement, and assemble into a PDF.

### 3.2 Enhancement & Filter Pipeline
- **F2.1 Illumination Division ("Magic White Paper" Filter)**: Morphological background estimation ($\text{dilate} \rightarrow \text{medianBlur}$) followed by illumination division ($\text{image} / \text{background} \times 255$) to eliminate yellowing, shadows, and uneven tube-light falloff while preserving ink and stamp colors.
- **F2.2 Crisp Black & White**: Adaptive binarization for legal documents and photocopies.
- **F2.3 Color Boost**: Dynamic range stretching preserving official blue/red stamps and signatures.
- **F2.4 Thermal Paper Recovery**: High-contrast curve tuning for faint POS slips and carbon copies.

### 3.3 Global Aadhaar Masking & Trust Engine
- **F3.1 Global Multi-Signal Detection**: Runs across **every page's OCR output** (not gated to ID tab):
  - Permissive Regex: `\b[2-9]{1}[0-9]{3}[\s-]?[0-9]{4}[\s-]?[0-9]{4}\b`
  - Verhoeff checksum algorithm validation.
  - Contextual keywords: "आधार", "Aadhaar", "UID", "DOB", "Male", "Female", "Government of India".
- **F3.2 8-Digit Auto-Masking**: Permanently blacks out the first 8 digits in the raster pixel buffer and replaces them with `XXXX XXXX ` in the PDF text layer.
- **F3.3 Fail-Loud Policy & Manual Redaction Brush**: If an Aadhaar card is detected with low confidence or checksum failure, the UI displays an **Amber Warning Banner** (*"Please verify Aadhaar masking"*) and routes to the **Manual Redaction Brush**.

### 3.4 On-Device OCR & Apache PDFBox Engine
- **F4.1 Baseline OCR**: On-device Google ML Kit Text Recognition v2 supporting **English + Devanagari (Hindi, Marathi)**.
- **F4.2 Dual-Layer Searchable PDF (`3 Tr`)**: Powered by Apache PDFBox-Android (`com.tom-roush:pdfbox-android`). Text glyphs are placed in the content stream with `RenderingMode.NEITHER` (`3 Tr`), making them searchable without violating PDF/A-1b rules or relying on alpha transparency.
- **F4.3 Adaptive Target-Size Compression**: Iteratively compresses images toward $<500\text{KB}$ (Govt Exam mode) and $<1\text{MB}$ (WhatsApp mode) while maintaining a minimum legibility floor.
- **F4.4 Library Management & "Combine Files"**: Full-text OCR search, folders, multi-select bulk actions (delete, move), sort by date/name, and multi-PDF merging.

---

## 4. Phased Feature Scope (Lean 8-Week Roadmap)

```
+───────────────────────────────────────────────────────────────────────────────────────────────+
|                                       PHASED SCOPE MATRIX                                     |
+───────────────────────────────────────────────────────────────┬───────────────────────────────+
| PHASE 1: LEAN MVP (Weeks 1–8) — 1–2 Developers                | PHASE 2+: POST-MVP & DEPTH    |
+───────────────────────────────────────────────────────────────┼───────────────────────────────+
| [x] ML Kit Document Scanner (Live Quad, Auto-Crop)            | [ ] Full Hindi/Indic UI Chrome|
| [x] 7-Mode Capture Tab Bar (incl. GST Bill & Aadhaar/PAN)     | [ ] DigiLocker Partner API    |
| [x] High-Speed Burst Scan + "Create from Photos"              | [ ] UPI Autopay Monetization  |
| [x] Illumination Division ("Magic White Paper") Filter        | [ ] 9 Additional Indic OCRs   |
| [x] Global Aadhaar Multi-Signal Auto-Masking (All Pages)      | [ ] CA / SME Multi-Seat Web   |
| [x] Fail-Loud Amber Prompt + Manual Redaction Brush           | [ ] Cloud Sync (AWS Mumbai)   |
| [x] English + Hindi On-Device OCR                             | [ ] Advanced Tally/CSV Export |
| [x] Apache PDFBox Searchable PDF Engine (Rendering Mode 3)    |                               |
| [x] Adaptive Target-Size Compression (<500KB Mode)            |                               |
| [x] Library: Search, Folders, Multi-Select, "Combine Files"   |                               |
| [x] 1-Tap Direct WhatsApp Share                               |                               |
| [x] DPDP Right to Erasure ("Erase All Data" Settings flow)    |                               |
+───────────────────────────────────────────────────────────────┴───────────────────────────────+
```
