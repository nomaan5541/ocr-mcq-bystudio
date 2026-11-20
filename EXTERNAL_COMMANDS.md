# External Setup & Deployment Playbook (`EXTERNAL_COMMANDS.md`)

Everything inside the Android app is already wired and tested. This file lists **every external action** outside this codebase that you run in **Cloudflare**, **Firebase**, **Google Play Console**, **Google AdMob**, and **AI Studio Secrets** so the complete application runs at full speed, zero downtime, and full Play Store compliance.

> **App Identity Reference**
> - **Package / Application ID:** `com.aistudio.ocrmcqsolver.vrsbsx`
> - **Version:** `3.5.2` (`versionCode = 6`)
> - **Cloudflare Worker URL:** `https://mcq-mate.nomaankhannfs2.workers.dev`
> - **Firestore Database ID:** `ai-studio-android-prismocr-23b5f3d4-3cc4-4f3d-beff-cb20ca94899a`
> - **AdMob App ID:** `ca-app-pub-6628813367564084~2373198575`
> - **AdMob Release Banner Unit ID:** `ca-app-pub-6628813367564084/4121394511`

---

## 1. Cloudflare Workers AI + Self-Healing KV Edge Cache (Required for Fast Free Lane)

Your Android app calls `https://mcq-mate.nomaankhannfs2.workers.dev` with:
- Normal solve: `POST {"extractedText": "..."}`
- Wrong-answer re-solve (Self-Healing Cache): `POST {"extractedText": "...", "forceFresh": true}`

Upgrading your Worker gives you:
1. **Shared KV Edge Cache (`ANSWERS_KV`)** — repeat questions return in **<40ms with 0 AI Neurons used**.
2. **Self-Healing `forceFresh: true`** — whenever a user taps **Re-Solve** or re-scans a wrong answer, the Worker bypasses KV, re-solves cleanly, and overwrites the poisoned entry permanently.
3. **Ultra-Fast Sub-1.5s Model Cascade** — puts `@cf/meta/llama-3.1-8b-instruct-fast` (0.5s–1.2s replies) FIRST instead of a slow 26B model, with `@cf/meta/llama-3.1-8b-instruct` and `@cf/qwen/qwen2.5-7b-instruct-fp8-fast` as instant backups.
4. **Built-in Per-IP Rate Guard** — protects your free daily 10,000 Neurons from abuse/bots.

### Step 1.1 — Install Wrangler & Login (Run on your local PC)

```bash
npm install -g wrangler
wrangler login
```

### Step 1.2 — Create Project Folder & KV Namespace

```bash
mkdir mcq-mate-worker && cd mcq-mate-worker
npm init -y
wrangler kv namespace create ANSWERS_KV
```

Copy the `id = "..."` printed by Cloudflare and paste it into `wrangler.toml` below.

### Step 1.3 — Create `wrangler.toml`

```toml
name = "mcq-mate"
main = "src/index.js"
compatibility_date = "2026-01-01"

[ai]
binding = "AI"

[[kv_namespaces]]
binding = "ANSWERS_KV"
id = "REPLACE_WITH_YOUR_KV_NAMESPACE_ID"
```

### Step 1.4 — Create `src/index.js`

```javascript
const MODEL_CASCADE = [
  "@cf/meta/llama-3.1-8b-instruct-fast",
  "@cf/meta/llama-3.1-8b-instruct",
  "@cf/qwen/qwen2.5-7b-instruct-fp8-fast"
];

const CACHE_TTL_SECONDS = 60 * 60 * 24 * 30; // 30 days
const RATE_LIMIT_WINDOW_SEC = 60;
const RATE_LIMIT_MAX_REQ = 45;

async function sha256Hex(text) {
  const normalized = text
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
  const data = new TextEncoder().encode("mcq_v2|" + normalized);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function extractJsonObject(rawText) {
  if (!rawText || typeof rawText !== "string") return null;
  const cleaned = rawText
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/i, "")
    .trim();
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  if (start === -1 || end <= start) return null;
  try {
    const parsed = JSON.parse(cleaned.slice(start, end + 1));
    const choice = String(parsed.correctChoice || "").trim();
    if (!choice || choice === "?" || choice.length > 40) return null;
    return {
      correctChoice: choice,
      shortAnswer: String(parsed.shortAnswer || `Option ${choice}`).trim().slice(0, 120),
      explanation: String(parsed.explanation || "").trim().slice(0, 380)
    };
  } catch {
    return null;
  }
}

function jsonResponse(body, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      ...extraHeaders
    }
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type"
        }
      });
    }

    if (request.method === "GET") {
      return jsonResponse({
        status: "ok",
        service: "mcq-mate-edge",
        models: MODEL_CASCADE,
        kvEnabled: Boolean(env.ANSWERS_KV)
      });
    }

    if (request.method !== "POST") {
      return jsonResponse({ error: "Method not allowed" }, 405);
    }

    // Lightweight per-IP rate limit via KV (if bound)
    const clientIp = request.headers.get("CF-Connecting-IP") || "unknown";
    if (env.ANSWERS_KV && clientIp !== "unknown") {
      const minuteBucket = Math.floor(Date.now() / (RATE_LIMIT_WINDOW_SEC * 1000));
      const rlKey = `rl:${clientIp}:${minuteBucket}`;
      const currentCount = parseInt((await env.ANSWERS_KV.get(rlKey)) || "0", 10);
      if (currentCount >= RATE_LIMIT_MAX_REQ) {
        return jsonResponse({ error: "Rate limit exceeded" }, 429);
      }
      await env.ANSWERS_KV.put(rlKey, String(currentCount + 1), {
        expirationTtl: RATE_LIMIT_WINDOW_SEC * 2
      });
    }

    let payload;
    try {
      payload = await request.json();
    } catch {
      return jsonResponse({ error: "Invalid JSON body" }, 400);
    }

    const extractedText = String(payload?.extractedText || "").trim().slice(0, 3800);
    const forceFresh = Boolean(payload?.forceFresh);
    if (extractedText.length < 6) {
      return jsonResponse({ error: "Question text too short" }, 400);
    }

    const cacheKey = "ans:" + (await sha256Hex(extractedText));

    // 1. Check Shared KV Edge Cache (unless self-healing forceFresh is requested)
    if (env.ANSWERS_KV && !forceFresh) {
      const cached = await env.ANSWERS_KV.get(cacheKey, { type: "json" });
      if (cached && cached.correctChoice) {
        return jsonResponse(cached, 200, { "X-Cache": "HIT" });
      }
    }

    const systemPrompt = [
      "You are a precise exam and quiz solver.",
      "Carefully read negatives (NOT, EXCEPT, FALSE) and match-the-following pairs.",
      "Reply ONLY with a strict JSON object containing exactly:",
      '{"correctChoice":"<single letter A/B/C/D or concise answer>","shortAnswer":"<exact option text or value>","explanation":"<1-2 concise sentences>"}',
      "No markdown fences, no extra prose."
    ].join(" ");

    // 2. Run 3-Tier Automatic Model Failover
    let lastError = "all_models_failed";
    for (const model of MODEL_CASCADE) {
      try {
        const aiResult = await env.AI.run(model, {
          messages: [
            { role: "system", content: systemPrompt },
            { role: "user", content: extractedText }
          ],
          temperature: forceFresh ? 0.1 : 0.0,
          max_tokens: 140
        });
        const textOut =
          typeof aiResult === "string"
            ? aiResult
            : aiResult?.response || aiResult?.choices?.[0]?.message?.content || "";
        const parsed = extractJsonObject(textOut);
        if (parsed) {
          // Persist (or self-heal overwrite when forceFresh === true) in KV
          if (env.ANSWERS_KV) {
            await env.ANSWERS_KV.put(cacheKey, JSON.stringify(parsed), {
              expirationTtl: CACHE_TTL_SECONDS
            });
          }
          return jsonResponse(parsed, 200, {
            "X-Cache": forceFresh ? "REFRESHED" : "MISS",
            "X-Model": model
          });
        }
      } catch (err) {
        lastError = String(err?.message || err);
        if (lastError.toLowerCase().includes("quota") || lastError.toLowerCase().includes("neuron")) {
          return jsonResponse({ error: "Workers AI quota reached" }, 429);
        }
      }
    }

    return jsonResponse({ error: lastError }, 502);
  }
};
```

### Step 1.5 — Deploy & Verify with `curl`

```bash
wrangler deploy

# 1. Health check
curl https://mcq-mate.nomaankhannfs2.workers.dev

# 2. First solve (should return X-Cache: MISS)
curl -i -X POST https://mcq-mate.nomaankhannfs2.workers.dev \
  -H "Content-Type: application/json" \
  -d '{"extractedText":"Which planet is known as the Red Planet? A) Venus B) Mars C) Jupiter D) Saturn"}'

# 3. Repeat solve (should return X-Cache: HIT in <40ms, 0 Neurons!)
curl -i -X POST https://mcq-mate.nomaankhannfs2.workers.dev \
  -H "Content-Type: application/json" \
  -d '{"extractedText":"Which planet is known as the Red Planet? A) Venus B) Mars C) Jupiter D) Saturn"}'

# 4. Self-healing forceFresh test (should return X-Cache: REFRESHED)
curl -i -X POST https://mcq-mate.nomaankhannfs2.workers.dev \
  -H "Content-Type: application/json" \
  -d '{"extractedText":"Which planet is known as the Red Planet? A) Venus B) Mars C) Jupiter D) Saturn","forceFresh":true}'
```

### Step 1.6 — Cloudflare Dashboard WAF Protection (Optional, Recommended)
1. Go to **Cloudflare Dashboard → Security → WAF → Rate limiting rules**.
2. Add rule: If `URI Path` starts with `/` on `mcq-mate.nomaankhannfs2.workers.dev` exceeds **60 requests / minute per IP**, action = **Block (60s)**.

---

## 2. Release Keystore & SHA-1 / SHA-256 Fingerprints (Required for Google Sign-In & Firebase)

Google Sign-In (`CredentialManager`) and Firebase App Check will fail with **Developer Error (10)** or **403** in Play Store release builds unless **BOTH** your Upload Key SHA-1/SHA-256 and your Google Play App Signing SHA-1/SHA-256 are registered in Firebase!

### Step 2.1 — Generate Your Release Upload Keystore (Only once — KEEP SAFE!)

```bash
keytool -genkey -v \
  -keystore my-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

### Step 2.2 — Print Upload Key SHA-1 & SHA-256

```bash
keytool -list -v -keystore my-upload-key.jks -alias upload
```

Copy both the **SHA1** and **SHA256** lines.

### Step 2.3 — Register Fingerprints in Firebase Console
1. Open **Firebase Console → Project Settings → Your Apps → Android (`com.aistudio.ocrmcqsolver.vrsbsx`)**.
2. Click **Add fingerprint** and add:
   - Your **Upload Key SHA-1** and **SHA-256** (from Step 2.2).
   - After creating the app in Play Console, go to **Play Console → Setup → App signing → App signing key certificate**, copy its **SHA-1** and **SHA-256**, and add those to Firebase too!
3. Click **Download `google-services.json`** and replace `app/google-services.json` if OAuth client entries changed.

---

## 3. Firebase Production Hardening (AI Logic, Firestore, App Check, Crashlytics)

### Step 3.1 — Deploy `firestore.rules` to Your Named Database
Run from the project root using Firebase CLI (`npm install -g firebase-tools && firebase login`):

```bash
firebase deploy --only firestore:rules
```

> Confirm in **Firebase Console → Firestore Database → `ai-studio-android-prismocr-23b5f3d4-3cc4-4f3d-beff-cb20ca94899a` → Rules** that `/users/{userId}/solvedQuestions/{questionId}` and `/sharedQuizCache/{questionHash}` rules are active.

### Step 3.2 — Enable Firebase Authentication (Google Provider)
1. **Firebase Console → Authentication → Sign-in method**.
2. Ensure **Google** is **Enabled** and a valid **Support email** is selected.
3. Keep **Anonymous** sign-in **Disabled** (strictly prohibited by your security rules).

### Step 3.3 — Verify Firebase App Check (Release Protection)
Your app uses `RecaptchaAppCheckProviderFactory` in release builds with `recaptcha_site_key` (protected from R8 shrinking).
1. Open **Firebase Console → App Check → Apps → `com.aistudio.ocrmcqsolver.vrsbsx`**.
2. Register **reCAPTCHA Enterprise** (matching `recaptcha_site_key` in `app/src/main/res/values/firebase_applet_config.xml`) AND/OR **Play Integrity** (automatically linked once Play App Signing SHA-256 is added).
3. Under **App Check → APIs**, monitor **Firebase AI Logic** and **Cloud Firestore** metrics first, then click **Enforce** once you see verified tokens from your Play Store closed test build.

### Step 3.4 — Verify Firebase AI Logic (Gemini 3.8 Flash & 3.5 Flash-Lite)
1. Open **Firebase Console → AI Logic (Vertex / Gemini Developer API)**.
2. Confirm **Gemini Developer API** is enabled for the project.
3. Set a **Google Cloud Billing Budget Alert** (e.g., `$5 / $15 / $25`) in **Google Cloud Console → Billing → Budgets & alerts** so unexpected viral traffic never surprises you.

### Step 3.5 — Verify Firebase Crashlytics
1. Open **Firebase Console → Crashlytics**.
2. Crashlytics plugin is already active in `app/build.gradle.kts` (`com.google.firebase.crashlytics`) and automatically uploads ProGuard/R8 mapping files when you build `:app:bundleRelease`.

---

## 4. Google AdMob Monetization & `app-ads.txt`

Your app already switches automatically:
- **Debug builds:** Google Test Banner ID (`ca-app-pub-3940256099942544/6300978111`)
- **Release builds:** Production Banner ID (`ca-app-pub-6628813367564084/4121394511`)

### Step 4.1 — Link App in AdMob & Host `app-ads.txt`
1. Open **Google AdMob → Apps → App settings → Link to Google Play** once your Play Store listing is live.
2. Host a plain text file at `https://<your-developer-website>/app-ads.txt` containing your publisher line from **AdMob → Apps → View all apps → app-ads.txt**:
   ```text
   google.com, pub-6628813367564084, DIRECT, f08c47fec0942fa0
   ```
3. Add that exact developer website URL inside **Google Play Console → Store settings → Website**.
4. *(Optional)* If you later enable Interstitial or App Open ads, replace `TEST_INTERSTITIAL_ID` / `TEST_APP_OPEN_ID` in `app/src/main/java/com/omnisolve/overlay/ads/AdConfig.kt` with your live AdMob unit IDs.

---

## 5. Optional Backup API Keys (Method 3 — Local Multi-Provider Failover)

> **Important:** Gemini (`gemini-3.8-flash` and `gemini-3.5-flash-lite`) runs through **Firebase AI Logic** and requires **NO `GEMINI_API_KEY`**.

If you want to bundle optional fallback keys for **Method 3** (NVIDIA NIM Free, OpenRouter, OpenAI), add them in the **AI Studio Secrets panel** (do **not** commit real keys to Git or `local.properties`):

| Secret Name | Provider | Where to Create Free Key |
| --- | --- | --- |
| `NVIDIA_API_KEY` | NVIDIA NIM | https://build.nvidia.com/ |
| `OPENROUTER_API_KEY` | OpenRouter | https://openrouter.ai/keys |
| `OPENAI_API_KEY` | OpenAI (optional) | https://platform.openai.com/api-keys |

*(Users can also enter their own keys locally inside the app's **Settings → AI Engines** screen — stored with Android Keystore encryption.)*

---

## 6. Build Release `.aab` & Google Play Console Launch Checklist

### Step 6.1 — Build Signed Release Android App Bundle (`.aab`)

Run locally with your upload keystore credentials:

```bash
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="YOUR_STORE_PASSWORD"
export KEY_PASSWORD="YOUR_KEY_PASSWORD"

gradle :app:bundleRelease
```

Your Play Store bundle is generated at:
`app/build/outputs/bundle/release/app-release.aab`

*(Also verify unit/Robolectric tests pass before every release: `gradle :app:testDebugUnitTest`)*

### Step 6.2 — Mandatory Google Play Console Declarations
1. **App Content → Foreground Service Permissions (`FOREGROUND_SERVICE_MEDIA_PROJECTION`)**:
   - Declare why the app needs MediaProjection: *"User-initiated on-demand screen capture triggered only when the user taps the floating Dynamic Island pill to OCR and solve on-screen study questions."*
   - Upload a **20–30 second unlisted YouTube screen-recording video** showing:
     1. Opening the app & tapping **Launch Dynamic Island**.
     2. Accepting Android's system **Screen Capture consent dialog**.
     3. Switching to a quiz/math question, tapping the floating pill, and viewing the solved answer.
     4. Stopping the Dynamic Island cleanly from the inline Stop icon or Settings.
2. **App Content → Data Safety**:
   - **Account info (Email / User ID):** Collected only if user signs in with Google (for cloud history sync); encrypted in transit; users can delete their account/data inside **Settings → Account**.
   - **App Activity / Crash Logs:** Crashlytics diagnostics (redacted — no questions, screenshots, or API keys are ever logged).
   - **Advertising ID:** Collected by Google Mobile Ads SDK (AdMob).
3. **App Content → Ads**: Select **"Yes, my app contains ads"**.
4. **Permissions Check (Already Play-Policy Compliant in Code)**:
   - Uses Android **Photo Picker** (`PickVisualMedia`) — **no** `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`.
   - Uses standard Settings intents for Battery Unrestricted guidance — **no** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
5. **In-App Updates Testing**:
   - Upload `versionCode = 6` to **Internal Testing**, install it from Play Store, then upload `versionCode = 7` to confirm the **Settings → Check for Play Updates** banner prompts users cleanly.

---

## 7. Final Go-Live Checklist

- [ ] Cloudflare Worker deployed (`wrangler deploy`) with `ANSWERS_KV` bound & `forceFresh: true` verified via `curl`
- [ ] Upload Key + Google Play App Signing SHA-1 & SHA-256 added in Firebase Console
- [ ] `firestore.rules` deployed to `ai-studio-android-prismocr-23b5f3d4-3cc4-4f3d-beff-cb20ca94899a`
- [ ] Firebase Google Sign-In enabled + App Check verified + GCP Billing Alert configured
- [ ] AdMob `app-ads.txt` published on your developer website
- [ ] Signed `app-release.aab` uploaded to Play Console with `mediaProjection` review demo video & Data Safety form completed
