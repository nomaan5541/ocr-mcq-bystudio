# Google Play Console Submission Package
## prism-ocr-solver

This document contains the exact declarations, text copy, and answers to copy-paste directly into your **Google Play Console** (`play.google.com/console`) for **Sub-Topics 3.1, 3.2, 3.3, and 3.4**.

---

### Sub-Topic 3.0: App Content & Ads Declaration
* **Does your app contain ads?**  
  * Answer: **Yes** (Select *"Yes, my app contains ads"* in Google Play Console under **Policy and programs → App content → Ads**).
* **Ad Placement Policy Compliance:**  
  * Ads are strictly limited to the main app interface (`MainActivity`). No ads are shown in floating system overlay windows or during screen captures.

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
prism-ocr-solver is an on-device assistive study tool designed for students and learners reviewing digital quizzes, practice exams, and PDF lecture notes. 

The FOREGROUND_SERVICE_MEDIA_PROJECTION permission is strictly user-initiated. When the user taps the floating Dynamic Island or Bubble HUD on their screen, the service takes a single snapshot of the visible question and multiple-choice options. The bitmap is processed in volatile device RAM via on-device ML Kit OCR to recognize text, and the image is immediately discarded. 

Screen capture never runs continuously in the background and is only activated by direct user interaction on the floating pill. The app does not save, record, or transmit user screen captures to any external servers or disks.
```

---

### Sub-Topic 3.3: Accessibility Service Declaration (`BIND_ACCESSIBILITY_SERVICE`)

Google Play requires a short demonstration video and an in-app disclosure for apps using `AccessibilityService`.

#### 1. In-App Disclosure (Already Built-in):
* Declared in `AndroidManifest.xml` with description:  
  * `"prism-ocr-solver: Assistive study tool for reading on-screen quiz questions and tapping solved options when enabled."`
* Configured with optional toggles in **Settings → Auto-Tap Solved Option**.

#### 2. Demonstration Video Script (15–30 Seconds for YouTube Unlisted Link):
Upload a short screen recording to YouTube as **Unlisted** and paste the link into the Play Console:
* **0:00 – 0:08**: Open prism-ocr-solver and navigate to **Settings → Floating Overlay Appearance & Controls**.
* **0:08 – 0:16**: Show the toggle for **"Auto-Tap Solved Option"**. When turned on, show the system dialog directing to Accessibility Settings.
* **0:16 – 0:25**: Open a sample multiple-choice quiz page. Tap the floating Dynamic Island. The solver identifies Option B and the accessibility gesture automatically taps choice B on screen.
* **Video Description in YouTube:**
  ```
  Demonstration of prism-ocr-solver Accessibility Service (BIND_ACCESSIBILITY_SERVICE).
  Used strictly as an optional assistive tool to tap the user-solved answer option on screen when enabled by the user in Settings. No personal data, keystrokes, or screen contents are collected or stored.
  ```

---

### Sub-Topic 3.4: Data Safety Form (Exact Responses)

Google Play's Data Safety questionnaire requires answers about user data collection and sharing:

| Question | Answer | Details / Justification |
| :--- | :--- | :--- |
| **Does your app collect or share any of the required user data types?** | **Yes** | Standard Google Mobile Ads SDK (AdMob) advertising identifiers; screen captures and OCR remain 100% ephemeral in RAM. |
| **Is all user data collected by your app encrypted in transit?** | **Yes** | All network communications (AI model endpoints and AdMob) use HTTPS / TLS 1.3. |
| **Do you provide a way for users to request that their data is deleted?** | **Yes** | All local test history and API keys can be purged instantly via in-app Settings ("Clear Local History") or by uninstalling; Ad ID can be deleted in Android Settings. |
| **Location Data:** | **No** | The app does not request or collect GPS or location. |
| **Personal Info (Name, Email, User IDs):** | **Optional / No** | Only standard Firebase Authentication if the user chooses Google Sign-In; no personal data is sold or shared. |
| **Financial Info:** | **No** | Zero financial or payment collection. |
| **Photos & Videos:** | **No** | Screen capture bitmaps are temporary RAM buffers discarded post-OCR; zero disk storage. |
| **Files & Documents:** | **No** | The app does not read local user storage. |
| **Audio Files:** | **No** | No audio recording. |
| **App Activity / Analytics:** | **Optional (AdMob)** | Google AdMob processes ad impressions for fraud prevention and performance. |
| **Device or other IDs:** | **Yes** | Advertising ID collected by Google Play Services / AdMob for ad serving and fraud detection. Declared as ephemeral, encrypted in transit. |

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
https://virusboss.in/app/prism-ocr-solver/#privacy-policy
Contact: nomaankhannfs2@gmail.com
```
