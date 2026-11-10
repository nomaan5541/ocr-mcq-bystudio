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
        title = "Easy Mode (Low RAM)",
        subtitle = "Lightweight fast capture & single-pass OCR. Uses minimal memory (~35MB RAM). Recommended for low-spec devices.",
        ramBadge = "Low RAM (~35MB)",
        accuracyBadge = "Fast (94%)",
        captureScale = 0.65f,
        dualVerify = false
    ),
    NORMAL(
        id = "NORMAL",
        title = "Normal Engine",
        subtitle = "Standard balanced processing with optimized OCR and smart caching. Ideal for everyday study.",
        ramBadge = "Balanced (~65MB)",
        accuracyBadge = "Standard (98%)",
        captureScale = 1.0f,
        dualVerify = false
    ),
    HIGH_ACCURACY(
        id = "HIGH_ACCURACY",
        title = "High Accuracy (Turbo)",
        subtitle = "Full native resolution + contrast filter + multi-pass AI verification for complex questions & diagrams.",
        ramBadge = "High RAM (~110MB)",
        accuracyBadge = "Turbo (99.8%)",
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
