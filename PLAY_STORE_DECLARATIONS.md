# Google Play Console Submission Package
## prism-ocr-solver

This document contains the exact declarations, text copy, and answers to copy-paste directly into your **Google Play Console** (`play.google.com/console`) for **Sub-Topics 3.1, 3.2, 3.3, and 3.4**.

---

### Sub-Topic 3.0: App Content & Ads Declaration
* **Does your app contain ads?**  
  * Answer: **No** (Select *"No, my app does not contain ads"* in Google Play Console under **Policy and programs → App content → Ads**).
* **Advertising ID (`AD_ID`) Declaration:**  
  * Answer: **No** (`com.google.android.gms.permission.AD_ID` is not declared, and zero advertising SDKs are bundled).

---

### Sub-Topic 3.1: Content Rating & Target Audience

* **Target Age Group Selection:**  
  * Check: **18 and over** (or **13 to 17**).
  * **DO NOT** select any age under 13 (Children). Selecting under 13 triggers Google Play Families Policy (COPPA) which restricts foreground screen projection and AI analysis.
* **Appeal to Children Question:**  
  * *"Could your store listing unintentionally appeal to children?"*  
  * Answer: **No**.
* **Content Rating Questionnaire:**  
  * Category: **Utility, Productivity, Communication or Other**.
  * Violence, sexual content, offensive language, controlled substances: **No**.
  * Does the app natively share the user's current physical location? **No**.
  * Does the app allow users to purchase digital goods? **No**.

---

### Sub-Topic 3.2: Media Projection Permission Declaration (`FOREGROUND_SERVICE_MEDIA_PROJECTION`)

When the Play Console prompts for your justification for the `FOREGROUND_SERVICE_MEDIA_PROJECTION` foreground service type:

#### 1. Primary User-Facing Purpose:
Select: **Study / Education / Screen Capture Utility**

#### 2. Exact Justification Text (Copy & Paste):
```
Prism Solver is an on-device assistive study tool designed for students and learners reviewing digital quizzes, practice exams, and PDF lecture notes. 

The FOREGROUND_SERVICE_MEDIA_PROJECTION permission is strictly user-initiated. When the user taps the floating Dynamic Island or Bubble HUD on their screen, the service takes a single snapshot of the visible question and multiple-choice options. The bitmap is processed in volatile device RAM via on-device ML Kit OCR to recognize text, and the image is immediately discarded. 

Screen capture never runs continuously in the background and is only activated by direct user interaction on the floating pill. The app does not save, record, or transmit user screen captures to any external servers or disks.
```

---

### Sub-Topic 3.3: Accessibility Service
* **Not Used:** Prism Solver does **not** declare or use `BIND_ACCESSIBILITY_SERVICE`.

---

### Sub-Topic 3.4: Data Safety Form (Exact Responses)

Google Play's Data Safety questionnaire requires answers about user data collection and sharing:

| Question | Answer | Details / Justification |
| :--- | :--- | :--- |
| **Does your app collect or share any of the required user data types?** | **Yes** | Optional Google Sign-In account info (name, email, user ID) for synced study history and Live Class Rooms, plus Crashlytics diagnostics. Screen captures remain 100% ephemeral in RAM. |
| **Is all user data collected by your app encrypted in transit?** | **Yes** | All network communications (Firebase AI Logic, Firestore, Auth, Crashlytics) use HTTPS / TLS 1.3. |
| **Do you provide a way for users to request that their data is deleted?** | **Yes** | Users can clear saved history, wipe local keys, or permanently delete their account directly inside in-app Settings; local history auto-clears after 7 days and Live Class Rooms expire after 24 hours. |
| **Location Data:** | **No** | The app does not request or collect GPS or location. |
| **Personal Info (Name, Email, User IDs):** | **Yes (Optional / Account Management)** | Collected via Google Sign-In (Firebase Auth) for cloud history sync, fair-use quotas, and Live Class Room display name/avatar. Never sold or shared with third-party advertisers. |
| **Financial Info:** | **No** | Zero financial or payment collection. |
| **Photos & Videos:** | **No (Ephemeral Only)** | Screen capture bitmaps and user-picked photos are processed ephemerally in memory to answer questions and are never stored permanently. |
| **Files & Documents:** | **No (Ephemeral Only)** | User-selected PDFs (via system picker) are processed ephemerally and discarded. |
| **Audio Files:** | **No** | No audio recording. |
| **App Info & Performance (Crash Logs):** | **Yes (App Functionality)** | Firebase Crashlytics collects anonymous crash logs to improve stability. |
| **Device or other IDs (Advertising ID):** | **No** | Zero advertising SDKs and zero Advertising ID (`AD_ID`) collection. |

---

### Store Listing Copy (Ready to Paste)

#### App Title:
```
prism-ocr-solver
```

#### Short Description (68 / 80 chars):
```
Floating AI study assistant with on-screen OCR and multi-model solver.
```

#### Full Description:
```
prism-ocr-solver is a modern, high-precision floating study companion designed to help learners and students navigate quizzes, practice exams, complex diagrams, and multi-step math problems with zero friction.

Docked unobtrusively at the top of your screen as a sleek Dynamic Island or draggable Mini Bubble, prism-ocr-solver lets you analyze questions across PDFs, educational browsers, and study apps with a single tap.

KEY FEATURES:

• 1-TAP FLOATING DYNAMIC ISLAND
Docked near your status bar with customizable 20% to 100% opacity. Tap anywhere on the pill body to instantly scan and solve the visible question.

• 2-VIEW SPLIT CAPTURE (1st + 2nd)
Easily solve long exam questions where diagrams or circuits are at the top and multiple-choice options are below the fold. Tap "1st" on the diagram, scroll down to options, tap "2nd" — prism-ocr-solver stitches both views and analyzes the full context.

• 2-SECOND SCROLL-CAPTURE TIMER (2s↓)
One-tap automated capture: snapshots View 1 immediately, displays a live 2-second countdown while you scroll down to options, and automatically snapshots View 2 without requiring a second tap.

• MATCH-THE-FOLLOWING & MATH+ MODES
Dedicated reasoning modes for Column I/II pairing (A-2, B-4, C-1, D-3) and complex multi-step math equations.

• SWIPE-DOWN LOOPING METHOD SWITCHER
Swipe down on the floating Dynamic Island to reveal an infinite loop of 3 solving engines: Server Sol, On-Device OCR Text Extractor, and Local API Keys.

• LOCAL MULTI-PROVIDER AI KEYS & AUTO-FAILOVER
Supports your own NVIDIA NIM, OpenRouter, OpenAI, and Google Gemini API keys. All keys are encrypted and stored strictly in private on-device storage. If any provider experiences rate limits, prism-ocr-solver automatically fails over to the next configured provider in milliseconds.

• ZERO-TRACKING PRIVACY GUARANTEE
Screen captures are processed strictly in volatile device memory (RAM) via Google ML Kit on-device text recognition and immediately discarded. No screen recordings, personal data, or images are ever stored on remote servers.

Official Website & Privacy Policy:
https://nomaan5541.github.io/prism-privacy-policy/
Contact: nomaankhannfs2@gmail.com
```
