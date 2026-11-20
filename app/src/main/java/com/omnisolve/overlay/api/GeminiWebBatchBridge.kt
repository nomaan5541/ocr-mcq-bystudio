package com.omnisolve.overlay.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.omnisolve.overlay.LoginActivity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class GeminiWebLoginRequiredException(message: String) : IllegalStateException(message)

/**
 * Ephemeral WebView bridge for Google Forms solving via the user's logged-in
 * `https://gemini.google.com/app` session ("Extract -> Send to Gemini AI -> Capture Response").
 *
 * Uses shared WebView cookies configured by [LoginActivity], injects the single-line batch
 * prompt cleanly into Angular's `<rich-textarea>`, waits for Gemini to finish streaming its
 * answer, captures the complete text, and destroys the offscreen WebView immediately on exit.
 */
object GeminiWebBatchBridge {

    private const val TAG = "GeminiWebBatchBridge"
    private const val GEMINI_URL = "https://gemini.google.com/app"

    @Volatile
    internal var bridgeOverrideForTest: (suspend (batchPrompt: String, expectedNumbers: Set<Int>) -> String)? = null

    fun hasGeminiWebSessionCookies(): Boolean {
        return try {
            val cookie = CookieManager.getInstance().getCookie("https://gemini.google.com")
            !cookie.isNullOrBlank() && (
                cookie.contains("SID") ||
                    cookie.contains("HSID") ||
                    cookie.contains("SSID") ||
                    cookie.contains("SAPISID") ||
                    cookie.contains("__Secure-1PSID")
                )
        } catch (_: Throwable) {
            false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun solveBatchViaGeminiWeb(
        context: Context,
        batchPrompt: String,
        expectedNumbers: Set<Int>,
        timeoutMs: Long = 26_000L
    ): String {
        bridgeOverrideForTest?.let { override ->
            return override(batchPrompt, expectedNumbers)
        }

        val singleLinePrompt = batchPrompt
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        require(singleLinePrompt.isNotBlank()) { "EMPTY_BATCH_PROMPT" }

        return withContext(Dispatchers.Main) {
            var webView: WebView? = null
            try {
                withTimeout(timeoutMs) {
                    var pageReady = false
                    val wv = WebView(context.applicationContext).apply {
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cm.setAcceptThirdPartyCookies(this, true)
                        }
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            userAgentString = LoginActivity.CHROME_USER_AGENT
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                pageReady = true
                            }
                        }
                        val wSpec = View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
                        val hSpec = View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY)
                        measure(wSpec, hSpec)
                        layout(0, 0, 900, 1400)
                        onResume()
                    }
                    webView = wv
                    wv.loadUrl(GEMINI_URL)

                    val inspectStateJs = """
                        (function() {
                            try {
                                function isVisible(el) {
                                    if (!el) return false;
                                    var st = window.getComputedStyle(el);
                                    if (st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity || '1') <= 0.05) return false;
                                    var r = el.getBoundingClientRect();
                                    return r.width > 0 && r.height > 0;
                                }
                                var editor = document.querySelector(
                                    'rich-textarea div[contenteditable="true"], div.ql-editor[contenteditable="true"], div[contenteditable="true"][role="textbox"], div[contenteditable="true"], textarea'
                                );
                                var signIn = document.querySelector(
                                    'a[href*="ServiceLogin"], a[aria-label*="Sign in" i], button[aria-label*="Sign in" i]'
                                );
                                var href = (window.location && window.location.href) ? window.location.href : '';
                                var onLoginPage = href.indexOf('accounts.google.com') !== -1 || (!editor && !!signIn);
                                var modelTurns = Array.from(document.querySelectorAll('model-response'));
                                if (modelTurns.length === 0) {
                                    modelTurns = Array.from(document.querySelectorAll('[data-message-author-role="model"], [data-test-id="model-turn"]'));
                                }
                                var stopVisible = Array.from(document.querySelectorAll('button[aria-label*="Stop" i], [data-test-id="stop-button"]')).some(isVisible);
                                return JSON.stringify({
                                    hasEditor: !!editor,
                                    needsLogin: onLoginPage,
                                    turnCount: modelTurns.length,
                                    generating: stopVisible
                                });
                            } catch (e) {
                                return JSON.stringify({ hasEditor: false, needsLogin: false, turnCount: 0, generating: false });
                            }
                        })();
                    """.trimIndent()

                    // 1. Wait for Gemini editor hydration or detect login wall immediately
                    var editorReady = false
                    for (tick in 1..45) {
                        if (!isActive) throw CancellationException()
                        delay(if (pageReady) 140L else 220L)
                        val st = evalJson(wv, inspectStateJs) ?: continue
                        if (st.optBoolean("needsLogin", false)) {
                            throw GeminiWebLoginRequiredException(
                                "Sign in to Gemini Web AI first (tap 'Connect Gemini') to use your gemini.google.com session."
                            )
                        }
                        if (st.optBoolean("hasEditor", false)) {
                            editorReady = true
                            break
                        }
                    }
                    if (!editorReady) {
                        throw GeminiWebLoginRequiredException(
                            "Gemini Web AI editor did not load. Sign in via 'Connect Gemini' or switch to Gemini 4 Argon."
                        )
                    }

                    // 2. Mark any pre-existing turns as old so we strictly read the new turn
                    val baselineJs = """
                        (function() {
                            var byTag = Array.from(document.querySelectorAll('model-response'));
                            var turns = byTag.length > 0
                                ? byTag
                                : Array.from(document.querySelectorAll('[data-message-author-role="model"], [data-test-id="model-turn"]'));
                            turns.forEach(function(el) { try { el.setAttribute('data-prism-old', '1'); } catch(_) {} });
                            return JSON.stringify({ baseline: turns.length });
                        })();
                    """.trimIndent()
                    val baselineObj = evalJson(wv, baselineJs)
                    val baselineTurns = baselineObj?.optInt("baseline", 0) ?: 0

                    // 3. Insert batch prompt and trigger Send
                    val quotedPrompt = JSONObject.quote(singleLinePrompt)
                    val insertJs = """
                        (function() {
                            try {
                                var prompt = $quotedPrompt;
                                var rt = document.querySelector('rich-textarea');
                                if (rt) { try { rt.click(); } catch(_) {} }
                                var input = document.querySelector('rich-textarea div[contenteditable="true"]') ||
                                            document.querySelector('div.ql-editor[contenteditable="true"]') ||
                                            document.querySelector('div[contenteditable="true"][role="textbox"]') ||
                                            document.querySelector('div[contenteditable="true"]') ||
                                            document.querySelector('textarea');
                                if (!input) return JSON.stringify({ ok: false, reason: 'no_input' });
                                input.focus();
                                if (input.tagName && (input.tagName.toLowerCase() === 'textarea' || input.tagName.toLowerCase() === 'input')) {
                                    var setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
                                    if (setter) setter.call(input, prompt); else input.value = prompt;
                                    input.dispatchEvent(new Event('input', { bubbles: true }));
                                } else {
                                    var inserted = false;
                                    try { inserted = document.execCommand('insertText', false, prompt); } catch(_) {}
                                    var cur = (input.innerText || input.textContent || '').trim();
                                    if (!inserted || !cur) {
                                        input.innerHTML = '';
                                        var p = document.createElement('p');
                                        p.textContent = prompt;
                                        input.appendChild(p);
                                        input.classList.remove('ql-blank');
                                    }
                                    try {
                                        input.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: 'insertText', data: prompt }));
                                    } catch(_) {
                                        input.dispatchEvent(new Event('input', { bubbles: true }));
                                    }
                                    input.dispatchEvent(new Event('change', { bubbles: true }));
                                }
                                var len = (input.innerText || input.textContent || input.value || '').trim().length;
                                return JSON.stringify({ ok: len > 0, length: len });
                            } catch (e) {
                                return JSON.stringify({ ok: false, reason: e.message });
                            }
                        })();
                    """.trimIndent()
                    val insRes = evalJson(wv, insertJs)
                    if (insRes?.optBoolean("ok", false) != true) {
                        throw IllegalStateException("Could not insert questions into Gemini Web editor.")
                    }

                    val sendJs = """
                        (function() {
                            try {
                                function isVis(el) {
                                    if (!el) return false;
                                    var st = window.getComputedStyle(el);
                                    if (st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity || '1') <= 0.05) return false;
                                    var r = el.getBoundingClientRect();
                                    return r.width > 0 && r.height > 0;
                                }
                                var list = [];
                                var iconBtn = document.querySelector('mat-icon[fonticon="send"], mat-icon[data-mat-icon-name="send"]');
                                if (iconBtn && iconBtn.closest('button')) list.push(iconBtn.closest('button'));
                                document.querySelectorAll('button[aria-label*="Send" i], button[data-test-id="send-button"], button.send-button').forEach(function(b) { list.push(b); });
                                for (var i = 0; i < list.length; i++) {
                                    var b = list[i];
                                    if (!b || !isVis(b)) continue;
                                    var lbl = (b.getAttribute('aria-label') || '').toLowerCase();
                                    if (lbl.indexOf('stop') !== -1 || lbl.indexOf('mic') !== -1) continue;
                                    b.removeAttribute('disabled');
                                    b.setAttribute('aria-disabled', 'false');
                                    b.click();
                                    return JSON.stringify({ sent: true });
                                }
                                return JSON.stringify({ sent: false });
                            } catch (e) {
                                return JSON.stringify({ sent: false });
                            }
                        })();
                    """.trimIndent()

                    var sentOk = false
                    for (attempt in 1..6) {
                        delay(60L)
                        if (evalJson(wv, sendJs)?.optBoolean("sent", false) == true) {
                            sentOk = true
                            break
                        }
                    }
                    if (!sentOk) {
                        val enterJs = """
                            (function() {
                                var input = document.querySelector('rich-textarea div[contenteditable="true"], div.ql-editor, div[contenteditable="true"], textarea');
                                if (!input) return JSON.stringify({ sent: false });
                                input.focus();
                                input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true }));
                                return JSON.stringify({ sent: true });
                            })();
                        """.trimIndent()
                        evalJson(wv, enterJs)
                    }

                    // 4. Poll fresh response until Gemini completes full batch output
                    val readTurnJs = """
                        (function() {
                            try {
                                function isVis(el) {
                                    if (!el) return false;
                                    var st = window.getComputedStyle(el);
                                    if (st.visibility === 'hidden' || st.display === 'none' || parseFloat(st.opacity || '1') <= 0.05) return false;
                                    var r = el.getBoundingClientRect();
                                    return r.width > 0 && r.height > 0;
                                }
                                var byTag = Array.from(document.querySelectorAll('model-response'));
                                var turns = byTag.length > 0
                                    ? byTag
                                    : Array.from(document.querySelectorAll('[data-message-author-role="model"], [data-test-id="model-turn"]'));
                                var fresh = turns.filter(function(el) { return el.getAttribute('data-prism-old') !== '1'; });
                                var stopVisible = Array.from(document.querySelectorAll('button[aria-label*="Stop" i], [data-test-id="stop-button"]')).some(isVis);
                                if (fresh.length === 0 && turns.length <= $baselineTurns) {
                                    return JSON.stringify({ hasTurn: false, cleanText: '', generating: stopVisible });
                                }
                                var latest = fresh.length > 0 ? fresh[fresh.length - 1] : turns[turns.length - 1];
                                var root = latest.querySelector('.markdown-main-panel, .markdown, message-content, .model-response-text') || latest;
                                var clone = root.cloneNode(true);
                                clone.querySelectorAll('model-thoughts, .thoughts-container, thinking-process, button, mat-icon, svg, script, style').forEach(function(n) { n.remove(); });
                                var cleanText = (clone.innerText || clone.textContent || '')
                                    .replace(/gemini[^\n]*is\s+working\.{0,3}/gi, ' ')
                                    .trim();
                                return JSON.stringify({ hasTurn: true, cleanText: cleanText, generating: stopVisible });
                            } catch (e) {
                                return JSON.stringify({ hasTurn: false, cleanText: '', generating: false });
                            }
                        })();
                    """.trimIndent()

                    var lastText = ""
                    var stableCount = 0
                    for (tick in 1..120) {
                        if (!isActive) throw CancellationException()
                        delay(140L)
                        val turnObj = evalJson(wv, readTurnJs) ?: continue
                        val hasTurn = turnObj.optBoolean("hasTurn", false)
                        val cleanText = turnObj.optString("cleanText", "").trim()
                        val generating = turnObj.optBoolean("generating", false)

                        if (hasTurn && cleanText.isNotEmpty()) {
                            if (cleanText == lastText) {
                                stableCount++
                            } else {
                                lastText = cleanText
                                stableCount = 0
                            }
                            val parsedSoFar = GoogleFormsSolverEngine.parseBatchAnswers(cleanText, expectedNumbers)
                            val allParsed = parsedSoFar.size >= expectedNumbers.size
                            if ((allParsed && !generating) || (!generating && stableCount >= 3)) {
                                Log.d(TAG, "Captured Gemini Web AI batch response (${parsedSoFar.size}/${expectedNumbers.size} parsed)")
                                return@withTimeout cleanText
                            }
                        }
                    }

                    if (lastText.isNotBlank() &&
                        GoogleFormsSolverEngine.parseBatchAnswers(lastText, expectedNumbers).isNotEmpty()
                    ) {
                        return@withTimeout lastText
                    }
                    throw IllegalStateException("Gemini Web AI did not finish generating answers in time.")
                }
            } finally {
                try {
                    CookieManager.getInstance().flush()
                    webView?.stopLoading()
                    webView?.onPause()
                    webView?.destroy()
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun evalJson(webView: WebView, script: String): JSONObject? {
        return suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(script) { raw ->
                if (!cont.isActive) return@evaluateJavascript
                val parsed = try {
                    val unquoted = GoogleFormsSolverEngine.unquoteEvaluateJavascriptJson(raw.orEmpty())
                    if (unquoted.isBlank() || unquoted == "null") null else JSONObject(unquoted)
                } catch (_: Exception) {
                    null
                }
                cont.resume(parsed)
            }
        }
    }
}
