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
    val title: String = "Prism Solver • Study Pin",
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
 * 1. Curated Welcome notifications for new Prism Solver users
 * 2. Hidden pool of 26 optional Hinglish study reminders, comeback nudges, and Prism tips (>= 2/week when inactive)
 * 3. Dedicated milestone, exam countdown, and solve-completion notification templates
 * 4. Customizable ongoing Pinned Notification Bar controller with Copy/Solve/Stealth/Unpin actions
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

    data class CuratedNotificationCopy(
        val title: String,
        val body: String,
        val expandedBody: String = body
    )

    // Curated Welcome variants (1-3)
    val WELCOME_TEMPLATES: List<CuratedNotificationCopy> = listOf(
        CuratedNotificationCopy(
            title = "Welcome to Prism ✨",
            body = "MCQ ho ya tricky sawaal, Prism saath hai. Aaj ek question crack karke dekhein? 😉"
        ),
        CuratedNotificationCopy(
            title = "Ab padhai ka scene set",
            body = "Question aap lao, Prism help karega. Hero wali entry toh aapki hi hai 😉"
        ),
        CuratedNotificationCopy(
            title = "Prism mein aapka welcome hai",
            body = "Ek question se shuru karein? Dekhte hain aaj kitni jaldi solve hota hai."
        )
    )

    // Curated Progress & Streak milestone templates (16-21), used only when the milestone is earned
    val PROGRESS_AND_STREAK_TEMPLATES: List<CuratedNotificationCopy> = listOf(
        CuratedNotificationCopy(
            title = "3 din ka streak 🔥",
            body = "Wah! Routine ban raha hai. Kal bhi ek quick round?"
        ),
        CuratedNotificationCopy(
            title = "7 din, solid scene!",
            body = "Aapki consistency full hero mode mein hai 😉"
        ),
        CuratedNotificationCopy(
            title = "10 questions done ✅",
            body = "Dheere-dheere progress ban rahi hai. Aaj ka round bhi complete!"
        ),
        CuratedNotificationCopy(
            title = "Personal best! 🏆",
            body = "Aapne apna best score beat kiya. Kya baat hai, topper ji."
        ),
        CuratedNotificationCopy(
            title = "Aaj ka goal complete",
            body = "Shabaash! Ab bina guilt ke break banta hai ☕"
        ),
        CuratedNotificationCopy(
            title = "Accuracy mein improvement 📈",
            body = "Aapki practice rang la rahi hai. Aise hi chalte rahiye!"
        )
    )

    // Curated Solve Completion templates (39-40)
    val SOLVE_COMPLETION_TEMPLATES: List<CuratedNotificationCopy> = listOf(
        CuratedNotificationCopy(
            title = "Aapka answer ready hai ✅",
            body = "Prism ne solve complete kar diya. App kholkar dekh lijiye."
        ),
        CuratedNotificationCopy(
            title = "Solve ho gaya",
            body = "Aapka question ready hai. Prism mein result check kar lijiye."
        )
    )

    fun formatExamCountdownCopy(daysLeft: Int): CuratedNotificationCopy {
        val safeDays = daysLeft.coerceAtLeast(1)
        return if (safeDays <= 3) {
            CuratedNotificationCopy(
                title = "Bas thoda sa aur 💪",
                body = "$safeDays din baaki hain. Aaj ka ek round bhi count karta hai."
            )
        } else {
            CuratedNotificationCopy(
                title = "Exam tak $safeDays din baaki",
                body = "Aaj ka chhota round, kal ka confidence. 5 questions kar lein?"
            )
        }
    }

    fun formatDailyGoalReminderCopy(targetQuestions: Int): CuratedNotificationCopy {
        val safeTarget = targetQuestions.coerceAtLeast(1)
        return CuratedNotificationCopy(
            title = "Aaj ka target yaad hai?",
            body = "Aapne $safeTarget questions ka goal set kiya tha. Jab ready hon, Prism khol lena."
        )
    }

    // Curated internal pool of 26 optional Hinglish study reminders, comeback nudges, tips, and light prompts
    private val HIDDEN_ENGAGEMENT_POOL: List<NudgeMessage> = listOf(
        // Study reminders (4-9)
        NudgeMessage(
            title = "Chai se pehle ek MCQ? ☕",
            body = "Bas ek quick round, chai bhi garam, dimaag bhi warm-up.",
            expandedBody = "Bas ek quick round, chai bhi garam, dimaag bhi warm-up. Jab ready hon, Prism kholkar ek sawaal check kar lein."
        ),
        NudgeMessage(
            title = "Aaj ka ek sawaal ho jaaye?",
            body = "2 minute nikaal lo, topper ji. Prism ready hai 😉",
            expandedBody = "2 minute nikaal lo, topper ji. Prism ready hai 😉 Ek quick MCQ se aaj ka revision shuru karein."
        ),
        NudgeMessage(
            title = "Break mila?",
            body = "Ek quick question kar lein? Phir araam se break continue.",
            expandedBody = "Ek quick question kar lein? Phir araam se break continue. Bas floating pill tap karke check kar sakte hain."
        ),
        NudgeMessage(
            title = "Padhai ka chhota sa date? 😉",
            body = "Aap, Prism aur ek MCQ. Aaj ka plan bura nahi hai.",
            expandedBody = "Aap, Prism aur ek MCQ. Aaj ka plan bura nahi hai. Jab free hon tab ek quick round kar lein."
        ),
        NudgeMessage(
            title = "Ek question pending hai",
            body = "Jab free hon, Prism kholkar ek quick round kar lena.",
            expandedBody = "Jab free hon, Prism kholkar ek quick round kar lena. Aapke saved notes aur settings wahin ready hain."
        ),
        NudgeMessage(
            title = "Thoda practice, thoda confidence",
            body = "Aaj bas ek MCQ. Baaki kal ka scene kal dekhenge.",
            expandedBody = "Aaj bas ek MCQ. Baaki kal ka scene kal dekhenge. Chhota step bhi kaafi hota hai."
        ),
        // Comeback notifications (10-15)
        NudgeMessage(
            title = "Arre, comeback karein?",
            body = "Koi pressure nahi. Ek easy question se phir shuru kar sakte hain.",
            expandedBody = "Koi pressure nahi. Ek easy question se phir shuru kar sakte hain. Apne pace par aage badhein."
        ),
        NudgeMessage(
            title = "Prism yahin hai ✨",
            body = "Jab mann ho, ek quick MCQ se restart kar lena.",
            expandedBody = "Jab mann ho, ek quick MCQ se restart kar lena. Floating pill aur Google Forms helper ready hain."
        ),
        NudgeMessage(
            title = "Kaafi din hue!",
            body = "Perfect streak ki zaroorat nahi. Aaj bas ek sawaal kar lein?",
            expandedBody = "Perfect streak ki zaroorat nahi. Aaj bas ek sawaal kar lein? Simple start sabse best hota hai."
        ),
        NudgeMessage(
            title = "Ek chhoti si comeback entry? 🎬",
            body = "Prism kholo, ek question try karo. Dheere-dheere rhythm aa jayegi.",
            expandedBody = "Prism kholo, ek question try karo. Dheere-dheere rhythm aa jayegi."
        ),
        NudgeMessage(
            title = "Aaj se phir shuru?",
            body = "Kal ka hisaab chhodo. Aaj ka ek MCQ kaafi hai.",
            expandedBody = "Kal ka hisaab chhodo. Aaj ka ek MCQ kaafi hai. Bas app kholkar ek question try kar lein."
        ),
        NudgeMessage(
            title = "No tension, boss",
            body = "Gap aa gaya toh kya hua? Ek question se wapas aate hain.",
            expandedBody = "Gap aa gaya toh kya hua? Ek question se wapas aate hain. Aaj bas ek quick check."
        ),
        // Exam revision reminders (23-25)
        NudgeMessage(
            title = "Aaj ka revision scene?",
            body = "Exam paas aa raha hai, ek quick MCQ round helpful rahega.",
            expandedBody = "Exam paas aa raha hai, ek quick MCQ round helpful rahega. Saved questions aur explanations bhi review kar sakte hain."
        ),
        NudgeMessage(
            title = "Revision ka mini-round",
            body = "5 minute ka practice session? Chhota step, badi tasalli 😉",
            expandedBody = "5 minute ka practice session? Chhota step, badi tasalli 😉"
        ),
        NudgeMessage(
            title = "Exam week aa gaya",
            body = "Aaj ek focused round kar lo, phir araam se next topic.",
            expandedBody = "Aaj ek focused round kar lo, phir araam se next topic. Math ya diagrams ke liye Photo & Diagrams mode ready hai."
        ),
        // Prism tips (28-32)
        NudgeMessage(
            title = "Screenshot tip 💡",
            body = "Question aur saare options frame mein hon, toh Prism ko solve karna aasaan hota hai.",
            expandedBody = "Question aur saare options frame mein hon, toh Prism ko solve karna aasaan hota hai."
        ),
        NudgeMessage(
            title = "Photo thodi blur hai?",
            body = "Ek clear screenshot try karein, answer dhoondhna phir zyada smooth hoga.",
            expandedBody = "Ek clear screenshot try karein, answer dhoondhna phir zyada smooth hoga."
        ),
        NudgeMessage(
            title = "Options bhi zaroor dikhayein",
            body = "Question ke saath A, B, C, D frame mein rakhein. Prism ko full scene samajh aayega.",
            expandedBody = "Question ke saath A, B, C, D frame mein rakhein. Prism ko full scene samajh aayega."
        ),
        NudgeMessage(
            title = "NOT ya EXCEPT dikha?",
            body = "Aise words par ek baar extra nazar daal lena. Chhoti detail, bada difference 😉",
            expandedBody = "Aise words par ek baar extra nazar daal lena. Chhoti detail, bada difference 😉"
        ),
        NudgeMessage(
            title = "Ek option atak gaya?",
            body = "Question dobara padhkar options compare karein, kabhi clue wahi chhupa hota hai.",
            expandedBody = "Question dobara padhkar options compare karein, kabhi clue wahi chhupa hota hai."
        ),
        // Light and playful notifications (33-38)
        NudgeMessage(
            title = "MCQ ne attitude dikhaya? 😏",
            body = "Prism kholo, milkar uska scene set karte hain.",
            expandedBody = "Prism kholo, milkar uska scene set karte hain. Floating pill se ek tap mein answer aur explanation dekhein."
        ),
        NudgeMessage(
            title = "Question tough, aap tougher",
            body = "Ek aur try karein? Is baar answer pakka paas hoga.",
            expandedBody = "Ek aur try karein? Is baar answer pakka paas hoga. Tricky sawaal ke liye Best Accuracy mode use kar sakte hain."
        ),
        NudgeMessage(
            title = "Aaj dimaag ki hero entry",
            body = "Ek quick question se shuru karein. Baaki story baad mein 😉",
            expandedBody = "Ek quick question se shuru karein. Baaki story baad mein 😉"
        ),
        NudgeMessage(
            title = "Padhai ka plot twist? 🎬",
            body = "Naya MCQ try karein, dekhte hain ending kya hoti hai.",
            expandedBody = "Naya MCQ try karein, dekhte hain ending kya hoti hai."
        ),
        NudgeMessage(
            title = "Aap aur MCQ, interesting jodi 😉",
            body = "Ek quick round karke dekhein? Prism side mein ready hai.",
            expandedBody = "Ek quick round karke dekhein? Prism side mein ready hai."
        ),
        NudgeMessage(
            title = "Chalo, ek sawaal nipta dein",
            body = "Phir aapki marzi: break, chai, ya ek aur round.",
            expandedBody = "Phir aapki marzi: break, chai, ya ek aur round."
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

        val welcome = WELCOME_TEMPLATES.first()
        val detailText = buildString {
            appendLine(welcome.body)
            appendLine()
            appendLine("Quick ways to use Prism:")
            appendLine("• Floating Dynamic Island: Tap once to read a question on your screen, or drag it where you like.")
            appendLine("• Solving Styles: Pick Fast Answer, Best Accuracy, or Photo & Diagrams for math and charts.")
            appendLine("• Google Forms Helper: Paste a form link to solve questions together or pin your answer key.")
            appendLine("• Quiet Display: Show answers as Charging 92% or gentle pocket vibration pulses.")
        }.trimEnd()

        return NotificationCompat.Builder(context, CHANNEL_WELCOME_ENGAGEMENT)
            .setSmallIcon(R.drawable.ic_prism_orb)
            .setColor(AndroidColor.parseColor("#D71921"))
            .setContentTitle(welcome.title)
            .setContentText(welcome.body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("Welcome to Prism")
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
            title = prefs.getString(PREF_PIN_TITLE, "Prism Solver • Study Pin")
                ?: "Prism Solver • Study Pin",
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
            config.title.trim().ifBlank { "Prism Solver • Pinned Key" }.take(56)
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
