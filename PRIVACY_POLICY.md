# Privacy Policy for prism-ocr-solver

**Last Updated:** November 6, 2026  
**Application Name:** prism-ocr-solver  
**Website:** [https://virusboss.in/app/prism-ocr-solver/#privacy-policy](https://virusboss.in/app/prism-ocr-solver/#privacy-policy)  
**Developer Contact:** nomaankhannfs2@gmail.com  

---

## 1. Overview
**prism-ocr-solver** is an on-device floating assistive study and quiz analysis tool. We are committed to transparency, data minimization, and user privacy. 

This Privacy Policy explains how prism-ocr-solver handles your data, permissions, and device interactions.

---

## 2. Information We Process & How It Is Handled

### A. Screen Captures (Media Projection)
* **What it does:** When you tap the floating Dynamic Island or Bubble to solve a quiz question on your screen, the app captures a temporary frame of your screen to extract text.
* **Storage:** Screen captures and stitched question images are processed strictly in volatile device memory (RAM).
* **Retention:** Bitmaps are discarded immediately after optical text recognition (OCR) or model inference completes. **We do not save, record, or store your screen captures on remote servers or disks.**

### B. On-Device Optical Character Recognition (OCR)
* We utilize Google ML Kit on-device Text Recognition (`com.google.mlkit:text-recognition`).
* Text extraction runs locally on your Android device without routing screen images through external analytics or ad trackers.

### C. Artificial Intelligence Inference
Depending on your selected answer method:
1. **Method 1 (Server Sol - Firebase AI Logic / Gemini):**
   * Extracted text is securely transmitted over TLS 1.3 to Firebase AI Logic backend using Google Cloud Gemini models to receive the single-line solved answer. No screen recordings or personal identifiers are sold or shared.
2. **Method 2 (OCR Text Extractor - Background Gemini):**
   * Uses on-device OCR followed by background Gemini inference to return the verified answer letter.
3. **Method 3 (Local API Keys - NVIDIA, OpenRouter, Google AI):**
   * If you choose to input your own API keys (e.g., NVIDIA NIM, OpenRouter, OpenAI, or Google AI), requests are made directly from your device to the respective provider's official REST endpoint over HTTPS.
   * **Your API keys are stored strictly on your local device** using Android `SharedPreferences`. Keys are never uploaded to any intermediary server or shared with third parties.

---

## 3. Android Permissions & Why They Are Needed

| Permission | Technical Name | Purpose |
| :--- | :--- | :--- |
| **Draw Over Other Apps** | `SYSTEM_ALERT_WINDOW` | Renders the unobtrusive floating Dynamic Island / Bubble HUD over your study materials and PDFs. |
| **Screen Capture** | `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Allows one-tap capture of the visible question and options upon your direct physical tap. |
| **Foreground Service** | `FOREGROUND_SERVICE` | Keeps the floating overlay active while switching between study apps and PDF readers. |
| **Internet Access** | `INTERNET` | Facilitates secure HTTPS communication with AI model providers for question analysis. |
| **Vibration** | `VIBRATE` | Optional haptic pulses (e.g. 1 buzz for A, 2 buzzes for B) for non-visual answer feedback. |
| **Accessibility Service** | `BIND_ACCESSIBILITY_SERVICE` | *Optional Assistive Feature:* Allows optional auto-tapping of the solved choice on screen only when explicitly enabled by you. Does not collect keystrokes or passwords. |

---

## 4. Data Sharing & Third-Party Services
* **No Resale of Personal Data:** We never sell, rent, monetize, or broker user data.
* **No Third-Party Ad Networks:** prism-ocr-solver contains zero advertising SDKs.
* **Third-Party AI Providers:** When you use Method 1, 2, or 3, queries are processed subject to Google Cloud / OpenAI / NVIDIA / OpenRouter standard data privacy agreements.

---

## 5. Security & Academic Integrity Disclaimer
* All network communication is enforced via HTTPS / TLS.
* **Educational & Study Use Only:** prism-ocr-solver is designed strictly as a personal study aid, revision companion, and educational analysis tool. Users are solely responsible for adhering to their academic institutions' codes of conduct and examination policies.

---

## 6. Your Rights & Data Deletion
* Because prism-ocr-solver does not store personal profiles, analytics, or screen captures on remote servers, your locally saved solved history and API keys can be cleared at any time directly in **Settings → Clear Local History** or by uninstalling the application.

---

## 7. Contact Us
If you have any questions or feedback regarding this Privacy Policy or your privacy rights, please contact:
* **Email:** nomaankhannfs2@gmail.com
* **Website:** [https://virusboss.in/app/prism-ocr-solver/#privacy-policy](https://virusboss.in/app/prism-ocr-solver/#privacy-policy)
