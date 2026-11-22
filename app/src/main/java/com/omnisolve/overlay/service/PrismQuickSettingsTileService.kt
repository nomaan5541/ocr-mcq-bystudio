package com.omnisolve.overlay.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R

/**
 * Android Pull-Down Quick Settings Tile for Prism Solver:
 * - When OverlayService is active, tapping the tile triggers an instant screen solve from any app.
 * - When OverlayService is stopped, tapping opens Prism Solver and launches the 1-tap floating helper flow.
 * - Stays synced automatically whenever OverlayService starts or stops via [requestTileRefresh].
 */
class PrismQuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        if (OverlayService.isServiceRunning()) {
            OverlayService.triggerSolveFromHardware()
            val tile = qsTile
            if (tile != null) {
                tile.label = "Prism Solve"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Solving screen..."
                }
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
            }
            Toast.makeText(this, "Prism: Solving current screen...", Toast.LENGTH_SHORT).show()
        } else {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_LAUNCH_OVERLAY_FROM_TILE, true)
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
        tile.label = if (running) "Prism Solve" else "Prism Pill"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) "Tap to Solve" else "Tap to Start"
        }
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        /**
         * Requests Android SystemUI to re-query and update the Quick Settings tile immediately
         * when OverlayService starts or stops.
         */
        fun requestTileRefresh(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            try {
                val component = ComponentName(context.applicationContext, PrismQuickSettingsTileService::class.java)
                requestListeningState(context.applicationContext, component)
            } catch (_: Exception) {
            }
        }

        /**
         * Prompts Android 13+ users with the native system sheet to add the Prism Quick Settings
         * tile in one tap. Falls back to a clear 1-line guide on Android 12 and below.
         */
        fun requestAddTileToShade(
            context: Context,
            onResult: (String) -> Unit
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val sbm = context.getSystemService(StatusBarManager::class.java)
                    if (sbm == null) {
                        onResult("Swipe down twice, tap Edit, and drag Prism Pill to your Quick Settings.")
                        return
                    }
                    val component = ComponentName(context.applicationContext, PrismQuickSettingsTileService::class.java)
                    val icon = Icon.createWithResource(context.applicationContext, R.drawable.ic_prism_orb)
                    sbm.requestAddTileService(
                        component,
                        "Prism Pill",
                        icon,
                        context.mainExecutor
                    ) { resultCode ->
                        val msg = when (resultCode) {
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                                "Prism Pill added to your Quick Settings shade!"
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                                "Prism Pill is already in your Quick Settings shade."
                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                                "Tile not added. You can add Prism Pill anytime from Quick Settings."
                            else ->
                                "Swipe down twice, tap Edit, and drag Prism Pill into Quick Settings."
                        }
                        onResult(msg)
                    }
                } catch (_: Exception) {
                    onResult("Swipe down twice, tap Edit, and drag Prism Pill into Quick Settings.")
                }
            } else {
                onResult("Swipe down twice from the top bar, tap Edit, and drag Prism Pill into Quick Settings.")
            }
        }
    }
}
