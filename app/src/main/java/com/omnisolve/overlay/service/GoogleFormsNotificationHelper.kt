package com.omnisolve.overlay.service

import android.Manifest
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
import com.omnisolve.overlay.api.GoogleFormSolvedItem

/**
 * Pins solved Google Forms answers as an ongoing notification in the Android notification bar
 * in the exact compact format `(1-A|2-B|3-D|4-A)`, with 1-tap Copy & Unpin notification actions.
 */
object GoogleFormsNotificationHelper {

    const val CHANNEL_ID = "prism_google_forms_pinned_answers"
    const val NOTIFICATION_ID = 4401

    const val ACTION_COPY_PINNED_ANSWERS = "com.omnisolve.overlay.ACTION_COPY_PINNED_FORMS_ANSWERS"
    const val ACTION_UNPIN_FORMS_NOTIFICATION = "com.omnisolve.overlay.ACTION_UNPIN_FORMS_NOTIFICATION"
    const val EXTRA_COMPACT_ANSWERS = "EXTRA_COMPACT_ANSWERS"

    private const val PREF_IS_PINNED = "PREF_FORMS_NOTIF_PINNED"

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationPinned(context: Context): Boolean {
        val prefs = context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_IS_PINNED, false)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pinned Google Forms Answers",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Keeps solved Google Form answers like (1-A|2-B|3-D|4-A) pinned in the notification bar."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun buildPinnedNotification(
        context: Context,
        formTitle: String,
        compactOutput: String,
        items: List<GoogleFormSolvedItem>
    ): android.app.Notification {
        ensureChannel(context)

        val openAppIntent = PendingIntent.getActivity(
            context,
            401,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            402,
            Intent(context, FormsNotificationActionReceiver::class.java).apply {
                action = ACTION_COPY_PINNED_ANSWERS
                putExtra(EXTRA_COMPACT_ANSWERS, compactOutput)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val unpinPendingIntent = PendingIntent.getBroadcast(
            context,
            403,
            Intent(context, FormsNotificationActionReceiver::class.java).apply {
                action = ACTION_UNPIN_FORMS_NOTIFICATION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val expandedDetails = buildString {
            appendLine(compactOutput)
            if (items.isNotEmpty()) {
                appendLine()
                items.forEach { item ->
                    val shortOpt = item.shortAnswer.take(48)
                    if (shortOpt.isNotBlank()) {
                        appendLine("${item.number}-${item.answerLetter}: $shortOpt")
                    } else {
                        appendLine("${item.number}-${item.answerLetter}: ${item.questionText.take(44)}")
                    }
                }
            }
        }.trimEnd()

        val cleanTitle = formTitle.ifBlank { "Google Form Answers" }.take(46)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_prism_orb)
            .setColor(AndroidColor.parseColor("#D71921"))
            .setContentTitle("$cleanTitle (${items.size} Qs)")
            .setContentText(compactOutput)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("$cleanTitle • Pinned Answers")
                    .bigText(expandedDetails)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openAppIntent)
            .addAction(0, "Copy", copyPendingIntent)
            .addAction(0, "Unpin", unpinPendingIntent)
            .build()
    }

    fun pinAnswersNotification(
        context: Context,
        formTitle: String,
        compactOutput: String,
        items: List<GoogleFormSolvedItem>
    ): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            val notification = buildPinnedNotification(
                context = context,
                formTitle = formTitle,
                compactOutput = compactOutput,
                items = items
            )
            nm.notify(NOTIFICATION_ID, notification)
            context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_IS_PINNED, true)
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pins a solved Photo / PDF / Paper Scan answer key to Android's notification shade
     * so students can glance at answers while viewing another app without switching screens.
     */
    fun pinFreeformAnswerNotification(
        context: Context,
        title: String,
        answerText: String
    ): Boolean {
        val cleaned = answerText.replace("**", "").trim()
        if (cleaned.isBlank()) return false
        return try {
            ensureChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            val openAppIntent = PendingIntent.getActivity(
                context,
                404,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val copyPendingIntent = PendingIntent.getBroadcast(
                context,
                405,
                Intent(context, FormsNotificationActionReceiver::class.java).apply {
                    action = ACTION_COPY_PINNED_ANSWERS
                    putExtra(EXTRA_COMPACT_ANSWERS, cleaned.take(1200))
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val unpinPendingIntent = PendingIntent.getBroadcast(
                context,
                406,
                Intent(context, FormsNotificationActionReceiver::class.java).apply {
                    action = ACTION_UNPIN_FORMS_NOTIFICATION
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val firstLine = cleaned.lineSequence().firstOrNull { it.isNotBlank() }?.take(90) ?: cleaned.take(90)
            val cleanTitle = title.ifBlank { "Prism Pinned Answer Key" }.take(46)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_prism_orb)
                .setColor(AndroidColor.parseColor("#D71921"))
                .setContentTitle(cleanTitle)
                .setContentText(firstLine)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle("$cleanTitle • Pinned")
                        .bigText(cleaned.take(1800))
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(openAppIntent)
                .addAction(0, "Copy", copyPendingIntent)
                .addAction(0, "Unpin", unpinPendingIntent)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
            context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_IS_PINNED, true)
                .apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun unpinAnswersNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
            context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_IS_PINNED, false)
                .apply()
        } catch (_: Exception) {
        }
    }
}

class FormsNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            GoogleFormsNotificationHelper.ACTION_COPY_PINNED_ANSWERS -> {
                val text = intent.getStringExtra(GoogleFormsNotificationHelper.EXTRA_COMPACT_ANSWERS).orEmpty()
                if (text.isNotBlank()) {
                    try {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("Google Form Answers", text))
                        Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                    }
                }
            }

            GoogleFormsNotificationHelper.ACTION_UNPIN_FORMS_NOTIFICATION -> {
                GoogleFormsNotificationHelper.unpinAnswersNotification(context)
                Toast.makeText(context, "Unpinned Google Form answers", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
