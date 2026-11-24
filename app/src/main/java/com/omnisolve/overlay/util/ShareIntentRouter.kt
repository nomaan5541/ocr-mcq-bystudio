package com.omnisolve.overlay.util

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Parses incoming Android Share-Sheet intents (`ACTION_SEND`) from WhatsApp, Telegram,
 * Chrome, Google Classroom, Gallery, or the Android Screenshot bar into strongly typed
 * Prism workflows so students never have to manually save files or copy links.
 */
sealed class SharedPayload {
    data class SharedPhoto(val uri: Uri, val caption: String = "") : SharedPayload()
    data class SharedPdf(val uri: Uri, val caption: String = "") : SharedPayload()
    data class SharedGoogleFormUrl(val url: String) : SharedPayload()
    data class SharedQuizWebUrl(val url: String) : SharedPayload()
    data class SharedLiveRoomCode(val roomCode: String) : SharedPayload()
    data class SharedQuestionText(val text: String) : SharedPayload()
}

object ShareIntentRouter {

    private const val EXTRA_CONSUMED = "com.omnisolve.overlay.EXTRA_PRISM_SHARE_CONSUMED"

    private val GOOGLE_FORMS_URL_REGEX = Regex(
        pattern = """https?://(?:docs\.google\.com/forms/[^\s<>"]+|forms\.gle/[^\s<>"]+)""",
        option = RegexOption.IGNORE_CASE
    )

    private val MICROSOFT_FORMS_URL_REGEX = Regex(
        pattern = """https?://(?:forms\.office\.com/[^\s<>"]+|forms\.cloud\.microsoft/[^\s<>"]+)""",
        option = RegexOption.IGNORE_CASE
    )

    private val GENERIC_HTTP_URL_REGEX = Regex(
        pattern = """https?://[^\s<>"]+""",
        option = RegexOption.IGNORE_CASE
    )

    private val PRISM_ROOM_DEEP_LINK_REGEX = Regex(
        pattern = """prism://room/([A-Za-z0-9\-]{6,7})""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Parses and consumes a single `ACTION_SEND` (Share Sheet) or `ACTION_VIEW` (direct tap
     * "Open with Prism" from WhatsApp / Telegram / Gmail) intent once so screen rotation or
     * activity relaunch never triggers duplicate solves.
     */
    fun parseAndConsume(intent: Intent?): SharedPayload? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_VIEW) return null
        if (intent.getBooleanExtra(EXTRA_CONSUMED, false)) return null

        val payload = parseReadOnly(intent)
        if (payload != null) {
            intent.putExtra(EXTRA_CONSUMED, true)
        }
        return payload
    }

    internal fun parseReadOnly(intent: Intent?): SharedPayload? {
        if (intent == null) return null

        // Direct link tap ("Open with Prism" on forms.gle, docs.google.com/forms, forms.office.com, or prism://room/...)
        if (intent.action == Intent.ACTION_VIEW) {
            val rawUrl = intent.dataString?.trim().orEmpty()
            if (rawUrl.isNotBlank()) {
                val roomMatch = PRISM_ROOM_DEEP_LINK_REGEX.find(rawUrl)?.groupValues?.getOrNull(1)
                    ?.uppercase()?.filter { it.isLetterOrDigit() }
                if (!roomMatch.isNullOrBlank() && roomMatch.length == 6) {
                    return SharedPayload.SharedLiveRoomCode(roomCode = roomMatch)
                }
                val googleFormMatched = GOOGLE_FORMS_URL_REGEX.find(rawUrl)?.value
                    ?.trimEnd('.', ',', ')', ']', ';', '!')
                if (!googleFormMatched.isNullOrBlank()) {
                    return SharedPayload.SharedGoogleFormUrl(url = googleFormMatched)
                }
                val msFormMatched = MICROSOFT_FORMS_URL_REGEX.find(rawUrl)?.value
                    ?.trimEnd('.', ',', ')', ']', ';', '!')
                if (!msFormMatched.isNullOrBlank()) {
                    return SharedPayload.SharedQuizWebUrl(url = msFormMatched)
                }
            }
            return null
        }

        if (intent.action != Intent.ACTION_SEND) return null

        val mime = intent.type?.lowercase()?.trim().orEmpty()
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val streamUri = extractStreamUri(intent)

        // 1. PDF shared from WhatsApp / Telegram / Drive / Classroom
        if (streamUri != null && (mime == "application/pdf" || isPdfUri(streamUri))) {
            val caption = extraText.ifBlank { extraSubject }
            return SharedPayload.SharedPdf(uri = streamUri, caption = caption)
        }

        // 2. Photo / Screenshot shared from WhatsApp / Telegram / Gallery / Screenshot bar
        if (streamUri != null && (mime.startsWith("image/") || isImageUri(streamUri))) {
            val caption = extraText.ifBlank { extraSubject }
            return SharedPayload.SharedPhoto(uri = streamUri, caption = caption)
        }

        // 3. Shared text containing a Live Room link, Google Form link, Microsoft Form / Quiz URL, or MCQ text
        val combinedText = listOf(extraSubject, extraText)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
        if (combinedText.isNotBlank()) {
            val roomDeep = PRISM_ROOM_DEEP_LINK_REGEX.find(combinedText)?.groupValues?.getOrNull(1)
                ?.uppercase()?.filter { it.isLetterOrDigit() }
            if (!roomDeep.isNullOrBlank() && roomDeep.length == 6) {
                return SharedPayload.SharedLiveRoomCode(roomCode = roomDeep)
            }
            val formUrlMatch = GOOGLE_FORMS_URL_REGEX.find(combinedText)?.value
                ?.trimEnd('.', ',', ')', ']', ';', '!')
            if (!formUrlMatch.isNullOrBlank()) {
                return SharedPayload.SharedGoogleFormUrl(url = formUrlMatch)
            }
            val msUrlMatch = MICROSOFT_FORMS_URL_REGEX.find(combinedText)?.value
                ?.trimEnd('.', ',', ')', ']', ';', '!')
            if (!msUrlMatch.isNullOrBlank()) {
                return SharedPayload.SharedQuizWebUrl(url = msUrlMatch)
            }
            val onlyUrlMatch = GENERIC_HTTP_URL_REGEX.find(combinedText)?.value
                ?.trimEnd('.', ',', ')', ']', ';', '!')
            if (!onlyUrlMatch.isNullOrBlank() && combinedText.length <= onlyUrlMatch.length + 40) {
                return SharedPayload.SharedQuizWebUrl(url = onlyUrlMatch)
            }
            return SharedPayload.SharedQuestionText(text = combinedText.take(4000))
        }

        return null
    }

    private fun extractStreamUri(intent: Intent): Uri? {
        val fromExtra = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
        } catch (_: Exception) {
            null
        }
        if (fromExtra != null) return fromExtra

        val clip = intent.clipData ?: return null
        if (clip.itemCount > 0) {
            return clip.getItemAt(0)?.uri
        }
        return null
    }

    private fun isPdfUri(uri: Uri): Boolean {
        val s = uri.toString().lowercase()
        return s.endsWith(".pdf") || s.contains(".pdf?")
    }

    private fun isImageUri(uri: Uri): Boolean {
        val s = uri.toString().lowercase()
        return s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg") ||
            s.endsWith(".webp") || s.endsWith(".heic")
    }
}
