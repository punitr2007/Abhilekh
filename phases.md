# Phased Implementation & Execution Roadmap — Lean v3
## Project: Abhilekh (अभिलेख) — Mobile Document Scanner

---

## 1. Roadmap Overview & Timeline

```
+───────────────────────────────────────────────────────────────────────────────────────────────+
|                                       EXECUTION PHASES                                        |
+───────────────────────────────────┬───────────────────────────────────┬───────────────────────+
| Phase                             | Focus & Key Deliverables          | Timeline & Team       |
+───────────────────────────────────┼───────────────────────────────────┼───────────────────────+
| **Phase 1: Lean MVP**             | ML Kit Scanner, 7 Tabs, PDFBox    | Weeks 1–8 (1–2 devs)  |
|                                   | Engine ('3 Tr'), Global Aadhaar   |                       |
|                                   | Masking, Illumination Filter,     |                       |
|                                   | Hindi OCR, WhatsApp Direct Share  |                       |
+───────────────────────────────────┼───────────────────────────────────┼───────────────────────+
| **Phase 2: Indic Depth & Growth** | Full Hindi/Indic UI Chrome,       | Weeks 9–14 (2 devs)   |
|                                   | 4 Regional OCRs, UPI Autopay,     |                       |
|                                   | India Cloud Backup (Mumbai)       |                       |
+───────────────────────────────────┼───────────────────────────────────┼───────────────────────+
| **Phase 3: Public Launch**        | Play Store Release, ASO, Seeding  | Weeks 15–18 (Team)    |
+───────────────────────────────────┼───────────────────────────────────┼───────────────────────+
| **Phase 4: Enterprise & Scale**   | DigiLocker API, CA Web Portal,    | Weeks 19–26+ (Scale)  |
|                                   | Tabular GST Export to Tally/CSV   |                       |
+───────────────────────────────────┴───────────────────────────────────┴───────────────────────+
```

---

## 2. Detailed Phase Breakdown

### Phase 1: Lean MVP (Weeks 1–8) — 1–2 Developers
- **Team**: 1-2 Android Developers (Kotlin + Jetpack Compose + C++ JNI).
- **Core Strategy**: Leverage Google ML Kit Document Scanner API for camera/edge detection; Apache PDFBox-Android for compliant searchable PDF generation; thread India-specific features (Aadhaar masking, GST tab, Illumination filter, Hindi OCR, WhatsApp share) throughout the build.
- **Tightened MVP Build Order**:
  1. **Week 1–2: Camera Capture & 7-Mode Tab Bar**:
     - Integrate Google Play Services ML Kit Document Scanner API.
     - Implement 7-mode capture picker (Whiteboard, Book, Document, Business Card, ID Card, **GST Bill**, **Aadhaar/PAN**).
     - Implement "High-Speed Scan" rapid burst mode.
  2. **Week 3: Gallery Ingestion & "Combine Files"**:
     - "Create from Photos" flow (importing gallery stills into the scanning pipeline).
     - Library "Combine Files" tool (merging multiple scanned PDFs via PDFBox).
  3. **Week 4: Enhancement Filters & Global Aadhaar Auto-Masking**:
     - Implement C++ Morphological Background Division ("Magic White Paper") filter.
     - Implement Global Aadhaar regex + Verhoeff + keyword heuristic detection on all OCR outputs.
     - Implement the fail-loud Manual Redaction Brush fallback.
  4. **Week 5–6: English + Hindi OCR & Apache PDFBox Searchable PDF (`3 Tr`)**:
     - Integrate ML Kit Text Recognition v2 for English + Devanagari.
     - Assemble dual-layer searchable PDF using Apache PDFBox-Android with `RenderingMode.NEITHER` (`3 Tr`).
     - Implement Adaptive Target-Size Compression (<500KB Govt Exam Portal mode).
  5. **Week 7: Library & WhatsApp Direct Share**:
     - Room DB with FTS5 search, folders, multi-select bulk actions, and sort.
     - 1-Tap direct WhatsApp share intent.
     - "Erase All Data" DPDP Settings flow.
  6. **Week 8: Internal Testing & Closed Alpha**:
     - Closed alpha on Google Play Internal Track ($\ge 99.5\%$ crash-free rate).
- **Exit Criteria**: Standalone, offline document scanner APK ready for closed beta testers.

---

### Phase 2: Indic Depth & Monetization (Weeks 9–14)
- **Team**: 2 Android / Cloud Developers.
- **Key Deliverables**:
  1. **Full Vernacular UI Chrome**: Complete Hindi and Indic language UI translation.
  2. **Regional Indic OCR Expansion**: Dynamic on-demand download of dedicated OCR language models for Tamil, Telugu, Bengali, and Gujarati.
  3. **UPI Autopay Billing**: Razorpay / Cashfree recurring subscription integration (₹49/month and ₹399/year Pro tiers).
  4. **India-Region Cloud Backup**: Optional client-side encrypted (AES-256) backup to AWS Mumbai (`ap-south-1`).
- **Exit Criteria**: 1,000 active beta testers; verified UPI subscription conversion in production.
