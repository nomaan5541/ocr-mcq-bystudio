package com.omnisolve.overlay.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class PlayUpdateUiState {
    data object Idle : PlayUpdateUiState()
    data object Checking : PlayUpdateUiState()
    data class Available(
        val isImmediate: Boolean,
        val availableVersionCode: Int
    ) : PlayUpdateUiState()
    data class Downloading(val progressPct: Int) : PlayUpdateUiState()
    data object ReadyToInstall : PlayUpdateUiState()
    data object UpToDate : PlayUpdateUiState()
    data class Unavailable(val message: String) : PlayUpdateUiState()
}

/**
 * Lifecycle-aware Google Play Store In-App Update controller.
 * - Automatically checks Play Store on app open and notifies users cleanly inside the app.
 * - Uses Flexible updates by default (background download + one-tap restart banner) and
 *   Immediate updates for high-priority releases (updatePriority >= 4).
 * - Fails gracefully in Robolectric/sideloaded builds without crashing or blocking UX.
 */
class InAppUpdateController(private val appContext: Context) {

    companion object {
        private const val TAG = "InAppUpdateController"
        private const val HIGH_PRIORITY_THRESHOLD = 4
    }

    private val _updateState = MutableStateFlow<PlayUpdateUiState>(PlayUpdateUiState.Idle)
    val updateState: StateFlow<PlayUpdateUiState> = _updateState.asStateFlow()

    private var appUpdateManager: AppUpdateManager? = null
    private var cachedUpdateInfo: AppUpdateInfo? = null
    private var listenerRegistered = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val total = state.totalBytesToDownload().coerceAtLeast(1L)
                val downloaded = state.bytesDownloaded().coerceIn(0L, total)
                val pct = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                _updateState.value = PlayUpdateUiState.Downloading(pct)
            }
            InstallStatus.DOWNLOADED -> {
                _updateState.value = PlayUpdateUiState.ReadyToInstall
            }
            InstallStatus.INSTALLED -> {
                _updateState.value = PlayUpdateUiState.UpToDate
                unregisterListener()
            }
            InstallStatus.FAILED, InstallStatus.CANCELED -> {
                _updateState.value = PlayUpdateUiState.Idle
            }
            else -> Unit
        }
    }

    private fun getOrCreateManager(): AppUpdateManager? {
        return appUpdateManager ?: runCatching {
            AppUpdateManagerFactory.create(appContext)
        }.onFailure {
            Log.d(TAG, "Play AppUpdateManager unavailable: ${it.message}")
        }.getOrNull()?.also { appUpdateManager = it }
    }

    fun checkForUpdates(
        manualCheck: Boolean = false,
        onManualFeedback: ((String) -> Unit)? = null
    ) {
        val manager = getOrCreateManager()
        if (manager == null) {
            if (manualCheck) {
                _updateState.value = PlayUpdateUiState.Unavailable("Google Play updates unavailable on this device")
                onManualFeedback?.invoke("Play Store update check unavailable on this build")
            }
            return
        }

        if (manualCheck) {
            _updateState.value = PlayUpdateUiState.Checking
        }

        runCatching {
            manager.appUpdateInfo.addOnSuccessListener { info ->
                cachedUpdateInfo = info
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> {
                        _updateState.value = PlayUpdateUiState.ReadyToInstall
                        if (manualCheck) {
                            onManualFeedback?.invoke("Update downloaded — tap Restart to install")
                        }
                    }
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE -> {
                        val isImmediate = info.updatePriority() >= HIGH_PRIORITY_THRESHOLD &&
                            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                        val isFlexible = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                        if (isImmediate || isFlexible) {
                            _updateState.value = PlayUpdateUiState.Available(
                                isImmediate = isImmediate,
                                availableVersionCode = info.availableVersionCode()
                            )
                            if (manualCheck) {
                                onManualFeedback?.invoke("New Prism Solver update available on Play Store!")
                            }
                        } else if (manualCheck) {
                            _updateState.value = PlayUpdateUiState.UpToDate
                            onManualFeedback?.invoke("You're on the latest version of Prism Solver")
                        }
                    }
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        _updateState.value = PlayUpdateUiState.Available(
                            isImmediate = true,
                            availableVersionCode = info.availableVersionCode()
                        )
                    }
                    else -> {
                        _updateState.value = PlayUpdateUiState.UpToDate
                        if (manualCheck) {
                            onManualFeedback?.invoke("You're on the latest version of Prism Solver")
                        }
                    }
                }
            }.addOnFailureListener { err ->
                Log.d(TAG, "In-app update check skipped: ${err.message}")
                if (manualCheck) {
                    _updateState.value = PlayUpdateUiState.Unavailable("Open Google Play Store to check for updates")
                    onManualFeedback?.invoke("Check Google Play Store for the latest release")
                }
            }
        }.onFailure {
            if (manualCheck) {
                onManualFeedback?.invoke("Check Google Play Store for the latest release")
            }
        }
    }

    fun startUpdateFlow(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onFallbackOpenPlayStore: () -> Unit = {}
    ) {
        val manager = getOrCreateManager()
        val info = cachedUpdateInfo
        if (manager == null || info == null) {
            onFallbackOpenPlayStore()
            return
        }
        val state = _updateState.value
        val useImmediate = (state as? PlayUpdateUiState.Available)?.isImmediate == true &&
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
        val updateType = if (useImmediate) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE

        if (updateType == AppUpdateType.FLEXIBLE && !listenerRegistered) {
            runCatching {
                manager.registerListener(installStateListener)
                listenerRegistered = true
            }
        }

        val started = runCatching {
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(updateType).build()
            )
        }.getOrDefault(false)

        if (!started) {
            onFallbackOpenPlayStore()
        }
    }

    fun completeFlexibleUpdate() {
        runCatching {
            getOrCreateManager()?.completeUpdate()
        }
    }

    fun onResumeCheck(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val manager = getOrCreateManager() ?: return
        runCatching {
            manager.appUpdateInfo.addOnSuccessListener { info ->
                cachedUpdateInfo = info
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    _updateState.value = PlayUpdateUiState.ReadyToInstall
                } else if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    manager.startUpdateFlowForResult(
                        info,
                        launcher,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                }
            }
        }
    }

    fun dismissBanner() {
        if (_updateState.value is PlayUpdateUiState.Available) {
            _updateState.value = PlayUpdateUiState.Idle
        }
    }

    fun unregisterListener() {
        if (listenerRegistered) {
            runCatching { appUpdateManager?.unregisterListener(installStateListener) }
            listenerRegistered = false
        }
    }
}

/**
 * Triggers Google Play's native In-App Review dialog gently after genuine value delivery
 * (5+ solved questions) with a 30-day cooldown so users are never spammed.
 */
object InAppReviewHelper {
    private const val PREF_LAST_REVIEW_PROMPT_MS = "PREF_PLAY_REVIEW_LAST_PROMPT_MS"
    private const val COOLDOWN_MS = 30L * 24L * 60L * 60L * 1000L
    private const val MIN_SOLVES_FOR_REVIEW = 5

    fun maybeRequestReview(activity: Activity, totalSolvedCount: Int) {
        if (totalSolvedCount < MIN_SOLVES_FOR_REVIEW) return
        val prefs = activity.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        val lastPrompt = prefs.getLong(PREF_LAST_REVIEW_PROMPT_MS, 0L)
        val now = System.currentTimeMillis()
        if (lastPrompt > 0L && (now - lastPrompt) < COOLDOWN_MS) return

        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    prefs.edit().putLong(PREF_LAST_REVIEW_PROMPT_MS, now).apply()
                    manager.launchReviewFlow(activity, task.result)
                }
            }
        }
    }
}

/**
 * One-tap Share with Friends & Play Store listing helper so friends immediately understand
 * what Prism Solver does when receiving a link.
 */
object AppShareHelper {

    fun openPlayStoreListing(context: Context) {
        val pkg = context.packageName
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(marketIntent) }
            .onFailure { runCatching { context.startActivity(webIntent) } }
    }

    fun shareAppWithFriends(context: Context) {
        val pkg = context.packageName
        val shareText = buildString {
            appendLine("Try Prism Solver — tap one floating pill over any quiz app to solve:")
            appendLine("• MCQs & True/False")
            appendLine("• Math equations & symbols (1/2, Σ, ∫, √)")
            appendLine("• Match-the-Following & Google Forms")
            appendLine("• Instant Offline Cache — 100% Free!")
            append("https://play.google.com/store/apps/details?id=$pkg")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Prism Solver — Instant Screen Quiz & Math Helper")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        val chooser = Intent.createChooser(sendIntent, "Share Prism Solver with Friends").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }
}
