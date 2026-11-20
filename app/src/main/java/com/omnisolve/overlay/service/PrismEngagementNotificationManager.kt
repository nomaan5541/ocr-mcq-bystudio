package com.omnisolve.overlay.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R
import java.util.Calendar
import kotlin.random.Random

data class CustomPinnedNotifConfig(
    val title: String = "Solve Anything • Study Pin",
    val body: String = "(1-A|2-B|3-D|4-A)",
    val templateId: String = TEMPLATE_FORMS_KEY,
    val showCopy: Boolean = true,
    val showSolveNow: Boolean = true,
    val showStealth: Boolean = false,
    val stealthDisguise: Boolean = false,
    val isPinned: Boolean = false
) {
    companion object {
        const val TEMPLATE_FORMS_KEY = "FORMS_KEY"
        const val TEMPLATE_STUDY_NOTE = "STUDY_NOTE"
        const val TEMPLATE_QUICK_HUD = "QUICK_HUD"
        const val TEMPLATE_BATTERY_STEALTH = "BATTERY_STEALTH"
    }
}

/**
 * Manages:
 * 1. Detailed "Welcome to Solve Anything" first notification for new users
 * 2. Hidden pool of 28 engaging, playful/witty/flirty re-engagement check-ins (>= 2/week when inactive)
 * 3. Fully customizable ongoing Pinned Notification Bar controller with Copy/Solve/Stealth/Unpin actions
 */
object PrismEngagementNotificationManager {

    const val CHANNEL_WELCOME_ENGAGEMENT = "prism_welcome_and_nudges"
    const val CHANNEL_CUSTOM_PINNED = "prism_custom_pinned_hud"

    const val NOTIF_ID_WELCOME = 5101
    const val NOTIF_ID_ENGAGEMENT = 5102
    const val NOTIF_ID_CUSTOM_PIN = 5103

    const val ACTION_FIRE_ENGAGEMENT_NUDGE = "com.omnisolve.overlay.ACTION_FIRE_ENGAGEMENT_NUDGE"
    const val ACTION_PIN_COPY_TEXT = "com.omnisolve.overlay.ACTION_CUSTOM_PIN_COPY"
    const val ACTION_PIN_UNPIN = "com.omnisolve.overlay.ACTION_CUSTOM_PIN_UNPIN"
    const val EXTRA_PIN_COPY_TEXT = "EXTRA_PIN_COPY_TEXT"

    private const val PREF_FILE = "OmniSolvePrefs"
    private const val PREF_LAST_APP_OPEN_MS = "PREF_ENGAGEMENT_LAST_APP_OPEN_MS"
    private const val PREF_WELCOME_SENT = "PREF_WELCOME_NOTIFICATION_SENT"
    private const val PREF_AUTO_ENGAGEMENT_ENABLED = "PREF_AUTO_ENGAGEMENT_ENABLED"
    private const val PREF_LAST_NUDGE_INDEX = "PREF_LAST_ENGAGEMENT_IDX"
    private const val PREF_NEXT_NUDGE_AT_MS = "PREF_NEXT_ENGAGEMENT_AT_MS"

    // Custom Pinned Notification preference keys
    private const val PREF_PIN_TITLE = "PREF_CUSTOM_PIN_TITLE"
    private const val PREF_PIN_BODY = "PREF_CUSTOM_PIN_BODY"
    private const val PREF_PIN_TEMPLATE = "PREF_CUSTOM_PIN_TEMPLATE"
    private const val PREF_PIN_SHOW_COPY = "PREF_CUSTOM_PIN_SHOW_COPY"
    private const val PREF_PIN_SHOW_SOLVE = "PREF_CUSTOM_PIN_SHOW_SOLVE"
    private const val PREF_PIN_SHOW_STEALTH = "PREF_CUSTOM_PIN_SHOW_STEALTH"
    private const val PREF_PIN_DISGUISE = "PREF_CUSTOM_PIN_DISGUISE"
    private const val PREF_PIN_ACTIVE = "PREF_CUSTOM_PIN_ACTIVE"

    private data class NudgeMessage(
        val title: String,
        val body: String,
        val expandedBody: String
    )

    // Strictly internal curated pool of 28 playful, friendly notifications (not shown in app UI)
    private val HIDDEN_ENGAGEMENT_POOL: List<NudgeMessage> = listOf(
        NudgeMessage(
            title = "Okay, quick question — did you forget about me?",
            body = "Your floating pill is waiting whenever you're studying.",
            expandedBody = "Your screen has been way too quiet lately. Summon the floating Dynamic Island anytime and get answers in seconds."
        ),
        NudgeMessage(
            title = "Someone's looking extra sharp today...",
            body = "Bring your favorite quiz helper along for backup.",
            expandedBody = "Confidence feels better when Best Accuracy mode double-checks tough questions for you. Tap in whenever you're ready."
        ),
        NudgeMessage(
            title = "I saved your favorite spot at the top",
            body = "Prism Solver is ready whenever you are.",
            expandedBody = "Whether it's a late-night study sheet or a sudden quiz link, your floating pill snaps right into place in one tap."
        ),
        NudgeMessage(
            title = "Don't leave your study helper hanging",
            body = "Fast, clear answers are one tap away.",
            expandedBody = "Paste a Google Form link or open your quiz and tap the top pill whenever you need a quick check."
        ),
        NudgeMessage(
            title = "One tap wakes up your floating pill",
            body = "Right where you left it at the top of your screen.",
            expandedBody = "Subtle when you want privacy, fast when you want answers. Open Prism Solver anytime."
        ),
        NudgeMessage(
            title = "Still thinking about how fast we solved that last one",
            body = "Want to knock out another study session today?",
            expandedBody = "Fast Answer and Best Accuracy are ready whenever you need help on a tricky question."
        ),
        NudgeMessage(
            title = "You + Floating Pill = Stress-Free Study",
            body = "Your shortcut to quick answers is one tap away.",
            expandedBody = "Pin answers cleanly to your notification bar, disguise them as battery percentage, or expand explanations anytime."
        ),
        NudgeMessage(
            title = "Are you ghosting your smartest study partner?",
            body = "Even Charging 92% Disguise noticed you went quiet.",
            expandedBody = "Come say hi — we kept your pill look, transparency, and preferences saved just how you like them."
        ),
        NudgeMessage(
            title = "Wait... have you tried Google Forms Auto-Fill yet?",
            body = "Paste one quiz link to get every answer neatly pinned.",
            expandedBody = "Paste a Google Form link to get clean keys like (1-A|2-B|3-D|4-A) pinned in your status bar or auto-filled inside the form."
        ),
        NudgeMessage(
            title = "Playing hard to get, huh?",
            body = "I respect it — your study streak is ready when you are.",
            expandedBody = "One quick session keeps everything fresh. Open Prism Solver and start your floating helper."
        ),
        NudgeMessage(
            title = "Study smarter, finish earlier",
            body = "Switch to Best Accuracy whenever a question gets tricky.",
            expandedBody = "Why guess on tough questions when Best Accuracy can double-check the answer and explain why?"
        ),
        NudgeMessage(
            title = "Smooth move alert: Disguise Mode is ready",
            body = "Show 91%–94% charging instead of A, B, C, or D.",
            expandedBody = "Keep your screen looking clean and simple while still getting the exact option you need."
        ),
        NudgeMessage(
            title = "Just checking in on today's study goals",
            body = "Got a tough quiz or worksheet tonight? Let's clear it faster.",
            expandedBody = "Use Photo & Diagrams mode whenever a question has math equations, graphs, or charts."
        ),
        NudgeMessage(
            title = "Your floating pill looks even better in action",
            body = "Drag it to any corner of your screen while you work.",
            expandedBody = "Pick Small, Normal, or Large pill size and adjust transparency in Floating Pill Look anytime."
        ),
        NudgeMessage(
            title = "Honest confession: I love tough questions",
            body = "Put a real challenge on your screen and watch.",
            expandedBody = "Math equations, science MCQs, history, or matching lists — Prism Solver handles them all."
        ),
        NudgeMessage(
            title = "Two words: Effortless answers.",
            body = "Stop typing long questions into search bars. Tap the pill once.",
            expandedBody = "Reads the question directly off your screen so you can stay focused on your quiz."
        ),
        NudgeMessage(
            title = "Miss that quiet helper at the top of your screen?",
            body = "Always ready to turn confusion into a clear A, B, C, or D.",
            expandedBody = "Open Prism Solver now — your saved notes and study cards are right where you left them."
        ),
        NudgeMessage(
            title = "Let's make studying quick and simple",
            body = "Zero clutter, instant answers, and gentle pocket buzzes.",
            expandedBody = "Turn on pocket vibration buzzes (1 to 4 pulses) or pin quick notes right in your notification bar."
        ),
        NudgeMessage(
            title = "Careful — skipping practice makes your streak nervous",
            body = "Come test one quick question in 10 seconds.",
            expandedBody = "Keep your edge sharp with saved notes and easy export whenever you want to review."
        ),
        NudgeMessage(
            title = "You know where to find me when questions get hard",
            body = "Ready to explain any answer in plain steps.",
            expandedBody = "Fast Answer, Best Accuracy, and Photo & Diagrams are waiting whenever you need a hand."
        ),
        NudgeMessage(
            title = "Quick reminder: smart looks good on you",
            body = "Especially when you finish ahead of schedule.",
            expandedBody = "Tap to start your floating helper or pin your Google Form answer key to your status bar."
        ),
        NudgeMessage(
            title = "Got another quiz link today?",
            body = "Don't stress over it alone — open Prism Solver first.",
            expandedBody = "Solve whole Google Forms in seconds or get help right over your browser."
        ),
        NudgeMessage(
            title = "Patiently waiting while you take a break...",
            body = "Come back anytime — your helper is ready in one tap.",
            expandedBody = "Fast start, clean dark interface, and a customizable floating pill waiting for you."
        ),
        NudgeMessage(
            title = "Screenshots stay private on your phone",
            body = "Nothing recorded, nothing saved after showing your answer.",
            expandedBody = "We only read the question on your screen when you tap the pill."
        ),
        NudgeMessage(
            title = "Ready for tonight's study session?",
            body = "Pin your answer bar or tap the floating pill anytime.",
            expandedBody = "Open Notifications in Prism Solver to pin custom study notes or quick actions to your status bar."
        ),
        NudgeMessage(
            title = "A little help goes a long way",
            body = "Honestly, we make a pretty great study team.",
            expandedBody = "Switch anytime between Fast Answer, Best Accuracy, or Photo & Diagrams."
        ),
        NudgeMessage(
            title = "Too handy to ignore, too fast to replace",
            body = "Give your floating pill one quick spin today.",
            expandedBody = "Adjust battery & memory settings in Stats anytime to match your phone."
        ),
        NudgeMessage(
            title = "Before today wraps up — one small win?",
            body = "Open Prism Solver for 30 seconds and review your notes.",
            expandedBody = "Check your saved questions, pin a Google Form key, or start the floating pill."
        )
    )

    fun hiddenMessageCount(): Int = HIDDEN_ENGAGEMENT_POOL.size

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val welcomeChannel = NotificationChannel(
            CHANNEL_WELCOME_ENGAGEMENT,
            "Welcome & Study Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Welcome tip and friendly study reminders when you are away."
            enableLights(true)
            lightColor = AndroidColor.parseColor("#D71921")
        }

        val customPinChannel = NotificationChannel(
            CHANNEL_CUSTOM_PINNED,
            "Pinned Study Bar",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Keeps your study notes, Google Form keys, and quick controls pinned."
            setShowBadge(false)
        }

        nm.createNotificationChannel(welcomeChannel)
        nm.createNotificationChannel(customPinChannel)
    }

    // ── Welcome Notification (First Notification for New Users) ─────────────

    fun hasSentWelcomeNotification(context: Context): Boolean {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(PREF_WELCOME_SENT, false)
    }

    fun buildWelcomeNotification(context: Context): android.app.Notification {
        ensureChannels(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            510,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val detailText = buildString {
            appendLine("Welcome to Prism Solver — your instant quiz and study helper is ready.")
            appendLine()
            appendLine("What you can do right away:")
            appendLine("• Floating Dynamic Island: Tap once to read any question on your screen in seconds, or drag it anywhere you like.")
            appendLine("• Choose Your Style: Use Fast Answer for quick checks, Best Accuracy to double-check, or Photo & Diagrams for math and charts.")
            appendLine("• Google Forms Helper: Paste a form link to get answers like (1-A|2-B|3-D|4-A) pinned in your notification bar or auto-filled.")
            appendLine("• Disguise Mode: Show answers quietly as Charging 92% or gentle pocket vibration buzzes.")
        }.trimEnd()

        return NotificationCompat.Builder(context, CHANNEL_WELCOME_ENGAGEMENT)
            .setSmallIcon(R.drawable.ic_prism_orb)
            .setColor(AndroidColor.parseColor("#D71921"))
            .setContentTitle("Welcome to Solve Anything")
            .setContentText("Your floating pill, solving styles & Google Forms helper are ready.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("Welcome to Solve Anything • Ready to Help")
                    .bigText(detailText)
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent)
            .build()
    }

    fun sendWelcomeNotificationIfNew(context: Context): Boolean {
        if (hasSentWelcomeNotification(context)) return false
        return sendWelcomeNotificationNow(context)
    }

    fun sendWelcomeNotificationNow(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            nm.notify(NOTIF_ID_WELCOME, buildWelcomeNotification(context))
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_WELCOME_SENT, true)
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── Hidden 28-Message Weekly Engagement Scheduler (>= 2/week inactive) ─

    fun isAutoEngagementEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(PREF_AUTO_ENGAGEMENT_ENABLED, true)
    }

    fun setAutoEngagementEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_AUTO_ENGAGEMENT_ENABLED, enabled)
            .apply()
        if (enabled) {
            scheduleRandomWeeklyReengagement(context)
        } else {
            cancelScheduledReengagement(context)
        }
    }

    fun recordAppOpened(context: Context) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_LAST_APP_OPEN_MS, now)
            .apply()
        if (isAutoEngagementEnabled(context)) {
            scheduleRandomWeeklyReengagement(context)
        }
    }

    fun getLastAppOpenMs(context: Context): Long {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getLong(PREF_LAST_APP_OPEN_MS, System.currentTimeMillis())
    }

    fun getNextScheduledNudgeMs(context: Context): Long {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getLong(PREF_NEXT_NUDGE_AT_MS, 0L)
    }

    /**
     * Picks a randomized daytime slot 48h..78h ahead so an inactive user receives at least
     * 2 randomized engagement notifications per week without spam or predictability.
     */
    fun computeNextRandomizedTriggerMs(nowMs: Long = System.currentTimeMillis()): Long {
        val minHours = 48
        val maxHours = 78
        val randomHours = Random.nextInt(minHours, maxHours + 1)
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs + randomHours * 3_600_000L
            // Clamp into pleasant daytime/evening window (10:00..21:30)
            val hour = get(Calendar.HOUR_OF_DAY)
            if (hour < 10) {
                set(Calendar.HOUR_OF_DAY, Random.nextInt(10, 14))
            } else if (hour >= 22) {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, Random.nextInt(10, 14))
            }
            set(Calendar.MINUTE, Random.nextInt(5, 55))
            set(Calendar.SECOND, 0)
        }
        return cal.timeInMillis
    }

    fun scheduleRandomWeeklyReengagement(context: Context): Long {
        if (!isAutoEngagementEnabled(context)) return 0L
        val triggerAtMs = computeNextRandomizedTriggerMs()
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_NEXT_NUDGE_AT_MS, triggerAtMs)
            .apply()

        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return triggerAtMs
            val pi = createEngagementPendingIntent(context)
            am.cancel(pi)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
            }
        } catch (_: Exception) {
        }
        return triggerAtMs
    }

    fun cancelScheduledReengagement(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.cancel(createEngagementPendingIntent(context))
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_NEXT_NUDGE_AT_MS)
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun createEngagementPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PrismEngagementAlarmReceiver::class.java).apply {
            action = ACTION_FIRE_ENGAGEMENT_NUDGE
        }
        return PendingIntent.getBroadcast(
            context,
            5190,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun pickNextHiddenIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val lastIdx = prefs.getInt(PREF_LAST_NUDGE_INDEX, -1)
        val poolSize = HIDDEN_ENGAGEMENT_POOL.size
        if (poolSize <= 1) return 0
        var chosen = Random.nextInt(poolSize)
        if (chosen == lastIdx) {
            chosen = (chosen + 1 + Random.nextInt(poolSize - 1)) % poolSize
        }
        prefs.edit().putInt(PREF_LAST_NUDGE_INDEX, chosen).apply()
        return chosen
    }

    fun buildEngagementNotification(context: Context, index: Int): android.app.Notification {
        ensureChannels(context)
        val safeIdx = index.coerceIn(0, HIDDEN_ENGAGEMENT_POOL.lastIndex)
        val msg = HIDDEN_ENGAGEMENT_POOL[safeIdx]

        val openAppIntent = PendingIntent.getActivity(
            context,
            511 + safeIdx,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_WELCOME_ENGAGEMENT)
            .setSmallIcon(R.drawable.ic_prism_orb)
            .setColor(AndroidColor.parseColor("#D71921"))
            .setContentTitle(msg.title)
            .setContentText(msg.body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(msg.title)
                    .bigText(msg.expandedBody)
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent)
            .build()
    }

    /**
     * Fires 1 random notification from the hidden pool right now so the user can test delivery.
     */
    fun sendRandomEngagementTestNotification(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            val idx = pickNextHiddenIndex(context)
            nm.notify(NOTIF_ID_ENGAGEMENT, buildEngagementNotification(context, idx))
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun handleScheduledTrigger(context: Context) {
        if (!isAutoEngagementEnabled(context)) return
        val now = System.currentTimeMillis()
        val lastOpen = getLastAppOpenMs(context)
        // Fire only if user hasn't opened app for at least 36 hours (or if initial timestamp missing)
        if (now - lastOpen >= 36L * 3_600_000L && hasNotificationPermission(context)) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val idx = pickNextHiddenIndex(context)
                nm?.notify(NOTIF_ID_ENGAGEMENT, buildEngagementNotification(context, idx))
            } catch (_: Exception) {
            }
        }
        // Always schedule the following randomized weekly slot (~2-3 days later)
        scheduleRandomWeeklyReengagement(context)
    }

    // ── Custom Pinned Notification Bar Builder & State ──────────────────────

    fun loadCustomPinConfig(context: Context): CustomPinnedNotifConfig {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        return CustomPinnedNotifConfig(
            title = prefs.getString(PREF_PIN_TITLE, "Solve Anything • Study Pin")
                ?: "Solve Anything • Study Pin",
            body = prefs.getString(PREF_PIN_BODY, "(1-A|2-B|3-D|4-A)")
                ?: "(1-A|2-B|3-D|4-A)",
            templateId = prefs.getString(PREF_PIN_TEMPLATE, CustomPinnedNotifConfig.TEMPLATE_FORMS_KEY)
                ?: CustomPinnedNotifConfig.TEMPLATE_FORMS_KEY,
            showCopy = prefs.getBoolean(PREF_PIN_SHOW_COPY, true),
            showSolveNow = prefs.getBoolean(PREF_PIN_SHOW_SOLVE, true),
            showStealth = prefs.getBoolean(PREF_PIN_SHOW_STEALTH, false),
            stealthDisguise = prefs.getBoolean(PREF_PIN_DISGUISE, false),
            isPinned = prefs.getBoolean(PREF_PIN_ACTIVE, false)
        )
    }

    fun saveCustomPinConfig(context: Context, config: CustomPinnedNotifConfig) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_PIN_TITLE, config.title)
            .putString(PREF_PIN_BODY, config.body)
            .putString(PREF_PIN_TEMPLATE, config.templateId)
            .putBoolean(PREF_PIN_SHOW_COPY, config.showCopy)
            .putBoolean(PREF_PIN_SHOW_SOLVE, config.showSolveNow)
            .putBoolean(PREF_PIN_SHOW_STEALTH, config.showStealth)
            .putBoolean(PREF_PIN_DISGUISE, config.stealthDisguise)
            .putBoolean(PREF_PIN_ACTIVE, config.isPinned)
            .apply()
    }

    fun buildCustomPinnedNotification(
        context: Context,
        config: CustomPinnedNotifConfig
    ): android.app.Notification {
        ensureChannels(context)

        val openAppIntent = PendingIntent.getActivity(
            context,
            520,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanTitle = if (config.stealthDisguise) {
            "System Power • Balanced (92%)"
        } else {
            config.title.trim().ifBlank { "Solve Anything • Pinned Key" }.take(56)
        }
        val cleanBody = config.body.trim().ifBlank { "(1-A|2-B|3-D|4-A)" }

        val builder = NotificationCompat.Builder(context, CHANNEL_CUSTOM_PINNED)
            .setSmallIcon(R.drawable.ic_prism_orb)
            .setColor(
                if (config.stealthDisguise) AndroidColor.parseColor("#374151")
                else AndroidColor.parseColor("#D71921")
            )
            .setContentTitle(cleanTitle)
            .setContentText(cleanBody.take(80))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(cleanTitle)
                    .bigText(cleanBody)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(
                if (config.stealthDisguise) NotificationCompat.VISIBILITY_SECRET
                else NotificationCompat.VISIBILITY_PRIVATE
            )
            .setContentIntent(openAppIntent)

        if (config.showCopy) {
            val copyPi = PendingIntent.getBroadcast(
                context,
                521,
                Intent(context, PrismEngagementAlarmReceiver::class.java).apply {
                    action = ACTION_PIN_COPY_TEXT
                    putExtra(EXTRA_PIN_COPY_TEXT, cleanBody)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Copy", copyPi)
        }

        if (config.showSolveNow) {
            val solveIntent = Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_NOTIF_SOLVE_NOW
            }
            val solvePi = PendingIntent.getService(
                context,
                522,
                solveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Solve Now", solvePi)
        }

        if (config.showStealth) {
            val stealthIntent = Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_NOTIF_TOGGLE_STEALTH
            }
            val stealthPi = PendingIntent.getService(
                context,
                523,
                stealthIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Stealth", stealthPi)
        }

        val unpinPi = PendingIntent.getBroadcast(
            context,
            524,
            Intent(context, PrismEngagementAlarmReceiver::class.java).apply {
                action = ACTION_PIN_UNPIN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "Unpin", unpinPi)

        return builder.build()
    }

    fun pinCustomNotification(context: Context, config: CustomPinnedNotifConfig): Boolean {
        if (!hasNotificationPermission(context)) return false
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            val updated = config.copy(isPinned = true)
            nm.notify(NOTIF_ID_CUSTOM_PIN, buildCustomPinnedNotification(context, updated))
            saveCustomPinConfig(context, updated)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun unpinCustomNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIF_ID_CUSTOM_PIN)
            val current = loadCustomPinConfig(context)
            saveCustomPinConfig(context, current.copy(isPinned = false))
        } catch (_: Exception) {
        }
    }
}

class PrismEngagementAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (PrismEngagementNotificationManager.isAutoEngagementEnabled(context)) {
                    PrismEngagementNotificationManager.scheduleRandomWeeklyReengagement(context)
                }
                val pinCfg = PrismEngagementNotificationManager.loadCustomPinConfig(context)
                if (pinCfg.isPinned) {
                    PrismEngagementNotificationManager.pinCustomNotification(context, pinCfg)
                }
            }

            PrismEngagementNotificationManager.ACTION_FIRE_ENGAGEMENT_NUDGE -> {
                PrismEngagementNotificationManager.handleScheduledTrigger(context)
            }

            PrismEngagementNotificationManager.ACTION_PIN_COPY_TEXT -> {
                val text = intent.getStringExtra(PrismEngagementNotificationManager.EXTRA_PIN_COPY_TEXT).orEmpty()
                if (text.isNotBlank()) {
                    try {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Pinned Notification Text", text))
                        Toast.makeText(context, "Copied pinned note", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                    }
                }
            }

            PrismEngagementNotificationManager.ACTION_PIN_UNPIN -> {
                PrismEngagementNotificationManager.unpinCustomNotification(context)
                Toast.makeText(context, "Unpinned notification", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
