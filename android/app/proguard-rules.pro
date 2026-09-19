# ─── ProGuard / R8 Rules for Abhilekh ──────────────────────────────────────────

# Keep Native JNI Bridge methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.abhilekh.app.core.cv.OpenCVNativeBridge { *; }

# Apache PDFBox for Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**

# Google ML Kit Document Scanner & Vision Text Recognition
-keep class com.google.android.gms.vision.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Room Database & SQLite
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep data models
-keepclassmembers class com.abhilekh.app.data.db.** { *; }
-keepclassmembers class com.abhilekh.app.core.pdf.PdfPageInput { *; }

# Kotlin Coroutines & Flow
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
