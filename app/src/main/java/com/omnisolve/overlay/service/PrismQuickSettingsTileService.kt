package com.omnisolve.overlay.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.omnisolve.overlay.MainActivity

/**
 * Android Pull-Down Quick Settings Tile for Prism AI:
 * - When OverlayService is active, tapping the tile triggers an immediate screen solve from anywhere.
 * - When OverlayService is stopped, tapping opens Prism AI to launch the floating overlay.
 */
class PrismQuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val tileFeatureEnabled = prefs.getBoolean(MainActivity.PREF_KEY_ADV_QUICK_TILE_NOTIF, false)

        if (OverlayService.isServiceRunning()) {
            OverlayService.triggerSolveFromHardware()
            Toast.makeText(this, "Prism AI: Solving current screen...", Toast.LENGTH_SHORT).show()
            refreshTileState()
        } else {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (!tileFeatureEnabled) {
                Toast.makeText(
                    this,
                    "Enable Quick Settings & Notification Controls in Advanced Settings or tap Start",
                    Toast.LENGTH_SHORT
                ).show()
            }
            launchMainSafely(launchIntent)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchMainSafely(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    201,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (_: Exception) {
            try {
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshTileState() {
        val tile = qsTile ?: return
        val running = OverlayService.isServiceRunning()
        tile.label = if (running) "Prism Solve" else "Prism AI"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) "Tap to Solve" else "Tap to Launch"
        }
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
