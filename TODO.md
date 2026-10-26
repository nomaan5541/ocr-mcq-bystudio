# 🚀 Project Roadmap & Graph Memory TODO List

This document outlines active development goals, planned architecture improvements, and feature roadmap items for the **OCR MCQ Solver & Smart Vision Stealth Suite**.

---

## 📌 Active Development Priorities (High Priority)

### 📲 1. QR Code Direct Launch into Native OmniSolve Android App
- **Goal:** Enable the QR code displayed in the Chrome Extension Options dashboard (`Tab 3: Mobile Sync`) to directly launch and pair the native installed **OmniSolve Android Application** (`com.omnisolve.overlay`), continuing the exact same seamless solve and vibration path natively without requiring a mobile browser.
- **Why this is critical:**
  - **Background Pocket Vibrations:** Mobile browsers (Chrome/Safari) throttle or disable the Web Vibration API when the screen locks or when the tab is backgrounded. A native Android service (`FOREGROUND_SERVICE` + `Vibrator` / `VibrationEffect.Composition`) can vibrate silently in the user's pocket even with the screen fully turned off.
  - **Zero-Latency WebSocket Bridge:** The Android app connects directly via a persistent WebSocket (`ws://<LAN_IP>:59124/ws/client`), receiving answer packets in under 15ms.
  - **Dual Display Modes:** User can choose between:
    1. **Pocket Haptics Mode:** Screen black/off, silent discrete vibration pulses.
    2. **Dynamic Island Mode:** Floating Apple-inspired pill widget overlay displaying answer and confidence.
- **Architecture & Implementation Steps:**
  - [x] **Deep Link Intent Filter in AndroidManifest:** Registered `omnisolve://sync` scheme in [app/src/main/AndroidManifest.xml](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/app/src/main/AndroidManifest.xml).
  - [x] **Intent Receiver in MainActivity:** Parse `intent.data` inside `MainActivity.kt` in `onCreate()` and `onNewIntent()` to extract companion server host IP and SSE/WebSocket port.
  - [x] **Background Sync Service:** Forward connection parameters to `OverlayService.kt` with live SSE `/events` streaming for pocket haptic pulses.
  - [x] **Extension Options Selector:** Toggle in [chrome-extension/options.html](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/chrome-extension/options.html) allowing instant switching between Web App QR Code and Native OmniSolve App Deep Link QR Code.

### 🛡️ 2. Stealth Privacy & OnePlus/Android 14 Clipboard Safeguards (Completed)
- **Problem:** On OxygenOS 14/15 (OnePlus Nord 5) and Android 13+, Android displays a floating clipboard chip/toast on screen whenever text is copied to clipboard. In older versions, prompt text was copied unconditionally on every solve, exposing prompt text on the display. Furthermore, Native Mode fell back to OCR whenever the OmniSolve overlay was touched, causing both engines to perform identical screenshot captures.
- **Fix Implemented:**
  - **Gated Clipboard Operations:** Created `sw_auto_copy_clipboard` (default OFF in [activity_main.xml](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/app/src/main/res/layout/activity_main.xml)). Gemini Web injection uses direct JavaScript string injection, eliminating clipboard touch and preventing the OnePlus Nord 5 clipboard toast popup.
  - **Window-Aware Native Scraping:** Updated `AutoClickAccessibilityService.kt` to traverse `windows` across all `TYPE_APPLICATION` windows instead of relying on `rootInActiveWindow`. Added `flagRetrieveInteractiveWindows|flagDefault|flagIncludeNotImportantViews|flagReportViewIds` in [accessibility_service_config.xml](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/app/src/main/res/xml/accessibility_service_config.xml).
  - **OCR Fallback Preference Switch:** Added `sw_native_ocr_fallback` (preference `PREF_KEY_NATIVE_FALLBACK_OCR`) so users who select Native extraction can enforce pure accessibility node reading without screenshot captures.

---

## 🍏 2. iOS Native Companion & Safari Web Extension (Completed in v2.0)
- **Status:** Complete & Architecture Ready! 🚀
- **Target Platform:** iOS 16.0+ (iPhone, iPad).
- **Architecture & Implementation:**
  - [x] **CoreHaptics Ultra-Crisp Tactile Feedback:** Custom haptic waveforms mimicking exact taps (1 tap for A, 2 for B, 3 for C, 4 for D) built in [ios/OmniSolve/CoreHapticManager.swift](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/ios/OmniSolve/CoreHapticManager.swift).
  - [x] **Background SSE Sync Client:** Real-time stream consumer with `AVAudioSession` background execution keeping connection alive in locked pocket in [ios/OmniSolve/CompanionSyncClient.swift](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/ios/OmniSolve/CompanionSyncClient.swift).
  - [x] **Pitch-Black OLED Stealth View:** Tap-and-hold stealth answer peek with zero light bleed and double-tap setup card in [ios/OmniSolve/StealthView.swift](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/ios/OmniSolve/StealthView.swift).
  - [x] **Deep-Link Pairing:** `omnisolve-ios://sync?host=...&port=...` URL scheme registered in [ios/OmniSolve/Info.plist](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/ios/OmniSolve/Info.plist) and handled in [ios/OmniSolve/OmniSolveApp.swift](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/ios/OmniSolve/OmniSolveApp.swift).
  - [x] **Extension Options 3-Way Selector:** Web App, Android Native, and iOS Native switches with dynamic QR code rendering in [chrome-extension/options.html](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/chrome-extension/options.html) and [chrome-extension/options.js](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/chrome-extension/options.js).
  - [x] **Universal iOS Web Audio Haptic Fallback:** Added Web Audio API sub-bass synthesized tactile driver to companion `/mobile` web app for iPhone Safari where `navigator.vibrate` is blocked in [companion/headless_gemini_server.py](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/companion/headless_gemini_server.py).
  - [x] **XcodeGen Project Specification:** [ios/project.yml](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/ios/project.yml) for 1-command `.xcodeproj` generation and CI builds.
  - [x] **Safari Web Extension Container:** Manifest v3 compliant extension structure in [safari-extension/manifest.json](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/safari-extension/manifest.json) and conversion script [safari-extension/convert_to_safari.sh](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/safari-extension/convert_to_safari.sh).
  - [x] **Windows Emulation & Development Guide:** Complete step-by-step documentation on using Appetize.io, BrowserStack, VMware macOS VM, and GitHub Actions in [ios/README.md](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/ios/README.md).

---

## 🎨 3. Stealth Suite Enhancements (Completed in v1.9)
- [x] **Innocent Hover Camouflage Tooltip:**
  - Fixed toolbar mouseover tooltip that previously leaked "Smart Vision (Click to solve MCQ)".
  - Replaced with customizable, innocent disguises (e.g., `"Google Translate - Web Page Translation"`, `"Volume Master - Sound Equalizer"`, `"Page Magnifier & Display Tools"`, etc.).
  - Added One-Click Auto-Match and quick preset pills.
- [x] **Coded Symbol Icons System:**
  - Replaced letter badges with innocent everyday symbol icons:
    - **Choice A:** ✈️ Airplane, 🍎 Apple, ⚓ Anchor, 🔺 Triangle
    - **Choice B:** 🎈 Balloon, ⚽ Ball, 📖 Book, 🟦 Square
    - **Choice C:** ☁️ Cloud, ⏰ Clock, ☕ Coffee Cup, 🟢 Circle
    - **Choice D:** 💎 Diamond, 📄 Document, 🎲 Dice, ⭐ Star
  - Full customization dropdowns with live image previews in the Options UI.
  - One-click Theme Presets (Alphabet, Tools, Shapes, Everyday).
  - Active Tab Test buttons.
- [x] **Full 64-Icon Asset Matrix:** Generated 16 symbol sets across 16x16, 32x32, 48x48, and 128x128 in [chrome-extension/icons/](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/chrome-extension/icons/).

---

## 💾 4. Packaging & Backup Archives
- Latest versioned archive: [backups/mcq_bypass_v2.0_ios_and_glass_ui.zip](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/backups/mcq_bypass_v2.0_ios_and_glass_ui.zip)
- Rolling latest release: [backups/mcq_bypass_latest_release.zip](file:///c:/Users/VIRUS/Downloads/mcq%20bypass/backups/mcq_bypass_latest_release.zip)
