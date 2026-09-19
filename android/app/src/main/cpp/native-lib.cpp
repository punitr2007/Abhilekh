#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cmath>
#include <vector>
#include <algorithm>

#define TAG "AbhilekhNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Structure for ARGB_8888 Pixel
struct PixelARGB {
    uint8_t red;
    uint8_t green;
    uint8_t blue;
    uint8_t alpha;
};

// 1. Morphological Background Division on RGBA Bitmap buffer
void processIlluminationDivision(uint8_t* pixels, int width, int height, int stride) {
    std::vector<uint8_t> gray(width * height);
    std::vector<uint8_t> bgDilated(width * height);

    // Convert RGBA to Grayscale
    for (int y = 0; y < height; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(pixels + y * stride);
        for (int x = 0; x < width; ++x) {
            uint32_t color = row[x];
            uint8_t r = (color >> 16) & 0xFF;
            uint8_t g = (color >> 8) & 0xFF;
            uint8_t b = color & 0xFF;
            gray[y * width + x] = static_cast<uint8_t>((299 * r + 587 * g + 114 * b) / 1000);
        }
    }

    // Morphological Dilation (Radius: 7px box max filter)
    int kRadius = 7;
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            uint8_t maxVal = 0;
            int yMin = std::max(0, y - kRadius);
            int yMax = std::min(height - 1, y + kRadius);
            int xMin = std::max(0, x - kRadius);
            int xMax = std::min(width - 1, x + kRadius);

            for (int ky = yMin; ky <= yMax; ky += 2) {
                for (int kx = xMin; kx <= xMax; kx += 2) {
                    uint8_t v = gray[ky * width + kx];
                    if (v > maxVal) maxVal = v;
                }
            }
            bgDilated[y * width + x] = maxVal;
        }
    }

    // Illumination Division + Contrast Stretch + Recombine
    for (int y = 0; y < height; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(pixels + y * stride);
        for (int x = 0; x < width; ++x) {
            uint32_t color = row[x];
            uint8_t a = (color >> 24) & 0xFF;
            uint8_t r = (color >> 16) & 0xFF;
            uint8_t g = (color >> 8) & 0xFF;
            uint8_t b = color & 0xFF;

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

// 2. Grayscale Filter
void processGrayscale(uint8_t* pixels, int width, int height, int stride) {
    for (int y = 0; y < height; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(pixels + y * stride);
        for (int x = 0; x < width; ++x) {
            uint32_t color = row[x];
            uint8_t a = (color >> 24) & 0xFF;
            uint8_t r = (color >> 16) & 0xFF;
            uint8_t g = (color >> 8) & 0xFF;
            uint8_t b = color & 0xFF;
            uint8_t gray = static_cast<uint8_t>((299 * r + 587 * g + 114 * b) / 1000);
            row[x] = (a << 24) | (gray << 16) | (gray << 8) | gray;
        }
    }
}

// 3. Clean B&W (Adaptive Binarization)
void processBinarization(uint8_t* pixels, int width, int height, int stride) {
    std::vector<uint8_t> gray(width * height);
    for (int y = 0; y < height; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(pixels + y * stride);
        for (int x = 0; x < width; ++x) {
            uint32_t color = row[x];
            uint8_t r = (color >> 16) & 0xFF;
            uint8_t g = (color >> 8) & 0xFF;
            uint8_t b = color & 0xFF;
            gray[y * width + x] = static_cast<uint8_t>((299 * r + 587 * g + 114 * b) / 1000);
        }
    }

    // Adaptive threshold with 15x15 window
    int winRadius = 8;
    for (int y = 0; y < height; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(pixels + y * stride);
        for (int x = 0; x < width; ++x) {
            int yMin = std::max(0, y - winRadius);
            int yMax = std::min(height - 1, y + winRadius);
            int xMin = std::max(0, x - winRadius);
            int xMax = std::min(width - 1, x + winRadius);

            int sum = 0;
            int count = 0;
            for (int ky = yMin; ky <= yMax; ky += 2) {
                for (int kx = xMin; kx <= xMax; kx += 2) {
                    sum += gray[ky * width + kx];
                    count++;
                }
            }
            int mean = sum / std::max(1, count);
            uint8_t currentPixel = gray[y * width + x];
            // If pixel is darker than local mean - offset (10), make black, else white
            uint8_t outVal = (currentPixel < (mean - 10)) ? 0x00 : 0xFF;
            uint8_t a = (row[x] >> 24) & 0xFF;
            row[x] = (a << 24) | (outVal << 16) | (outVal << 8) | outVal;
        }
    }
}

// 4. Color Boost (Vibrancy & Contrast Enhancement)
void processColorBoost(uint8_t* pixels, int width, int height, int stride) {
    for (int y = 0; y < height; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(pixels + y * stride);
        for (int x = 0; x < width; ++x) {
            uint32_t color = row[x];
            uint8_t a = (color >> 24) & 0xFF;
            float r = static_cast<float>((color >> 16) & 0xFF);
            float g = static_cast<float>((color >> 8) & 0xFF);
            float b = static_cast<float>(color & 0xFF);

            float avg = (r + g + b) / 3.0f;
            // Boost saturation
            r = avg + 1.35f * (r - avg);
            g = avg + 1.35f * (g - avg);
            b = avg + 1.35f * (b - avg);

            // Contrast stretch
            r = (r - 128.0f) * 1.15f + 138.0f;
            g = (g - 128.0f) * 1.15f + 138.0f;
            b = (b - 128.0f) * 1.15f + 138.0f;

            uint8_t finalR = static_cast<uint8_t>(std::clamp(r, 0.0f, 255.0f));
            uint8_t finalG = static_cast<uint8_t>(std::clamp(g, 0.0f, 255.0f));
            uint8_t finalB = static_cast<uint8_t>(std::clamp(b, 0.0f, 255.0f));

            row[x] = (a << 24) | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}

// ─── JNI Exports ──────────────────────────────────────────────────────────────

extern "C" JNIEXPORT void JNICALL
Java_com_abhilekh_app_core_cv_OpenCVNativeBridge_nativeApplyIlluminationDivision(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;
    processIlluminationDivision(reinterpret_cast<uint8_t*>(pixels), info.width, info.height, info.stride);
    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_abhilekh_app_core_cv_OpenCVNativeBridge_nativeApplyGrayscale(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;
    processGrayscale(reinterpret_cast<uint8_t*>(pixels), info.width, info.height, info.stride);
    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_abhilekh_app_core_cv_OpenCVNativeBridge_nativeApplyBinarization(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;
    processBinarization(reinterpret_cast<uint8_t*>(pixels), info.width, info.height, info.stride);
    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_abhilekh_app_core_cv_OpenCVNativeBridge_nativeApplyColorBoost(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;
    processColorBoost(reinterpret_cast<uint8_t*>(pixels), info.width, info.height, info.stride);
    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_abhilekh_app_core_cv_OpenCVNativeBridge_nativeMaskBoundingBox(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap,
    jint left,
    jint top,
    jint right,
    jint bottom
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    int x0 = std::max(0, (int)left);
    int y0 = std::max(0, (int)top);
    int x1 = std::min((int)info.width, (int)right);
    int y1 = std::min((int)info.height, (int)bottom);

    for (int y = y0; y < y1; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(reinterpret_cast<uint8_t*>(pixels) + y * info.stride);
        for (int x = x0; x < x1; ++x) {
            row[x] = 0xFF000000;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}
