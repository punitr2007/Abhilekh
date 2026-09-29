# Phased Implementation & Execution Roadmap — Hardened v4
## Project: Abhilekh (अभिलेख) — Sovereign Multiplatform Document Scanner

---

## 1. 5-Sprint Roadmap Overview & Milestones

```
                                  5-SPRINT PRODUCTION ROADMAP (10 WEEKS)
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ SPRINT 1 (Weeks 1–2): Unified Core Algorithms & Ephemeral Storage Architecture                          │
│ • Setup unified C++17 library (`libabhilekh_cv`) with JNI & Kotlin/Native cinterop bindings             │
│ • Implement & benchmark Aadhaar Multi-Signal Engine + Verhoeff dihedral group D5 validator              │
│ • Build `EphemeralSessionStorage` AES-256-GCM crypto-shredder                                          │
│ • Setup synthetic Aadhaar card generation test bench in CI                                              │
├─────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ SPRINT 2 (Weeks 3–4): Native Camera Capture, Filters & Dual Thermal Governor                            │
│ • Integrate Android Play Services ML Kit Document Scanner & iOS VisionKit HUD                           │
│ • Implement Morphological Illumination Division & Adaptive Sauvola Binarization in C++                  │
│ • Implement Android API 30+ / API 24–29 fallback Thermal Governor + iOS ProcessInfo Monitor             │
│ • 30-Page batch processing stress test with thermal headroom monitoring                                 │
├─────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ SPRINT 3 (Weeks 5–6): Searchable PDF/A-1b Engine & Island Live Progress                                 │
│ • Implement Apache PDFBox Mode 3 (`3 Tr`) Dual-Layer Searchable PDF Assembler                           │
│ • Configure sRGB ICC profile output intents & XMP metadata packets for strict PDF/A-1b                  │
│ • Setup automated veraPDF CLI validation workflow in GitHub Actions CI                                  │
│ • Implement Hand-Rolled iOS Swift ActivityKit Dynamic Island + Android 16 Live Updates Notification     │
├─────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ SPRINT 4 (Weeks 7–8): Owned Design System & Interactive UI Polish                                       │
│ • Implement Compose & SwiftUI Owned Design System (`AbhilekhTokens`, `CropCanvas`, `FilterCarousel`)     │
│ • Build Aadhaar Amber Alert Review Modal & Manual Redaction Brush                                       │
│ • Implement WhatsApp 1-Tap Direct Share intent & Target Size Compression Slider (<500KB Govt Mode)      │
│ • Integrate Room Database with FTS5 Full-Text Search and Folder Hierarchy                               │
├─────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ SPRINT 5 (Weeks 9–10): Hardware Matrix Validation, DPDP Compliance & Release                            │
│ • Physical device testing across 4 hardware tiers (Budget 3GB RAM, Mid-range Dimensity, Flagship, iOS)  │
│ • Automated veraPDF conformance audit on 100 sample documents                                           │
│ • Zero-data-exfiltration network packet inspection & memory leak verification                           │
│ • Signed APK/AAB build generation and Play Store / TestFlight staging                                   │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Sprint Deliverables Breakdown

### Sprint 1: Unified Core & Privacy Baseline (Weeks 1–2)
- **Deliverables**:
  - Unified C++17 library build (`CMakeLists.txt` for Android NDK + `.def` definition for iOS `cinterop`).
  - Pure Kotlin Verhoeff Dihedral Group $D_5$ algorithm + Contextual Indic Keyword evaluation.
  - Ephemeral AES-256-GCM session storage with memory key wipe (Crypto-Shredder).
  - Synthetic Aadhaar generation test harness (`AadhaarMaskingBenchmarkTest.kt`).
- **Exit Criteria**: 100% pass on synthetic Aadhaar test suite with zero digit leaks.

### Sprint 2: Capture & Thermal Throttle Pipeline (Weeks 3–4)
- **Deliverables**:
  - Android ML Kit Document Scanner & iOS VisionKit HUD capture intents.
  - Native C++ Morphological Illumination Division filter.
  - Multi-tier Thermal Monitor (`PowerManager.getThermalHeadroom` on API 30+, Battery Temp on API 24–29, `ProcessInfo.thermalState` on iOS).
- **Exit Criteria**: 30-page scanning burst runs on low-end test device without thermal kill or OOM.

### Sprint 3: Archival PDF Engine & Dynamic Island (Weeks 5–6)
- **Deliverables**:
  - Apache PDFBox Dual-Layer Searchable PDF engine using Text Rendering Mode 3 (`3 Tr`).
  - Strict PDF/A-1b metadata dictionary, sRGB ICC profile, and embedded font subsets.
  - Automated `veraPDF` validation workflow integrated in CI.
  - iOS ActivityKit Dynamic Island WidgetExtension & Android Live Updates Progress Notification.
- **Exit Criteria**: `veraPDF --flavour 1b` reports 0 violations on all sample outputs.

### Sprint 4: Owned Design System & User Interface (Weeks 7–8)
- **Deliverables**:
  - Owned design system token architecture (`AbhilekhTokens.kt` / `AbhilekhTokens.swift`).
  - High-precision custom Canvas crop overlay with magnetic corner snapping and magnifying loupe.
  - Fail-loud Aadhaar Amber Alert modal and Manual Redaction Brush.
  - SQLite/Room DB with FTS5 search index and 1-Tap WhatsApp Direct Share button.
- **Exit Criteria**: Complete end-to-end capture, crop, filter, mask, and export flow running at 60fps.

### Sprint 5: Multi-Device Hardening & Production Release (Weeks 9–10)
- **Deliverables**:
  - Matrix validation on physical hardware (Vivo/iQOO, Xiaomi, Samsung, iPhone).
  - Zero-data-exfiltration network isolation verification.
  - Signed release build packaging and store listings.
- **Exit Criteria**: Play Store and TestFlight ready builds with $\ge 99.5\%$ crash-free metric.
