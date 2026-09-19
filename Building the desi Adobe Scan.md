Product & engineering playbook — India document scanning

# Building the desi Adobe Scan

A practical build plan for a React Native document scanner tuned for Indian users — features, stack, differentiation, monetisation, roadmap, and the traps that sink most scanner apps.

10 sections MVP → launch roadmap React Native focus

[1. Opportunity](https://77363578-cc77-419b-9cf3-c9ad30d2f3f3.frame.claudeusercontent.com/_f/1789817493-cfe0/#opportunity) [2. Table stakes](https://77363578-cc77-419b-9cf3-c9ad30d2f3f3.frame.claudeusercontent.com/_f/1789817493-cfe0/#match) [3. Features](https://77363578-cc77-419b-9cf3-c9ad30d2f3f3.frame.claudeusercontent.com/_f/1789817493-cfe0/#features) [4. Tech stack](https://77363578-cc77-419b-9cf3-c9ad30d2f3f3.frame.claudeusercontent.com/_f/1789817493-cfe0/#stack) [5. Data portability](https://77363578-cc77-419b-9cf3-c9ad30d2f3f3.frame.claudeusercontent.com/_f/1789817493-cfe0/#portability) [6. Differentiation](https://77363578-cc77-419b-9cf3-c9ad30d2f3f3.frame.claudeusercontent.com/_f/1789817493-cfe0/#differentiation) [7. Monetisation](https://77363578-cc77-419b-9cf3-c9ad30d2f3f3.frame.claudeusercontent.com/_f/1789817493-cfe0/#monetisation) [8. Roadmap](https://77363578-cc77-419b-9cf3-c9ad30d2f3f3.frame.claudeusercontent.com/_f/1789817493-cfe0/#roadmap) [9. Challenges](https://77363578-cc77-419b-9cf3-c9ad30d2f3f3.frame.claudeusercontent.com/_f/1789817493-cfe0/#challenges) [10. Checklist](https://77363578-cc77-419b-9cf3-c9ad30d2f3f3.frame.claudeusercontent.com/_f/1789817493-cfe0/#checklist)

01

## The opportunity is real, and partly created by policy

CamScanner — once the default scanner for a huge share of Indian students, lawyers, and clerks — has been officially banned in India since June 2020, blocked along with dozens of other Chinese apps over data-security concerns. It's telling that government departments, courts, and police units have kept getting caught using it years later simply because no confidence-inspiring local alternative fully replaced it. That's a market gap, not a footnote.

Adobe Scan is the default replacement, but it's a global product with a global roadmap: no Aadhaar/PAN-aware capture guidance, no DigiLocker bridge, weak Indian-language OCR, and a subscription (Acrobat Pro) priced in dollars-equivalent terms that feels steep for a student or a kirana shop owner. That gap — **trustworthy, India-first, script-aware, cheap-or-free** — is what you're building into.

Framing to hold onto

You're not out-building Adobe's computer-vision team. You're out-localising them: language coverage, ID document handling, low-bandwidth/low-storage design, data residency, and pricing that matches Indian willingness-to-pay. Win on fit, not on raw feature count.

02

## Table stakes visible in Adobe Scan's own UI

Your screenshot is actually a useful spec sheet. Whatever else you build, these behaviours are what users already expect from "a scanner app" and their absence will read as a bug, not a missing feature:

#### Per-scan action sheet

Share, Save as Word, Save as JPEG, and a "More" overflow menu on every single scan — not buried in settings.

#### Background cloud sync

An "Uploading…" chip on a thumbnail the instant a scan is captured — sync feels automatic, not a manual export step.

#### Unified library + search

A single home feed of all scans with dates, a search icon in the header, and a persistent Home / Capture / Files bottom nav.

#### Folders

Lightweight organisation nudges ("Organize into folders") — users file scans by subject, client, or month.

Build these into your MVP, not your v2. They're the baseline that makes the app feel finished.

03

## Feature set: universal core + India-specific layer

### Universal core (must-have, MVP)

- **Auto edge detection & perspective correction** — real-time document boundary detection during capture, not just post-hoc cropping.
- **Multi-page capture into one PDF** — continuous-capture mode so a 10-page assignment doesn't mean 10 separate exports.
- **Filters** — auto (B/W, greyscale, colour, magic-clean-up) matched to document type: whiteboard, ID card, printed page, handwritten note.
- **OCR with searchable-PDF output** — text layer under the image, not a separate transcript.
- **Export formats** — PDF, JPEG, and DOCX (via OCR-to-editable-text), matching what your screenshot already shows users expect.
- **Compression control** — a visible small/medium/high size toggle before export; this matters enormously on Indian data plans and low-storage phones (see Challenges).
- **E-signature and annotation** — draw/type a signature, add text boxes, highlight — table stakes for forms and contracts.

### India-specific layer (your actual moat)

- **Regional-language OCR** — Hindi, Bengali, Tamil, Telugu, Marathi, Gujarati, Kannada, Malayalam, Punjabi, Odia at minimum; prioritise by your first target city/state rather than trying all 22 scheduled languages on day one.
- **ID-document capture modes** — dedicated Aadhaar and PAN capture templates that auto-crop to the card's known aspect ratio, flag glare/blur before accepting the shot, and — critically — **offer on-device masking of the Aadhaar number** (show only last 4 digits) before export or share, mirroring UIDAI's own masked-Aadhaar guidance. This is a genuine trust feature, not a gimmick.
- **GST invoice mode** — a capture template tuned for the dense, small-print tabular layout of Indian tax invoices, plus optional structured-field extraction (GSTIN, invoice no., taxable value, CGST/SGST/IGST) for small-business users doing manual bookkeeping.
- **Low-light and low-quality-paper handling** — many source documents are photocopies of photocopies, or shot under a single tube light. Tune your enhancement pipeline (adaptive thresholding, shadow removal) against real Indian paper stock and lighting, not just clean lab scans.
- **Low-bandwidth-first sync** — resumable uploads, sync-on-Wi-Fi-only toggle, and aggressive default compression for users on limited mobile data.
- **WhatsApp-native sharing** — a one-tap "Share to WhatsApp" as a first-class action, not routed through the generic OS share sheet as an afterthought. WhatsApp is the dominant document-transmission channel in India by a wide margin.
- **DigiLocker bridge** (phase 2+) — let users pull an existing DigiLocker-issued document in, or push a scanned+verified document out, via DigiLocker's partner API. This is a genuine differentiator no global competitor will prioritise.

04

## Technical stack for React Native

React Native is a defensible choice here — this is a camera-plus-CRUD app, not a 3D engine, and RN's ecosystem for camera/ML bridging has matured a lot. The trick is knowing which pieces to buy off-the-shelf versus build.

| Layer                                   | Recommendation                                                                                                                                                | Why                                                                                                                                                                                                                                                               |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Camera capture                          | `react-native-vision-camera`                                                                                                                                  | Frame-processor architecture lets you run real-time edge detection on the live preview, not just the captured still.                                                                                                                                              |
| Edge detection / perspective warp       | Native module wrapping **OpenCV** (Android: OpenCV4Android via JNI; iOS: OpenCV via a small Swift bridge), invoked from a VisionCamera frame processor        | Contour detection + `warpPerspective` is a solved OpenCV problem; don't reinvent it in JS.                                                                                                                                                                        |
| On-device OCR (English + major scripts) | **Google ML Kit Text Recognition v2** via a thin native module, with a fallback to **Tesseract.js/tesseract-ocr** for scripts ML Kit doesn't cover well       | ML Kit is free, fast, fully on-device, and already ships in Google Drive/Pixel Camera's own scanner — good baseline accuracy for Latin + Devanagari-adjacent scripts. Layer in Tesseract's regional-language trained data for scripts where ML Kit underperforms. |
| ID/GST field extraction (phase 2)       | Cloud OCR API (Google Cloud Vision Document AI, or an Indian vendor with document-specific parsers) called only when the user opts into cloud processing      | Structured extraction (GSTIN, Aadhaar layout) needs more than raw text recognition; keep it opt-in for privacy reasons.                                                                                                                                           |
| PDF assembly                            | `react-native-pdf-lib` / native `PDFKit` (iOS) & `PdfDocument` (Android) via a bridge                                                                         | Multi-page assembly, compression, and text-layer embedding need native PDF primitives, not a pure-JS library.                                                                                                                                                     |
| Local storage & DB                      | **WatermelonDB** or **SQLite** (via `op-sqlite`) for metadata; files on-device filesystem                                                                     | Needs to work fully offline and sync later — a reactive local DB with a sync adapter beats trying to keep everything in cloud state.                                                                                                                              |
| Auth                                    | Firebase Auth or Supabase Auth, with Google Sign-In and phone-OTP (very important for India, where email logins have lower completion) as first-class options | Phone-number auth matches how Indian users actually verify identity across apps.                                                                                                                                                                                  |
| Cloud storage & sync                    | Firebase Storage / AWS S3 with an **ap-south-1 (Mumbai)** region, or an Indian cloud provider for stricter data-residency positioning                         | Latency and, more importantly, a credible "your Aadhaar scan never leaves India" claim.                                                                                                                                                                           |
| Backend                                 | Node.js/Express or Supabase Edge Functions for thin API layer (auth, subscription checks, DigiLocker OAuth relay)                                             | Keep business logic thin; most heavy lifting (OCR, image processing) should stay on-device for cost and privacy.                                                                                                                                                  |
| Payments                                | **Razorpay** or **Cashfree** subscriptions, with UPI Autopay for recurring billing                                                                            | UPI Autopay has dramatically higher conversion in India than card-on-file for recurring subscriptions.                                                                                                                                                            |

Skip Flutter/native-only debates here — the deciding factor for you isn't RN vs Flutter in the abstract, it's that RN's camera/native-module ecosystem for this exact use case (VisionCamera + OpenCV bridges) is well-trodden, which shortens your MVP timeline.

05

## On "porting" existing Adobe Scan data via Google Sign-In

Direct answer

No — signing a user into your app with the same Google account they use in Adobe Scan does **not** give you access to their existing scans. Google Sign-In only authenticates identity (confirms "this is the same person" and hands you their name/email). It grants zero access to another app's private storage. Adobe Scan's files live in Adobe's own account system (Adobe Document Cloud / Creative Cloud storage tied to an Adobe ID), which is entirely separate from Google's identity layer even if the user happens to use "Sign in with Google" to reach it.

There are exactly three realistic paths to "bring your old scans in," and they differ a lot in effort:

- **Manual export, your side does the work of feeling instant.** Adobe Scan lets users export/share individual files or bulk-export to their device or to a connected cloud drive. You build a slick "Import scans" flow (pick from Files app / Google Drive / Photos), bulk-ingest PDFs and JPEGs, run your own OCR pass over the imported files, and drop them into the user's library. This is the only path that needs zero cooperation from Adobe and it's genuinely how most scanner-app migrations happen in practice.
- **If Adobe Scan already saves to the user's Google Drive** (many users configure this), you can request Google Drive API scopes during your own Google Sign-In flow and, with the user's explicit consent, list and pull files from a specific Drive folder. This isn't "the same account gives you access" magically — it's a separate, explicit OAuth consent grant for Drive specifically, on top of basic sign-in, and only works for whatever Adobe already exported there.
- **Formal data portability APIs.** Adobe doesn't publish a public API for pulling a user's Document Cloud files into a third-party app, so this path isn't available to you as an indie developer — don't plan around it.

Practically: design your onboarding around **"Import your existing scans"** as a first-run screen (pick files from device storage / Google Drive / Photos), not around any illusion of automatic account-linked migration. Users are used to this pattern from switching note apps, photo apps, etc.

06

## Market differentiation vs. Adobe Scan and CamScanner

| Axis                  | Adobe Scan                                       | CamScanner                                       | Your wedge                                                                                                                                     |
| --------------------- | ------------------------------------------------ | ------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| Legal status in India | Fine                                             | Banned since 2020, still flagged when spotted    | Lead with "Made in India, RBI/MeitY-friendly hosting" as a trust signal, especially for govt/enterprise users still quietly using banned apps. |
| Language/script OCR   | Weak regional coverage                           | Weak                                             | Dedicated regional-language OCR, prioritised by your launch geography.                                                                         |
| ID document handling  | Generic capture only                             | Generic capture only                             | Aadhaar/PAN-aware capture + masking as a named feature.                                                                                        |
| Data residency        | Global (US-centric) cloud                        | Historically opaque, China-linked                | India-region storage, explicit no-third-party-sharing policy, visible in-app.                                                                  |
| Pricing               | Bundled into Acrobat subscription, dollar-pegged | Freemium with aggressive upsell/ads historically | Rupee-first pricing, a genuinely usable free tier, UPI-billed subscription.                                                                    |
| Offline behaviour     | Good                                             | Good                                             | Match this — it's non-negotiable, not a differentiator, given patchy connectivity outside metros.                                              |

Your honest positioning: *"Everything Adobe Scan does, tuned for how documents actually look and move in India — plus you're not trusting a banned Chinese app with your Aadhaar card."*

07

## Monetisation

- **Free tier** — unlimited scans, watermark-free PDF export, basic OCR (English + 1–2 regional languages), capped cloud storage (e.g. 100 scans or 500MB synced). Indian users churn hard on aggressive paywalls; make the free tier good enough to be a daily driver.
- **Premium subscription** — priced meaningfully below Adobe's bundle (think ₹99–₹199/month or ₹599–₹999/year), unlocking: full regional-language OCR, unlimited cloud sync, GST/ID templates with structured field extraction, e-sign, batch export, and ad removal if you run ads on free tier.
- **Ads on free tier** — non-intrusive, interstitial only at natural break points (after export, not mid-scan). Avoid ads inside the capture flow itself — that's the single fastest way to feel like the CamScanner users fled.
- **Small-business / enterprise tier** — team accounts for CA firms, law offices, and small retailers: shared folders, GST-invoice batch processing, admin controls, and API access for bulk digitisation. Price per-seat, sold directly to accounting/CA associations and MSME clusters rather than through app-store discovery alone.
- **Institutional partnerships** — tie-ups with coaching institutes, CA/CS training bodies, and college administrations for bulk licensing (assignment/answer-sheet scanning is a real recurring use case in Indian higher ed, visible in your own screenshot).

08

## Development roadmap

Phase 0 — Validation (2–3 weeks) 1 person

Clickable prototype + 15–20 user interviews (students, a CA/small shop owner, a teacher) to pressure-test which India-specific features actually matter to your first cohort versus which are nice-to-have. Confirm your first target language beyond Hindi/English.

Phase 1 — MVP (8–10 weeks) 2–3 engineers, 1 designer

Camera capture + edge detection + multi-page PDF, filters, on-device English+Hindi OCR, PDF/JPEG export, basic library with search and folders, share sheet incl. WhatsApp, phone-OTP + Google auth, offline-first storage. Ship to a closed beta (TestFlight/Play internal track).

Phase 2 — India-native layer (6–8 weeks) 2–3 engineers

ID capture modes with masking, GST invoice template, 3–4 additional regional-language OCR packs, compression controls, cloud sync to India-region storage, subscription billing via Razorpay/UPI Autopay, "Import your scans" onboarding flow.

Phase 3 — Public launch (4 weeks) Full team + marketing

Play Store/App Store launch, ASO targeting regional-language search terms ("hindi document scanner", "GST bill scan app"), influencer/CA-community seeding, monitor crash-free rate and OCR accuracy telemetry closely in the first weeks.

Phase 4 — Depth (ongoing, quarterly) Team scales with revenue

DigiLocker integration, structured GST/ID field extraction via cloud OCR, small-business team accounts, remaining regional languages, e-signature workflows for contracts.

09

## Challenges & solutions

### OCR accuracy on Indian scripts and handwriting

Off-the-shelf OCR (ML Kit, base Tesseract) is tuned mostly on clean Latin-script text. Accuracy drops hard on Devanagari/complex-script ligatures and on handwriting. **Solution:** use Tesseract's language-specific trained data (`hin.traineddata`, `tam.traineddata`, etc.) as a fallback path, benchmark against real user documents (not clean test sets) before launch, and be honest in-app about which languages are "beta."

### Device fragmentation

The bulk of the Indian Android install base skews toward mid-range and budget devices (limited RAM, weaker cameras, older Android versions) — very different from a flagship-first testing setup. **Solution:** test on real budget devices (Redmi/Realme mid-range, not just an emulator or a flagship), keep native OpenCV/ML Kit work off the JS thread, and offer a lower-fidelity capture mode for weak cameras.

### Storage and bandwidth constraints

Many users are on 32–64GB devices already near capacity, and on limited prepaid data. **Solution:** aggressive default compression, a visible storage-used indicator, Wi-Fi-only sync as default (not opt-out buried in settings), and delete-after-sync as a user-controlled option.

### Trust around Aadhaar/PAN data

Any app that touches ID documents in India faces justified skepticism after the CamScanner episode. **Solution:** process ID scans on-device by default (cloud upload opt-in only), mask Aadhaar numbers automatically before export/share, publish a plain-language privacy page (not just a legal ToS wall), and consider a security audit/certification once you have traction — make privacy a visible feature, not fine print.

### Payment conversion

Card-based subscriptions convert poorly in India relative to UPI. **Solution:** lead with UPI Autopay at checkout, not card entry; keep the free tier strong enough that paywall friction isn't your main growth blocker.

10

## What to add beyond your original list — quick checklist

- Aadhaar-number auto-masking before export/share (trust feature, cheap to build, high perceived value)
- GST invoice capture template with structured field extraction as a phase-2 item
- Phone-OTP login alongside Google Sign-In — higher completion than email-first auth in India
- UPI Autopay for subscriptions, not card-only billing
- "Import your scans" onboarding flow instead of relying on account-linked migration (see Section 5)
- WhatsApp as a first-class share target, not buried in the OS share sheet
- Explicit on-device-processing-by-default stance for ID documents, marketed as a privacy feature
- India-region cloud storage (or an Indian provider) as a stated policy, not just a technical default
- Wi-Fi-only sync default and a visible storage/compression control
- DigiLocker bridge as a post-launch differentiator once you have a user base to justify the integration effort

Prepared as a working playbook — treat every price point and timeline as a starting hypothesis to validate with your own users, not a fixed spec.
