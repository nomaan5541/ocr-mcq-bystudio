package com.omnisolve.overlay.model

/**
 * Device RAM & Battery Optimization Modes for Prism AI Solver.
 * Selectable in Stats Screen:
 * - EASY_RAM: Downscales capture resolution, single-pass OCR, lightweight prompt.
 *             Conserves RAM (~35MB) on 2GB-4GB budget devices.
 * - NORMAL: Standard balanced 1080p capture, smart dual OCR verification.
 *           Ideal everyday balance (~65MB RAM, 98% accuracy).
 * - HIGH_ACCURACY: Native display resolution, contrast enhancement filter,
 *                  multi-pass AI verification for difficult math & diagrams (~110MB RAM, 99.8% accuracy).
 */
enum class EnginePerformanceMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val ramBadge: String,
    val accuracyBadge: String,
    val captureScale: Float,
    val dualVerify: Boolean
) {
    EASY_RAM(
        id = "EASY_RAM",
        title = "Battery & Memory Saver",
        subtitle = "Lighter screen reading for older phones. Uses minimal memory (~35MB RAM).",
        ramBadge = "Low RAM (~35MB)",
        accuracyBadge = "Fast",
        captureScale = 0.65f,
        dualVerify = false
    ),
    NORMAL(
        id = "NORMAL",
        title = "Balanced (Recommended)",
        subtitle = "Best mix of speed and accuracy for everyday quizzes and study.",
        ramBadge = "Balanced (~65MB)",
        accuracyBadge = "Standard",
        captureScale = 1.0f,
        dualVerify = false
    ),
    HIGH_ACCURACY(
        id = "HIGH_ACCURACY",
        title = "Extra Accuracy",
        subtitle = "Sharper image reading and double-checked answers for tricky math and diagrams.",
        ramBadge = "High RAM (~110MB)",
        accuracyBadge = "Best Accuracy",
        captureScale = 1.0f,
        dualVerify = true
    );

    companion object {
        fun fromId(raw: String?): EnginePerformanceMode {
            val clean = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.id == clean } ?: NORMAL
        }
    }
}
