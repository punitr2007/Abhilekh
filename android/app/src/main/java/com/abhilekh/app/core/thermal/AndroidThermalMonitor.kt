package com.abhilekh.app.core.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

enum class ThermalTier {
    NOMINAL,   // Full resolution (300 DPI), max concurrency
    MODERATE,  // Downsample to 200 DPI, 50ms yield per page
    SEVERE     // Downsample to 150 DPI, 150ms yield per page, single-threaded
}

class AndroidThermalMonitor(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /**
     * Determines current thermal state using Android 11+ Headroom API with API 24–29 Battery Temperature fallback.
     */
    fun getCurrentThermalTier(): ThermalTier {
        // Android 11+ (API 30+) Native Thermal Headroom API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                val headroom = powerManager.getThermalHeadroom(30)
                return when {
                    headroom >= 0.9f -> ThermalTier.SEVERE
                    headroom >= 0.75f -> ThermalTier.MODERATE
                    else -> ThermalTier.NOMINAL
                }
            } catch (_: Exception) {
                // In case vendor OEM throws exception, gracefully fallback
            }
        }

        // Fallback for Android 7.0–10 (API 24–29): Battery Temperature Monitoring
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenthsCelsius = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempCelsius = tempTenthsCelsius / 10.0

            when {
                tempCelsius >= 43.0 -> ThermalTier.SEVERE
                tempCelsius >= 39.0 -> ThermalTier.MODERATE
                else -> ThermalTier.NOMINAL
            }
        } catch (_: Exception) {
            ThermalTier.NOMINAL
        }
    }
}
