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

// Morphological Background Division on RGBA Bitmap buffer
void processIlluminationDivision(uint8_t* pixels, int width, int height, int stride) {
    std::vector<uint8_t> gray(width * height);
    std::vector<uint8_t> bgDilated(width * height);
    std::vector<uint8_t> bgSmooth(width * height);

    // 1. Convert RGBA to Grayscale
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

    // 2. Morphological Dilation (Radius: 7px box max filter)
    int kRadius = 7;
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            uint8_t maxVal = 0;
            int yMin = std::max(0, y - kRadius);
            int yMax = std::min(height - 1, y + kRadius);
            int xMin = std::max(0, x - kRadius);
            int xMax = std::min(width - 1, x + kRadius);

            for (int ky = yMin; ky <= yMax; ky += 2) { // Subsampled kernel step for speed
                for (int kx = xMin; kx <= xMax; kx += 2) {
                    uint8_t v = gray[ky * width + kx];
                    if (v > maxVal) maxVal = v;
                }
            }
            bgDilated[y * width + x] = maxVal;
        }
    }

    // 3. Illumination Division + Contrast Stretch + Recombine
    for (int y = 0; y < height; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(pixels + y * stride);
        for (int x = 0; x < width; ++x) {
            uint32_t color = row[x];
            uint8_t a = (color >> 24) & 0xFF;
            uint8_t r = (color >> 16) & 0xFF;
            uint8_t g = (color >> 8) & 0xFF;
            uint8_t b = color & 0xFF;

            float bg = std::max(1.0f, static_cast<float>(bgDilated[y * width + x]));
            
            // Background division per channel
            float normR = std::min(255.0f, (r / bg) * 255.0f);
            float normG = std::min(255.0f, (g / bg) * 255.0f);
            float normB = std::min(255.0f, (b / bg) * 255.0f);

            // Contrast enhancement curve
            uint8_t finalR = static_cast<uint8_t>(std::clamp(normR * 1.05f - 5.0f, 0.0f, 255.0f));
            uint8_t finalG = static_cast<uint8_t>(std::clamp(normG * 1.05f - 5.0f, 0.0f, 255.0f));
            uint8_t finalB = static_cast<uint8_t>(std::clamp(normB * 1.05f - 5.0f, 0.0f, 255.0f));

            row[x] = (a << 24) | (finalR << 16) | (finalG << 8) | finalB;
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_abhilekh_app_core_cv_OpenCVNativeBridge_nativeApplyIlluminationDivision(
    JNIEnv* env,
    jobject /* this */,
    jobject bitmap
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format must be RGBA_8888");
        return;
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return;
    }

    processIlluminationDivision(
        reinterpret_cast<uint8_t*>(pixels),
        info.width,
        info.height,
        info.stride
    );

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

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    int x0 = std::max(0, (int)left);
    int y0 = std::max(0, (int)top);
    int x1 = std::min((int)info.width, (int)right);
    int y1 = std::min((int)info.height, (int)bottom);

    for (int y = y0; y < y1; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(reinterpret_cast<uint8_t*>(pixels) + y * info.stride);
        for (int x = x0; x < x1; ++x) {
            row[x] = 0xFF000000; // Solid black fill (Alpha 255, RGB 0,0,0)
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}
