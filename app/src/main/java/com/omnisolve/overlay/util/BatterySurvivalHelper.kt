package com.omnisolve.overlay.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

/**
 * Play-Policy-compliant Battery Optimization & OEM Keep-Alive advisor.
 *
 * Uses standard Android Settings intents (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` and
 * `ACTION_APPLICATION_DETAILS_SETTINGS`) without requesting the restricted
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` manifest permission, giving the user direct 1-tap
 * control to set Prism Solver to "Unrestricted" so Battery Saver never restarts or delays
 * the Dynamic Island overlay.
 */
data class BatterySurvivalStatus(
    val isIgnoringBatteryOptimizations: Boolean,
    val isPowerSaveModeActive: Boolean,
    val oemDisplayName: String,
    val oemStepsHint: String,
    val isAggressiveOem: Boolean
) {
    val isProtected: Boolean
        get() = isIgnoringBatteryOptimizations

    val shortBadge: String
        get() = when {
            isIgnoringBatteryOptimizations && isPowerSaveModeActive -> "Unrestricted • Saver On"
            isIgnoringBatteryOptimizations -> "Unrestricted • Protected"
            isPowerSaveModeActive -> "Battery Saver Active"
            else -> "Optimize Off Recommended"
        }
}

object BatterySurvivalHelper {

    fun inspect(context: Context): BatterySurvivalStatus {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoring = try {
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (_: Exception) {
            false
        }
        val powerSave = try {
            pm?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }

        val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.US)
        val brand = (Build.BRAND ?: "").lowercase(Locale.US)
        val oemKey = "$manufacturer $brand"

        return when {
            oemKey.contains("xiaomi") || oemKey.contains("redmi") || oemKey.contains("poco") -> {
                BatterySurvivalStatus(
                    isIgnoringBatteryOptimizations = ignoring,
                    isPowerSaveModeActive = powerSave,
                    oemDisplayName = "Xiaomi / HyperOS",
                    oemStepsHint = "Tap Battery Saver → No restrictions, and turn on Autostart so MIUI never restarts the Dynamic Island.",
                    isAggressiveOem = true
                )
            }
            oemKey.contains("samsung") -> {
                BatterySurvivalStatus(
                    isIgnoringBatteryOptimizations = ignoring,
                    isPowerSaveModeActive = powerSave,
                    oemDisplayName = "Samsung One UI",
                    oemStepsHint = "Tap Battery → Unrestricted so Samsung Sleeping Apps never pause or delay answers.",
                    isAggressiveOem = true
                )
            }
            oemKey.contains("vivo") || oemKey.contains("iqoo") -> {
                BatterySurvivalStatus(
                    isIgnoringBatteryOptimizations = ignoring,
                    isPowerSaveModeActive = powerSave,
                    oemDisplayName = "Vivo / iQOO",
                    oemStepsHint = "Tap Battery → High background power consumption (Unrestricted) to keep the floating pill instant.",
                    isAggressiveOem = true
                )
            }
            oemKey.contains("oppo") || oemKey.contains("realme") || oemKey.contains("oneplus") -> {
                BatterySurvivalStatus(
                    isIgnoringBatteryOptimizations = ignoring,
                    isPowerSaveModeActive = powerSave,
                    oemDisplayName = if (oemKey.contains("oneplus")) "OnePlus OxygenOS" else "Oppo / Realme",
                    oemStepsHint = "Tap Battery usage → Allow background activity & disable Auto-optimize to stop overlay restarts.",
                    isAggressiveOem = true
                )
            }
            oemKey.contains("huawei") || oemKey.contains("honor") -> {
                BatterySurvivalStatus(
                    isIgnoringBatteryOptimizations = ignoring,
                    isPowerSaveModeActive = powerSave,
                    oemDisplayName = "Huawei / Honor",
                    oemStepsHint = "Tap App Launch → Manage manually and allow Auto-launch + Run in background.",
                    isAggressiveOem = true
                )
            }
            else -> {
                BatterySurvivalStatus(
                    isIgnoringBatteryOptimizations = ignoring,
                    isPowerSaveModeActive = powerSave,
                    oemDisplayName = "Android Battery",
                    oemStepsHint = "Set App battery usage to Unrestricted so Battery Saver never restarts or slows down Dynamic Island.",
                    isAggressiveOem = false
                )
            }
        }
    }

    /**
     * Opens Android's Battery Optimization list (or App Details Battery screen as fallback)
     * in strict compliance with Google Play Developer policies.
     */
    fun openUnrestrictedBatterySettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // try next
            }
        }
        return false
    }

    fun openBatteryOptimizationList(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openUnrestrictedBatterySettings(context)
        }
    }
}
