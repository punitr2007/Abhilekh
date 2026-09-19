# Engineering Rules & Quality Constraints
## Project: Abhilekh (अभिलेख) — Mobile Document Scanner (v3)

---

## 1. Native Memory & Computer Vision Safety Rules

### 1.1 C++ / OpenCV Lifecycle & Resource Management
1. **Algorithm Correctness**: The document enhancement filter is strictly specified as **Morphological Background Estimation & Illumination Division** (`cv::dilate` $\rightarrow$ `cv::medianBlur` $\rightarrow$ `cv::divide` $\rightarrow$ YCrCb chroma recombination). Do not substitute Difference of Gaussians (DoG) for this filter.
2. **Explicit C++ Memory Deallocation**: All `cv::Mat` objects created during image processing must be explicitly freed or scoped to stack frames. Never retain lingering `cv::Mat` allocations in persistent memory.
3. **Bitmap Recycling in Android**: When handling 12MP high-resolution camera images, always explicitly invoke `bitmap.recycle()` or release native `DirectByteBuffer` buffers as soon as the image is encoded into PDF or saved to disk.

```cpp
// CORRECT PATTERN: Explicit cleanup and non-leaking native transformation
cv::Mat enhanceDocumentPaper(const cv::Mat& srcRgb) {
    cv::Mat gray, bgIllumination, normalized, result;
    cv::cvtColor(srcRgb, gray, cv::COLOR_RGB2GRAY);
    
    cv::dilate(gray, bgIllumination, cv::getStructuringElement(cv::MORPH_RECT, cv::Size(15, 15)));
    cv::medianBlur(bgIllumination, bgIllumination, 21);
    
    cv::Mat grayFloat, bgFloat;
    gray.convertTo(grayFloat, CV_32F);
    bgIllumination.convertTo(bgFloat, CV_32F);
    cv::divide(grayFloat, bgFloat, normalized, 255.0);
    normalized.convertTo(normalized, CV_8U);
    
    cv::normalize(normalized, normalized, 0, 255, cv::NORM_MINMAX);
    
    std::vector<cv::Mat> ycrcb;
    cv::cvtColor(srcRgb, result, cv::COLOR_RGB2YCrCb);
    cv::split(result, ycrcb);
    ycrcb[0] = normalized;
    cv::merge(ycrcb, result);
    cv::cvtColor(result, result, cv::COLOR_YCrCb2RGB);
    
    return result;
}
```

---

## 2. Privacy, DPDP Act 2023 & UIDAI Compliance Rules

1. **Global Aadhaar Masking & Fail-Loud Rule**:
   - Aadhaar detection must execute against **every page's OCR stream**, regardless of the user's selected capture mode.
   - If an Aadhaar card is detected but digits fail Verhoeff validation or have low confidence, the app must **fail loud** by showing an amber warning and prompting the user to apply the **Manual Redaction Brush**.
   - Masked documents must have the first 8 digits permanently redacted in **both** the raster pixel layer (black box fill) **and** the embedded PDF text layer (`XXXX XXXX `).
2. **Zero Unconsented Telemetry**:
   - Never send document images, OCR text, file names, or metadata to any telemetry or analytics service.
3. **DPDP Right to Erasure**:
   - The app must provide an accessible "Erase All Data" option in Settings that completely purges all local database records, cached images, PDFs, and encryption keys.

---

## 3. PDF Compliance & Invisible Text Rules

1. **Text Rendering Mode 3 (`3 Tr`) Invariant**:
   - Invisible OCR text layers must be written to the PDF content stream using `RenderingMode.NEITHER` (`3 Tr`). Never use alpha transparency (`Color.TRANSPARENT`), which violates PDF/A-1b and causes rendering artifacts across third-party viewers.
2. **Adaptive Compression Floor**:
   - The compression engine must iteratively optimize toward target file sizes (<500KB for Govt Exam mode) while maintaining minimum visual legibility; if page density prevents meeting the target, warn the user rather than over-compressing into an illegible artifact.
