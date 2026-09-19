# UI/UX Design System & Product Experience
## Project: Abhilekh (अभिलेख) — Document Scanner (v3)

---

## 1. Design Philosophy: "Speed, Clarity, Frictionless Trust"

1. **2-Tap Document Velocity**: From opening the app to sending a compressed PDF to WhatsApp takes $\le 2$ taps.
2. **Clean English Chrome at MVP**: App UI chrome uses clean, simple English (matching PhonePe/WhatsApp usage in India), while content-level OCR supports English and Hindi out of the box.
3. **Global Fail-Loud Privacy Feedback**: Aadhaar detection runs globally across all scan modes. If an Aadhaar card is captured, the app visibly confirms redaction status with a green badge; if confidence is low, an amber banner prompts manual verification.
4. **Sunlight-Readable Contrast**: High-contrast slate and crisp paper palettes with minimum $7:1$ contrast ratio.

---

## 2. Design Tokens & Typography

### 2.1 Color System
```
┌────────────────────────────────────────────────────────────────────────┐
│                          COLOR SYSTEM MATRIX                           │
├──────────────────────┬──────────────────────┬──────────────────────────┤
│ Token                │ Hex Code             │ Purpose / Semantic Role  │
├──────────────────────┼──────────────────────┼──────────────────────────┤
│ `brand-primary`      │ `#0F172A` (Slate 900)│ Core header, deep canvas │
│ `brand-accent`       │ `#2563EB` (Royal)    │ Primary actions & tabs   │
│ `trust-emerald`      │ `#059669` (Emerald)  │ Masked Aadhaar / Private │
│ `action-whatsapp`    │ `#25D366` (WA Green) │ 1-Tap Direct WhatsApp CTA│
│ `warning-amber`      │ `#D97706` (Amber)    │ Aadhaar verify alert     │
│ `surface-canvas`     │ `#F8FAFC` (Slate 50) │ App background canvas    │
│ `surface-card`       │ `#FFFFFF`            │ Elevated document cards  │
│ `text-primary`       │ `#0F172A`            │ Main document titles     │
│ `text-muted`         │ `#64748B`            │ Subtitles, page counts   │
└──────────────────────┴──────────────────────┴──────────────────────────┘
```

---

## 3. Core Screen Flows & Wireframes

### 3.1 Screen 1: Home & Document Library
```
┌─────────────────────────────────────────────────────────┐
│  Abhilekh               [ 🔀 Combine ] [ 🔍 Search ] [ ⚙️ ]│
│                                                         │
│  [ 📁 All Scans ]  [ 🪪 ID Cards ]  [ 🧾 GST Bills ] [+]│
├─────────────────────────────────────────────────────────┤
│  [ ☑️ Multi-Select ]              [ ⇅ Sort: Date / Name ]│
│                                                         │
│  ┌───────────┐  Aadhaar_Card_Masked.pdf                 │
│  │ [🖼️ Thumb] │  2 pages • 420 KB • 10 mins ago          │
│  │ [🛡️ MASKED]│  [🟢 1-Tap WhatsApp]  [📤 Share] [ ⋮ ]   │
│  └───────────┘                                          │
│                                                         │
│  ┌───────────┐  GST_Tax_Invoice_Surat.pdf               │
│  │ [🖼️ Thumb] │  1 page • 180 KB • Yesterday            │
│  │           │  [🟢 1-Tap WhatsApp]  [📤 Share] [ ⋮ ]   │
│  └───────────┘                                          │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  [ 🖼️ Create from Photos ]          ( 📸 [SCAN] )        │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Screen 2: Capture Viewfinder & 7-Mode Horizontal Tab Bar
```
┌─────────────────────────────────────────────────────────┐
│  [⚡ Flash: Auto]    [ ⚡⚡ High-Speed: OFF ]   [ ❌ Close ]│
│                                                         │
│         ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐               │
│           Auto-Edge Detection HUD                       │
│         │   [ Animated Green Quad ]     │               │
│             "Hold steady... capturing"                  │
│         │                               │               │
│         └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘               │
│                                                         │
│  [ Auto / Manual ] ── [ Single / Multi-Page (3) ]       │
│                                                         │
│  Whiteboard │ Book │ Doc │ Card │ ID (2-in-1) │ GST │ ⭐ Aadhaar│
│                                                         │
│   [ 🖼️ Gallery ]          ( 🔘 [SHUTTER] )     [ 🔄 Flip ]│
└─────────────────────────────────────────────────────────┘
```

### 3.3 Screen 3: Enhancement Filters & Global Aadhaar Masking HUD
```
┌─────────────────────────────────────────────────────────┐
│  Page 1 of 2                         [ 💾 Save / Export ]│
├─────────────────────────────────────────────────────────┤
│  ⚠️ AMBER BANNER (Global Fail-Loud Warning):            │
│  "Aadhaar detected on this page. 8 digits auto-masked.  │
│   Please verify before sharing."                        │
│                                                         │
│         ┌─────────────────────────────────────┐         │
│         │  GOVERNMENT OF INDIA                │         │
│         │  Name: Ramesh Kumar                 │         │
│         │  UID: [████ ████] 4821 🛡️ MASKED    │         │
│         └─────────────────────────────────────┘         │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  TOOLS:                                                 │
│  [ 🛡️ Mask Aadhaar: ON ]  [ 🖌️ Manual Redact Brush ]    │
├─────────────────────────────────────────────────────────┤
│  FILTERS:                                               │
│  ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐  │
│  │ Orig  │  │ Magic │  │ B & W │  │ Color │  │ Grays │  │
│  │       │  │ White │  │ Crisp │  │ Boost │  │ cale  │  │
│  └───────┘  └───────┘  └───────┘  └───────┘  └───────┘  │
└─────────────────────────────────────────────────────────┘
```

### 3.4 Screen 4: Export Modal with Adaptive Compression
```
┌─────────────────────────────────────────────────────────┐
│  Export Document                           [ ❌ Cancel ] │
├─────────────────────────────────────────────────────────┤
│  Document Title: [ SSC_CGL_Application_Form_Oct26 ]     │
│  Pages: 4 Pages • Size: 380 KB                          │
│                                                         │
│  TARGET SIZE & QUALITY:                                 │
│  [ 🟢 Govt Portal (<500KB) ]  [ 🔵 WhatsApp ]  [ 🟣 HD ] │
│  ───●───────────────────────────────────────────────    │
│  Target: ~350 KB (High-clarity compressed WebP/JPEG)    │
│                                                         │
│  [x] Embed Searchable OCR Text Layer (English + Hindi)  │
│                                                         │
│  PRIMARY EXPORT ACTION:                                 │
│  ┌───────────────────────────────────────────────────┐  │
│  │ 🟢  DIRECT SHARE TO WHATSAPP                      │  │
│  └───────────────────────────────────────────────────┘  │
│  [ 📥 Save to Device ]           [ 🔀 Combine with PDF ]│
└─────────────────────────────────────────────────────────┘
```
